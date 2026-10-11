package com.maxonaddon;

import com.maxonaddon.network.StartHostileQtePayload;
import com.maxonaddon.network.StopHostileQtePayload;
import com.maxonaddon.network.HitQteKeyPayload;
import daot.VillagerTransformTracker;
import daot.BladeItem;
import com.maxonaddon.util.IPlayerStats; // Убедись, что импорт правильный
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HostileQteHandler {

    public static class EscapeSession {
        public Monster titan;
        public int requiredHits = 5;
        public int currentHits = 0;
        public int maxFails = 3;
        public int failedHits = 0;
    }

    public static final Map<UUID, EscapeSession> activeEscapes = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> qteUsageCount = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> qteLongCooldowns = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> grabImmunityEnds = new ConcurrentHashMap<>();

    // Карта для фиксации слота хотбара
    public static final Map<UUID, Integer> lockedHotbarSlots = new ConcurrentHashMap<>();

    public static final Set<UUID> currentlyGrabbed = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> eligibleForQte = ConcurrentHashMap.newKeySet();

    public static boolean hasGrabImmunity(ServerPlayer player) {
        Long ends = grabImmunityEnds.get(player.getUUID());
        return ends != null && player.serverLevel().getGameTime() < ends;
    }

    public static void giveGrabImmunity(ServerPlayer player) {
        grabImmunityEnds.put(player.getUUID(), player.serverLevel().getGameTime() + 60L);
    }

    // Проверка мечей
    private static boolean hasValidSword(ServerPlayer player) {
        Item item = player.getMainHandItem().getItem();
        return item instanceof BladeItem ||
                item == Items.IRON_SWORD ||
                item == Items.DIAMOND_SWORD ||
                item == Items.NETHERITE_SWORD;
    }

    /**
     * Игрок всё ещё в захвате у ЭТОГО титана?
     * <p>
     * Раньше здесь была только эвристика "тег being_grabbed ИЛИ едет верхом на *Grab-сущности",
     * и для ЧИСТЫХ титанов она разваливалась: тег dannys-aot:being_grabbed выставляется ровно
     * один раз (в TitanEntity.performEat), а внутри TitanEntity.tick() он снимается в ШЕСТИ
     * разных местах при смене фаз поедания (проверено байткодом) — и обратно уже не ставится.
     * Отдельной *TitanGrabEntity у чистых титанов вообще нет (она есть только у шифтеров:
     * Armored/Attack/Beast/Female). Итог: через считанные тики после начала QTE обе проверки
     * давали false, сессия сносилась, игрок получал иммунитет, а PureTitanEatMixin по этому
     * иммунитету вызывал cancelEating() — отсюда "титаны просто моментально отпускают".
     * <p>
     * Теперь главный источник истины — сам титан: ест ли он ИМЕННО этого игрока. Это ровно тот
     * же критерий, по которому PureTitanEatMixin вообще запускает QTE. Старая эвристика осталась
     * запасным вариантом для шифтеров, у которых нет getEatingTarget/isEating.
     */
    private static boolean isStillHeldBy(ServerPlayer player, Monster titan) {
        try {
            Method isEating = titan.getClass().getMethod("isEating");
            Method getEatingTarget = titan.getClass().getMethod("getEatingTarget");
            boolean eating = (boolean) isEating.invoke(titan);
            return eating && getEatingTarget.invoke(titan) == player;
        } catch (Exception ignored) {
            // У этого титана нет "поедающего" API — значит это шифтер, проверяем по-старому.
        }
        return player.getTags().contains("dannys-aot:being_grabbed")
                || (player.getVehicle() != null && player.getVehicle().getClass().getSimpleName().contains("Grab"));
    }

    public static void startEscapeQte(ServerPlayer player, Monster titan) {

        if (!((IPlayerStats) player).hasUngrabingMastery()) return;

        // Проверка правильного оружия в руках
        if (!hasValidSword(player)) return;

        // Сессия уже идёт — молча выходим, НЕ сжигая попытку. Без этого второй титан,
        // вцепившийся в того же игрока (или повторный запуск после ложного сноса сессии),
        // прокручивал счётчик qteUsageCount до лимита 3 за доли секунды и загонял игрока
        // в 2-минутный кулдаун, из-за чего QTE "переставало работать" вовсе.
        if (activeEscapes.containsKey(player.getUUID())) return;

        // На мёртвом/умирающем титане QTE не имеет смысла — он и так отпустит.
        if (titan.isDeadOrDying()) return;

        String owner = VillagerTransformTracker.getOwnerName(titan);
        if (owner != null && owner.equals(player.getGameProfile().getName())) {
            return;
        }

        long currentTick = player.serverLevel().getGameTime();

        if (qteLongCooldowns.containsKey(player.getUUID())) {
            if (currentTick < qteLongCooldowns.get(player.getUUID())) {
                return;
            } else {
                qteLongCooldowns.remove(player.getUUID());
                qteUsageCount.put(player.getUUID(), 0);
            }
        }

        int uses = qteUsageCount.getOrDefault(player.getUUID(), 0) + 1;
        qteUsageCount.put(player.getUUID(), uses);

        if (uses > 3) return;

        EscapeSession session = new EscapeSession();
        session.titan = titan;
        activeEscapes.put(player.getUUID(), session);

        ServerPlayNetworking.send(player, new StartHostileQtePayload());

        if (uses == 3) {
            qteLongCooldowns.put(player.getUUID(), currentTick + 2400L);
            player.displayClientMessage(Component.translatable("message.maxonaddon.qte.last_chance").withStyle(ChatFormatting.YELLOW), true);
        } else {
            player.displayClientMessage(Component.translatable("message.maxonaddon.qte.used", uses).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static void processHit(ServerPlayer player, boolean isSuccess) {
        EscapeSession session = activeEscapes.get(player.getUUID());
        if (session != null) {
            if (isSuccess) {
                session.currentHits++;

                // === ЭФФЕКТЫ УДАРА МЕЧА И КРОВИ ===
                ServerLevel serverLevel = (ServerLevel) player.level();

                // 1. Блок-партиклы (видимые для всех снаружи)
                net.minecraft.core.particles.BlockParticleOption bloodParticle =
                        new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
                serverLevel.sendParticles(bloodParticle, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.4, 0.4, 0.1);

                // 2. Спецэффект мода (брызги на экран).
                daot.network.EffectPayload bloodPayload = new daot.network.EffectPayload("blood", player.getX(), player.getY() + 1.0, player.getZ(), 1.0F);

                // ОТПРАВЛЯЕМ САМОМУ ИГРОКУ (ОБЯЗАТЕЛЬНО, ЧТОБЫ У НЕГО БЫЛ ЭФФЕКТ)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, bloodPayload);

                // Отправляем остальным, кто смотрит на это место
                for (ServerPlayer trackingPlayer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(serverLevel, player.blockPosition())) {
                    if (!trackingPlayer.getUUID().equals(player.getUUID())) { // Исключаем дублирование
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(trackingPlayer, bloodPayload);
                    }
                }

                // 3. Звук слэша
                net.minecraft.sounds.SoundEvent[] slashSounds = new net.minecraft.sounds.SoundEvent[]{daot.ModSounds.SLASH1, daot.ModSounds.SLASH2, daot.ModSounds.SLASH3};
                net.minecraft.sounds.SoundEvent slash = slashSounds[player.getRandom().nextInt(3)];
                float pitch = 0.9F + player.getRandom().nextFloat() * 0.1F;
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), slash, net.minecraft.sounds.SoundSource.PLAYERS, 8.0F, pitch);
                // ===================================

                if (session.currentHits >= session.requiredHits) {
                    activeEscapes.remove(player.getUUID());
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
                    player.displayClientMessage(Component.translatable("message.maxonaddon.qte.escaped").withStyle(ChatFormatting.GREEN), true);
                    player.stopRiding();
                    try {
                        Method triggerEyeHurt = session.titan.getClass().getMethod("triggerEyeHurt");
                        triggerEyeHurt.invoke(session.titan);
                    } catch (Exception e) {
                        session.titan.hurt(session.titan.damageSources().generic(), 15.0f);
                    }
                    giveGrabImmunity(player);
                    ServerPlayNetworking.send(player, new StopHostileQtePayload());
                }
            } else {
                session.failedHits++;
                if (session.failedHits >= session.maxFails) {
                    activeEscapes.remove(player.getUUID());
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.maxonaddon.qte.failed").withStyle(ChatFormatting.DARK_RED)));
                    ServerPlayNetworking.send(player, new StopHostileQtePayload());
                }
            }
        }
    }
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                UUID uuid = oldPlayer.getUUID();
                qteUsageCount.remove(uuid);
                qteLongCooldowns.remove(uuid);
                activeEscapes.remove(uuid);
                currentlyGrabbed.remove(uuid);
                eligibleForQte.remove(uuid);
                grabImmunityEnds.remove(uuid);
                lockedHotbarSlots.remove(uuid);
            }
        });

        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayer player) {
                UUID uuid = player.getUUID();
                qteUsageCount.remove(uuid);
                qteLongCooldowns.remove(uuid);
                grabImmunityEnds.remove(uuid);

                player.displayClientMessage(
                        Component.translatable("message.maxonaddon.qte.slept_reset").withStyle(ChatFormatting.GREEN),
                        true
                );
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Игрок получает этот тег при захвате
                boolean hasGrabTag = player.getTags().contains("dannys-aot:being_grabbed");
                boolean isRidingGrab = player.getVehicle() != null && player.getVehicle().getClass().getSimpleName().contains("Grab");
                boolean isGrabbed = hasGrabTag || isRidingGrab;
                boolean wasGrabbed = currentlyGrabbed.contains(player.getUUID());

                if (isGrabbed) {
                    if (!wasGrabbed) {
                        currentlyGrabbed.add(player.getUUID());
                        eligibleForQte.add(player.getUUID());
                        // БЛОКИРОВКА: Запоминаем слот при захвате
                        lockedHotbarSlots.put(player.getUUID(), player.getInventory().selected);
                    } else {
                        // БЛОКИРОВКА: Если слот изменился, принудительно возвращаем
                        Integer lockedSlot = lockedHotbarSlots.get(player.getUUID());
                        if (lockedSlot != null && player.getInventory().selected != lockedSlot) {
                            player.getInventory().selected = lockedSlot;
                            // Отправляем пакет клиенту, чтобы визуал хотбара тоже вернулся на место
                            player.connection.send(new ClientboundSetCarriedItemPacket(lockedSlot));
                        }
                    }
                } else if (!isGrabbed && wasGrabbed) {
                    currentlyGrabbed.remove(player.getUUID());
                    eligibleForQte.remove(player.getUUID());
                    lockedHotbarSlots.remove(player.getUUID());
                }
            }

            for (Map.Entry<UUID, EscapeSession> entry : activeEscapes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                EscapeSession session = entry.getValue();

                boolean stillGrabbed = player != null && isStillHeldBy(player, session.titan);

                if (player == null || session.titan.isDeadOrDying() || !stillGrabbed) {
                    activeEscapes.remove(entry.getKey());

                    if (player != null) {
                        // ВАЖНО: иммунитет здесь БОЛЬШЕ НЕ выдаём. Он выдаётся только за реально
                        // выигранное QTE (см. processHit). Раньше любой снос сессии — в том числе
                        // ложный, из-за слетевшего тега being_grabbed — давал игроку 3 секунды
                        // иммунитета, а PureTitanEatMixin, увидев иммунитет, немедленно звал
                        // cancelEating() у титана. Именно это и выглядело как "титан схватил и
                        // тут же отпустил", причём QTE не успевало отработать.
                        ServerPlayNetworking.send(player, new StopHostileQtePayload());
                    }
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HitQteKeyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                processHit(context.player(), payload.success());
            });
        });
    }
}
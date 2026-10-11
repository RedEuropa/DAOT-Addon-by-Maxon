package com.maxonaddon;

import com.maxonaddon.network.OutpostGuiPayload;
import com.maxonaddon.network.SyncQuestPayload;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Зачистка от сторожевой вышки: кадеты просят выбить титанов, подошедших к посту.
 * <p>
 * Задание общее на всю вышку, а не на конкретного кадета. Все трое носят один и тот же
 * групповой тег (см. GROUP_PREFIX), и он же лежит в «цели» активного квеста игрока —
 * поэтому взять задание можно у любого, сдать тоже любому, а второй раз у соседа по
 * площадке уже не возьмёшь. Если вышку вырезали целиком — задание проваливается: сдавать
 * его становится некому.
 * <p>
 * Требуемое число титанов кладём в getRescueTitansRequired(): поле по смыслу общее
 * («сколько титанов нужно убить»), лежит в сохранении игрока и переживает перезаход, а
 * одновременно активным может быть только один квест, так что делить его со спасательным
 * безопасно.
 */
public final class OutpostHuntQuestHandler {

    public static final String QUEST_ID = "outpost_hunt";

    /** Префикс тега, которым помечены кадеты одной вышки: outpost_grp_<x>_<z>. */
    public static final String GROUP_PREFIX = "outpost_grp_";

    /** Разделитель группы и числа целей внутри "цели" квеста. */
    public static final String TARGET_SEPARATOR = "|";

    private static final int TITANS_MIN = 5;
    private static final int TITANS_MAX = 7;

    /** На каком удалении от вышки поднимаются титаны. */
    private static final double SPAWN_DISTANCE = 30.0;
    private static final double SPAWN_SPREAD = 6.0;

    private static final double TALK_RADIUS_SQR = 100.0;
    /** Перерыв между зачистками — 40 минут игрового времени. */
    private static final long COOLDOWN_TICKS = 40L * 60L * 20L;
    private static final int REWARD_REP = 5;
    private static final int DECLINE_REP_PENALTY = 1;

    /** В каком радиусе от погибшего кадета искать выживших сослуживцев. */
    private static final double GROUP_SEARCH_RADIUS = 96.0;

    private static final EntityType<?>[] WILD_TITAN_TYPES = {
            daot.DannysAot.TITAN, daot.DannysAot.SMALL_TITAN, daot.DannysAot.SMALL_TITAN_2,
            daot.DannysAot.ABNORMAL_TITAN, daot.DannysAot.CRAWLING_ABNORMAL_TITAN,
            daot.DannysAot.SAD_TITAN, daot.DannysAot.CRAWLER_TITAN,
    };

    private OutpostHuntQuestHandler() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof Villager cadet)) return InteractionResult.PASS;
            if (!cadet.getTags().contains(OutpostCadetHandler.CADET_TAG)) return InteractionResult.PASS;

            onInteract(serverPlayer, cadet);
            return InteractionResult.SUCCESS;
        });

        // Вышку вырезали — задание сдавать некому.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Villager cadet)) return;
            if (!cadet.getTags().contains(OutpostCadetHandler.CADET_TAG)) return;
            if (!(cadet.level() instanceof ServerLevel level)) return;

            String group = groupOf(cadet);
            if (group == null) return;
            if (hasLivingCadet(level, cadet.blockPosition(), group, cadet)) return;

            failQuestsFor(level, group);
        });
    }

    // ===============================================
    // РАЗГОВОР
    // ===============================================

    private static void onInteract(ServerPlayer player, Villager cadet) {
        if (!MaxonConfig.isReputationEnabled()) return;

        String group = groupOf(cadet);
        if (group == null) return;

        // Сигнальная ракета на любой разговор — кадет вызывает внимание к посту.
        // Владельца не ставим: с ним базовый мод созвал бы сюда разведку
        // (см. FlareProjectileEntityMixin и шапку SurvivorQuestHandler).
        fireSignalFlare(cadet);

        IPlayerStats stats = (IPlayerStats) player;
        String activeId = stats.getActiveQuestId();

        // Своё задание у этой же вышки — значит пришли сдаваться.
        if (QUEST_ID.equals(activeId) && group.equals(groupFromTarget(stats.getActiveQuestTarget()))) {
            tryTurnIn(player, cadet, stats);
            return;
        }

        if (!activeId.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.already_has_quest")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Кадеты ещё не отошли от прошлой вылазки — открываем то же окно, но с благодарностью
        // и таймером вместо предложения. В креативе перерыва нет: это режим для отладки и
        // строительства, ждать там нечего.
        int cooldownLeft = remainingCooldown(player, stats);
        if (cooldownLeft > 0) {
            ServerPlayNetworking.send(player,
                    new OutpostGuiPayload(cadet.getId(), 0, wallNameOf(cadet), cooldownLeft));
            return;
        }

        int required = TITANS_MIN + player.getRandom().nextInt(TITANS_MAX - TITANS_MIN + 1);
        // Число уже фиксируем: иначе показанное в окне и выданное при согласии разошлись бы.
        stats.setRescueTitansRequired(required);
        ServerPlayNetworking.send(player, new OutpostGuiPayload(cadet.getId(), required, wallNameOf(cadet), 0));
    }

    /** Зовётся из обработчика OutpostActionPayload. */
    public static void handleAction(ServerPlayer player, int action, int cadetId) {
        if (!MaxonConfig.isReputationEnabled()) return;

        Entity entity = player.level().getEntity(cadetId);
        if (!(entity instanceof Villager cadet)) return;
        if (!cadet.getTags().contains(OutpostCadetHandler.CADET_TAG)) return;
        if (cadet.distanceToSqr(player) > TALK_RADIUS_SQR) return;

        if (action == 0) {
            accept(player, cadet);
        } else if (action == 1) {
            decline(player, cadet);
        }
    }

    private static void accept(ServerPlayer player, Villager cadet) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.getActiveQuestId().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.already_has_quest")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String group = groupOf(cadet);
        if (group == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        // Тот же гейт, что и на интеракте: пакет с согласием мог прийти в обход окна.
        if (remainingCooldown(player, stats) > 0) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.cooldown_short")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int required = stats.getRescueTitansRequired();
        if (required < TITANS_MIN || required > TITANS_MAX) {
            required = TITANS_MIN + player.getRandom().nextInt(TITANS_MAX - TITANS_MIN + 1);
            stats.setRescueTitansRequired(required);
        }

        // Кулдаун отсчитываем от момента получения, а не сдачи: иначе, не сдавая задание,
        // его можно было бы переоткрывать сколько угодно.
        stats.setOutpostQuestReadyAt(now + COOLDOWN_TICKS);

        // В "цели" квеста едет и группа, и число титанов через разделитель: журнал на клиенте
        // читает её из обычного SyncQuestPayload, и заводить отдельный пакет ради одного
        // числа не пришлось (см. QuestJournalScreen и groupFromTarget ниже).
        stats.setActiveQuest(QUEST_ID, group + TARGET_SEPARATOR + required + TARGET_SEPARATOR + wallNameOf(cadet));
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall("");

        spawnTitans(level, cadet.blockPosition(), required);
        syncQuest(player, stats);

        player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.accepted", required)
                .withStyle(ChatFormatting.GOLD));
    }

    private static void decline(ServerPlayer player, Villager cadet) {
        Wall wall = WallHelper.getWallForCommander(cadet);
        if (wall != null) {
            ((IPlayerStats) player).addReputation(wall, -DECLINE_REP_PENALTY);
        }
        player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.declined")
                .withStyle(ChatFormatting.DARK_RED));
    }

    // ===============================================
    // СДАЧА
    // ===============================================

    private static void tryTurnIn(ServerPlayer player, Villager cadet, IPlayerStats stats) {
        int required = stats.getRescueTitansRequired();
        int killed = stats.getQuestTitansKilled();

        if (killed < required) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.not_done",
                    killed, required).withStyle(ChatFormatting.YELLOW));
            return;
        }

        giveReward(player, cadet);

        stats.addQuestCompleted();
        stats.setActiveQuest("", "");
        stats.setQuestTitansKilled(0);
        stats.setRescueTitansRequired(0);
        syncQuest(player, stats);
    }

    /**
     * Награда. Картриджи бросаются первыми, а всё остальное считается уже от них: больше
     * сигналок — меньше газа и еды, как и задумано. Диапазоны: 3–6 картриджей, 5–2 консервы,
     * 15–10 процентов бака газа.
     */
    private static void giveReward(ServerPlayer player, Villager cadet) {
        RandomSource random = player.getRandom();

        int cartridges = 3 + random.nextInt(4);
        int step = cartridges - 3;              // 0..3
        int food = 5 - step;                    // 5..2
        int gasPercent = 15 - (step * 5 / 3);   // 15, 13, 11, 10

        // Именно патроны к марлийской винтовке (ими же её и перезаряжают, см.
        // reloadMarleyanRifle), а не сигнальные картриджи для ракетницы.
        giveOrDrop(player, new ItemStack(daot.DannysAot.APG_CARTRIDGE, cartridges));
        giveOrDrop(player, new ItemStack(daot.DannysAot.CANNED_HERRING, food));

        if (!refillWornOdm(player, gasPercent)) {
            // УПМ не надет — газ выдаём канистрами, чтобы награда не пропала впустую.
            giveOrDrop(player, new ItemStack(daot.DannysAot.GAS_CANISTER, 1));
        }

        Wall wall = WallHelper.getWallForCommander(cadet);
        if (wall != null) {
            ((IPlayerStats) player).addReputation(wall, REWARD_REP);
        }

        player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.completed",
                cartridges, food, gasPercent).withStyle(ChatFormatting.GREEN));
    }

    /** Доливает газ в надетое УПМ. Возвращает false, если доливать некуда. */
    private static boolean refillWornOdm(ServerPlayer player, int percent) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legs.getItem() instanceof daot.ODMGearItem) {
            int add = daot.ODMGearItem.MAX_GAS * percent / 100;
            daot.ODMGearItem.setGas(legs, Math.min(daot.ODMGearItem.MAX_GAS, daot.ODMGearItem.getGas(legs) + add));
            return true;
        }
        if (legs.getItem() instanceof daot.ODMAPGItem) {
            int add = daot.ODMAPGItem.MAX_GAS * percent / 100;
            daot.ODMAPGItem.setGas(legs, Math.min(daot.ODMAPGItem.MAX_GAS, daot.ODMAPGItem.getGas(legs) + add));
            return true;
        }
        return false;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ===============================================
    // ПРОВАЛ
    // ===============================================

    private static void failQuestsFor(ServerLevel level, String group) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) continue;
            if (!group.equals(groupFromTarget(stats.getActiveQuestTarget()))) continue;

            stats.setActiveQuest("", "");
            stats.setQuestTitansKilled(0);
            stats.setRescueTitansRequired(0);
            syncQuest(player, stats);

            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.failed")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** Остался ли в группе хоть один живой кадет, кроме только что погибшего. */
    private static boolean hasLivingCadet(ServerLevel level, BlockPos around, String group, Villager exclude) {
        AABB box = new AABB(around).inflate(GROUP_SEARCH_RADIUS);
        List<Villager> mates = level.getEntitiesOfClass(Villager.class, box, v ->
                v != exclude
                        && v.isAlive()
                        && v.getTags().contains(OutpostCadetHandler.CADET_TAG)
                        && v.getTags().contains(group));
        return !mates.isEmpty();
    }

    // ===============================================
    // ВСПОМОГАТЕЛЬНОЕ
    // ===============================================

    /**
     * Сколько тиков осталось до нового задания. Ноль — можно брать хоть сейчас.
     * <p>
     * В творческом режиме перерыва нет вовсе: там мир строят и проверяют, а не отыгрывают.
     */
    private static int remainingCooldown(ServerPlayer player, IPlayerStats stats) {
        if (player.isCreative()) return 0;
        long left = stats.getOutpostQuestReadyAt() - player.level().getGameTime();
        return left <= 0 ? 0 : (int) Math.min(left, Integer.MAX_VALUE);
    }

    /**
     * Имя стены, в которой стоит вышка — им называется само задание («Пост в стене Мария»).
     * Определяется по геометрии кольца, на котором стоит кадет: теги стены у него не
     * проставлены, поэтому работает запасной путь WallHelper по радиусу.
     */
    private static String wallNameOf(Villager cadet) {
        Wall wall = WallHelper.getWallForCommander(cadet);
        return wall == null ? "" : wall.name();
    }

    /** Вытаскивает групповой тег из "цели" квеста, отбрасывая всё приписанное после него. */
    private static String groupFromTarget(String target) {
        int sep = target.indexOf(TARGET_SEPARATOR);
        return sep < 0 ? target : target.substring(0, sep);
    }

    /** Групповой тег вышки, к которой приписан кадет. */
    public static String groupOf(Villager cadet) {
        for (String tag : cadet.getTags()) {
            if (tag.startsWith(GROUP_PREFIX)) return tag;
        }
        return null;
    }

    private static void spawnTitans(ServerLevel level, BlockPos center, int count) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            EntityType<?> type = WILD_TITAN_TYPES[random.nextInt(WILD_TITAN_TYPES.length)];
            Entity titan = type.create(level);
            if (titan == null) continue;

            // По кругу вокруг поста: так они приходят с разных сторон, а не толпой с одной.
            double angle = (Math.PI * 2.0 / count) * i + random.nextDouble() * 0.4;
            double dist = SPAWN_DISTANCE + (random.nextDouble() - 0.5) * SPAWN_SPREAD;
            int tx = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int tz = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);

            titan.setPos(tx + 0.5, ty, tz + 0.5);
            level.addFreshEntity(titan);
        }
    }

    private static void fireSignalFlare(Villager cadet) {
        if (!(cadet.level() instanceof ServerLevel level)) return;

        @SuppressWarnings("unchecked")
        EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow> flareType =
                (EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) daot.DannysAot.FLARE_PROJECTILE;
        daot.FlareProjectileEntity flare = new daot.FlareProjectileEntity(flareType, level);
        flare.setPos(cadet.getX(), cadet.getY() + 1.5, cadet.getZ());
        flare.setFlareColorOrdinal(daot.FlareCartridgeItem.FlareColor.PURPLE.ordinal());
        flare.shoot(0, 1.0, 0, 2.5F, 1.0F);
        level.addFreshEntity(flare);
    }

    private static void syncQuest(ServerPlayer player, IPlayerStats stats) {
        ServerPlayNetworking.send(player, new SyncQuestPayload(
                stats.getActiveQuestId(),
                stats.getActiveQuestTarget(),
                stats.getQuestTitansKilled(),
                stats.getTestQuestCompletions(),
                stats.getDeliveryDeadlineTick(),
                stats.getTestQuestCommanderMask()));
    }

    /** Нужен обработчику убийств: подсказывает, сколько ещё осталось. */
    public static void onTitanKillCountChanged(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!QUEST_ID.equals(stats.getActiveQuestId())) return;

        int left = stats.getRescueTitansRequired() - stats.getQuestTitansKilled();
        if (left == 0) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.all_dead")
                    .withStyle(ChatFormatting.GREEN));
        } else if (left > 0 && left <= 2) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.outpost.left", left)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}

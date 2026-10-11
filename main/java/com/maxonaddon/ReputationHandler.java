package com.maxonaddon;

import com.maxonaddon.event.CarriageSpawner;
import com.maxonaddon.util.DistrictHelper;
import com.maxonaddon.util.IChestOwnership;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.ReputationHelper;
import com.maxonaddon.util.SmugglerTradeHelper;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Центральный обработчик репутации у трёх стен (см. Wall/WallHelper/ReputationHelper):
 * - тегирует жителей городов их стеной (периодически, чтобы не пропустить старые сохранения);
 * - разбой: удар/убийство жителя в городе -реп (зацеп ОДМ-крюком не считается);
 * - обыск/слом ЧУЖОГО (не поставленного игроком) сундука на глазах жителя -реп;
 * - спасение жителя, которого ел титан, +реп.
 * Награда за квест — отдельно, в MaxonDaotAddon (там уже есть вся нужная привязка к командиру).
 */
public final class ReputationHandler {

    public static final int QUEST_REP_GAIN = 8;
    private static final int HIT_PENALTY = 3;
    private static final int KILL_PENALTY = 15;
    private static final int CHEST_NOTICED_PENALTY = 10;
    private static final int SAVE_VILLAGER_REWARD = 10;
    private static final double NOTICE_RADIUS = 12.0;
    private static final double GRAB_DETECT_RADIUS = 6.0;
    private static final int WALL_TAG_INTERVAL_TICKS = 200; // раз в 10 секунд

    private static final ResourceKey<DamageType> ODM_HOOK_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_hook"));
    private static final ResourceKey<net.minecraft.world.level.Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static int villagerTagTickCounter = 0;

    private ReputationHandler() {}

    public static void register() {
        // === 1. РАЗБОЙ: удар/убийство жителя в городе ===
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F) return;
            if (!(victim instanceof Villager villager)) return;
            if (source.is(ODM_HOOK_DAMAGE)) return; // зацеп крюком — не разбой
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return;

            Wall wall = WallHelper.getWallAt(villager);
            if (wall == null) return;

            ((IPlayerStats) attacker).addReputation(wall, -HIT_PENALTY);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Villager villager) {
                if (!source.is(ODM_HOOK_DAMAGE) && source.getEntity() instanceof ServerPlayer attacker) {
                    Wall wall = WallHelper.getWallAt(villager);
                    if (wall != null) {
                        ((IPlayerStats) attacker).addReputation(wall, -KILL_PENALTY);
                    }
                }
            }
        });

        // === 2. СПАСЕНИЕ ЖИТЕЛЯ: погиб чистый титан, евший жителя, от руки игрока ===
        //
        // ВАЖНО: специально НЕ через ServerLivingEntityEvents.AFTER_DEATH — для наших титанов
        // он не срабатывает. UniversalTitanDeathMixin отменяет (ci.cancel()) их tickDeath()
        // прямо в HEAD и вместо этого 200 тиков сам проигрывает "труп дымится/рассыпается",
        // реально удаляя сущность только в самом конце — обычный ванильный AFTER_DEATH-путь до
        // этого просто не доходит. Проверенный рабочий способ ловить "игрок убил титана" в этом
        // моде — ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY (тот же приём, что уже
        // используется чуть выше для обычной награды за титана и для перка за убийство Огра —
        // см. PerkUnlockHandler.onTitanKilled).
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killedEntity) -> {
            if (entity instanceof ServerPlayer savior) {
                checkSaveVillagerBonus(killedEntity, savior);
            }
        });

        // === 3. ОБЫСК/СЛОМ ЧУЖОГО СУНДУКА НА ГЛАЗАХ ЖИТЕЛЯ ===
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity containerBe) {
                onChestTouched(serverPlayer, containerBe, pos, "message.maxonaddon.reputation.chest_noticed_open");
            }
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && blockEntity instanceof RandomizableContainerBlockEntity containerBe) {
                onChestTouched(serverPlayer, containerBe, pos, "message.maxonaddon.reputation.chest_noticed_break");
            }
            return true;
        });

        // === 4. ТОРГОВЛЯ С КОНТРАБАНДИСТАМИ ГЕЙТИТСЯ РЕПУТАЦИЕЙ СТЕНЫ, ГДЕ ОНИ СТОЯТ ===
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            // Репутации нет — контрабандисты торгуют со всеми одинаково, без гейта по ней
            // (иначе при выключенной системе все навсегда остались бы на стартовых 50).
            if (!MaxonConfig.isReputationEnabled()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof Villager smuggler)) return InteractionResult.PASS;
            if (!smuggler.getTags().contains("smuggler") || smuggler.getTags().contains("smuggler_caught")) return InteractionResult.PASS;
            if (!SmugglerTradeHelper.isHoodOn(smuggler)) return InteractionResult.PASS; // без маскировки торговли и так нет

            Wall wall = WallHelper.getWallAt(smuggler);
            if (wall == null) return InteractionResult.PASS;

            int rep = ((IPlayerStats) serverPlayer).getReputation(wall);
            if (ReputationHelper.isEnemy(rep)) {
                serverPlayer.displayClientMessage(Component.translatable("message.maxonaddon.reputation.smuggler_refuses").withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }

            // Перестраиваем товар под репутацию ИМЕННО этого игрока прямо перед открытием
            // торговли — общий список предложений один на всех (ограничение ванильного
            // Villager), так что "качество" синхронно с последним, кто подошёл торговать.
            SmugglerTradeHelper.applyOffers(smuggler, ReputationHelper.isFriend(rep),
                    com.maxonaddon.perk.PerkManager.tradeDiscount(serverPlayer));
            return InteractionResult.PASS;
        });

        // === 5. ПЕРИОДИЧЕСКОЕ ТЕГИРОВАНИЕ ЖИТЕЛЕЙ ГОРОДОВ ИХ СТЕНОЙ ===
        // === 6. РАЗБЛОКИРОВКА ПЕРКА "ГЕРОЙ ЧЕЛОВЕЧЕСТВА" — ДРУЖЕСКАЯ РЕПУТАЦИЯ ВСЕХ 3 СТЕН СРАЗУ ===
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++villagerTagTickCounter < WALL_TAG_INTERVAL_TICKS) return;
            villagerTagTickCounter = 0;
            // Перк "Герой Человечества" выдаётся за дружбу со всеми тремя стенами — без
            // системы репутации это условие недостижимо, проверять нечего.
            if (!MaxonConfig.isReputationEnabled()) return;
            tagVillagersWithWalls(server);
            checkHeroPerkUnlock(server);
        });
    }

    private static void checkHeroPerkUnlock(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (stats.hasUnlockedPerk(com.maxonaddon.perk.ModPerk.HUMANITY_HERO.getId())) continue;

            boolean allFriendly = true;
            for (Wall wall : Wall.values()) {
                if (!ReputationHelper.isFriend(stats.getReputation(wall))) {
                    allFriendly = false;
                    break;
                }
            }

            if (allFriendly) {
                com.maxonaddon.perk.PerkUnlockHandler.grantPerk(player, com.maxonaddon.perk.ModPerk.HUMANITY_HERO);
            }
        }
    }

    private static void onChestTouched(ServerPlayer player, RandomizableContainerBlockEntity containerBe, BlockPos pos, String messageKey) {
        IChestOwnership ownership = (IChestOwnership) containerBe;
        if (ownership.maxon$isPlayerPlaced()) return; // свой сундук — не разбой
        if (ownership.maxon$isNoticedAlready()) return; // этот сундук уже "спалил" один раз

        if (!(player.level() instanceof ServerLevel level)) return;
        Wall wall = WallHelper.getWallAt(pos.getX(), pos.getZ());
        if (wall == null) return; // не в городе — жителям всё равно неоткуда взяться рядом

        AABB box = new AABB(pos).inflate(NOTICE_RADIUS);
        List<Villager> witnesses = level.getEntitiesOfClass(Villager.class, box, v ->
                v.isAlive()
                        && !v.getTags().contains("quest_commander")
                        && !v.getTags().contains("carriage_driver")
                        && v.hasLineOfSight(player));

        if (witnesses.isEmpty()) return;

        ownership.maxon$setNoticedAlready(true);
        ((IPlayerStats) player).addReputation(wall, -CHEST_NOTICED_PENALTY);
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), true);
    }

    /**
     * ИСПРАВЛЕНО: getEatingTarget()/isEating() (как в PureTitanEatMixin) для жителей ненадёжны —
     * судя по всему, эта пара методов заточена под ServerPlayer-жертву и для Villager либо не
     * срабатывает, либо возвращает не то. Настоящий источник истины для "кого держит титан" —
     * тег "being_grabbed" на самой жертве (см. комментарий isStillHeldBy в HostileQteHandler —
     * ровно та же история с этим тегом уже разбиралась там для игроков). Поэтому вместо
     * рефлексии на титане просто ищем рядом с только что убитым титаном живых жителей с этим
     * тегом — с очень высокой вероятностью это ровно тот, кого он ел.
     */
    private static void checkSaveVillagerBonus(LivingEntity possibleTitan, ServerPlayer savior) {
        String name = possibleTitan.getClass().getName();
        boolean isPureTitan = name.startsWith("daot.") && (name.contains("Titan") || name.contains("ConnieFather"))
                && !name.contains("Grab") && !name.contains("Eye") && !name.contains("Nape") && !name.contains("Dummy");
        if (!isPureTitan) return;
        if (!(possibleTitan.level() instanceof ServerLevel level)) return;

        AABB box = possibleTitan.getBoundingBox().inflate(GRAB_DETECT_RADIUS);
        List<Villager> grabbed = level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.getTags().contains("being_grabbed"));
        if (grabbed.isEmpty()) return;

        for (Villager villager : grabbed) {
            // Титан-хозяин мёртв — снимаем захват в любом случае.
            villager.removeTag("being_grabbed");

            Wall wall = WallHelper.getWallAt(villager);
            if (wall == null) wall = WallHelper.getWallAt(savior);
            if (wall == null) continue;

            ((IPlayerStats) savior).addReputation(wall, SAVE_VILLAGER_REWARD);
            savior.displayClientMessage(Component.translatable("message.maxonaddon.reputation.villager_saved").withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static void tagVillagersWithWalls(MinecraftServer server) {
        ServerLevel level = server.getLevel(PARADIS_KEY);
        if (level == null) return;

        for (int i = 0; i < CarriageSpawner.SPAWN_POINTS.length; i++) {
            Wall wall = WallHelper.wallForDistrictIndex(i + 1);
            if (wall == null) continue;

            BlockPos center = CarriageSpawner.SPAWN_POINTS[i];
            AABB box = new AABB(center).inflate(DistrictHelper.CITY_RADIUS, 200.0, DistrictHelper.CITY_RADIUS);
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive);
            if (villagers.isEmpty()) continue; // округ сейчас не загружен — ждём следующего прохода

            for (Villager villager : villagers) {
                if (hasAnyWallTag(villager)) continue;
                villager.addTag(wall.tag);
            }
        }
    }

    private static boolean hasAnyWallTag(Villager villager) {
        for (Wall wall : Wall.values()) {
            if (villager.getTags().contains(wall.tag)) return true;
        }
        return false;
    }
}

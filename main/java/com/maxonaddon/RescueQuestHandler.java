package com.maxonaddon;

import com.maxonaddon.network.SyncQuestPayload;
import com.maxonaddon.network.SyncRescuePayload;
import com.maxonaddon.network.SyncRescueStatusPayload;
import com.maxonaddon.util.DistrictHelper;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.WallHelper;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Квест "Спасение застрявших": в отличие от квеста доставки, тут никакая точка НЕ ищется —
 * сервер сам ставит здание (случайное из существующих house/shighouse построек dannys-aot),
 * сажает на крышу кадетов и спавнит вокруг титанов ПРЯМО в момент принятия квеста (см.
 * startRescueQuest), синхронно, форс-загрузив нужный чанк. Это специально сделано так,
 * чтобы не наступить на те же грабли, что были у квеста доставки (сущности в незагруженном
 * чанке искать ненадёжно) — тут никого искать не надо, всё создаётся заново каждый раз.
 * <p>
 * Кадеты — обычные Villager с профессией NITWIT (не мешает торговым/расписанным AI) и
 * setNoAi(true): не убегают с крыши и не пытаются погибнуть специально, но titan AI всё
 * равно считает их валидной добычей (см. daot.SmallTitanEntity$SmallTitanFindTargetGoal —
 * AbstractVillager — валидная цель), так что риск для них настоящий. Урон/убийство титанами
 * НЕ считается "разбоем" в ReputationHandler, т.к. точка специально ставится вне города
 * (WallHelper.getWallAt для неё всегда null).
 * <p>
 * Счётчик "убито титанов" — тот же общий IPlayerStats#getQuestTitansKilled(), что уже
 * используется тестовым квестом (см. MaxonDaotAddon — ServerEntityCombatEvents.
 * AFTER_KILLED_OTHER_ENTITY), никакого отдельного тегирования титанов не требуется.
 */
public final class RescueQuestHandler {

    public static final String QUEST_ID = "rescue_quest";

    private static final int TRAVEL_TIME_TICKS = 5 * 60 * 20; // 5 минут — успеть добраться
    private static final double MIN_DISTANCE = 500.0;
    private static final double MAX_DISTANCE = 3000.0;
    private static final double ARRIVE_RADIUS = 60.0;
    private static final int FAIL_REP_PENALTY_LATE = 10;
    private static final int FAIL_REP_PENALTY_ALL_DIED = 7;
    private static final int MIN_CADETS = 3;
    private static final int MAX_CADETS = 5;
    private static final int MIN_TITANS = 5;
    private static final int MAX_TITANS = 10;
    private static final int DEADLINE_CHECK_INTERVAL_TICKS = 100; // раз в 5 секунд
    private static final int TITAN_SPAWN_MIN_DIST = 15;
    private static final int TITAN_SPAWN_MAX_DIST = 35;
    private static final int PLACEMENT_ATTEMPTS = 20;

    /** Тег кадетов — быстрый отбор по классу; чей именно это кадет, см. cadetOwners ниже. */
    private static final String CADET_TAG = "rescue_cadet";

    /**
     * UUID кадета -> UUID игрока-владельца. Обычный Entity в ваниль/Fabric (в отличие от
     * Forge) не имеет встроенного "мешка" произвольных NBT-данных типа getPersistentData() —
     * заводить под это отдельный миксин ради сущности, живущей от силы 5-10 минут одного
     * прохождения квеста, избыточно. В памяти достаточно: если сервер перезапустится прямо
     * посреди активного квеста, привязка потеряется — цена принята сознательно (тот же
     * компромисс, что и processedChunks/processedRegions у генераторов структур).
     */
    private static final Map<UUID, UUID> cadetOwners = new ConcurrentHashMap<>();

    /**
     * UUID игрока -> UUID заспавненных ему титанов. В отличие от кадетов, тут нужен список
     * (а не обратная карта), потому что цель другая — не "чей это титан", а "все ли МОИ
     * титаны ещё живы и где они" (для подсветки последних двух, см. applyGlowToRemainingTitans).
     */
    private static final Map<UUID, List<UUID>> ownerTitans = new ConcurrentHashMap<>();

    private static final int GLOW_TRIGGER_REMAINING = 2;
    private static final int GLOW_DURATION_TICKS = 20 * 60 * 6; // 6 минут — с запасом на добивание
    private static final int TITAN_LOCATOR_INTERVAL_TICKS = 20; // раз в секунду — титан двигается

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static final ResourceLocation[] BUILDING_IDS = {
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "house1"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "house2"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "house3"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "house4"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "house5"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "shighouse1"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "shighouse2"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "shighouse3"),
            ResourceLocation.fromNamespaceAndPath("dannys-aot", "shighouse4"),
    };

    private static final EntityType<?>[] WILD_TITAN_TYPES = {
            daot.DannysAot.TITAN, daot.DannysAot.SMALL_TITAN, daot.DannysAot.SMALL_TITAN_2,
            daot.DannysAot.ABNORMAL_TITAN, daot.DannysAot.CRAWLING_ABNORMAL_TITAN,
            daot.DannysAot.SAD_TITAN, daot.DannysAot.CRAWLER_TITAN,
    };

    private static int tickCounter = 0;
    private static int titanLocatorTickCounter = 0;

    private RescueQuestHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < DEADLINE_CHECK_INTERVAL_TICKS) return;
            tickCounter = 0;
            checkArrivals(server);
        });

        // Титан движется, поэтому красный "компас" на него нужно освежать чаще, чем
        // раз-в-5-секундную проверку дедлайна выше.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++titanLocatorTickCounter < TITAN_LOCATOR_INTERVAL_TICKS) return;
            titanLocatorTickCounter = 0;
            updateTitanLocators(server);
        });

        // Гибель кадета (от чего угодно — вживую проверено, что для НАШИХ титанов
        // ServerLivingEntityEvents.AFTER_DEATH ненадёжен из-за кастомной анимации смерти
        // (см. комментарий в ReputationHandler), но тут это неважно: смерть отслеживаем у
        // САМОГО КАДЕТА (обычный Villager, стандартная смерть, AFTER_DEATH для него
        // срабатывает нормально), а не у титана.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.getTags().contains(CADET_TAG)) return;
            UUID ownerUuid = cadetOwners.remove(entity.getUUID());
            if (ownerUuid == null) return;
            if (!(entity.level() instanceof ServerLevel level)) return;

            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner == null) return;

            IPlayerStats stats = (IPlayerStats) owner;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) return;

            stats.setRescueCadetsAlive(Math.max(0, stats.getRescueCadetsAlive() - 1));
            sendStatus(owner, stats);
        });
    }

    private static void checkArrivals(MinecraftServer server) {
        ServerLevel paradis = server.getLevel(PARADIS_KEY);
        if (paradis == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) continue;
            if (stats.isRescueArrived()) continue;

            double dx = player.getX() - stats.getRescueTargetX();
            double dz = player.getZ() - stats.getRescueTargetZ();
            if (dx * dx + dz * dz <= ARRIVE_RADIUS * ARRIVE_RADIUS && player.level() == paradis) {
                stats.setRescueArrived(true);
                clearLocator(player);

                // Титаны появляются именно СЕЙЧАС, а не при принятии — раньше они спавнились
                // сразу и за долгую дорогу (до 5 минут) успевали разбрестись обычным AI куда
                // попало, так что к приходу игрока рядом со зданием никого не оставалось.
                BlockPos center = new BlockPos(stats.getRescueTargetX(), stats.getRescueTargetY(), stats.getRescueTargetZ());
                spawnTitans(paradis, center, stats.getRescueTitansRequired(), player.getUUID());

                sendStatus(player, stats);
                player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.arrived").withStyle(ChatFormatting.GOLD));
                continue;
            }

            if (paradis.getGameTime() >= stats.getRescueDeadlineTick()) {
                failLate(paradis, player, stats);
            }
        }
    }

    /**
     * Красный "компас" (тот же трек над XP-баром, что и жёлтый на здание, но titanMode=true —
     * см. GuiMixin#renderRescueLocator) на ближайшего ещё живого КВЕСТОВОГО титана: игрок
     * видит, кого именно из титанов на карте нужно убивать, а не гадает среди диких. Титан
     * двигается, поэтому пересчитываем и шлём заново каждую секунду, а не один раз при
     * прибытии.
     */
    private static void updateTitanLocators(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;
            if (!QUEST_ID.equals(stats.getActiveQuestId())) continue;
            if (!stats.isRescueArrived()) continue;
            if (stats.getQuestTitansKilled() >= stats.getRescueTitansRequired()) continue; // все убиты — указывать больше не на что

            List<UUID> ids = ownerTitans.get(player.getUUID());
            if (ids == null || ids.isEmpty()) continue;
            if (!(player.level() instanceof ServerLevel level)) continue;

            net.minecraft.world.entity.LivingEntity nearest = null;
            double bestDistSq = Double.MAX_VALUE;
            for (UUID id : ids) {
                if (level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                    double distSq = living.distanceToSqr(player);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        nearest = living;
                    }
                }
            }

            if (nearest != null) {
                sendTitanLocator(player, nearest);
            }
        }
    }

    /**
     * Принять квест у originCommander. Возвращает false (сама уже отправила игроку сообщение
     * об ошибке), если не получилось создать сцену — квест в этом случае НЕ активируется.
     */
    public static boolean startRescueQuest(ServerPlayer player, Villager originCommander) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.getActiveQuestId().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.already_has_quest").withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel paradis = player.server.getLevel(PARADIS_KEY);
        if (paradis == null) return false;

        BlockPos target = pickTargetPoint(paradis, player);
        if (target == null) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.no_spot").withStyle(ChatFormatting.RED));
            return false;
        }

        ResourceLocation buildingId = BUILDING_IDS[paradis.getRandom().nextInt(BUILDING_IDS.length)];
        Optional<StructureTemplate> templateOpt = paradis.getStructureManager().get(buildingId);
        if (templateOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.no_spot").withStyle(ChatFormatting.RED));
            return false;
        }
        StructureTemplate template = templateOpt.get();

        Rotation rotation = Rotation.getRandom(paradis.getRandom());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        // ВАЖНО: под поворотом 90°/270° локальный (0,0,0) НЕ является минимальным углом
        // размещённой структуры — StructureTemplate.getBoundingBox() поворачивает оба
        // локальных угла (0,0,0) и (size-1) вокруг pivot'а и сама берёт из них min/max, так
        // что после поворота структура может уйти в ОТРИЦАТЕЛЬНУЮ сторону от точки, которую
        // мы передаём в placeInWorld. Раньше здесь вручную вычислялся origin в предположении
        // "она всегда растёт от origin в плюс", что верно только для Rotation.NONE — при
        // повороте кадеты сажались по расчётным координатам РЯДОМ со зданием, а не на его
        // настоящей (сдвинутой) крыше. Теперь считаем через ту же самую bounding box, которой
        // пользуется сам placeInWorld — сначала "примеряем" её в (0,0,0), потом сдвигаем так,
        // чтобы её центр по X/Z и низ (minY) совпали с target.
        net.minecraft.world.level.levelgen.structure.BoundingBox rawBox = template.getBoundingBox(settings, BlockPos.ZERO);
        int posX = target.getX() - rawBox.getCenter().getX();
        int posY = target.getY() - rawBox.minY();
        int posZ = target.getZ() - rawBox.getCenter().getZ();
        BlockPos origin = new BlockPos(posX, posY, posZ);
        net.minecraft.world.level.levelgen.structure.BoundingBox placedBox = rawBox.moved(posX, posY, posZ);

        boolean placed = template.placeInWorld(paradis, origin, origin, settings, paradis.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.no_spot").withStyle(ChatFormatting.RED));
            return false;
        }

        int cadetsTotal = MIN_CADETS + paradis.getRandom().nextInt(MAX_CADETS - MIN_CADETS + 1);
        int titansRequired = MIN_TITANS + paradis.getRandom().nextInt(MAX_TITANS - MIN_TITANS + 1);

        List<BlockPos> roofSpots = findRoofSpots(paradis, placedBox, cadetsTotal);
        spawnCadets(paradis, player, roofSpots);
        // Титанов НЕ спавним тут — см. checkArrivals: если поставить их сразу при принятии,
        // за долгую дорогу (до 5 минут) они успевают разбрестись обычным AI куда попало, и к
        // приходу игрока рядом со зданием никого не остаётся.

        Wall originWall = WallHelper.getWallForCommander(originCommander);
        stats.setActiveQuest(QUEST_ID, "");
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall(originWall != null ? originWall.name() : "");
        stats.setRescueTarget(target.getX(), target.getY(), target.getZ());
        stats.setRescueArrived(false);
        stats.setRescueTitansRequired(titansRequired);
        stats.setRescueCadetsTotal(cadetsTotal);
        stats.setRescueCadetsAlive(cadetsTotal);
        stats.setRescueDeadlineTick(paradis.getGameTime() + TRAVEL_TIME_TICKS);

        sendLocator(player, target);
        sendStatus(player, stats);

        player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.started").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Случайная точка в кольце [MIN_DISTANCE; MAX_DISTANCE] от игрока, вне города, на суше. */
    private static BlockPos pickTargetPoint(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);

            if (DistrictHelper.isInAnyDistrict(x, z)) continue;

            // Форс-загрузка 3x3 чанков вокруг точки — синхронна и надёжна для ТЕРРЕЙНА/
            // размещения блоков (в отличие от поиска ЖИВЫХ сущностей, который и подвёл квест
            // доставки — тут искать вообще нечего, всё создаётся заново). Одного чанка мало:
            // здание шириной до 22 блоков легко перелезает границу соседнего чанка.
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            for (int cx = chunkX - 1; cx <= chunkX + 1; cx++) {
                for (int cz = chunkZ - 1; cz <= chunkZ + 1; cz++) {
                    level.getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }

            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (groundY <= level.getSeaLevel()) continue;

            return new BlockPos(x, groundY, z);
        }
        return null;
    }

    /**
     * N случайных точек в средней части ФАКТИЧЕСКОЙ (уже развёрнутой поворотом) bounding box
     * структуры — высота берётся ПОСЛЕ размещения структуры через heightmap, поэтому подходит
     * для любой формы крыши (скат, ступени и т.п.), а не только плоской.
     */
    private static List<BlockPos> findRoofSpots(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box, int count) {
        List<BlockPos> spots = new ArrayList<>();
        RandomSource random = level.getRandom();
        int spanX = box.getXSpan();
        int spanZ = box.getZSpan();
        int marginX = Math.max(1, spanX / 5);
        int marginZ = Math.max(1, spanZ / 5);
        int rangeX = Math.max(1, spanX - marginX * 2);
        int rangeZ = Math.max(1, spanZ - marginZ * 2);

        int guard = 0;
        while (spots.size() < count && guard++ < count * 8) {
            int wx = box.minX() + marginX + random.nextInt(rangeX);
            int wz = box.minZ() + marginZ + random.nextInt(rangeZ);
            int wy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
            spots.add(new BlockPos(wx, wy, wz));
        }
        return spots;
    }

    private static void spawnCadets(ServerLevel level, ServerPlayer owner, List<BlockPos> spots) {
        RandomSource random = level.getRandom();
        for (BlockPos pos : spots) {
            Villager cadet = EntityType.VILLAGER.create(level);
            if (cadet == null) continue;

            cadet.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            cadet.setVillagerData(cadet.getVillagerData().setProfession(VillagerProfession.NITWIT));
            cadet.setNoAi(true); // не убегают с крыши, не пытаются погибнуть специально
            cadet.setPersistenceRequired();
            cadet.addTag(CADET_TAG);
            cadetOwners.put(cadet.getUUID(), owner.getUUID());

            cadet.setItemSlot(EquipmentSlot.CHEST, new ItemStack(daot.DannysAot.UNIFORM));
            ItemStack legs = new ItemStack(daot.DannysAot.ODM_GEAR);
            if (legs.getItem() instanceof daot.ODMGearItem) {
                daot.ODMGearItem.setGas(legs, daot.ODMGearItem.MAX_GAS / 2);
            }
            cadet.setItemSlot(EquipmentSlot.LEGS, legs);
            cadet.setItemSlot(EquipmentSlot.FEET, new ItemStack(daot.DannysAot.ODM_BOOTS));

            // Рукояти без клинков в обеих руках — не атакуют титанов (см. класс doc), просто
            // сидят вооружённые "на вид".
            ItemStack handleMain = new ItemStack(daot.DannysAot.BLADE);
            ItemStack handleOff = new ItemStack(daot.DannysAot.BLADE);
            if (handleMain.getItem() instanceof daot.BladeItem) {
                daot.BladeItem.initializeEmpty(handleMain);
                daot.BladeItem.initializeEmpty(handleOff);
            }
            cadet.setItemSlot(EquipmentSlot.MAINHAND, handleMain);
            cadet.setItemSlot(EquipmentSlot.OFFHAND, handleOff);

            float yaw = random.nextFloat() * 360.0F;
            cadet.setYRot(yaw);
            cadet.yHeadRot = yaw;
            cadet.yBodyRot = yaw;

            level.addFreshEntity(cadet);
        }
    }

    private static void spawnTitans(ServerLevel level, BlockPos center, int count, UUID ownerUuid) {
        RandomSource random = level.getRandom();
        List<UUID> ids = ownerTitans.computeIfAbsent(ownerUuid, k -> new java.util.concurrent.CopyOnWriteArrayList<>());

        for (int i = 0; i < count; i++) {
            EntityType<?> type = WILD_TITAN_TYPES[random.nextInt(WILD_TITAN_TYPES.length)];
            net.minecraft.world.entity.Entity titan = type.create(level);
            if (titan == null) continue;

            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = TITAN_SPAWN_MIN_DIST + random.nextDouble() * (TITAN_SPAWN_MAX_DIST - TITAN_SPAWN_MIN_DIST);
            int tx = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int tz = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);

            titan.setPos(tx + 0.5, ty, tz + 0.5);
            level.addFreshEntity(titan);
            ids.add(titan.getUUID());
        }
    }

    /** Осталось совсем немного — подсвечиваем оставшихся титанов этого игрока (Glowing), чтобы было легче их найти. */
    private static void applyGlowToRemainingTitans(ServerLevel level, ServerPlayer owner) {
        List<UUID> ids = ownerTitans.get(owner.getUUID());
        if (ids == null) return;

        for (UUID id : ids) {
            if (level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, GLOW_DURATION_TICKS, 0, false, false));
            }
        }
    }

    private static void sendLocator(ServerPlayer player, BlockPos target) {
        ServerPlayNetworking.send(player, new SyncRescuePayload(
                true, PARADIS_KEY.location().toString(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, false
        ));
    }

    private static void clearLocator(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncRescuePayload(false, "", 0.0, 0.0, 0.0, false));
    }

    /** Красный трек на ближайшего ещё живого КВЕСТОВОГО титана — чтобы было видно, кого именно нужно убить, а не просто "titanMode=false" точку здания. */
    private static void sendTitanLocator(ServerPlayer player, net.minecraft.world.entity.LivingEntity titan) {
        ServerPlayNetworking.send(player, new SyncRescuePayload(
                true, titan.level().dimension().location().toString(), titan.getX(), titan.getY(), titan.getZ(), true
        ));
    }

    private static void sendStatus(ServerPlayer player, IPlayerStats stats) {
        boolean active = QUEST_ID.equals(stats.getActiveQuestId());
        ServerPlayNetworking.send(player, new SyncRescueStatusPayload(
                active, stats.isRescueArrived(), stats.getRescueCadetsTotal(), stats.getRescueCadetsAlive(),
                stats.getRescueTitansRequired(), stats.getRescueDeadlineTick()
        ));
    }

    /** Зовётся из MaxonDaotAddon сразу после инкремента questTitansKilled — проверяет, не хватило ли только что убитого титана (и не осталось ли ровно GLOW_TRIGGER_REMAINING). */
    public static void onTitanKillCountChanged(ServerLevel level, ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!QUEST_ID.equals(stats.getActiveQuestId())) return;
        if (!stats.isRescueArrived()) return;

        // Инкремент всегда +1, поэтому "==" гарантированно поймает нужный тик ровно один раз
        // и не сработает повторно.
        int remaining = stats.getRescueTitansRequired() - stats.getQuestTitansKilled();
        if (remaining == GLOW_TRIGGER_REMAINING) {
            applyGlowToRemainingTitans(level, player);
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.titans_glowing").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (remaining != 0) return;

        BlockPos target = new BlockPos(stats.getRescueTargetX(), stats.getRescueTargetY(), stats.getRescueTargetZ());
        fireGreenFlare(level, target);
        despawnCadets(level, player);
        ownerTitans.remove(player.getUUID());
        clearLocator(player); // последний титан мёртв — красный "компас" больше не на кого наводить

        player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.titans_cleared").withStyle(ChatFormatting.GREEN));
        sendStatus(player, stats);
    }

    private static void fireGreenFlare(ServerLevel level, BlockPos pos) {
        @SuppressWarnings("unchecked")
        EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow> flareType =
                (EntityType<? extends net.minecraft.world.entity.projectile.AbstractArrow>) daot.DannysAot.FLARE_PROJECTILE;
        daot.FlareProjectileEntity flare = new daot.FlareProjectileEntity(flareType, level);
        flare.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        flare.setFlareColorOrdinal(daot.FlareCartridgeItem.FlareColor.GREEN.ordinal());
        flare.shoot(0, 1.0, 0, 2.5F, 1.0F);
        level.addFreshEntity(flare);
    }

    /** Все ещё живые кадеты этого игрока "спасены" и исчезают — cadetsAlive на этот момент уже финальное число выживших. */
    private static void despawnCadets(ServerLevel level, ServerPlayer owner) {
        IPlayerStats stats = (IPlayerStats) owner;
        BlockPos target = new BlockPos(stats.getRescueTargetX(), stats.getRescueTargetY(), stats.getRescueTargetZ());
        AABB box = new AABB(target).inflate(80.0, 60.0, 80.0);
        // Отбор по тегу с той же оговоркой, что и в refresh: запись в cadetOwners может
        // потеряться (она только в памяти), и кадеты остались бы стоять на крыше навсегда.
        // Безхозного кадета в радиусе своей же точки квеста считаем своим.
        List<Villager> cadets = level.getEntitiesOfClass(Villager.class, box, v -> {
            if (!v.getTags().contains(CADET_TAG)) return false;
            UUID knownOwner = cadetOwners.get(v.getUUID());
            return knownOwner == null || owner.getUUID().equals(knownOwner);
        });
        for (Villager cadet : cadets) {
            cadetOwners.remove(cadet.getUUID());
            cadet.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    private static void failLate(ServerLevel paradis, ServerPlayer player, IPlayerStats stats) {
        Wall wall = Wall.fromNameSafe(stats.getActiveQuestWall());
        if (wall != null) {
            stats.addReputation(wall, -FAIL_REP_PENALTY_LATE);
        }

        despawnCadets(paradis, player);
        ownerTitans.remove(player.getUUID());
        clearLocator(player);
        clearRescueState(stats);

        player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.failed_late").withStyle(ChatFormatting.DARK_RED));
        sendStatus(player, stats);
        syncGenericQuest(player, stats);
    }

    /** Зовётся из MaxonDaotAddon при action == 2 (Сдать), если активен именно этот квест. */
    public static void tryTurnIn(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;

        if (stats.getQuestTitansKilled() < stats.getRescueTitansRequired()) {
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.not_ready").withStyle(ChatFormatting.RED));
            return;
        }

        int total = Math.max(1, stats.getRescueCadetsTotal());
        int alive = stats.getRescueCadetsAlive();
        Wall wall = Wall.fromNameSafe(stats.getActiveQuestWall());

        if (alive <= 0) {
            if (wall != null) stats.addReputation(wall, -FAIL_REP_PENALTY_ALL_DIED);
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.all_died").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (wall != null) stats.addReputation(wall, ReputationHandler.QUEST_REP_GAIN);

            if (alive == total) {
                giveOrDrop(player, new ItemStack(daot.DannysAot.BLADE_COMPONENT, 4));
                refillWornOdm(player);
                giveOrDrop(player, new ItemStack(daot.DannysAot.ICE_BURST_CLUSTER, 20));
                giveOrDrop(player, new ItemStack(Items.EMERALD, 5));
                player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.all_saved").withStyle(ChatFormatting.GREEN));
            } else if (alive == 1) {
                giveOrDrop(player, new ItemStack(daot.DannysAot.ICE_BURST_CLUSTER, 20));
                giveOrDrop(player, new ItemStack(Items.EMERALD, 1));
                player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.lost_many").withStyle(ChatFormatting.DARK_RED));
            } else if (alive * 2 <= total) { // 50% и меньше
                refillWornOdm(player);
                giveOrDrop(player, new ItemStack(Items.EMERALD, 3));
                giveOrDrop(player, new ItemStack(daot.DannysAot.BLADE_COMPONENT, 2));
                player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.partial_low").withStyle(ChatFormatting.YELLOW));
            } else { // больше 50%, но не все — промежуточный уровень (не описан явно в ТЗ)
                refillWornOdm(player);
                giveOrDrop(player, new ItemStack(daot.DannysAot.ICE_BURST_CLUSTER, 10));
                giveOrDrop(player, new ItemStack(Items.EMERALD, 4));
                giveOrDrop(player, new ItemStack(daot.DannysAot.BLADE_COMPONENT, 3));
                player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.partial_high").withStyle(ChatFormatting.GREEN));
            }
        }

        clearLocator(player);
        // Считаем сдачу выполнением независимо от того, скольких кадетов довезли живыми:
        // задание доведено до конца, а за потери игрок уже расплатился репутацией.
        stats.addQuestCompleted();
        clearRescueState(stats);
        sendStatus(player, stats);
        syncGenericQuest(player, stats);
    }

    private static void refillWornOdm(ServerPlayer player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legs.getItem() instanceof daot.ODMGearItem) {
            daot.ODMGearItem.setGas(legs, daot.ODMGearItem.MAX_GAS);
        } else if (legs.getItem() instanceof daot.ODMAPGItem) {
            daot.ODMAPGItem.setGas(legs, daot.ODMAPGItem.MAX_GAS);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void syncGenericQuest(ServerPlayer player, IPlayerStats stats) {
        ServerPlayNetworking.send(player, new SyncQuestPayload(
                stats.getActiveQuestId(), stats.getActiveQuestTarget(), stats.getQuestTitansKilled(),
                stats.getTestQuestCompletions(), stats.getDeliveryDeadlineTick(), stats.getTestQuestCommanderMask()
        ));
    }

    /**
     * Отказ от уже взятого задания (кнопка "Отказаться" в журнале). Сцену убираем: кадеты
     * исчезают (их "эвакуировали без нас"), привязка титанов сбрасывается. Сами титаны
     * остаются жить в мире — они уже часть ландшафта, и молча удалять их из-под игрока,
     * который может стоять с ними в бою, хуже, чем оставить. Штраф репутацией начисляет
     * вызывающий код (см. MaxonDaotAddon, action == 1).
     */
    /**
     * Ручная пересборка состояния квеста по тому, что РЕАЛЬНО происходит в мире.
     * <p>
     * Штатный учёт держится на событиях: убийство титана поднимает счётчик, смерть кадета
     * его уменьшает. Событие можно и не получить — титан-скелет успевает исчезнуть (выгрузка
     * чанка, чистка сущностей, гибель не от игрока), и тогда фраг не засчитывается, а квест
     * навсегда виснет с одним «недобитым» титаном. Здесь мы не ждём событий, а просто смотрим,
     * кто из выданных квестом сущностей ещё существует, и приводим счётчики к факту.
     */
    public static void refresh(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!QUEST_ID.equals(stats.getActiveQuestId())) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel paradis = server.getLevel(PARADIS_KEY);
        if (paradis == null) return;

        // До прибытия считать нечего: ни титанов, ни кадетов ещё нет.
        if (!stats.isRescueArrived()) {
            player.displayClientMessage(
                    Component.translatable("message.maxonaddon.rescue.refresh_not_arrived").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // 1. Титаны. Пропавшие из мира вычёркиваем из списка и считаем убитыми.
        List<UUID> titanIds = ownerTitans.get(player.getUUID());
        int aliveTitans = 0;
        if (titanIds != null) {
            for (UUID id : new ArrayList<>(titanIds)) {
                net.minecraft.world.entity.Entity titan = paradis.getEntity(id);
                if (titan != null && titan.isAlive() && !titan.isRemoved()) {
                    aliveTitans++;
                } else {
                    titanIds.remove(id);
                }
            }
        }
        int required = stats.getRescueTitansRequired();
        stats.setQuestTitansKilled(Math.max(0, required - aliveTitans));

        // 2. Кадеты — ищем ПО ТЕГУ прямо в мире, а не по карте cadetOwners.
        //
        // Карта живёт только в оперативной памяти сервера: перезаход, рестарт или любая
        // потеря записи оставляют кадетов в мире с тегом, но "ничьими" — и подсчёт выдавал
        // ноль при живых кадетах прямо перед носом. Тег на сущности переживает всё это,
        // поэтому он и есть надёжный источник истины. Найденных заодно привязываем обратно
        // к игроку, чтобы дальше снова работали события смерти и уборка после зачистки.
        BlockPos questTarget = new BlockPos(stats.getRescueTargetX(), stats.getRescueTargetY(), stats.getRescueTargetZ());
        AABB cadetBox = new AABB(questTarget).inflate(80.0, 60.0, 80.0);
        List<Villager> aliveCadetList = paradis.getEntitiesOfClass(Villager.class, cadetBox, v -> {
            if (!v.isAlive() || !v.getTags().contains(CADET_TAG)) return false;
            UUID knownOwner = cadetOwners.get(v.getUUID());
            // Чужого кадета (рядом квест другого игрока) не трогаем; безхозного считаем своим.
            return knownOwner == null || player.getUUID().equals(knownOwner);
        });
        for (Villager cadet : aliveCadetList) {
            cadetOwners.putIfAbsent(cadet.getUUID(), player.getUUID());
        }
        stats.setRescueCadetsAlive(Math.min(stats.getRescueCadetsTotal(), aliveCadetList.size()));

        // 3. Титанов не осталось — доводим до того же финала, что и штатное убийство
        //    последнего. Условие titanIds != null отсекает повторный запуск: после финала
        //    список удаляется, и второе нажатие уже ничего не подожжёт.
        if (aliveTitans == 0 && titanIds != null) {
            BlockPos target = questTarget;
            fireGreenFlare(paradis, target);
            despawnCadets(paradis, player);
            ownerTitans.remove(player.getUUID());
            clearLocator(player);
            player.sendSystemMessage(Component.translatable("message.maxonaddon.rescue.titans_cleared").withStyle(ChatFormatting.GREEN));
        }

        sendStatus(player, stats);
        syncGenericQuest(player, stats);
        player.displayClientMessage(
                Component.translatable("message.maxonaddon.rescue.refreshed", required - aliveTitans, required)
                        .withStyle(ChatFormatting.AQUA), true);
    }

    public static void abandon(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (player.level() instanceof ServerLevel level) {
            despawnCadets(level, player);
        }
        ownerTitans.remove(player.getUUID());
        clearLocator(player);
        clearRescueState(stats);
        sendStatus(player, stats);
    }

    private static void clearRescueState(IPlayerStats stats) {
        stats.setActiveQuest("", "");
        stats.setQuestTitansKilled(0);
        stats.setActiveQuestWall("");
        stats.setRescueTarget(0, 0, 0);
        stats.setRescueArrived(false);
        stats.setRescueTitansRequired(0);
        stats.setRescueCadetsTotal(0);
        stats.setRescueCadetsAlive(0);
        stats.setRescueDeadlineTick(0L);
    }
}

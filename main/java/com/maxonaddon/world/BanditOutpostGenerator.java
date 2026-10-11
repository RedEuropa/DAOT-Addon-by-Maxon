package com.maxonaddon.world;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.util.DistrictHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Редкие аванпосты разбойников на Парадизе — БЕЗ самих разбойников (это чисто структура
 * с лутом, никаких мобов не спавнится). Архитектура намеренно скопирована с
 * daot.world.TitanCountryStructureGenerator (капелла/утгард из базового мода): подписка на
 * ServerChunkEvents.CHUNK_LOAD + очередь, разбираемая маленькими порциями на
 * ServerTickEvents.END_SERVER_TICK, а не встраивание в ChunkGenerator — Paradis использует
 * свой кастомный генератор рельефа (ParadisChunkGenerator), и вписаться в его собственный
 * пайплайн структур сложнее и хрупче, чем просто ставить структуру постфактум по факту
 * загрузки чанка (ровно так и делает сам базовый мод для своих редких построек).
 * <p>
 * Шанс "5%" — не за каждый чанк (тогда башня стояла бы буквально на каждом шагу, чанк —
 * это всего 16х16 блоков), а за РЕГИОН REGION_SIZE_CHUNKS x REGION_SIZE_CHUNKS чанков:
 * для каждого региона (по seed мира + координатам региона, детерминированно) один раз
 * бросается кубик 0..99, и только 5 исходов из 100 дают аванпост — в среднем это башня
 * примерно на каждые ~850 блоков, что уже читается как "редкая находка", а не частокол.
 * <p>
 * Лут НЕ зашит в саму NBT структуры (проверено на живых примерах ваниллы — igloo/shipwreck
 * хранят в сундуках просто {id: "minecraft:chest"} без LootTable): вместо этого, как и
 * ванильные IglooPieces/ShipwreckPieces, здесь после постройки структуры отдельно находятся
 * оба сундука через StructureTemplate#filterBlocks и им назначается loot-таблица
 * maxon-daot-addon:chests/bandit_outpost через RandomizableContainerBlockEntity#setLootTable —
 * лут раскатывается один раз, при первом открытии, как у любого ванильного сундука в подземелье.
 */
public final class BanditOutpostGenerator {

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static final ResourceLocation STRUCTURE_ID =
            ResourceLocation.fromNamespaceAndPath(MaxonDaotAddon.MOD_ID, "bandit_outpost");
    private static final ResourceKey<LootTable> LOOT_TABLE_KEY =
            ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(MaxonDaotAddon.MOD_ID, "chests/bandit_outpost"));

    // Регион = 12x12 чанков (192x192 блока). 5% шанс на регион даёт в среднем ~850 блоков
    // между аванпостами (sqrt(1/0.05) * 192) — ощущается как редкая, но не абсурдно
    // редкая находка, сравнимо по духу с ванильным Pillager Outpost.
    private static final int REGION_SIZE_CHUNKS = 12;
    private static final int SPAWN_CHANCE_PERCENT = 5;

    // Не ставим ближе CITY_RADIUS (350) к округам — с запасом, чтобы башня не вылезала
    // на окраину города.
    private static final double MIN_DISTANCE_FROM_CITY = 400.0;
    // Максимальный перепад высоты по периметру footprint'а — иначе на резком склоне/обрыве
    // просто пропускаем эту точку (аванпост в этом регионе не появится).
    private static final int MAX_HEIGHT_VARIANCE = 4;
    private static final int MAX_FOUNDATION_DEPTH = 12;
    private static final int MAX_PROCESS_PER_TICK = 1;

    private static final Set<Long> processedRegions = new HashSet<>();
    private static final Queue<BlockPos> pendingPlacements = new ConcurrentLinkedQueue<>();
    private static ServerLevel cachedParadis;

    private BanditOutpostGenerator() {}

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (level.dimension() != PARADIS_KEY) return;
            if (!level.getGameRules().getBoolean(MaxonConfig.DO_BANDIT_OUTPOSTS)) return;

            ChunkPos pos = chunk.getPos();
            int regionX = Math.floorDiv(pos.x, REGION_SIZE_CHUNKS);
            int regionZ = Math.floorDiv(pos.z, REGION_SIZE_CHUNKS);
            long regionKey = ChunkPos.asLong(regionX, regionZ);
            if (!processedRegions.add(regionKey)) return;
            if (processedRegions.size() > 20000) processedRegions.clear();
            if (cachedParadis == null) cachedParadis = level;

            long hash = mixHash(level.getSeed(), regionX, regionZ);
            if (Math.floorMod(hash, 100L) >= SPAWN_CHANCE_PERCENT) return;

            long hash2 = mixHash(hash, regionX, regionZ);
            int regionBlockSize = REGION_SIZE_CHUNKS * 16;
            int localX = (int) Math.floorMod(hash2 >>> 8, (long) regionBlockSize);
            int localZ = (int) Math.floorMod(hash2 >>> 24, (long) regionBlockSize);
            int worldX = regionX * regionBlockSize + localX;
            int worldZ = regionZ * regionBlockSize + localZ;

            pendingPlacements.offer(new BlockPos(worldX, 0, worldZ));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cachedParadis == null || pendingPlacements.isEmpty()) return;
            for (int i = 0; i < MAX_PROCESS_PER_TICK; i++) {
                BlockPos target = pendingPlacements.poll();
                if (target == null) break;
                try {
                    tryPlace(cachedParadis, target);
                } catch (Exception e) {
                    MaxonDaotAddon.LOGGER.error("[BanditOutpostGenerator] Не удалось поставить аванпост у {}: {}", target, e.getMessage());
                }
            }
        });

        // На случай выхода в меню/переключения мира без перезапуска процесса — не тащим
        // ServerLevel предыдущего мира и не путаем регионы одного мира с другим (тот же
        // класс проблем, что чинили для локатора квеста доставки: "статика мода пережила
        // смену мира").
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            cachedParadis = null;
            processedRegions.clear();
            pendingPlacements.clear();
        });
    }

    private static long mixHash(long seed, int regionX, int regionZ) {
        long h = seed ^ ((long) regionX * 341873128712L) ^ ((long) regionZ * 132897987541L) ^ 987654321L;
        h = h * 6364136223846793005L + 1442695040888963407L;
        return h ^ (h >>> 31);
    }

    private static void tryPlace(ServerLevel level, BlockPos target) {
        if (DistrictHelper.isNearAnyDistrict(target.getX(), target.getZ(), MIN_DISTANCE_FROM_CITY)) return;

        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(STRUCTURE_ID);
        if (templateOpt.isEmpty()) return;
        StructureTemplate template = templateOpt.get();

        Rotation rotation = Rotation.getRandom(level.getRandom());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        if (groundY <= level.getSeaLevel()) return;

        // Позицию считаем через ту же bounding box, которой пользуется сам placeInWorld:
        // под поворотом 90°/270° локальный (0,0,0) НЕ является минимальным углом размещённой
        // структуры, поэтому наивное "origin = target - size/2" промахивалось бы мимо цели
        // (ровно та же ошибка, что чинилась в RescueQuestHandler — фундамент подсыпался не
        // под зданием, а рядом с ним).
        BoundingBox rawBox = template.getBoundingBox(settings, BlockPos.ZERO);
        int posX = target.getX() - rawBox.getCenter().getX();
        int posY = groundY - rawBox.minY();
        int posZ = target.getZ() - rawBox.getCenter().getZ();
        BlockPos origin = new BlockPos(posX, posY, posZ);
        BoundingBox placedBox = rawBox.moved(posX, posY, posZ);

        int minY = groundY;
        int maxY = groundY;
        int[][] samples = {
                {placedBox.minX(), placedBox.minZ()}, {placedBox.maxX(), placedBox.minZ()},
                {placedBox.minX(), placedBox.maxZ()}, {placedBox.maxX(), placedBox.maxZ()},
                {placedBox.getCenter().getX(), placedBox.getCenter().getZ()}
        };
        for (int[] s : samples) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s[0], s[1]);
            if (h <= level.getSeaLevel()) return; // угол/центр footprint'а упирается в воду
            minY = Math.min(minY, h);
            maxY = Math.max(maxY, h);
        }
        if (maxY - minY > MAX_HEIGHT_VARIANCE) return;

        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) return;

        fillFoundation(level, placedBox);
        assignLoot(level, template, origin, settings);
        spawnSurvivorOnRoof(level, placedBox);
    }

    /**
     * Раненый выживший на крыше башни — см. SurvivorQuestHandler: он сидит, пускает
     * фиолетовые сигнальные ракеты и предлагает игроку донести его до города.
     * Высоту берём heightmap'ом уже ПОСЛЕ постройки — это верх площадки крыши (зубцы стоят
     * только по краю кольца, центр свободен), поэтому работает независимо от поворота.
     */
    private static void spawnSurvivorOnRoof(ServerLevel level, BoundingBox box) {
        int cx = box.getCenter().getX();
        int cz = box.getCenter().getZ();
        int roofY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        com.maxonaddon.SurvivorQuestHandler.spawnWoundedSurvivor(level, new BlockPos(cx, roofY, cz));
    }

    /** Подсыпка под нижним этажом на случай небольшого перепада рельефа внутри допуска MAX_HEIGHT_VARIANCE. */
    private static void fillFoundation(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                p.set(x, box.minY() - 1, z);
                int filled = 0;
                while (filled < MAX_FOUNDATION_DEPTH && level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    p.move(Direction.DOWN);
                    filled++;
                }
            }
        }
    }

    private static void assignLoot(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> chests = template.filterBlocks(origin, settings, Blocks.CHEST);
        long lootSeed = level.getRandom().nextLong();
        for (StructureTemplate.StructureBlockInfo info : chests) {
            if (level.getBlockEntity(info.pos()) instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(LOOT_TABLE_KEY);
                container.setLootTableSeed(lootSeed ^ info.pos().asLong());
                container.setChanged();
            }
        }
    }
}

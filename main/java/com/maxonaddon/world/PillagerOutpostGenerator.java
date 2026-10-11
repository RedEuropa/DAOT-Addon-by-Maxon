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
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
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
 * Сторожевые вышки на Парадизе — ванильная башня Pillager Outpost, но без единого пиллагера:
 * это просто постройка с нашим лутом, на крыше которой дежурят три кадета с винтовками
 * и отстреливают подходящих титанов (см. OutpostCadetHandler).
 * <p>
 * Устройство один в один как у BanditOutpostGenerator: подписка на CHUNK_LOAD, очередь,
 * разбираемая по одной точке за тик, детерминированный бросок кубика на регион. Отличия
 * ровно три, и все — следствие того, что шаблон здесь ванильный, а не наш:
 * <ul>
 *   <li>берём {@code minecraft:pillager_outpost/watchtower} прямо из ресурсов игры;</li>
 *   <li>ставим JigsawReplacementProcessor — иначе служебные jigsaw-блоки, по которым ваниль
 *       пристраивает к башне палатки и клетки, остались бы торчать в стенах как обычные
 *       блоки (при ручной установке шаблона их никто не разбирает);</li>
 *   <li>{@code setIgnoreEntities(true)} — страховка от любых сущностей, зашитых в шаблон.
 *       Пиллагеров там и так нет: ванильные патрули приходят из spawn_overrides самой
 *       структуры, а мы структуру не регистрируем, только ставим её шаблон.</li>
 * </ul>
 */
public final class PillagerOutpostGenerator {

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    /** Ванильный шаблон башни — лежит в ресурсах самой игры, свой .nbt держать не нужно. */
    private static final ResourceLocation STRUCTURE_ID =
            ResourceLocation.withDefaultNamespace("pillager_outpost/watchtower");
    private static final ResourceKey<LootTable> LOOT_TABLE_KEY =
            ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(MaxonDaotAddon.MOD_ID, "chests/pillager_outpost"));

    // Те же 12x12 чанков, что у аванпоста разбойников, но шанс ниже: вышка с гарнизоном
    // должна быть заметно более редкой находкой, чем заброшенная башня с сундуком.
    private static final int REGION_SIZE_CHUNKS = 12;
    private static final int SPAWN_CHANCE_PERCENT = 3;

    private static final double MIN_DISTANCE_FROM_CITY = 400.0;
    private static final int MAX_HEIGHT_VARIANCE = 4;
    private static final int MAX_FOUNDATION_DEPTH = 12;
    private static final int MAX_PROCESS_PER_TICK = 1;

    private static final Set<Long> processedRegions = new HashSet<>();
    private static final Queue<BlockPos> pendingPlacements = new ConcurrentLinkedQueue<>();
    private static ServerLevel cachedParadis;

    private PillagerOutpostGenerator() {}

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (level.dimension() != PARADIS_KEY) return;
            if (!level.getGameRules().getBoolean(MaxonConfig.DO_CADET_OUTPOSTS)) return;

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
                    MaxonDaotAddon.LOGGER.error("[PillagerOutpostGenerator] Не удалось поставить вышку у {}: {}",
                            target, e.getMessage());
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            cachedParadis = null;
            processedRegions.clear();
            pendingPlacements.clear();
        });
    }

    /**
     * Константа в конце — не та, что в BanditOutpostGenerator, и это принципиально: с общей
     * солью обе постройки выпадали бы в одних и тех же регионах и на одних и тех же
     * координатах внутри них, то есть стояли бы вплотную друг к другу.
     */
    private static long mixHash(long seed, int regionX, int regionZ) {
        long h = seed ^ ((long) regionX * 341873128712L) ^ ((long) regionZ * 132897987541L) ^ 471102938L;
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
                .setIgnoreEntities(true)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
        if (groundY <= level.getSeaLevel()) return;

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
            if (h <= level.getSeaLevel()) return;
            minY = Math.min(minY, h);
            maxY = Math.max(maxY, h);
        }
        if (maxY - minY > MAX_HEIGHT_VARIANCE) return;

        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) return;

        fillFoundation(level, placedBox);
        assignLoot(level, template, origin, settings);
        spawnCadetsAtChest(level, template, origin, settings, placedBox);
    }

    /**
     * Три стрелка на том же ярусе, где стоит сундук — это крытый балкон вышки, а не скат
     * крыши: оттуда и обзор есть, и с площадки никого не сдувает.
     * <p>
     * Позицию берём у самого сундука, а не расчётом от габаритов: она уже учитывает и
     * поворот шаблона, и отметку, на которую в итоге встала башня.
     */
    private static void spawnCadetsAtChest(ServerLevel level, StructureTemplate template, BlockPos origin,
                                           StructurePlaceSettings settings, BoundingBox placedBox) {
        List<StructureTemplate.StructureBlockInfo> chests = template.filterBlocks(origin, settings, Blocks.CHEST);
        // Сундука не нашлось (чужой ресурспак подменил шаблон) — ставим по центру постройки,
        // чтобы вышка не осталась вовсе без гарнизона.
        BlockPos anchor = chests.isEmpty()
                ? new BlockPos(placedBox.getCenter().getX(),
                               level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                       placedBox.getCenter().getX(), placedBox.getCenter().getZ()),
                               placedBox.getCenter().getZ())
                : chests.get(0).pos();

        // Общий тег поста: по нему кадеты этой вышки считаются одним отрядом с общим
        // заданием (см. OutpostHuntQuestHandler).
        String groupTag = com.maxonaddon.OutpostHuntQuestHandler.GROUP_PREFIX
                + anchor.getX() + "_" + anchor.getZ();

        int placed = 0;
        for (int radius = 1; radius <= 4 && placed < 3; radius++) {
            for (int dx = -radius; dx <= radius && placed < 3; dx++) {
                for (int dz = -radius; dz <= radius && placed < 3; dz++) {
                    // На каждом радиусе обходим только его «кольцо» — внутренние клетки уже
                    // проверены на предыдущих шагах.
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    BlockPos spot = anchor.offset(dx, 0, dz);
                    if (!isStandable(level, spot.getX(), spot.getY(), spot.getZ())) continue;

                    com.maxonaddon.OutpostCadetHandler.spawnCadet(level, spot, groupTag);
                    placed++;
                }
            }
        }
    }

    /** Есть ли опора под ногами и два свободных блока над ней. */
    private static boolean isStandable(ServerLevel level, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

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

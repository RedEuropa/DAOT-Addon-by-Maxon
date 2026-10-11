package com.maxonaddon.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "daot.world.ParadisChunkGenerator")
public abstract class WorldBoundaryMixin {

    @Shadow public abstract boolean isInsideVillageWalls(int x, int z);
    @Shadow public abstract boolean isOnPathFast(int x, int z);

    @Inject(method = "method_12110", at = @At("TAIL"), remap = false)
    private void onBuildSurfaceParadis(WorldGenRegion region, StructureManager structureManager, RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        int topY = chunk.getMaxBuildHeight() - 1;
        int bottomY = chunk.getMinBuildHeight();

        generateUnderground(chunk, topY, bottomY);
        // Передаем region, чтобы получить Сид мира для цельных рек
        generateOrganicWaterAndLava(chunk, region, topY, bottomY);
    }

    private void generateOrganicWaterAndLava(ChunkAccess chunk, WorldGenRegion region, int topY, int bottomY) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        java.util.Random rand = new java.util.Random(chunk.getPos().toLong());

        // 1. ПОЛНОЦЕННЫЕ РЕКИ (Детерминированный алгоритм)
        generateRivers(chunk, region, topY, bottomY);

        // 2. Редкие озерца-кляксы (Сильно уменьшен шанс: раз в 10 чанков + 10% вероятность)
        if (Math.abs(chunkX % 10) == 0 && Math.abs(chunkZ % 10) == 0 && rand.nextInt(10) == 0) {
            generateBlobLake(chunk, startX, startZ, topY, bottomY, rand);
        }

        // 3. Подземная лава (Лава-пады и озера)
        if (Math.abs(chunkX % 4) == 2 && Math.abs(chunkZ % 4) == 2 && rand.nextInt(4) == 0) {
            generateCaveLava(chunk, startX, startZ, bottomY, rand);
        }
    }

    // --- АЛГОРИТМ РЕК ---
// --- АЛГОРИТМ РЕК ---
    private void generateRivers(ChunkAccess chunk, WorldGenRegion region, int topY, int bottomY) {
        long seed = region.getSeed();
        int currentChunkX = chunk.getPos().x;
        int currentChunkZ = chunk.getPos().z;

        // Сетка 20x20 чанков (Гарантирует дистанцию между руслами)
        int gridX = Math.floorDiv(currentChunkX, 20);
        int gridZ = Math.floorDiv(currentChunkZ, 20);

        // Проверяем текущий квадрат 20х20 и 8 соседних (на случай, если река притекла оттуда)
        for (int gx = gridX - 1; gx <= gridX + 1; gx++) {
            for (int gz = gridZ - 1; gz <= gridZ + 1; gz++) {
                // Сид квадрата для жесткой привязки к миру
                java.util.Random riverRand = new java.util.Random(seed ^ (gx * 341873128712L + gz * 132897987541L));

                // Шанс старта реки 20%
                if (riverRand.nextInt(100) >= 20) continue;

                // Стартовая точка
                int startChunkX = gx * 20 + riverRand.nextInt(20);
                int startChunkZ = gz * 20 + riverRand.nextInt(20);

                double curX = startChunkX * 16 + 8;
                double curZ = startChunkZ * 16 + 8;
                double angle = riverRand.nextDouble() * Math.PI * 2;

                int length = 1;

                // Симулируем путь реки
                while (true) {
                    int chunkStartX = chunk.getPos().getMinBlockX();
                    int chunkStartZ = chunk.getPos().getMinBlockZ();

                    // Если узел реки проходит рядом с нашим генерируемым чанком - копаем
                    if (Math.abs(curX - (chunkStartX + 8)) < 32 && Math.abs(curZ - (chunkStartZ + 8)) < 32) {
                        carveRiverNode(chunk, (int)curX, (int)curZ, topY, bottomY, riverRand);
                    }

                    length++;

                    // Логика длины: первые 4 чанка строятся со 100% вероятностью
                    if (length > 4) {
                        int survivalChance = 0;

                        if (length == 5) {
                            survivalChance = 75;      // Удлинение на 1
                        } else if (length == 6) {
                            survivalChance = 50;      // Удлинение на 2
                        } else if (length == 7) {
                            survivalChance = 25;      // Удлинение на 3
                        } else if (length == 8) {
                            survivalChance = 10;      // Удлинение на 4
                        } else {
                            survivalChance = 5;       // Удлинение на 5 и дальше
                        }

                        // Бросаем кубик (0-99). Если выпало больше или равно шансу - река иссякла
                        if (riverRand.nextInt(100) >= survivalChance) {
                            break;
                        }
                    }

                    // Изгиб реки (меняем угол) и шаг в 16 блоков (1 чанк)
                    angle += (riverRand.nextDouble() - 0.5) * 1.5;
                    curX += Math.cos(angle) * 16.0;
                    curZ += Math.sin(angle) * 16.0;
                }
            }
        }
    }
    private void carveRiverNode(ChunkAccess chunk, int cx, int cz, int topY, int bottomY, java.util.Random rand) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();

        int minY = topY;
        int maxY = bottomY;
        boolean hitWallOrPath = false;

        // 1. Проверяем окружение и ищем самую низкую точку русла
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = startX + lx;
                int worldZ = startZ + lz;

                double dist = Math.pow(worldX - cx, 2) + Math.pow(worldZ - cz, 2);
                if (dist <= 10 * 10) {
                    if (isOnPathFast(worldX, worldZ) || isCityAreaForWorldgen(worldX, worldZ, 8)) {
                        hitWallOrPath = true;
                        break;
                    }
                    int y = getTrueGroundY(chunk, worldX, worldZ, topY, bottomY);
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
            if (hitWallOrPath) break;
        }

        if (hitWallOrPath || maxY - minY > 8) return;

        int waterLevel = minY - 1;

        // 2. Копаем и заливаем
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int worldX = startX + lx;
                int worldZ = startZ + lz;

                double dist = Math.sqrt(Math.pow(worldX - cx, 2) + Math.pow(worldZ - cz, 2));
                double radius = 5.0 + Math.sin(worldX * 0.2) * 2.0;

                if (dist <= radius) {
                    int localY = getTrueGroundY(chunk, worldX, worldZ, topY, bottomY);

                    for (int clearY = localY; clearY <= localY + 15 && clearY < topY; clearY++) {
                        chunk.setBlockState(new BlockPos(worldX, clearY, worldZ), Blocks.AIR.defaultBlockState(), false);
                    }
                    for (int y = localY; y > waterLevel; y--) {
                        chunk.setBlockState(new BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState(), false);
                    }

                    int depth = 3;
                    for (int y = waterLevel; y > waterLevel - depth; y--) {
                        BlockPos wPos = new BlockPos(worldX, y, worldZ);
                        chunk.setBlockState(wPos, Blocks.WATER.defaultBlockState(), false);
                        // МАГИЯ ЗДЕСЬ: Заставляем воду реки обновляться и растекаться!
                        chunk.markPosForPostprocessing(wPos);
                    }

                    BlockState bottom = (rand.nextBoolean()) ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    chunk.setBlockState(new BlockPos(worldX, waterLevel - depth, worldZ), bottom, false);

                } else if (dist <= radius + 2.0) {
                    int localY = getTrueGroundY(chunk, worldX, worldZ, topY, bottomY);
                    if (localY >= waterLevel) {
                        chunk.setBlockState(new BlockPos(worldX, localY, worldZ), Blocks.DIRT.defaultBlockState(), false);
                        BlockPos upPos = new BlockPos(worldX, localY + 1, worldZ);
                        if (!chunk.getBlockState(upPos).isAir()) {
                            chunk.setBlockState(upPos, Blocks.AIR.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }
    // --- ОЗЕРА (КЛЯКСЫ) И ЛАВА ---
    private void generateBlobLake(ChunkAccess chunk, int startX, int startZ, int topY, int bottomY, java.util.Random rand) {
        int cx = 8;
        int cz = 8;
        double baseRadius = 3.5 + rand.nextDouble() * 2.0;
        double phase1 = rand.nextDouble() * Math.PI * 2;
        double freq1 = 2 + rand.nextInt(3);
        double amp1 = 1.0 + rand.nextDouble() * 2.0;

        int minY = topY;
        int maxY = bottomY;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                double dx = lx - cx;
                double dz = lz - cz;
                double angle = Math.atan2(dz, dx);

                double radiusAtAngle = baseRadius + Math.sin(angle * freq1 + phase1) * amp1;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist <= radiusAtAngle + 1.5) {
                    int worldX = startX + lx;
                    int worldZ = startZ + lz;
                    if (isOnPathFast(worldX, worldZ) || isCityAreaForWorldgen(worldX, worldZ, 8)) return;
                    if (dist <= radiusAtAngle) {
                        int y = getTrueGroundY(chunk, worldX, worldZ, topY, bottomY);
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
        }

        if (maxY - minY > 5) return;

        int waterLevel = minY - 1;
        int depth = 2 + rand.nextInt(2);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                double dx = lx - cx;
                double dz = lz - cz;
                double angle = Math.atan2(dz, dx);

                double radiusAtAngle = baseRadius + Math.sin(angle * freq1 + phase1) * amp1;
                double dist = Math.sqrt(dx * dx + dz * dz);

                int worldX = startX + lx;
                int worldZ = startZ + lz;
                int localY = getTrueGroundY(chunk, worldX, worldZ, topY, bottomY);

                if (dist <= radiusAtAngle) {
                    for (int clearY = localY; clearY <= localY + 15 && clearY < topY; clearY++) {
                        chunk.setBlockState(new BlockPos(worldX, clearY, worldZ), Blocks.AIR.defaultBlockState(), false);
                    }
                    for (int y = localY; y > waterLevel; y--) {
                        chunk.setBlockState(new BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState(), false);
                    }
                    for (int y = waterLevel; y > waterLevel - depth; y--) {
                        BlockPos wPos = new BlockPos(worldX, y, worldZ);
                        chunk.setBlockState(wPos, Blocks.WATER.defaultBlockState(), false);
                        // МАГИЯ ЗДЕСЬ: Заставляем воду озер обновляться и растекаться!
                        chunk.markPosForPostprocessing(wPos);
                    }
                    chunk.setBlockState(new BlockPos(worldX, waterLevel - depth, worldZ), Blocks.DIRT.defaultBlockState(), false);

                } else if (dist <= radiusAtAngle + 1.5 && localY >= waterLevel) {
                    BlockPos pos = new BlockPos(worldX, localY, worldZ);
                    chunk.setBlockState(pos, Blocks.SAND.defaultBlockState(), false);
                    BlockPos upPos = pos.above();
                    if (!chunk.getBlockState(upPos).isAir()) chunk.setBlockState(upPos, Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }
    /**
     * Полностью отменяем спавн мобов на этапе генерации чанка в Парадизе.
     * <p>
     * Именно отсюда берутся животные, зависшие в воздухе над стеной и лесом. Сущности,
     * созданные во время генерации (MobSpawnType.CHUNK_GENERATION, вне основного тика
     * сервера), получаются "мёртвыми": без гравитации и работающего ИИ, OnGround: 0b,
     * Motion: [0,0,0] — где поставили, там и висят. Наш собственный декоративный спавн из-за
     * этого выключили ещё раньше, но ванильная половина метода продолжала работать и
     * спавнить животных по биомным спискам — а список мы недавно расширили овцами, свиньями,
     * коровами и лошадьми, отчего висящих стало заметно больше.
     * <p>
     * Ничего не теряем: NaturalSpawner доспавнит ту же живность штатно, как только чанк
     * реально загрузится и начнёт тикаться — тогда и физика, и ИИ у неё будут настоящими.
     * <p>
     * ParadisAnimalUnstucker при этом оставлен: он лечит тех, кто уже осел в старых чанках.
     */
    @Inject(method = "method_12107", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSpawnOriginalMobs(WorldGenRegion region, CallbackInfo ci) {
        ci.cancel();
    }

    private void generateCaveLava(ChunkAccess chunk, int startX, int startZ, int bottomY, java.util.Random rand) {
        int cx = 8 + rand.nextInt(6) - 3;
        int cz = 8 + rand.nextInt(6) - 3;

        // Лава-пад: Помечаем блок, чтобы лава растекалась вниз после спавна
        if (rand.nextInt(100) < 40) {
            for (int y = bottomY + 45; y > bottomY + 10; y--) {
                BlockPos pos = new BlockPos(startX + cx, y, startZ + cz);
                BlockState aboveState = chunk.getBlockState(pos.above());

                if (chunk.getBlockState(pos).isAir() && (aboveState.is(Blocks.STONE) || aboveState.is(Blocks.DEEPSLATE))) {
                    chunk.setBlockState(pos, Blocks.LAVA.defaultBlockState(), false);
                    chunk.markPosForPostprocessing(pos);
                    return;
                }
            }
            return;
        }

        // Озеро лавы на полу пещеры
        int y = bottomY + 10 + rand.nextInt(25);
        BlockPos center = new BlockPos(startX + cx, y, startZ + cz);
        if (!chunk.getBlockState(center).isAir()) return;

        int floorY = y;
        while (floorY > bottomY && chunk.getBlockState(new BlockPos(startX + cx, floorY, startZ + cz)).isAir()) {
            floorY--;
        }

        double baseRadius = 2.0 + rand.nextDouble();
        double phase = rand.nextDouble() * Math.PI * 2;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                double dx = lx - cx;
                double dz = lz - cz;
                double angle = Math.atan2(dz, dx);

                double radiusAtAngle = baseRadius + Math.sin(angle * 3 + phase) * 1.0;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist <= radiusAtAngle) {
                    int worldX = startX + lx;
                    int worldZ = startZ + lz;

                    for (int dy = 0; dy >= -1; dy--) {
                        BlockPos pos = new BlockPos(worldX, floorY + dy, worldZ);
                        BlockState state = chunk.getBlockState(pos);

                        if (dy == 0 && (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE))) {
                            chunk.setBlockState(pos.above(), Blocks.AIR.defaultBlockState(), false);
                            chunk.setBlockState(pos, Blocks.LAVA.defaultBlockState(), false);
                        } else if (dy == -1 && (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.isAir())) {
                            chunk.setBlockState(pos, Blocks.LAVA.defaultBlockState(), false);
                        }
                    }
                }
            }
        }
    }

    private int getTrueGroundY(ChunkAccess chunk, int x, int z, int topY, int bottomY) {
        int y = topY;
        while (y > bottomY) {
            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
            if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.STONE) ||
                    state.is(Blocks.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.PODZOL)) {
                return y;
            }
            y--;
        }
        return bottomY;
    }

    // --- ГЕНЕРАЦИЯ ДЛИННЫХ ТУННЕЛЕЙ И ВОРОНОК ---
    private void generateUnderground(ChunkAccess chunk, int topY, int bottomY) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        java.util.Random rand = new java.util.Random(chunk.getPos().toLong());

        // 1. Непрерывные трубчатые туннели (начинаются под землей, поэтому траву не трогают)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                // isInsideVillageWalls — старая защита стен; DistrictHelper — наши 12 реальных
                // округов (см. CarriageSpawner.SPAWN_POINTS), радиус 300 блоков — не роем под городом.
                if (isCityAreaForWorldgen(worldX, worldZ, 0)) continue;
                int surfaceY = topY;
                while (surfaceY > bottomY && chunk.getBlockState(new BlockPos(worldX, surfaceY, worldZ)).isAir()) {
                    surfaceY--;
                }

                double mask = Math.sin(worldX * 0.01) + Math.cos(worldZ * 0.01);
                if (mask > 0.5) {
                    for (int y = surfaceY - 5; y > bottomY + 2; y--) {
                        double nx = worldX * 0.02 + Math.sin(y * 0.05);
                        double ny = y * 0.03;
                        double nz = worldZ * 0.02 + Math.cos(y * 0.05);

                        double val1 = Math.sin(nx) + Math.cos(ny) + Math.sin(nz);
                        double val2 = Math.cos(nx) + Math.sin(ny) + Math.cos(nz);

                        if (Math.abs(val1) < 0.5 && Math.abs(val2) < 0.5) {
                            BlockPos pos = new BlockPos(worldX, y, worldZ);
                            BlockState state = chunk.getBlockState(pos);

                            if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA)
                                    && !state.is(Blocks.SAND) && !state.is(Blocks.SANDSTONE)
                                    && !state.is(Blocks.STONE_BRICKS) && !state.is(Blocks.CRACKED_STONE_BRICKS)) { // <--- Добавлено
                                chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                            }
                        }
                    }
                }
            }
        }

        // 2. Спуски (Карстовые провалы) - ВОТ ЗДЕСЬ ИСПРАВЛЕНА ЛЕВИТИРУЮЩАЯ ТРАВА
        if (rand.nextInt(25) == 0) {
            int cx = rand.nextInt(16);
            int cz = rand.nextInt(16);
            int worldX = startX + cx;
            int worldZ = startZ + cz;

            // Воронка смещается на 5 блоков по синусоиде и имеет радиус до 6, то есть
            // расходится от центра примерно на 11 блоков. Проверять одну точку было мало —
            // провал начинался снаружи и выгрызал край города, отсюда чёрные ямы у стены.
            if (!isCityAreaForWorldgen(worldX, worldZ, 16)) {
                int surfaceY = topY;
                while (surfaceY > bottomY && chunk.getBlockState(new BlockPos(worldX, surfaceY, worldZ)).isAir()) {
                    surfaceY--;
                }

                if (surfaceY > 51) {
                    int depth = rand.nextInt(40) + 20;
                    double radius = 4.0 + rand.nextDouble() * 2.0;

                    for (int y = surfaceY; y > surfaceY - depth && y > bottomY + 2; y--) {
                        int ox = cx + (int)(Math.sin(y * 0.1) * 5);
                        int oz = cz + (int)(Math.cos(y * 0.1) * 5);

                        for (int dx = -8; dx <= 8; dx++) {
                            for (int dz = -8; dz <= 8; dz++) {
                                if (dx * dx + dz * dz <= radius * radius) {
                                    int lx = ox + dx;
                                    int lz = oz + dz;

                                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                                        int wx = startX + lx;
                                        int wz = startZ + lz;

                                        // МАГИЯ ЗДЕСЬ: Если мы вырезаем самый верхний блок воронки,
                                        // очищаем над ним столб воздуха, чтобы трава и деревья упали/уничтожились
                                        if (y == surfaceY) {
                                            for (int clearY = surfaceY + 1; clearY <= surfaceY + 15 && clearY < topY; clearY++) {
                                                BlockPos clearPos = new BlockPos(wx, clearY, wz);
                                                if (!chunk.getBlockState(clearPos).isAir()) {
                                                    chunk.setBlockState(clearPos, Blocks.AIR.defaultBlockState(), false);
                                                }
                                            }
                                        }

                                        BlockPos pos = new BlockPos(wx, y, wz);
                                        BlockState state = chunk.getBlockState(pos);

                                        if (!state.isAir() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA)
                                                && !state.is(Blocks.SAND) && !state.is(Blocks.SANDSTONE)
                                                && !state.is(Blocks.STONE_BRICKS) && !state.is(Blocks.CRACKED_STONE_BRICKS)) { // <--- Добавлено
                                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                                        }
                                    }
                                }
                            }
                        }
                        radius -= 0.05;
                        if (radius < 2.0) radius = 2.0;
                    }
                }
            }
        }

        // 3. Генерация новых руд
        generateVein(chunk, Blocks.COAL_ORE.defaultBlockState(), 10, 6, 0, 80, rand, startX, startZ);
        generateVein(chunk, Blocks.IRON_ORE.defaultBlockState(), 8, 5, -20, 60, rand, startX, startZ);
        generateVein(chunk, Blocks.COPPER_ORE.defaultBlockState(), 6, 5, -10, 50, rand, startX, startZ);
        generateVein(chunk, Blocks.GOLD_ORE.defaultBlockState(), 3, 4, -40, 30, rand, startX, startZ);
        generateVein(chunk, Blocks.LAPIS_ORE.defaultBlockState(), 2, 4, -60, 30, rand, startX, startZ);
        generateVein(chunk, Blocks.REDSTONE_ORE.defaultBlockState(), 4, 4, -60, 15, rand, startX, startZ);
        generateVein(chunk, Blocks.DIAMOND_ORE.defaultBlockState(), 2, 3, -60, 12, rand, startX, startZ);
    }

    private void generateVein(ChunkAccess chunk, BlockState ore, int veinsPerChunk, int veinSize, int minY, int maxY, java.util.Random rand, int minX, int minZ) {
        for (int i = 0; i < veinsPerChunk; i++) {
            int startX = rand.nextInt(16);
            int startZ = rand.nextInt(16);
            int startY = minY + rand.nextInt(Math.max(1, maxY - minY + 1));

            for (int v = 0; v < veinSize; v++) {
                int offsetX = startX + rand.nextInt(3) - 1;
                int offsetZ = startZ + rand.nextInt(3) - 1;
                int offsetY = startY + rand.nextInt(3) - 1;

                if (offsetX >= 0 && offsetX <= 15 && offsetZ >= 0 && offsetZ <= 15) {
                    BlockPos pos = new BlockPos(minX + offsetX, offsetY, minZ + offsetZ);
                    BlockState current = chunk.getBlockState(pos);

                    if (current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE)) {
                        chunk.setBlockState(pos, ore, false);
                    }
                }
            }
        }
    }
    /**
     * Единая проверка "тут город / стена, копать и лить воду нельзя".
     * isInsideVillageWalls берёт строгий интерьер округа, DistrictHelper.isCityOrNear
     * добавляет к нему саму стену, мега-кольца и запас margin. Раньше защита опиралась
     * только на круг радиусом 400 вокруг центра округа, а округ в генераторе — это
     * 800 на 600 блоков с выступом наружу, поэтому его углы и окраины оставались
     * незащищёнными: именно там в городе и появлялись вода и провалы.
     */
    private boolean isCityAreaForWorldgen(int x, int z, int margin) {
        return isInsideVillageWalls(x, z)
                || isInsideMegaWall(x, z)
                || com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, margin);
    }

    private boolean isInsideMegaWall(int x, int z) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        // Проверяем дистанцию до колец 1000, 7000 и 13000 с запасом +-64 блока
        return Math.abs(dist - 1000.0) <= 64.0 ||
                Math.abs(dist - 7000.0) <= 64.0 ||
                Math.abs(dist - 13000.0) <= 64.0;
    }
}
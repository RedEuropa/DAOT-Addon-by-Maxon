package com.maxonaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public class ForestLocator {

    private static BlockPos cachedForestPos = null;
    private static boolean searchAttempted = false;

    public static BlockPos getOrFindForest(ServerLevel level, BlockPos npcPos) {
        if (cachedForestPos != null) {
            return cachedForestPos;
        }
        if (searchAttempted) {
            return null;
        }

        searchAttempted = true;
        ResourceKey<Biome> forestKey = ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("dannys-aot", "giant_forest")
        );

        System.out.println("[MaxonAddon] Запуск спирального поиска Леса от позиции НПС: " + npcPos);

        // Идем спиралью от НПС с шагом 320 блоков в радиусе до 30 000 блоков
        int step = 320;
        int maxRadius = 30000;

        for (int r = step; r <= maxRadius; r += step) {
            for (int x = -r; x <= r; x += step) {
                for (int z = -r; z <= r; z += step) {
                    // Проверяем только внешнее кольцо спирали
                    if (Math.abs(x) == r || Math.abs(z) == r) {
                        int checkX = npcPos.getX() + x;
                        int checkZ = npcPos.getZ() + z;

                        BlockPos testPos = new BlockPos(checkX, 80, checkZ);

                        // Получаем держатель биома в точке
                        Holder<Biome> biomeHolder = level.getBiome(testPos);

                        // Безопасная проверка через unwrapKey()
                        boolean isForest = biomeHolder.unwrapKey()
                                .map(key -> key.equals(forestKey))
                                .orElse(false);

                        if (isForest) {
                            // Принудительно загружаем чанк и вычисляем точную высоту земли
                            level.getChunk(checkX >> 4, checkZ >> 4, ChunkStatus.FULL, true);
                            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ);

                            cachedForestPos = new BlockPos(checkX, groundY + 1, checkZ);
                            System.out.println("[MaxonAddon] Лес успешно найден по координатам: " + cachedForestPos);
                            return cachedForestPos;
                        }
                    }
                }
            }
        }

        System.out.println("[MaxonAddon] Лес не найден в радиусе поиска!");
        return null;
    }

    public static BlockPos getCachedForestPos() {
        return cachedForestPos;
    }
}
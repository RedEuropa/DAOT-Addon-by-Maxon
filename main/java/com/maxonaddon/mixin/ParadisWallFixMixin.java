package com.maxonaddon.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "daot.world.ParadisChunkGenerator")
public abstract class ParadisWallFixMixin {

    // ВНИМАНИЕ: WALL_INNER_RADIUS_BASE_SQ / WALL_OUTER_RADIUS_SQ — это радиусы стены ВОКРУГ
    // ОТДЕЛЬНОГО ОКРУГА (290 и 290+DISTRICT_WALL_THICKNESS, т.е. ~305 блоков), а вовсе не
    // мега-стен. Мега-стены строятся по RING_RADII = {1000, 7000, 13000} от центра мира,
    // и именно по этим кольцам generateMegaWalls сам решает, попадает ли чанк в стену.
    // Здесь берём готовые границы колец в квадратах — те же, что использует оригинал.
    @Shadow(remap = false) private static long[] RING_OUTER_SQ;
    @Shadow(remap = false) private static long[] RING_INNER_BASE_SQ;

    /**
     * ОПТИМИЗАЦИЯ СТЕН: Отсекаем пустые чанки
     */
    @Inject(
            method = "generateMegaWalls",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void optimizeMegaWalls(WorldGenRegion region, int chunkX, int chunkZ, CallbackInfo ci) {
        if (!shouldGenerateWallsInChunk(chunkX, chunkZ)) {
            ci.cancel();
        }
    }

    private boolean shouldGenerateWallsInChunk(int chunkX, int chunkZ) {
        // Не смогли прочитать границы колец — не мешаем оригиналу работать.
        if (RING_OUTER_SQ == null || RING_INNER_BASE_SQ == null) return true;

        long startX = chunkX * 16L;
        long startZ = chunkZ * 16L;
        long endX = startX + 15L;
        long endZ = startZ + 15L;

        // Мега-стены — концентрические кольца вокруг (0,0), поэтому меряем от центра мира.
        long closestX = (startX <= 0 && endX >= 0) ? 0 : Math.min(Math.abs(startX), Math.abs(endX));
        long closestZ = (startZ <= 0 && endZ >= 0) ? 0 : Math.min(Math.abs(startZ), Math.abs(endZ));
        double minDist = Math.sqrt((double) (closestX * closestX + closestZ * closestZ));

        long furthestX = Math.max(Math.abs(startX), Math.abs(endX));
        long furthestZ = Math.max(Math.abs(startZ), Math.abs(endZ));
        double maxDist = Math.sqrt((double) (furthestX * furthestX + furthestZ * furthestZ));

        // Запас на гребни/декор, которые вылезают за формальную границу кольца.
        double margin = 64.0;

        int rings = Math.min(RING_OUTER_SQ.length, RING_INNER_BASE_SQ.length);
        for (int i = 0; i < rings; i++) {
            double outer = Math.sqrt((double) RING_OUTER_SQ[i]) + margin;
            double inner = Math.sqrt((double) RING_INNER_BASE_SQ[i]) - margin;

            // Чанк нужен, если его диапазон расстояний пересекается с полосой кольца.
            if (minDist <= outer && maxDist >= inner) {
                return true;
            }
        }
        return false;
    }

    /**
     * ГЛОБАЛЬНЫЙ ФИКС "far chunk": Перехватываем ВСЕ методы генератора Парадиза.
     * Если структура (дерево, стена, дом) лезет за границу — обрубаем.
     * Она достроится сама, когда дойдет очередь до соседнего чанка.
     */
    @Redirect(
            method = {
                    "buildSurface",
                    "placeStructure",
                    "placeStructureChunkAware",
                    "placeStructureFoundation",
                    "placeVillageHouseFoundation",
                    "placeVillageHouseFoundationFromBounds",
                    "generateVillageStructures",
                    "generateMiniPaths",
                    "drawMiniPathSegment",
                    "placeMiniPathBlock",
                    "generateLargeOakTree",
                    "generateLeafCluster",
                    "generatePineTree",
                    "generateGiantTree",
                    "generateThickDreadlock",
                    "addGiantTreeGroundVegetation",
                    "generateBush",
                    "generateSmallPineTree",
                    "generateOreVeins",
                    "generateOreVein",
                    "placeWallColumn",
                    "placeRidgeDecoration",
                    "generateVillageWalls",
                    "placeDistrictWallRidges",
                    "generateMegaWalls",
                    "placeMegaWallRidges",
                    "generateRadialCrossroads",
                    "generatePaths",
                    "generateVillageGround",
                    "generateVillageCrossroads",
                    "generateSmallPonds",
                    "generateIronBambooPatch"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_3233;method_8652(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;I)Z"
            ),
            require = 0,
            remap = false
    )
    private boolean redirectRegionSetBlock(WorldGenRegion instance, BlockPos pos, BlockState state, int flags) {
        // Разрешаем писать только в текущий генерируемый чанк
        if ((pos.getX() >> 4) == instance.getCenter().x && (pos.getZ() >> 4) == instance.getCenter().z) {
            return instance.setBlock(pos, state, flags);
        }
        return false;
    }

    /**
     * Страховочный перехватчик для тех же методов, если они используют ChunkAccess
     */
    @Redirect(
            method = {
                    "buildSurface", "placeStructure", "placeStructureChunkAware", "placeStructureFoundation",
                    "placeVillageHouseFoundation", "placeVillageHouseFoundationFromBounds", "generateVillageStructures",
                    "generateMiniPaths", "drawMiniPathSegment", "placeMiniPathBlock", "generateLargeOakTree",
                    "generateLeafCluster", "generatePineTree", "generateGiantTree", "generateThickDreadlock",
                    "addGiantTreeGroundVegetation", "generateBush", "generateSmallPineTree",
                    "generateOreVeins", "generateOreVein", "placeWallColumn", "placeRidgeDecoration",
                    "generateVillageWalls", "placeDistrictWallRidges", "generateMegaWalls", "placeMegaWallRidges",
                    "generateRadialCrossroads", "generatePaths", "generateVillageGround", "generateVillageCrossroads",
                    "generateSmallPonds", "generateIronBambooPatch"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_2791;method_12010(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Z)Lnet/minecraft/class_2680;"
            ),
            require = 0,
            remap = false
    )
    private BlockState redirectChunkSetBlockState(ChunkAccess instance, BlockPos pos, BlockState state, boolean isMoving) {
        if ((pos.getX() >> 4) == instance.getPos().x && (pos.getZ() >> 4) == instance.getPos().z) {
            return instance.setBlockState(pos, state, isMoving);
        }
        return null;
    }
    /**
     * Отступ, с которым отменяется дерево: его собственный радиус кроны плюс запас.
     * <p>
     * Раньше здесь стояла фиксированная восьмёрка — из-за неё и висела листва в городах.
     * Дерево с кроной шире восьми блоков росло снаружи (ствол в город не попадал, отмена не
     * срабатывала), а крона перелезала через границу — и оставалась там одна, потому что
     * ствол-то стоял за городом. Считаем от настоящего радиуса: тогда до города не
     * дотянется ни одна ветка.
     */
    @Unique
    private static int maxon$treeMargin(int radius) {
        return Math.max(8, radius + 2);
    }

    @Inject(method = "generateGiantTree", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelGiantTree(WorldGenRegion region, int x, int y, int z, int height, int radius, CallbackInfo ci) {
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, maxon$treeMargin(radius))) ci.cancel();
    }

    @Inject(method = "generatePineTree", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelPineTree(WorldGenRegion region, int x, int y, int z, long seed, int height, int radius, CallbackInfo ci) {
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, maxon$treeMargin(radius))) ci.cancel();
    }

    @Inject(method = "generateLargeOakTree", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelOakTree(WorldGenRegion region, int x, int y, int z, java.util.Random rand, int height, int radius, CallbackInfo ci) {
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, maxon$treeMargin(radius))) ci.cancel();
    }
    /**
     * Трава, папоротники, мох и цветы внутри города.
     *
     * Раньше здесь стояли отмены по @At("HEAD"), но addVegetation и addClusterVegetation
     * принимают КООРДИНАТЫ ЧАНКА, а не блока (в buildSurface им передаются chunk.getPos().x/z).
     * Мы же скармливали эти числа в isTooCloseForCaves, который сравнивает их с блочными
     * центрами округов (0, -13273 и т.п.). Чанк (0, -849) "отстоял" от округа на 12 с лишним
     * тысяч — условие не выполнялось НИ РАЗУ, и отмена не работала вообще нигде.
     *
     * Вместо отмены всего чанка целиком фильтруем каждую травинку по её собственной позиции:
     * внутри города не ставим, за его пределами оставляем как было — иначе вокруг стен
     * появлялась бы неестественная лысая полоса шириной в чанк.
     */
    @Redirect(
            method = {"addVegetation", "addClusterVegetation"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_3233;method_8652(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;I)Z"
            ),
            require = 0,
            remap = false
    )
    private boolean redirectVegetationSetBlock(WorldGenRegion instance, BlockPos pos, BlockState state, int flags) {
        if ((pos.getX() >> 4) != instance.getCenter().x || (pos.getZ() >> 4) != instance.getCenter().z) return false;
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(pos.getX(), pos.getZ(), 0)) return false;
        return instance.setBlock(pos, state, flags);
    }

    @Inject(method = "generateLeafCluster", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelLeafCluster(WorldGenRegion region, int x, int y, int z, int size, BlockState state, int p1, int p2, java.util.Random rand, CallbackInfo ci) {
        // Кластер листвы — это крона без ствола, ей запас нужен тем более.
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, maxon$treeMargin(size))) ci.cancel();
    }

    @Inject(method = "generateBush", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelBush(WorldGenRegion region, int x, int y, int z, int p1, int p2, BlockState state, java.util.Random rand, CallbackInfo ci) {
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, 8)) ci.cancel();
    }

    @Inject(method = "generateSmallPineTree", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelSmallPine(WorldGenRegion region, int x, int y, int z, long p1, int p2, int p3, CallbackInfo ci) {
        if (com.maxonaddon.util.DistrictHelper.isCityOrNear(x, z, 8)) ci.cancel();
    }
}
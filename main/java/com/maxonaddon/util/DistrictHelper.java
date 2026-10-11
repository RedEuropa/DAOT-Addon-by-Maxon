package com.maxonaddon.util;

import com.maxonaddon.event.CarriageSpawner;
import net.minecraft.core.BlockPos;

/**
 * "Город" в этом мире — это не окрестности (0,0) (там реального округа вообще нет,
 * ближайший — на ~1300 блоков дальше), а 12 конкретных округов из CarriageSpawner.SPAWN_POINTS
 * (district_1..district_12), разбросанных по карте измерения dannys-aot:paradis.
 * Полиция и контрабандисты используют эту же геометрию, чтобы определить "я в городе".
 */
public final class DistrictHelper {

    public static final double CITY_RADIUS = 350.0;
    // Радиус, в котором генератор рельефа не должен рыть пещеры/тоннели/провалы под городом.
    public static final double NO_CAVES_RADIUS = 400.0;

    /** Радиусы мега-стен из ParadisChunkGenerator.RING_RADII. */
    private static final int[] MEGA_RING_RADII = {1000, 7000, 13000};

    private static final double CITY_RADIUS_SQ = CITY_RADIUS * CITY_RADIUS;
    private static final double NO_CAVES_RADIUS_SQ = NO_CAVES_RADIUS * NO_CAVES_RADIUS;

    private DistrictHelper() {}

    public static boolean isInAnyDistrict(double x, double z) {
        return isNear(x, z, CITY_RADIUS_SQ);
    }

    public static boolean isNearAnyDistrict(double x, double z, double radius) {
        return isNear(x, z, radius * radius);
    }

    /** Специально для генератора рельефа — не рыть пещеры под городом (радиус NO_CAVES_RADIUS). */
    public static boolean isTooCloseForCaves(double x, double z) {
        return isNear(x, z, NO_CAVES_RADIUS_SQ);
    }

    /**
     * Настоящая геометрия застройки из dannys-aot: интерьер округа вместе со стеной
     * плюс мега-кольца. Наши SPAWN_POINTS с радиусом 400 — это лишь грубый круг вокруг
     * центра округа, а сам округ в генераторе имеет размеры DISTRICT_WIDTH=800 на
     * DISTRICT_PROTRUSION=600, то есть его углы и выступы уходят от центра на ~500-600
     * блоков и в круг радиусом 400 не помещаются. Именно на этих окраинах и вылезали
     * трава, провалы и вода посреди города.
     */
    public static boolean isInsideCityGeometry(int x, int z) {
        return daot.world.ParadisChunkGenerator.isPositionInsideAnyVillage(x, z);
    }

    /**
     * "Здесь город или он совсем рядом" — основная проверка для генерации мира.
     * margin задаёт запас в блоках: воде он нужен, чтобы она не растекалась внутрь
     * с окраины, провалам — чтобы их воронка не подъедала край застройки.
     */
    public static boolean isCityOrNear(int x, int z, int margin) {
        if (!mayBeNearCity(x, z, margin)) return false;
        if (isTooCloseForCaves(x, z)) return true;
        if (isInsideCityGeometry(x, z)) return true;
        if (margin <= 0) return false;
        return isInsideCityGeometry(x + margin, z)
                || isInsideCityGeometry(x - margin, z)
                || isInsideCityGeometry(x, z + margin)
                || isInsideCityGeometry(x, z - margin);
    }

    /**
     * Дешёвый предфильтр: точная геометрия округов заметно дороже простой арифметики,
     * а вызывается она на каждый блок чанка. Округ целиком укладывается в ~1100 блоков
     * от своего центра (800 на 600 плюс выступ 600), мега-кольца — узкие полосы вокруг
     * 1000/7000/13000. Всё, что не попадает даже в эти щедрые границы, городом быть
     * не может, и дорогую проверку для него запускать незачем.
     */
    private static boolean mayBeNearCity(int x, int z, int margin) {
        double reach = 1500.0 + margin;
        if (isNear(x, z, reach * reach)) return true;

        double dist = Math.sqrt((double) x * x + (double) z * z);
        double ringBand = 200.0 + margin;
        for (int radius : MEGA_RING_RADII) {
            if (Math.abs(dist - radius) <= ringBand) return true;
        }
        return false;
    }

    private static boolean isNear(double x, double z, double radiusSq) {
        for (BlockPos center : CarriageSpawner.SPAWN_POINTS) {
            double dx = x - center.getX();
            double dz = z - center.getZ();
            if (dx * dx + dz * dz <= radiusSq) return true;
        }
        return false;
    }
}

package com.maxonaddon.util;

import com.maxonaddon.event.CarriageSpawner;
import net.minecraft.core.BlockPos;

/**
 * Цены проезда у проводника.
 *
 * Геометрия подтверждена: 12 округов CarriageSpawner.SPAWN_POINTS образуют РОВНО 4 "линии" —
 * оси +X/-X/+Z/-Z от центра карты — по 3 города на линию (ближний/средний/дальний тир).
 * Индекс округа в массиве SPAWN_POINTS = "district_N" - 1.
 *
 *   индекс: 0    1    2    3    4    5    6    7    8    9   10   11
 *   линия:  +X   -X   +Z   -Z   +Z   -Z   -X   +X   +X   -X   +Z   -Z
 *   тир:    0    0    0    0    1    1    1    1    2    2    2    2
 *
 * Правила цены (подтверждены пользователем):
 *  - та же линия, соседний тир (1 хоп)         -> 1 изумруд
 *  - та же линия, через тир (2 хопа, "проскочить") -> 2 изумруда
 *  - другая линия (любые тиры)                 -> 3 изумруда
 *  - до леса: 3 (ближний тир водителя) / 4 (средний) / 5 (дальний)
 */
public final class CarriagePricing {

    private static final int[] LINE_OF_DISTRICT = {0, 1, 2, 3, 2, 3, 1, 0, 0, 1, 2, 3};
    private static final int[] TIER_OF_DISTRICT = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2};

    private CarriagePricing() {}

    /** "district_N" -> N-1, либо -1 если строка не распознана. */
    public static int indexOf(String cityId) {
        if (cityId == null || !cityId.startsWith("district_")) return -1;
        try {
            int n = Integer.parseInt(cityId.substring("district_".length()));
            int idx = n - 1;
            return (idx >= 0 && idx < CarriageSpawner.SPAWN_POINTS.length) ? idx : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Цена в изумрудах между двумя округами. 0, если fromIdx==toIdx или индексы некорректны. */
    public static int priceBetweenDistricts(int fromIdx, int toIdx) {
        if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return 0;

        int lineFrom = LINE_OF_DISTRICT[fromIdx];
        int lineTo = LINE_OF_DISTRICT[toIdx];

        if (lineFrom != lineTo) return 3;

        int tierDiff = Math.abs(TIER_OF_DISTRICT[fromIdx] - TIER_OF_DISTRICT[toIdx]);
        return Math.max(1, tierDiff); // 1 хоп = 1, 2 хопа (проскочить) = 2
    }

    /** Цена в изумрудах до леса от округа с индексом fromIdx. */
    public static int priceToForest(int fromIdx) {
        if (fromIdx < 0) return 3;
        return 3 + TIER_OF_DISTRICT[fromIdx];
    }

    /** Удобный оверлоад по строковым ID (city_N / "giant_forest"). -1, если origin неизвестен. */
    public static int priceTo(String originCityId, String destinationId) {
        int fromIdx = indexOf(originCityId);
        if (fromIdx < 0) return -1;

        if ("giant_forest".equals(destinationId)) {
            return priceToForest(fromIdx);
        }
        int toIdx = indexOf(destinationId);
        if (toIdx < 0) return -1;
        return priceBetweenDistricts(fromIdx, toIdx);
    }

    // Насколько близко нужно оказаться к одной из 12 канонических точек, чтобы считать
    // это "тем же самым округом" (а не случайным совпадением координат где-то ещё).
    private static final double SAME_DISTRICT_RADIUS_SQR = 64.0 * 64.0;

    /**
     * Ищет "district_N" по ближайшей к pos точке CarriageSpawner.SPAWN_POINTS.
     * Нужно для самовосстановления водителей, переживших смену версий мода: если
     * водитель стоит буквально на одной из 12 канонических точек, но почему-то без
     * тега "daot_city:" (спавн из старой версии, до появления этой системы тегов),
     * мы всё равно можем корректно определить, какой это округ — вместо выдумывания
     * несуществующего "city_X_Z", которого нет ни в одной кнопке CarriageScreen.
     * Возвращает null, если рядом нет ни одной канонической точки.
     */
    public static String nearestDistrictId(BlockPos pos) {
        int bestIdx = -1;
        double bestDistSqr = SAME_DISTRICT_RADIUS_SQR;

        for (int i = 0; i < CarriageSpawner.SPAWN_POINTS.length; i++) {
            BlockPos p = CarriageSpawner.SPAWN_POINTS[i];
            double dx = p.getX() - pos.getX();
            double dz = p.getZ() - pos.getZ();
            double distSqr = dx * dx + dz * dz;
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                bestIdx = i;
            }
        }

        return bestIdx >= 0 ? "district_" + (bestIdx + 1) : null;
    }
}

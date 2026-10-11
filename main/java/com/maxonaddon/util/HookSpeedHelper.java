package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;

/**
 * Время полёта крюка в нашей улучшенной механике.
 * <p>
 * Базовая формула — distance * 16.66 мс, не меньше 50 мс. Навыки "Ускоренный крюк"
 * делят это время на свой множитель: чем он больше, тем быстрее крюк долетает.
 * <p>
 * Множитель считает клиент (навыки берутся у локального игрока) и кладёт сюда — сами
 * миксины крюка живут в общем сорс-сете и о клиентских классах знать не должны.
 */
public final class HookSpeedHelper {

    private static final double MS_PER_BLOCK = 16.66;
    private static final double MIN_DURATION_MS = 50.0;

    private static volatile double speedMultiplier = 1.0;

    private HookSpeedHelper() {}

    /** Вызывается клиентским тиком по набору навыков локального игрока. */
    public static void setSpeedMultiplier(double multiplier) {
        // Нижняя граница страхует от деления на ноль и от "крюк летит вечно",
        // если сюда когда-нибудь прилетит мусорное значение.
        speedMultiplier = Math.max(0.1, multiplier);
    }

    /** С выключенной улучшенной механикой навыки не действуют — крюк летит как в оригинале. */
    public static double getSpeedMultiplier() {
        return MaxonConfig.get().enableAdvancedHookMechanics ? speedMultiplier : 1.0;
    }

    public static double flightDurationMs(double distance) {
        return Math.max(distance * MS_PER_BLOCK / getSpeedMultiplier(), MIN_DURATION_MS);
    }
}

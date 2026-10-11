package com.maxonaddon.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский список контрабандистов, которых подсвечивает перк "Торговец".
 * <p>
 * Наполняется пакетом SmugglerGlowPayload, который сервер шлёт ТОЛЬКО владельцу перка.
 * Раз списка нет у остальных — они и не увидят свечения: решение о подсветке принимается
 * на клиенте (см. SmugglerGlowMixin), а не свойством самой сущности.
 */
public final class SmugglerGlowTracker {

    private static final Set<Integer> IDS = ConcurrentHashMap.newKeySet();

    private SmugglerGlowTracker() {}

    public static void update(java.util.Collection<Integer> ids) {
        IDS.clear();
        IDS.addAll(ids);
    }

    public static void clear() {
        IDS.clear();
    }

    public static boolean shouldGlow(int entityId) {
        return IDS.contains(entityId);
    }
}

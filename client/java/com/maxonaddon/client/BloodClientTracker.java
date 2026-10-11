package com.maxonaddon.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кэш стадии окровавленности по entityId игрока (0..3).
 * Заполняется из SyncBloodPayload, читается слоями рендера.
 */
public final class BloodClientTracker {

    private static final Map<Integer, Integer> STAGES = new ConcurrentHashMap<>();

    private BloodClientTracker() {}

    public static void setStage(int entityId, int stage) {
        if (stage <= 0) {
            STAGES.remove(entityId);
        } else {
            STAGES.put(entityId, Math.min(3, stage));
        }
    }

    public static int getStage(int entityId) {
        return STAGES.getOrDefault(entityId, 0);
    }
}

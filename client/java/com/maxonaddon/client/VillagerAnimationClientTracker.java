package com.maxonaddon.client;

import java.util.HashMap;
import java.util.Map;

public class VillagerAnimationClientTracker {
    private static final Map<Integer, Float> biteAnimations = new HashMap<>();

    public static void startBite(int entityId) {
        biteAnimations.put(entityId, 0.0f);
    }

    public static void tick() {
        biteAnimations.replaceAll((id, time) -> time + 0.05f); // 1 тик = 0.05 сек
        biteAnimations.values().removeIf(time -> time > 1.5417f); // Длина анимации из твоего JSON
    }

    public static float getBiteTime(int entityId) {
        return biteAnimations.getOrDefault(entityId, -1.0f);
    }
}
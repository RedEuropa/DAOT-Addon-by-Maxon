package com.maxonaddon.client;

import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerHookTracker {
    public static final Map<Integer, HookData> HOOKS = new ConcurrentHashMap<>();

    public static class HookData {
        public boolean active;
        public Vec3 target;
        /** Сколько секунд подряд трос активен — используется для перехода double_hook -> double_hook_flying. */
        public float activeSeconds = 0F;

        public HookData(boolean active, Vec3 target) {
            this.active = active;
            this.target = target;
        }
    }

    public static void updateHook(int entityId, boolean active, Vec3 target) {
        if (active) {
            HookData existing = HOOKS.get(entityId);
            if (existing != null) {
                // Трос уже был активен — просто обновляем цель, не сбрасывая накопленное время,
                // иначе анимация double_hook_flying никогда не начнётся (сервер шлёт синк каждый тик).
                existing.active = true;
                existing.target = target;
            } else {
                HOOKS.put(entityId, new HookData(true, target));
            }
        } else {
            HOOKS.remove(entityId);
        }
    }

    public static HookData getHook(int entityId) {
        return HOOKS.get(entityId);
    }

    /** Тикается раз в клиентский тик (0.05 сек), копит длительность зацепа для анимации. */
    public static void tick() {
        for (HookData data : HOOKS.values()) {
            data.activeSeconds += 0.05F;
        }
    }
}

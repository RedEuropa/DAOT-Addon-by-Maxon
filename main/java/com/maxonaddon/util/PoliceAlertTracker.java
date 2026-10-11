package com.maxonaddon.util;

import net.minecraft.world.entity.npc.Villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Короткоживущий "сигнал тревоги" полицейскому: игрок X напал на мирного жителя рядом.
 * Кладётся сюда обработчиком урона, забирается (и потребляется) целью
 * {@link com.maxonaddon.ai.PoliceDefendVillagersGoal} на её собственном canUse().
 */
public final class PoliceAlertTracker {

    private static final Map<UUID, UUID> ALERTED_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> EXPIRES_AT = new ConcurrentHashMap<>();

    private PoliceAlertTracker() {}

    public static void alert(Villager officer, UUID attackerId, long expireAtGameTime) {
        ALERTED_PLAYER.put(officer.getUUID(), attackerId);
        EXPIRES_AT.put(officer.getUUID(), expireAtGameTime);
    }

    /** Возвращает UUID игрока-нарушителя, если тревога ещё не протухла, и сразу гасит её (одноразовая). */
    public static UUID consume(Villager officer, long currentGameTime) {
        UUID officerId = officer.getUUID();
        Long expiresAt = EXPIRES_AT.remove(officerId);
        UUID attackerId = ALERTED_PLAYER.remove(officerId);
        if (expiresAt == null || attackerId == null || currentGameTime > expiresAt) {
            return null;
        }
        return attackerId;
    }
}

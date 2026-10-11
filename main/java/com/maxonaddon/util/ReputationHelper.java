package com.maxonaddon.util;

/**
 * Пороги и цвета репутации у стены. Единая точка правды — используются и на сервере (гейты
 * поведения: торговля, полиция, атака титана), и на клиенте (цвет названия стены в StatsScreen).
 */
public final class ReputationHelper {

    public static final int MIN = 0;
    public static final int MAX = 100;
    public static final int DEFAULT = 50;

    /** <= этого — "враг": контрабандисты не торгуют, полиция бьёт один раз при сближении/обыске. */
    public static final int ENEMY_THRESHOLD = 10;
    /** == этого — "война": вся военная сила города реально атакует игрока. */
    public static final int WAR_THRESHOLD = 0;
    /** >= этого — "друг": скидки, лучший товар у контрабандистов, титана не атакуют (кроме берсерка). */
    public static final int FRIEND_THRESHOLD = 70;

    private ReputationHelper() {}

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public static boolean isAtWar(int rep) {
        return rep <= WAR_THRESHOLD;
    }

    public static boolean isEnemy(int rep) {
        return rep <= ENEMY_THRESHOLD;
    }

    public static boolean isFriend(int rep) {
        return rep >= FRIEND_THRESHOLD;
    }

    /** Цвет ARGB (непрозрачный) для названия стены и полоски — красный/жёлтый/зелёный. */
    public static int getColor(int rep) {
        if (isEnemy(rep)) return 0xFFFF5555;
        if (isFriend(rep)) return 0xFF55FF55;
        return 0xFFFFFF55;
    }
}

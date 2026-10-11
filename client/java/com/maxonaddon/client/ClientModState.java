package com.maxonaddon.client;

/**
 * Локальный кэш клиента для флагов, присланных сервером через SyncModFlagsPayload.
 * Сервер — источник истины; клиент использует это только для отображения UI.
 */
public class ClientModState {
    private static volatile boolean statisticsEnabled = true;
    private static volatile boolean fearSystemEnabled = true;
    private static volatile boolean reputationEnabled = true;

    public static boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public static void setStatisticsEnabled(boolean enabled) {
        statisticsEnabled = enabled;
    }

    public static boolean isFearSystemEnabled() {
        return fearSystemEnabled;
    }

    public static void setFearSystemEnabled(boolean enabled) {
        fearSystemEnabled = enabled;
    }

    /** Включены ли квесты и репутация (серверный конфиг reputationAllow). */
    public static boolean isReputationEnabled() {
        return reputationEnabled;
    }

    public static void setReputationEnabled(boolean enabled) {
        reputationEnabled = enabled;
    }
}

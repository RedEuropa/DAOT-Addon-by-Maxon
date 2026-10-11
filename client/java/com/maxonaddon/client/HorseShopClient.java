package com.maxonaddon.client;

/**
 * Клиентское зеркало лавки лошадей: seed текущего предложения и признак того,
 * что у игрока уже есть купленная лошадь. Заполняется из HorseShopPayload.
 */
public final class HorseShopClient {

    /** Seed предложения. Ноль означает, что сервер его ещё не прислал. */
    public static long seed = 0L;

    public static boolean ownsHorse = false;

    private HorseShopClient() {}
}

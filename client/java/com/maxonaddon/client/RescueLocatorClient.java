package com.maxonaddon.client;

/** Синхронизируемое с сервера состояние квеста "Спасение застрявших" — и локатор (см.
 * SyncRescuePayload), и живые счётчики для журнала (см. SyncRescueStatusPayload). */
public class RescueLocatorClient {
    public static boolean active = false;
    public static String dimension = "";
    public static double x = 0.0;
    public static double y = 0.0;
    public static double z = 0.0;
    /** false — трек ведёт на здание (жёлто-зелёный); true — на ближайшего квестового титана (красный). */
    public static boolean titanMode = false;

    public static boolean arrived = false;
    public static int cadetsTotal = 0;
    public static int cadetsAlive = 0;
    public static int titansRequired = 0;
    public static long deadlineTick = 0L;
}

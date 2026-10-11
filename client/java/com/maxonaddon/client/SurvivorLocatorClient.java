package com.maxonaddon.client;

/** Синхронизируемое с сервера состояние маркера на раненого выжившего (см. SyncSurvivorPayload). */
public class SurvivorLocatorClient {
    public static boolean active = false;
    public static String dimension = "";
    public static double x = 0.0;
    public static double y = 0.0;
    public static double z = 0.0;
    /** Житель сейчас на спине — стрелка не нужна, вместо неё подсказка "донесите до командира". */
    public static boolean carried = false;
}

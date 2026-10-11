package com.maxonaddon.client;

import java.util.UUID;

public class FearManager {
    public static double currentFearLevel = 0.0;
    public static double targetFearLevel = 0.0;
    public static int clientTitansKilled = 0;
    public static int scanTickCounter = 0;
    public static UUID lastOwnedTitanUuid = null;

    // === Дыхание от страха (start_breath -> breath [зациклен] -> stop_breath) ===
    /** Играет ли сейчас цикл дыхания — грань для start_breath/stop_breath (см. тик-обработчик). */
    public static boolean breathActive = false;
    /** Текущий зацикленный экземпляр звука "breath" — нужен, чтобы явно его остановить. */
    public static net.minecraft.client.resources.sounds.SoundInstance breathLoopInstance = null;
}

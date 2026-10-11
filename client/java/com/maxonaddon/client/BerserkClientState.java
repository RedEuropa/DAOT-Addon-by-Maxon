package com.maxonaddon.client;

/** Синхронизируемый с сервера флаг "игрок сейчас в берсерк-режиме потери контроля". */
public class BerserkClientState {
    public static boolean controlLossActive = false;

    // === Смех берсерка — личный, не позиционный зацикленный звук, пока идёт потеря контроля ===
    public static boolean laughActive = false;
    public static net.minecraft.client.resources.sounds.SoundInstance laughInstance = null;
}

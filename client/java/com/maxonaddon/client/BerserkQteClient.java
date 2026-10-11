package com.maxonaddon.client;

import com.maxonaddon.network.BerserkControlLossPayload;
import com.maxonaddon.network.BerserkQteHitPayload;
import com.maxonaddon.network.BerserkQteStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

/**
 * Клиентская часть QTE "вырывания" из берсерк-режима (см. BerserkEscapeQteHandler) и ambient-
 * текста потери контроля (см. BerserkTextManager). Одна случайная клавиша (WASD, как в
 * EscapeQteClient) на 1.5 секунды — сервер только говорит "старт/стоп" и получает единственный
 * пакет-подтверждение при успехе, саму клавишу и попадание клиент проверяет сам.
 */
public class BerserkQteClient {

    private static boolean qteActive = false;
    private static int ticksRemaining = 0;
    private static int targetKey = -1;
    private static Component qteTitle = Component.empty();

    private static boolean prevW = false;
    private static boolean prevA = false;
    private static boolean prevS = false;
    private static boolean prevD = false;

    private static final Random RANDOM = new Random();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BerserkControlLossPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                BerserkClientState.controlLossActive = payload.active();
                if (!payload.active()) {
                    stopQte(context.client());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BerserkQteStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.active()) {
                    startQte(context.client());
                } else {
                    stopQte(context.client());
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BerserkTextManager.tick(BerserkClientState.controlLossActive);
            tickLaughSound(client, BerserkClientState.controlLossActive);

            if (!qteActive || client.player == null) return;

            if (client.player.isDeadOrDying()) {
                stopQte(client);
                return;
            }

            ticksRemaining--;
            if (ticksRemaining <= 0) {
                stopQte(client);
                return;
            }

            boolean w = client.options.keyUp.isDown();
            boolean a = client.options.keyLeft.isDown();
            boolean s = client.options.keyDown.isDown();
            boolean d = client.options.keyRight.isDown();

            boolean justPressed =
                    (w && !prevW && targetKey == 0) ||
                    (a && !prevA && targetKey == 1) ||
                    (s && !prevS && targetKey == 2) ||
                    (d && !prevD && targetKey == 3);

            prevW = w;
            prevA = a;
            prevS = s;
            prevD = d;

            if (justPressed) {
                ClientPlayNetworking.send(new BerserkQteHitPayload());
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
                stopQte(client);
                return;
            }

            // Форсируем каждый тик — тот же приём, что и в EscapeQteClient, чтобы никто
            // (сервер, другой мод) не стёр надпись раньше времени.
            client.gui.setTimes(0, 5, 5);
            client.gui.setTitle(qteTitle);
        });
    }

    private static void startQte(Minecraft client) {
        qteActive = true;
        ticksRemaining = 30; // 1.5 секунды
        targetKey = RANDOM.nextInt(4);

        prevW = client.options.keyUp.isDown();
        prevA = client.options.keyLeft.isDown();
        prevS = client.options.keyDown.isDown();
        prevD = client.options.keyRight.isDown();

        qteTitle = Component.literal("§e[ §c" + keyName(targetKey) + " §e]");
        client.gui.setTimes(0, 5, 5);
        client.gui.setTitle(qteTitle);
    }

    private static void stopQte(Minecraft client) {
        if (!qteActive) return;
        qteActive = false;
        targetKey = -1;
        qteTitle = Component.empty();
        client.gui.setTimes(0, 20, 10);
        client.gui.setTitle(Component.empty());
    }

    /**
     * Смех берсерка — слышит только сам игрок (личный, не позиционный, Attenuation.NONE +
     * relative=true, как дыхание от страха в MaxonDaotAddonClient). Тон поднят на 20%
     * (pitch 1.2), а категория звука — SoundSource.MUSIC: так его громкость сама зависит
     * от ползунка "Музыка" в настройках Minecraft, без ручного пересчёта.
     */
    private static void tickLaughSound(Minecraft client, boolean active) {
        net.minecraft.client.sounds.SoundManager soundManager = client.getSoundManager();

        if (active && !BerserkClientState.laughActive) {
            BerserkClientState.laughActive = true;

            net.minecraft.client.resources.sounds.SimpleSoundInstance loop = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                    com.maxonaddon.sound.ModSounds.BERSERK_LAUGH.getLocation(),
                    net.minecraft.sounds.SoundSource.MUSIC,
                    1.0F, 1.2F,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(),
                    true, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                    0.0, 0.0, 0.0, true
            );
            BerserkClientState.laughInstance = loop;
            soundManager.play(loop);
        } else if (!active && BerserkClientState.laughActive) {
            BerserkClientState.laughActive = false;
            if (BerserkClientState.laughInstance != null) {
                soundManager.stop(BerserkClientState.laughInstance);
                BerserkClientState.laughInstance = null;
            }
        }
    }

    private static String keyName(int key) {
        return switch (key) {
            case 0 -> "W";
            case 1 -> "A";
            case 2 -> "S";
            default -> "D";
        };
    }
}

package com.maxonaddon.client;

import com.maxonaddon.network.HitQteKeyPayload;
import com.maxonaddon.network.StartHostileQtePayload;
import com.maxonaddon.network.StopHostileQtePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

public class EscapeQteClient {

    public static boolean isQteActive = false;
    public static int targetKey = -1;
    public static int feedbackTicks = 0;

    public static int localFails = 0;
    public static final int MAX_FAILS = 3;

    private static final Random random = new Random();

    private static boolean prevW = false;
    private static boolean prevA = false;
    private static boolean prevS = false;
    private static boolean prevD = false;

    // Сохраняем текущий текст QTE, чтобы форсировать его отрисовку каждый тик
    private static Component currentQteText = Component.empty();

    public static void register() {

        ClientPlayNetworking.registerGlobalReceiver(StartHostileQtePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                isQteActive = true;
                feedbackTicks = 0;
                localFails = 0;
                targetKey = -1;

                Minecraft client = context.client();
                prevW = client.options.keyUp.isDown();
                prevA = client.options.keyLeft.isDown();
                prevS = client.options.keyDown.isDown();
                prevD = client.options.keyRight.isDown();

                generateNextKey();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(StopHostileQtePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                stopQte(context.client());
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isQteActive && client.player != null) {
                if (client.player.isDeadOrDying()) {
                    stopQte(client);
                    return;
                }
            }

            if (!isQteActive || client.player == null) return;

            if (feedbackTicks > 0) {
                feedbackTicks--;
                updatePrevKeys(client);

                if (feedbackTicks == 0) {
                    if (localFails >= MAX_FAILS) {
                        stopQte(client);
                        return;
                    }

                    if (targetKey == -1) {
                        generateNextKey();
                    } else {
                        updateQteText(); // Возвращаем кнопку на экран после паузы
                    }
                }
            } else {
                // Обработка нажатий
                boolean w = client.options.keyUp.isDown();
                boolean a = client.options.keyLeft.isDown();
                boolean s = client.options.keyDown.isDown();
                boolean d = client.options.keyRight.isDown();

                boolean justPressedW = w && !prevW;
                boolean justPressedA = a && !prevA;
                boolean justPressedS = s && !prevS;
                boolean justPressedD = d && !prevD;

                prevW = w;
                prevA = a;
                prevS = s;
                prevD = d;

                int pressedKey = -1;
                if (justPressedW) pressedKey = 0;
                else if (justPressedA) pressedKey = 1;
                else if (justPressedS) pressedKey = 2;
                else if (justPressedD) pressedKey = 3;

                if (pressedKey != -1) {
                    if (pressedKey == targetKey) {
                        // === УСПЕХ ===
                        currentQteText = Component.literal("§aV");
                        ClientPlayNetworking.send(new HitQteKeyPayload(true));

                        targetKey = -1;
                        feedbackTicks = 5;
                    } else {
                        // === ПРОМАХ ===
                        localFails++;
                        int remainingFails = MAX_FAILS - localFails;

                        client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
                        ClientPlayNetworking.send(new HitQteKeyPayload(false));

                        if (remainingFails > 0) {
                            currentQteText = Component.literal("§c(" + localFails + "/3)");
                        } else {
                            currentQteText = Component.literal("§4X");
                        }

                        feedbackTicks = 5;
                    }
                }
            }

            // === САМОЕ ВАЖНОЕ: ФОРСИРУЕМ ОТРИСОВКУ КАЖДЫЙ ТИК ===
            // Это не даст серверу или другим модам "стереть" нашу надпись
            if (isQteActive) {
                client.gui.setTimes(0, 5, 5);
                client.gui.setTitle(currentQteText);
            }
        });
    }

    private static void stopQte(Minecraft client) {
        isQteActive = false;
        localFails = 0; // Полностью обнуляем счетчик попыток
        feedbackTicks = 0; // Сбрасываем задержки
        targetKey = -1; // Сбрасываем клавишу

        currentQteText = Component.empty();
        client.gui.setTimes(0, 20, 10);
        client.gui.setTitle(Component.empty());
    }

    private static void updatePrevKeys(Minecraft client) {
        prevW = client.options.keyUp.isDown();
        prevA = client.options.keyLeft.isDown();
        prevS = client.options.keyDown.isDown();
        prevD = client.options.keyRight.isDown();
    }

    private static void generateNextKey() {
        targetKey = random.nextInt(4);
        updateQteText();
    }

    private static void updateQteText() {
        String keyName = switch(targetKey) {
            case 0 -> "W";
            case 1 -> "A";
            case 2 -> "S";
            case 3 -> "D";
            default -> "W";
        };
        currentQteText = Component.literal("§e[ §c" + keyName + " §e]");
    }
}
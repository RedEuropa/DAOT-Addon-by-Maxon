package com.maxonaddon.client;

import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class HeadTraumaClient {
    public static int checkTimer = 0;
    public static int concussionTimer = 0;

    // Переменная от 0.0 до 1.0, которая управляет силой визуального эффекта
    public static float currentIntensity = 0.0f;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null) return;

            IPlayerStats stats = (IPlayerStats) player;

            if (stats.hasTrauma(TraumaType.HEAD)) {
                checkTimer++;

                // Каждые 30 секунд (600 тиков)
                if (checkTimer >= 600) {
                    checkTimer = 0;
                    int chance = player.getRandom().nextInt(10) + 1; // 1-10

                    if (chance > 8) { // 20% шанс (9 или 10)
                        concussionTimer = 400; // 20 секунд жесткой контузии

                        // Глухой удар колокола (звон в ушах)
                        player.level().playLocalSound(
                                player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BELL_BLOCK, SoundSource.AMBIENT,
                                1.0F, 0.5F, false
                        );
                    }
                }

                if (concussionTimer > 0) {
                    concussionTimer--;
                    // Плавное усиление до максимума (сильная контузия)
                    currentIntensity += (1.0f - currentIntensity) * 0.05f;
                } else {
                    // Плавный спад до легкого эффекта (базовое помутнение)
                    currentIntensity += (0.3f - currentIntensity) * 0.05f;
                }

            } else {
                checkTimer = 0;
                concussionTimer = 0;
                // Плавное исчезновение эффекта при исцелении головы
                if (currentIntensity > 0.01f) {
                    currentIntensity += (0.0f - currentIntensity) * 0.1f;
                } else {
                    currentIntensity = 0.0f;
                }
            }
        });
    }
}
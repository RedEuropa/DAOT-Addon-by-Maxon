package com.maxonaddon.client;

import daot.CameraPanState;
import daot.HookPoint;
import daot.ODMTickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class CameraDynamics {

    // Текущие плавные значения наклона и сдвига
    public static float currentRoll = 0f;
    public static float currentPanX = 0f;

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float targetRoll = 0f;
        float targetPanX = 0f;

        HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUUID());
        HookPoint rightHook = ODMTickHandler.getRightHook(player.getUUID());

        boolean leftActive = leftHook != null && leftHook.active;
        boolean rightActive = rightHook != null && rightHook.active;

        // Проверяем буст: пробел (isReeling) или W (isGasBoosting)
        boolean isBoosting = mc.options.keyJump.isDown() || mc.options.keyUp.isDown();

        if (isBoosting && (leftActive || rightActive)) {
            Vec3 look = player.getLookAngle();
            Vec3 eyePos = player.getEyePosition();

            if (leftActive && rightActive) {
                // Двойной хук: проверяем разницу высот для переворота
                double deltaY = leftHook.position.y - rightHook.position.y;
                if (deltaY > 2.0) {
                    targetRoll = 8.0f;  // Левый крюк выше -> крен вправо
                } else if (deltaY < -2.0) {
                    targetRoll = -8.0f; // Правый крюк выше -> крен влево
                }
            } else {
                // Одиночный хук: сдвигаем камеру вбок
                HookPoint activeHook = leftActive ? leftHook : rightHook;

                // Вектор к крюку и вектор "вправо" от взгляда игрока
                Vec3 toHook = activeHook.position.subtract(eyePos).normalize();
                Vec3 rightVec = look.cross(new Vec3(0, 1, 0)).normalize();

                // Проекция: > 0 значит крюк справа, < 0 значит слева
                double sideDot = toHook.dot(rightVec);

                // Если смотрим не прямо на крюк
                if (Math.abs(sideDot) > 0.05) {
                    targetPanX = (float) (sideDot * 1.5);   // Сдвигаем камеру в сторону крюка
                    targetRoll = (float) (sideDot * 15.0);  // Наклоняем (крен) туда же
                }
            }
        }

        // Плавная интерполяция (сглаживание кадров)
        currentRoll += (targetRoll - currentRoll) * 0.05f;
        currentPanX += (targetPanX - currentPanX) * 0.05f;

        // Безопасно отдаем сдвиг камеры в оригинальный класс мода!
        CameraPanState.horizontalPan = currentPanX;
    }
}
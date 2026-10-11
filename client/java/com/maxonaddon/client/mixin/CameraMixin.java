package com.maxonaddon.client.mixin;

import com.maxonaddon.client.FearManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private float xRot;
    @Shadow private float yRot;

    // Позволяет нам вызвать внутренний метод Minecraft для поворота камеры
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void setupFearShake(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        // Возвращаем твою систему дрожания камеры при страхе
        if (FearManager.currentFearLevel > 0.05) {
            // Рассчитываем силу тряски
            float shakeAmount = (float) (FearManager.currentFearLevel * 2.5);

            float shakeX = (float) ((Math.random() - 0.5) * shakeAmount);
            float shakeY = (float) ((Math.random() - 0.5) * shakeAmount);

            // Применяем новые углы поворота вместе с нашей тряской
            this.setRotation(this.yRot + shakeY, this.xRot + shakeX);
        }
    }
}
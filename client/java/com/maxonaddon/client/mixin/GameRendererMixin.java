package com.maxonaddon.client.mixin;

import com.maxonaddon.client.FearManager;
import com.maxonaddon.client.HeadTraumaClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Ванильная цепочка размытия — та же, которой игра замыливает фон при открытии меню. */
    @Shadow private PostChain blurEffect;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void modifyFovForMaxon(Camera camera, float partialTick, boolean useFOVSetting, CallbackInfoReturnable<Double> cir) {
        double baseFov = cir.getReturnValue();
        double fovOffset = 0.0;

        // 1. Страх (Сильное приближение)
        if (FearManager.currentFearLevel > 0.05) {
            fovOffset -= FearManager.currentFearLevel * 20.0;
        }

        // 2. Контузия (Пульсация FOV для эффекта головокружения/укачивания)
        if (HeadTraumaClient.currentIntensity > 0.01f) {
            double time = System.currentTimeMillis() / 300.0;
            double wobble = Math.sin(time) * 12.0 * HeadTraumaClient.currentIntensity;
            fovOffset += wobble;
        }

        // 3. Прицеливание из марлийской винтовки (плавный зум, см. rifleAimProgress
        // в MaxonDaotAddonClient — тикается там же, где перехватываются ЛКМ/ПКМ).
        if (com.maxonaddon.client.MaxonDaotAddonClient.rifleAimProgress > 0.01f) {
            fovOffset -= com.maxonaddon.client.MaxonDaotAddonClient.rifleAimProgress * 25.0;
        }

        if (fovOffset != 0.0) {
            cir.setReturnValue(baseFov + fovOffset);
        }
    }

    /**
     * Травма головы размывает картинку.
     * <p>
     * Раньше это делалось туманом — FogRenderer'у подменялись fogStart/fogEnd. Приём рабочий,
     * но Distant Horizons строит свою дальнюю геометрию по тем же параметрам тумана и от такой
     * подмены ломается. Размытие живёт в пост-обработке и до параметров мира не дотягивается,
     * поэтому DH его попросту не замечает.
     * <p>
     * Точка входа — перед отрисовкой интерфейса: мир к этому моменту уже нарисован, а HUD ещё
     * нет, так что замыливается только картинка мира, а полоски здоровья и хотбар остаются
     * чёткими.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void maxon$blurOnHeadTrauma(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        float intensity = HeadTraumaClient.currentIntensity;
        if (intensity <= 0.01f || this.blurEffect == null) return;

        // База 3 — постоянное лёгкое помутнение при травме (intensity держится на 0.3).
        // Контузия разгоняет intensity до 1.0 и добавляет ещё 7, упираясь в ванильный
        // потолок GameRenderer.MAX_BLUR_RADIUS = 10.
        float basePart = 3.0f * Math.min(1.0f, intensity / 0.3f);
        float concussionPart = 7.0f * Mth.clamp((intensity - 0.3f) / 0.7f, 0.0f, 1.0f);
        float radius = basePart + concussionPart;

        // Ванильный шейдер осмыслен только от радиуса 1 — на затухании эффекта просто
        // перестаём рисовать, вместо того чтобы моргать минимальным размытием.
        if (radius < 1.0f) return;

        this.blurEffect.setUniform("Radius", radius);
        this.blurEffect.process(deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
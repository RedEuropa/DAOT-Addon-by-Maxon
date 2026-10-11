package com.maxonaddon.client.mixin;

import com.maxonaddon.client.BloodClientTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import daot.BladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Накладывает слой крови титана на клинок поверх его обычной текстуры.
 * Текстуры крови нарисованы на прозрачном фоне, поэтому мы просто рендерим ту же
 * геометрию второй раз с нашей текстурой (тот же приём наслаивания, что и в BloodLayer
 * для скина/плаща) — вместо подмены текстуры, как делает GeoModelMixin.
 *
 * Ограничение: у рендерера предмета нет ссылки на владельца, поэтому стадия берётся
 * у локального игрока — ровно тот же подход, что уже используется в GeoModelMixin
 * для подмены моделей УПМ/АПГ.
 */
// Реализуем интерфейс GeoRenderer, а НЕ наследуем GeoItemRenderer: у класса-предка
// перегруженные конструкторы, и вызов super(null) из миксина неоднозначен для javac
// (оба кандидата подходят). Интерфейсу конструктор не нужен, а reRender() — его
// default-метод, так что доступ к нему сохраняется.
@Mixin(value = daot.BladeItemRenderer.class, remap = false)
public abstract class BladeBloodRenderMixin implements GeoRenderer<BladeItem> {

    @Unique
    private static final ResourceLocation[] maxon$BLOOD_TEXTURES = {
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/bladeitem_blood1.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/bladeitem_blood2.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/bladeitem_blood3.png")
    };

    @Inject(method = "actuallyRender", at = @At("TAIL"), require = 0)
    private void maxon$renderBloodOverlay(PoseStack poseStack, BladeItem animatable, BakedGeoModel model,
                                          RenderType renderType, MultiBufferSource bufferSource,
                                          VertexConsumer buffer, boolean isReRender, float partialTick,
                                          int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        // Второй проход перерисовки не трогаем — иначе слой ляжет несколько раз.
        if (isReRender) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int stage = BloodClientTracker.getStage(player.getId());
        if (stage <= 0) return;

        ResourceLocation bloodTexture = maxon$BLOOD_TEXTURES[Math.min(stage, 3) - 1];
        RenderType bloodType = RenderType.entityTranslucent(bloodTexture);

        this.reRender(model, poseStack, bufferSource, animatable, bloodType,
                bufferSource.getBuffer(bloodType), partialTick, packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}

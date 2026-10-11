package com.maxonaddon.client.mixin;

import com.maxonaddon.client.BloodClientTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Кровь титана на плаще.
 *
 * Плащ — это НЕ часть модели игрока: он рендерится отдельной геометрией через
 * daot.CloakRenderer (наследник geckolib GeoArmorRenderer). Поэтому наложение крови
 * на модель игрока (BloodLayer) плащ не пачкает — там просто другая геометрия.
 * Инжектимся в общий GeoArmorRenderer и рисуем слой поверх той же геометрии плаща.
 */
@Mixin(software.bernie.geckolib.renderer.GeoArmorRenderer.class)
public abstract class CloakBloodRenderMixin<T extends GeoItem> implements GeoRenderer<T> {

    @Unique
    private static final ResourceLocation[] maxon$CLOAK_BLOOD = {
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_cloack_1.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_cloack_2.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_cloack_3.png")
    };

    @Inject(method = "actuallyRender", at = @At("TAIL"), remap = false, require = 0)
    private void maxon$renderCloakBlood(PoseStack poseStack, Item animatable, BakedGeoModel model,
                                        RenderType renderType, MultiBufferSource bufferSource,
                                        VertexConsumer buffer, boolean isReRender, float partialTick,
                                        int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        if (isReRender) return;
        if (!(animatable instanceof daot.CloakItem)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int stage = BloodClientTracker.getStage(player.getId());
        if (stage <= 0) return;

        ResourceLocation texture = maxon$CLOAK_BLOOD[Math.min(stage, 3) - 1];
        RenderType bloodType = RenderType.entityTranslucent(texture);

        @SuppressWarnings("unchecked")
        T typed = (T) animatable;
        this.reRender(model, poseStack, bufferSource, typed, bloodType,
                bufferSource.getBuffer(bloodType), partialTick, packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}

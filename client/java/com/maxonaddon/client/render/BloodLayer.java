package com.maxonaddon.client.render;

import com.maxonaddon.client.BloodClientTracker;
import com.maxonaddon.util.BloodManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Рисует слой крови титана поверх скина игрока (или поверх плаща, если он надет).
 * Текстуры нарисованы на прозрачном фоне, поэтому просто накладываются сверху
 * тем же приёмом, что и TraumaLayer: рендерим модель ещё раз, но с нашей текстурой.
 *
 * Кровь на плаще и на скине взаимоисключающи: если на голове надет плащ (cloak),
 * кровь идёт только на плащ.
 */
public class BloodLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation[] SKIN_TEXTURES = {
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_skin.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_skin2.png"),
            ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/misc/blood_skin3.png")
    };

    // Текстуры крови для плаща живут в CloakBloodRenderMixin — у плаща своя геометрия.

    public BloodLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        int stage = BloodClientTracker.getStage(player.getId());
        if (stage <= 0) return;

        // Если надет плащ — кровь рисуется на НЁМ (см. CloakBloodRenderMixin), а не на скине:
        // у плаща своя геометрия, и красить ею модель игрока бессмысленно.
        if (BloodManager.hasCloak(player)) return;

        int index = Math.min(stage, 3) - 1;
        ResourceLocation texture = SKIN_TEXTURES[index];

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();

        poseStack.pushPose();
        // Крошечное увеличение, чтобы слой крови не мерцал с основным скином (Z-fighting) —
        // тот же приём, что уже используется в TraumaLayer.
        poseStack.scale(1.01f, 1.01f, 1.01f);
        poseStack.translate(0.0f, -0.01f, 0.0f);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
    }
}

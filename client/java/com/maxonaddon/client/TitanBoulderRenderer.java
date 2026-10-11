package com.maxonaddon.client;

import com.maxonaddon.entity.TitanBoulderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TitanBoulderRenderer extends GeoEntityRenderer<TitanBoulderEntity> {

    public TitanBoulderRenderer(EntityRendererProvider.Context context) {
        super(context, new TitanBoulderModel());
    }

    @Override
    public void render(TitanBoulderEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // ВАЖНО: Если твоя 3D модель камня изначально маленькая, увеличь ее здесь.
        // Можешь менять значения 4.0F на любые другие, чтобы подогнать размер под Титана.
        poseStack.scale(16.0F, 16.0F, 16.0F);

        // Если нужно отцентрировать модель относительно хитбокса, используй translate:
        // poseStack.translate(0, -1.0, 0);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
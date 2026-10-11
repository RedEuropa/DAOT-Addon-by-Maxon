package com.maxonaddon.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import daot.ThunderSpearArmFeature;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.math.Axis;

@Mixin(value = ThunderSpearArmFeature.class, remap = false)
public abstract class ThunderSpearArmFeatureMixin {

    @Inject(
            method = "renderSpearOnArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldaot/GeoModelHelper;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
            )
    )
    private void adjustSpearPosition(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, boolean isRightHand, CallbackInfo ci) {


        // 2. СМЕЩЕНИЕ ПОД РУКУ И К СПИНЕ
        if (isRightHand) {
            // Переносим значения из Blockbench (делим на 16, чтобы получить метры)
            // Z = положительное значение смещает к спине!
            // X = смещает под руку (ближе к телу)
            double shiftX = -0.2; // Вперед назда
            double shiftY = 0.17;   // Лево право
            double shiftZ = 0.2;  // Верх низ

            poseStack.translate(shiftX, shiftY, shiftZ);
        } else {
            // Для левой руки ось X обычно зеркалится
            double shiftX = 0.2; // Лево право
            double shiftY = 0.17;   // Вперед назда
            double shiftZ = 0.2;  // Верх низ
            poseStack.translate(shiftX, shiftY, shiftZ);
        }
    }
}
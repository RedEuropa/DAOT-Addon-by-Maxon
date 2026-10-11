package com.maxonaddon.client.render;

import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import com.maxonaddon.client.model.GasCisternaModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import daot.IceburstFurnaceBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import org.joml.Matrix4f;

public class GasCisternaRenderer extends GeoBlockRenderer<GasCisternaBlockEntity> {
    public GasCisternaRenderer(BlockEntityRendererProvider.Context context) {
        super(new GasCisternaModel());
    }

    // GeoBlockRenderer САМ поворачивает модель по FACING/HORIZONTAL_FACING блокстейта —
    // это встроенный механизм GeckoLib (actuallyRender() внутри вызывает
    // rotateBlock(getFacing(animatable), poseStack) БЕЗУСЛОВНО, даже если render() уже
    // переопределён). Из-за этого наш собственный поворот ниже (translate->rotateY(-rot)->...)
    // накладывался ВТОРЫМ поверх автоматического — итоговый угол получался неверным, и
    // визуально цистерна вообще не выглядела поворачивающейся при установке.
    //
    // Отключаем автоповорот GeckoLib, всегда возвращая NORTH — для этого направления
    // rotateBlock() применяет поворот на 0° (см. таблицу в GeoBlockRenderer), то есть
    // фактически становится no-op, и остаётся только наш ручной поворот ниже — тот же расчёт,
    // с которым уже согласован код телепортации игрока к патрубку в GasCisternaBlock.
    @Override
    protected Direction getFacing(GasCisternaBlockEntity animatable) {
        return Direction.NORTH;
    }

    @Override
    public void render(GasCisternaBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // --- 1. ОРИГИНАЛЬНЫЙ КОД РЕНДЕРА И МАСШТАБА ---
        Direction facing = animatable.getBlockState().getValue(IceburstFurnaceBlock.FACING);
        float rot = facing.toYRot();

        poseStack.pushPose();

        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-rot));
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.translate(-0.5f, 0, -0.5f);

        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        // --- 2. ГОЛОГРАММА С ЧИСЛОМ КЛАСТЕРОВ ---
        ItemStack clusters = animatable.getItem(0);
        int count = clusters.getCount();

        if (count > 0) {
            poseStack.pushPose();

            // Поднимаем над блоком (ты можешь поменять 1.2 на нужную высоту)
            poseStack.translate(0.5, 1.2, 0.5);

            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            String text = "❄ " + count + " / 99";
            Font font = Minecraft.getInstance().font;
            float textWidth = (float) (-font.width(text) / 2);
            Matrix4f matrix4f = poseStack.last().pose();

            font.drawInBatch(text, textWidth, 0, 0x00FFFF, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0x40000000, packedLight);

            poseStack.popPose();
        }
    }

    // РАСШИРЯЕМ ЗОНУ ОТРИСОВКИ

    public AABB getRenderBoundingBox(GasCisternaBlockEntity animatable) {
        return new AABB(
                animatable.getBlockPos().getX() - 1,
                animatable.getBlockPos().getY(),
                animatable.getBlockPos().getZ() - 1,
                animatable.getBlockPos().getX() + 2,
                animatable.getBlockPos().getY() + 4,
                animatable.getBlockPos().getZ() + 2
        );
    }
}
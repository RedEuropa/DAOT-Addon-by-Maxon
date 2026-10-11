package com.maxonaddon.client.render;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.client.model.SwordBoxItemModel;
import com.maxonaddon.item.SwordBoxItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class SwordBoxItemRenderer extends GeoItemRenderer<SwordBoxItem> {

    public SwordBoxItemRenderer() {
        super(new SwordBoxItemModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Если предмет находится в инвентаре (GUI), рамке (FIXED) или валяется на земле
        if (transformType == ItemDisplayContext.GUI || transformType == ItemDisplayContext.FIXED || transformType == ItemDisplayContext.GROUND) {

            Minecraft mc = Minecraft.getInstance();
            // Подгружаем нашу 2D модель из item_box_gui.json
            BakedModel guiModel = mc.getModelManager().getModel(MaxonDaotAddon.id("item/item_box_gui"));

            poseStack.pushPose();
            // Отрисовываем плоскую иконку стандартным рендерером Minecraft
            mc.getItemRenderer().render(stack, transformType, false, poseStack, bufferSource, packedLight, packedOverlay, guiModel);
            poseStack.popPose();

        } else {
            // Во всех остальных случаях (в руках от 1-го и 3-го лица) рисуем объемную 3D-модель GeckoLib
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }
}
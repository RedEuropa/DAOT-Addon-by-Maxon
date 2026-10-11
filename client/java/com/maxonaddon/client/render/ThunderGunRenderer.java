package com.maxonaddon.client.render;

import com.maxonaddon.client.model.ThunderGunModel;
import com.maxonaddon.item.ThunderGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import daot.ThunderSpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class ThunderGunRenderer extends GeoItemRenderer<ThunderGunItem> {

    public ThunderGunRenderer() {
        super(new ThunderGunModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // === 1. ПЕРЕХВАТ 2D ИКОНКИ ДЛЯ ИНВЕНТАРЯ И РАМКИ ===
        if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED) {
            Minecraft mc = Minecraft.getInstance();
            BakedModel guiModel = mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "item/thunder_gun_gui"));

            poseStack.pushPose();
            mc.getItemRenderer().render(stack, displayContext, false, poseStack, bufferSource, packedLight, packedOverlay, guiModel);
            poseStack.popPose();

            return; // Выходим из метода, чтобы 3D модель не накладывалась поверх
        }

        // === 2. ТВОЯ ЛОГИКА 3D МОДЕЛИ С КОПЬЯМИ ===
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int loadedSpears = ThunderGunItem.getLoadedSpears(stack);
            int inventorySpears = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() instanceof ThunderSpearItem) {
                    inventorySpears += player.getInventory().getItem(i).getCount();
                }
            }

            boolean isLeftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ||
                    displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            boolean isRightHand = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                    displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

            // Проверяем, держит ли игрок пушки в обеих руках
            boolean dualWielding = player.getMainHandItem().getItem() instanceof ThunderGunItem &&
                    player.getOffhandItem().getItem() instanceof ThunderGunItem;

            // Логика распределения копий
            boolean show1 = inventorySpears >= 1;
            boolean show2 = inventorySpears >= 2;
            boolean show3 = inventorySpears >= 3;

            if (dualWielding) {
                if (isLeftHand) {
                    // Четные в левую
                    show1 = inventorySpears >= 2;
                    show2 = inventorySpears >= 4;
                    show3 = inventorySpears >= 6;
                } else {
                    // Нечетные в правую
                    show1 = inventorySpears >= 1;
                    show2 = inventorySpears >= 3;
                    show3 = inventorySpears >= 5;
                }
            }

            GeoBone spearLoaded1 = this.getGeoModel().getBone("thunder_spear").orElse(null);
            GeoBone spearLoaded2 = this.getGeoModel().getBone("thunder_spear2").orElse(null);

            GeoBone spearRight1 = this.getGeoModel().getBone("thunder_spear3").orElse(null);
            GeoBone spearRight2 = this.getGeoModel().getBone("thunder_spear4").orElse(null);
            GeoBone spearRight3 = this.getGeoModel().getBone("thunder_spear5").orElse(null);

            GeoBone spearLeft1 = this.getGeoModel().getBone("thunder_spear3_left").orElse(null);
            GeoBone spearLeft2 = this.getGeoModel().getBone("thunder_spear4_left").orElse(null);
            GeoBone spearLeft3 = this.getGeoModel().getBone("thunder_spear5_left").orElse(null);

            if (spearLoaded1 != null) spearLoaded1.setHidden(loadedSpears < 1);
            if (spearLoaded2 != null) spearLoaded2.setHidden(loadedSpears < 2);

            if (isLeftHand) {
                if (spearRight1 != null) spearRight1.setHidden(true);
                if (spearRight2 != null) spearRight2.setHidden(true);
                if (spearRight3 != null) spearRight3.setHidden(true);

                if (spearLeft1 != null) spearLeft1.setHidden(!show1);
                if (spearLeft2 != null) spearLeft2.setHidden(!show2);
                if (spearLeft3 != null) spearLeft3.setHidden(!show3);
            } else {
                if (spearLeft1 != null) spearLeft1.setHidden(true);
                if (spearLeft2 != null) spearLeft2.setHidden(true);
                if (spearLeft3 != null) spearLeft3.setHidden(true);

                if (spearRight1 != null) spearRight1.setHidden(!show1);
                if (spearRight2 != null) spearRight2.setHidden(!show2);
                if (spearRight3 != null) spearRight3.setHidden(!show3);
            }
        }

        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
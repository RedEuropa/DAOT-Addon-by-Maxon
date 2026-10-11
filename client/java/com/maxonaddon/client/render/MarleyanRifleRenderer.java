package com.maxonaddon.client.render;

import com.maxonaddon.client.model.MarleyanRifleModel;
import com.maxonaddon.item.MarleyanRifleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * По образцу ThunderGunRenderer. Кости right_arm/left_arm (руки, держащие винтовку в самой
 * модели оружия) всегда скрыты — вместо них теперь настоящие руки игрока с отдельной
 * анимацией (player_animations/marleyan_rifle_hand.animation.json, см. OdmAnimHelper и
 * MaxonDaotAddonClient), а не перекрашенный кусок геометрии оружия.
 */
public class MarleyanRifleRenderer extends GeoItemRenderer<MarleyanRifleItem> {

    public MarleyanRifleRenderer() {
        super(new MarleyanRifleModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        GeoBone rightArm = this.getGeoModel().getBone("right_arm").orElse(null);
        GeoBone leftArm = this.getGeoModel().getBone("left_arm").orElse(null);

        if (rightArm != null) rightArm.setHidden(true);
        if (leftArm != null) leftArm.setHidden(true);

        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
    }
}

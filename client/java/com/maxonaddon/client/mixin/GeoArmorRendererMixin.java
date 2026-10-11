package com.maxonaddon.client.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.VertexConsumer;

@Mixin(GeoArmorRenderer.class)
public abstract class GeoArmorRendererMixin<T extends GeoItem> {

    @Shadow(remap = false)
    public abstract ItemStack getCurrentStack();

    @Shadow(remap = false)
    public abstract GeoModel<T> getGeoModel();

    @Inject(method = "preRender", at = @At("HEAD"), remap = false)
    private void onMaxonGearPreRender(PoseStack poseStack, Item animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        try {
            ItemStack stack = this.getCurrentStack();
            if (stack == null || stack.isEmpty()) return;

            boolean isOdm = stack.getItem() instanceof daot.ODMGearItem;
            boolean isApg = stack.getItem() instanceof daot.ODMAPGItem;

            if (isOdm || isApg) {
                ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
                contents.copyInto(items);

                int leftCount = items.get(0).getCount();
                int rightCount = items.get(1).getCount();

                boolean hasBlades = items.get(0).getItem() instanceof daot.BladeComponentItem || items.get(1).getItem() instanceof daot.BladeComponentItem;
                boolean hasCartridges = items.get(0).getItem() instanceof daot.APGCartridgeItem || items.get(1).getItem() instanceof daot.APGCartridgeItem;

                // Если есть мечи ИЛИ это обычный УПМ без картриджей -> РЕЖИМ МЕЧЕЙ
                if (hasBlades || (!hasBlades && !hasCartridges && isOdm)) {
                    setBoneHidden("left_catridge", true);
                    setBoneHidden("left_1", true); setBoneHidden("left_2", true); setBoneHidden("left_3", true);
                    setBoneHidden("right_catridge", true);
                    setBoneHidden("right_1", true); setBoneHidden("right_2", true); setBoneHidden("right_3", true);

                    setBoneHidden("left_blade", leftCount < 1);
                    setBoneHidden("left_blade2", leftCount < 2);
                    setBoneHidden("right_blade", rightCount < 1);
                    setBoneHidden("right_blade2", rightCount < 2);
                }
                // Если есть картриджи ИЛИ это обычный АПГ без мечей -> РЕЖИМ ОГНЕСТРЕЛА
                else if (hasCartridges || (!hasBlades && !hasCartridges && isApg)) {
                    setBoneHidden("left_blade", true); setBoneHidden("left_blade2", true);
                    setBoneHidden("right_blade", true); setBoneHidden("right_blade2", true);

                    setBoneHidden("left_catridge", false);
                    setBoneHidden("left_1", leftCount < 1);
                    setBoneHidden("left_2", leftCount < 2);
                    setBoneHidden("left_3", leftCount < 3);

                    setBoneHidden("right_catridge", false);
                    setBoneHidden("right_1", rightCount < 1);
                    setBoneHidden("right_2", rightCount < 2);
                    setBoneHidden("right_3", rightCount < 3);
                }
            }
        } catch (Exception ignored) {}
    }

    @Unique
    private void setBoneHidden(String boneName, boolean hidden) {
        getGeoModel().getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
    }
}
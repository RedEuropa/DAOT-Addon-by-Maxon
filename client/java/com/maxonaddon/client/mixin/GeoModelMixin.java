package com.maxonaddon.client.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.ODMAPGModel;
import daot.ODMGearModel;
import daot.ODMAPGItem;
import daot.ODMGearItem;
import daot.BladeItem;
import daot.BladeComponentItem;
import daot.APGGunItem;
import daot.APGCartridgeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ODMAPGModel.class, ODMGearModel.class}, remap = false)
public abstract class GeoModelMixin {

    @Unique
    private boolean maxon$hasBlades(LocalPlayer player) {
        ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legItem.getItem() instanceof daot.ODMAPGItem || legItem.getItem() instanceof daot.ODMGearItem) {
            ItemContainerContents contents = legItem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            for (ItemStack stack : contents.nonEmptyItems()) {
                if (stack.getItem() instanceof BladeComponentItem) return true;
            }
        }
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return main instanceof BladeItem || off instanceof BladeItem;
    }

    @Unique
    private boolean maxon$hasCartridges(LocalPlayer player) {
        ItemStack legItem = player.getItemBySlot(EquipmentSlot.LEGS);
        if (legItem.getItem() instanceof daot.ODMAPGItem || legItem.getItem() instanceof daot.ODMGearItem) {
            ItemContainerContents contents = legItem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            for (ItemStack stack : contents.nonEmptyItems()) {
                if (stack.getItem() instanceof APGCartridgeItem) return true;
            }
        }
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return main instanceof APGGunItem || off instanceof APGGunItem;
    }

    // ==========================================
    // ИНЖЕКТЫ ДЛЯ АПГ (ODMAPGModel)
    // ==========================================
    @Inject(method = "getModelResource(Ldaot/ODMAPGItem;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 0)
    private void swapApgModel(ODMAPGItem animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ((IPlayerStats) player).hasConnectorMastery()) {
            if (maxon$hasBlades(player)) {
                cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/odm_2.geo.json"));
            }
        }
    }

    @Inject(method = "getTextureResource(Ldaot/ODMAPGItem;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 0)
    private void swapApgTexture(ODMAPGItem animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ((IPlayerStats) player).hasConnectorMastery()) {
            if (maxon$hasBlades(player)) {
                cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/armor/odm_2.png"));
            }
        }
    }

    // ==========================================
    // ИНЖЕКТЫ ДЛЯ УПМ (ODMGearModel)
    // ==========================================
    @Inject(method = "getModelResource(Ldaot/ODMGearItem;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 0)
    private void swapOdmModel(ODMGearItem animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ((IPlayerStats) player).hasConnectorMastery()) {
            if (maxon$hasCartridges(player)) {
                cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/odm_2_apg.geo.json"));
            }
        }
    }

    @Inject(method = "getTextureResource(Ldaot/ODMGearItem;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, require = 0)
    private void swapOdmTexture(ODMGearItem animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ((IPlayerStats) player).hasConnectorMastery()) {
            if (maxon$hasCartridges(player)) {
                cir.setReturnValue(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/armor/odm_2_apg.png"));
            }
        }
    }
}
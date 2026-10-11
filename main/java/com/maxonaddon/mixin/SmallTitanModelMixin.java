package com.maxonaddon.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "daot.YellowTitanModel",
        "daot.ConnieFatherModel",
        "daot.SmallTitanModel",
        "daot.SadTitanModel"
})
public class SmallTitanModelMixin {

    private static final ResourceLocation DEAD_MODEL = ResourceLocation.parse("maxon-daot-addon:geo/titan_dead_small.geo.json");
    private static final ResourceLocation DEAD_TEXTURE = ResourceLocation.parse("maxon-daot-addon:textures/entity/bone_titan.png");

    // Анимация папы Конни для всех мелких
    private static final ResourceLocation EMPTY_ANIM = ResourceLocation.parse("maxon-daot-addon:animations/empty.animation.json");
    @Inject(method = "getModelResource*", at = @At("HEAD"), cancellable = true, remap = false)
    private void swapModelOnDeath(software.bernie.geckolib.animatable.GeoAnimatable animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        if (com.maxonaddon.MaxonConfig.get().enableTitanSkeletons && animatable instanceof LivingEntity titan && titan.isDeadOrDying() && titan.deathTime >= 30) {
            cir.setReturnValue(DEAD_MODEL);
        }
    }

    @Inject(method = "getTextureResource*", at = @At("HEAD"), cancellable = true, remap = false)
    private void swapTextureOnDeath(software.bernie.geckolib.animatable.GeoAnimatable animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        if (com.maxonaddon.MaxonConfig.get().enableTitanSkeletons && animatable instanceof LivingEntity titan && titan.isDeadOrDying() && titan.deathTime >= 30) {
            cir.setReturnValue(DEAD_TEXTURE);
        }
    }

    @Inject(method = "getAnimationResource*", at = @At("HEAD"), cancellable = true, remap = false)
    private void swapAnimationOnDeath(software.bernie.geckolib.animatable.GeoAnimatable animatable, CallbackInfoReturnable<ResourceLocation> cir) {
        if (com.maxonaddon.MaxonConfig.get().enableTitanSkeletons && animatable instanceof LivingEntity titan && titan.isDeadOrDying() && titan.deathTime >= 30) {
            cir.setReturnValue(EMPTY_ANIM);
        }
    }

}
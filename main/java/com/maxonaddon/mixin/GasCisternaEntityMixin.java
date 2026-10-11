package com.maxonaddon.mixin;

import daot.IceburstFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mixin(IceburstFurnaceBlockEntity.class)
public class GasCisternaEntityMixin implements GeoBlockEntity {

    @Unique
    private final AnimatableInstanceCache dassodm$cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Сюда можно добавить анимации работы цистерны в будущем
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.dassodm$cache;
    }
}
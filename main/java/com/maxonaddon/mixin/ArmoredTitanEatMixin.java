package com.maxonaddon.mixin;

import daot.ArmoredTitanEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmoredTitanEntity.class)
public abstract class ArmoredTitanEatMixin extends Monster {

    protected ArmoredTitanEatMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    // Тот же набор методов, что и у Атакующего
    @Shadow public abstract boolean isDefeated();
    @Shadow public abstract boolean isGrabbing();
    @Shadow public abstract boolean isThrowing();
    @Shadow public abstract boolean isEating();
    @Shadow public abstract int getGrabPhase();
    @Shadow public abstract void startEat();
    @Shadow public abstract void startThrow();

    // Перехватываем удар (ЛКМ)
    @Inject(method = "triggerAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private void forceEatOnLeftClick(CallbackInfo ci) {
        if (!this.isDefeated() && this.isGrabbing() && !this.isThrowing() && !this.isEating()) {

            // Если заряжен бросок, кидаем
            if (this.getGrabPhase() == 2) {
                this.startThrow();
            } else {
                // Иначе едим!
                this.startEat();
            }

            ci.cancel();
        }
    }
}
package com.maxonaddon.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "daot.TitanEntity")
public abstract class TitanPositionMixin {

    @Inject(method = "getPassengerRidingPosition", at = @At("RETURN"), cancellable = true)
    private void onGetPassengerRidingPosition(Entity passenger, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;

        if (self.getFirstPassenger() == passenger && passenger instanceof Player) {
            // Поднимаем камеру на 1 блок вверх
            cir.setReturnValue(cir.getReturnValue().add(0.0, 1.0, 0.0));
        }
    }
}
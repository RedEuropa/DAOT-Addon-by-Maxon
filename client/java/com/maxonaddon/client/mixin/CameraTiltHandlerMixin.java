package com.maxonaddon.client.mixin;

import com.maxonaddon.client.CameraDynamics;
import daot.CameraTiltHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CameraTiltHandler.class, remap = false)
public class CameraTiltHandlerMixin {

    @Inject(method = "getCurrentTilt", at = @At("RETURN"), cancellable = true)
    private static void onGetCurrentTilt(float partialTick, CallbackInfoReturnable<Float> cir) {
        // Только забираем значение крена, вся логика теперь тикает в клиенте!
        float originalTilt = cir.getReturnValue();
        cir.setReturnValue(originalTilt + CameraDynamics.currentRoll);
    }
}
package com.maxonaddon.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Часть "Титанической воли": пока у игрока висит тег "titan_stealth"
 * (см. TitanicWillHandler), взрыв трансформации не срабатывает — тайный ре-шифт.
 */
@Mixin(targets = "daot.network.ModNetworking", remap = false)
public abstract class TitanicWillExplosionMixin {

    @Inject(method = "triggerTransformationExplosion", at = @At("HEAD"), cancellable = true)
    private static void maxon$suppressStealthExplosion(ServerLevel level, double x, double y, double z,
                                                        ServerPlayer player, double radius, float damage,
                                                        double knockback, CallbackInfo ci) {
        if (player != null && player.getTags().contains("titan_stealth")) {
            ci.cancel();
        }
    }
}

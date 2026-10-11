package com.maxonaddon.mixin;

import daot.BladeBlockTracker;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BladeBlockTracker.class)
public class BladeBlockTrackerMixin {

    @Inject(method = "setBlocking", at = @At("HEAD"), remap = false)
    private static void onSetBlocking(ServerPlayer player, boolean blocking, CallbackInfo ci) {
        if (blocking) {
            // Записываем тик в главный класс
            com.maxonaddon.MaxonDaotAddon.blockStartTicks.put(player.getUUID(), player.server.getTickCount());
        } else {
            // Удаляем при отпускании блока
            com.maxonaddon.MaxonDaotAddon.blockStartTicks.remove(player.getUUID());
        }
    }
}
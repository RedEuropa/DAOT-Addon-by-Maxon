package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.BloodlineData;
import daot.BloodlineType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = BloodlineData.class, remap = false)
public abstract class BloodlineDataMixin {

    @Inject(method = "setBloodline", at = @At("HEAD"))
    private void resetBloodlinePoints(UUID playerUUID, BloodlineType type, CallbackInfo ci) {
        // Получаем текущий сервер через Fabric API
        MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                // Обнуляем очки
                ((IPlayerStats) player).setBloodlinePoints(0);
            }
        }
    }
}
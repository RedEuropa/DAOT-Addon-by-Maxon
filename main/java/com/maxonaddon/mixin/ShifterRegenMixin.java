package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.network.ModNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModNetworking.class, remap = false)
public abstract class ShifterRegenMixin {

    // Срабатывает ПЕРЕД тем, как мод начнет лечить шифтеров
    @Inject(method = "tickShifterRegeneration", at = @At("HEAD"))
    private static void addFakeStealth(MinecraftServer server, CallbackInfo ci) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;

            // Если навыка регенерации нет, и на игроке сейчас НЕТ настоящего стелса
            if (!stats.hasShifterRegen() && !player.getTags().contains("titan_stealth")) {
                player.addTag("titan_stealth"); // Прячем от мода
                player.addTag("maxon_fake_stealth"); // Ставим свою метку, чтобы потом узнать
            }
        }
    }

    @Inject(method = "tickShifterRegeneration", at = @At("TAIL"))
    private static void healTraumas(MinecraftServer server, CallbackInfo ci) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IPlayerStats stats = (IPlayerStats) player;

            // Если у игрока есть реген шифтера (или он в форме)
            if (stats.hasShifterRegen()) {
                // TODO: Уменьшить таймеры всех травм
                // Если таймер достиг 0 -> травма исцелена, восстанавливаем ХП (если был сломан корпус)
            }
        }
    }

    // Срабатывает СРАЗУ ПОСЛЕ того, как мод закончил проверки
    @Inject(method = "tickShifterRegeneration", at = @At("TAIL"))
    private static void removeFakeStealth(MinecraftServer server, CallbackInfo ci) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Если это мы повесили фейковый стелс - снимаем его
            if (player.getTags().contains("maxon_fake_stealth")) {
                player.removeTag("titan_stealth");
                player.removeTag("maxon_fake_stealth");
            }
        }
    }
}
package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.BloodlineData;
import daot.BloodlineType;
import daot.network.ModNetworking; // <--- ИЗМЕНИЛИ ИМПОРТ
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// <--- ИЗМЕНИЛИ ЦЕЛЕВОЙ КЛАСС НА ModNetworking
@Mixin(value = ModNetworking.class, remap = false)
public abstract class RoyalRoarMixin {

    // Перехватываем метод крика в самом начале
    @Inject(method = "handleTitanRoar", at = @At("HEAD"), cancellable = true)
    private static void blockUnawakenedRoar(ServerPlayer player, CallbackInfo ci) {
        IPlayerStats stats = (IPlayerStats) player;
        BloodlineType type = BloodlineData.get(player.serverLevel()).getBloodline(player.getUUID());

        // Если игрок с Королевской кровью, но не вкачал навык "Пробуждение"
        if (type == BloodlineType.ROYAL && !stats.hasBloodlineAwakening()) {
            // Выводим сообщение и отменяем выполнение крика
            player.displayClientMessage(Component.translatable("message.maxonaddon.skills.not_awakened"), true);
            ci.cancel();
        }
    }
}
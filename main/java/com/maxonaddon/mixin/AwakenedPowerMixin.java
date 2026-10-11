package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import daot.AwakenedPowerTracker;
import daot.BloodlineData;
import daot.BloodlineType;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AwakenedPowerTracker.class, remap = false)
public abstract class AwakenedPowerMixin {

    // Блокируем активацию и выводим надпись
    @Inject(method = "activate", at = @At("HEAD"), cancellable = true)
    private static void onActivate(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        IPlayerStats stats = (IPlayerStats) player;
        BloodlineType type = BloodlineData.get(player.serverLevel()).getBloodline(player.getUUID());

        if (type == BloodlineType.ACKERMAN && !stats.hasBloodlineAwakening()) {
            // Используем ключ перевода и применяем красный цвет
            player.displayClientMessage(Component.translatable("message.maxonaddon.not_awakened").withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(false); // Отменяем активацию
        }
    }

    // Блокируем первичное заполнение стамины, чтобы она визуально не копилась
    @Inject(method = "fillCharge", at = @At("HEAD"), cancellable = true)
    private static void onFillCharge(ServerPlayer player, CallbackInfo ci) {
        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.hasBloodlineAwakening()) {
            ci.cancel();
        }
    }
}
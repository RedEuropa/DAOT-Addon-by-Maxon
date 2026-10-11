package com.maxonaddon.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "daot.TitanPowerHelper", remap = false)
public abstract class TitanPowerHelperMixin {

    // Создаем "тень" оригинального приватного метода.
    // Тело метода оставляем пустым, Mixin сам перенаправит вызов куда нужно.
    @Shadow
    private static void revertPlayerToHuman(ServerPlayer player, Monster titan, ServerLevel level) {
        throw new AssertionError();
    }

    // Сет для предотвращения зацикливания
    private static final java.util.Set<java.util.UUID> DELAYED_REVERTS = new java.util.HashSet<>();

    @Inject(method = "revertPlayerToHuman", at = @At("HEAD"), cancellable = true)
    private static void maxon$delayRevertToHuman(ServerPlayer player, Monster titan, ServerLevel level, CallbackInfo ci) {
        if (player != null && !DELAYED_REVERTS.contains(player.getUUID())) {
            DELAYED_REVERTS.add(player.getUUID());

            // Откладываем выполнение на безопасный момент (конец тика сервера)
            level.getServer().execute(() -> {
                try {
                    // Вызываем оригинальный метод через нашу @Shadow-копию
                    revertPlayerToHuman(player, titan, level);
                } finally {
                    DELAYED_REVERTS.remove(player.getUUID());
                }
            });

            // Отменяем опасное синхронное выполнение (которое вызывает краш Ticking entity)
            ci.cancel();
        }
    }
}
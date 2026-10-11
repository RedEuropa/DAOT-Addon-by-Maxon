package com.maxonaddon.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class ShifterVesselEatMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void maxon$onWeakenedVillagerDeath(DamageSource source, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;

        if (!villager.level().isClientSide() && villager.getTags().contains("weakened_shifter_vessel")) {
            Entity attacker = source.getEntity();

            // СТРОГАЯ ПРОВЕРКА: Игрок не может забрать силу, будучи человеком!
            // Атакующим обязательно должен быть сам титан, а игрок должен сидеть у него внутри.
            if (attacker != null && attacker.getFirstPassenger() instanceof Player player) {

                // Убеждаемся, что мы управляем именно титаном (Ползущим, Обычным и т.д.)
                if (attacker.getClass().getSimpleName().contains("Titan")) {
                    player.addTag("has_shifted_before");
                    player.displayClientMessage(Component.translatable("Вы успешно забрали силу титана!").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                }
            }
        }
    }
}
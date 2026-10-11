package com.maxonaddon.mixin;

import com.mojang.logging.LogUtils;
import daot.DefeatedCarryTracker;
import daot.HandcuffsTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerCarryInteractMixin {

    @Unique
    private static final Logger MAXON$LOG = LogUtils.getLogger();

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void maxon$allowCarryingWeakenedVillager(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Villager villager = (Villager) (Object) this;

        if (villager.getTags().contains("weakened_shifter_vessel")) {

            if (!villager.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {

                // Житель УЖЕ сидит на ЭТОМ игроке — снимаем
                if (villager.isPassenger() && villager.getVehicle() == player) {
                    villager.stopRiding();
                    HandcuffsTracker.uncuffEntity(villager, serverPlayer);
                }
                // Иначе подбираем
                else {
                    if (villager.isPassenger()) {
                        villager.stopRiding(); // срываем с чего угодно
                    }

                    DefeatedCarryTracker.markDefeated(villager.getUUID());

                    // ВАЖНО: сажаем ДО наручников.
                    // cuffEntity() приводит моба в «зафиксированное» состояние
                    // (ИИ, NoGravity), и после него посадка срывалась с первого раза.
                    boolean mounted = villager.startRiding(player, true);

                    HandcuffsTracker.cuffEntity(villager, serverPlayer);

                    if (!mounted) {
                        MAXON$LOG.info("[MaxonAddon/CARRY] startRiding вернул false — NoAI={} NoGravity={} vehicle={} пассажиров у игрока={}",
                                villager.isNoAi(), villager.isNoGravity(),
                                villager.getVehicle() == null ? "НЕТ" : villager.getVehicle().getClass().getSimpleName(),
                                player.getPassengers().size());

                        // Страховка: не оставляем жителя висеть с включённой невесомостью
                        villager.setNoGravity(false);
                        villager.hasImpulse = true;
                    }
                }
            }

            cir.setReturnValue(InteractionResult.sidedSuccess(villager.level().isClientSide()));
        }
    }
}
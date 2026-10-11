package com.maxonaddon.mixin;

import daot.BladeAttackTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BladeAttackTracker.class)
public class BladeAttackTrackerMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void checkCooldownBeforeAttack(ServerPlayer player, int attackType, int chargeTimeTicks, float playerSpeed, CallbackInfo ci) {

        // 1. БЛОКИРОВКА В ЗАХВАТЕ
        if (player.getVehicle() != null && player.getVehicle().getClass().getSimpleName().contains("Grab")) {
            if (player.getTags().contains("shifter_qte_success")) {
                // Забираем тег, чтобы больше в захвате бить было нельзя
                player.removeTag("shifter_qte_success");

                // Освобождаем игрока прямо в момент удара
                player.stopRiding();

                // Мы НЕ вызываем ci.cancel()!
                // Метод пойдет дальше, и по руке титана пройдет полноценный урон от клинков.
            } else {
                ci.cancel();
                return;
            }
        }

        // 2. БЛОКИРОВКА ПО КУЛДАУНУ
        Item mainItem = player.getMainHandItem().getItem();
        Item offItem = player.getOffhandItem().getItem();

        if (mainItem instanceof daot.BladeItem && player.getCooldowns().isOnCooldown(mainItem)) {
            ci.cancel();
        } else if (offItem instanceof daot.BladeItem && player.getCooldowns().isOnCooldown(offItem)) {
            ci.cancel();
        }
    }

    /**
     * Помечаем, что взмах реально начался. До TAIL мы доходим только если атаку не отменили
     * ни выше по коду, ни наш же HEAD-инжект. По этой отметке TitanMeleeNapeMixin отличает
     * настоящий удар клинком от обычного клика мышью по хитбоксу шеи — источник урона у них
     * одинаковый (playerAttack), различить их иначе нечем.
     */
    @Inject(method = "startAttack", at = @At("TAIL"), remap = false)
    private static void markSwingStarted(ServerPlayer player, int attackType, int chargeTimeTicks, float playerSpeed, CallbackInfo ci) {
        com.maxonaddon.util.BladeAttackWindow.mark(player);
    }
}
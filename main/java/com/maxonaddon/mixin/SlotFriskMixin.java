package com.maxonaddon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.maxonaddon.inventory.FriskContainer;

@Mixin(Slot.class)
public class SlotFriskMixin {

    // Перехватываем ванильную проверку "Можно ли положить предмет?"
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void enforceFriskContainerRules(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;

        // Если это наш контейнер для обыска
        if (slot.container instanceof FriskContainer frisk) {

            // Вызываем нашу строгую проверку (которая разрешает только броню)
            if (!frisk.canPlaceItem(slot.getContainerSlot(), stack)) {
                cir.setReturnValue(false); // Запрещаем класть предмет!
            }
        }
    }
}
package com.maxonaddon.mixin;

import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import com.maxonaddon.block.entity.SwordBoxBlockEntity;
import daot.DannysAot;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow @Final public Container container;
    @Shadow public abstract int getContainerSlot();

    // ВАЖНО: ванильный Slot.mayPlace(ItemStack) по умолчанию — просто "return true;"
    // (проверено байткодом), он НИКАК не обращается к Container.canPlaceItem(). То есть
    // canPlaceItem(...), переопределённый в GasCisternaBlockEntity/SwordBoxBlockEntity,
    // сам по себе ни на что не влиял в реальном UI (ни на обычную установку предмета,
    // ни на шифт-клик — оба идут именно через mayPlace, см. AbstractContainerMenu) — нужен
    // явный мостик, вот он.
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void restrictCustomContainerItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.container instanceof GasCisternaBlockEntity cisterna) {
            cir.setReturnValue(cisterna.canPlaceItem(this.getContainerSlot(), stack));
        } else if (this.container instanceof SwordBoxBlockEntity swordBox) {
            cir.setReturnValue(swordBox.canPlaceItem(this.getContainerSlot(), stack));
        }
    }

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void expandCisternaCapacity(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (this.container instanceof GasCisternaBlockEntity && stack.is(DannysAot.ICE_BURST_CLUSTER)) {
            cir.setReturnValue(99); // Строгий лимит 1.21.1
        }
    }
}
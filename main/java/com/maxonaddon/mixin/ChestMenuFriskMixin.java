package com.maxonaddon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.Slot;

import com.maxonaddon.inventory.FriskContainer;

@Mixin(AbstractContainerMenu.class)
public class ChestMenuFriskMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void onFriskSlotClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        if (slotId < 0 || slotId >= menu.slots.size()) {
            return;
        }

        Slot slot = menu.getSlot(slotId);

        if (slot.container instanceof FriskContainer frisk) {

            // Защита черных панелей (слоты 41-53)
            if (slotId > 40 && slotId < 54) {
                if (player instanceof ServerPlayer sp) {
                    // Возвращаем предмет обратно на курсор игрока!
                    sp.connection.send(new ClientboundContainerSetSlotPacket(
                            -1, menu.getStateId(), -1, sp.containerMenu.getCarried()
                    ));
                }
                ci.cancel();
                return;
            }

            // Логика раскрытия барьеров (слоты 0-40)
            if (!frisk.isRevealed(slotId)) {
                frisk.reveal(slotId);

                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundContainerSetSlotPacket(
                            menu.containerId, menu.getStateId(), slotId, slot.getItem()
                    ));
                    sp.connection.send(new ClientboundContainerSetSlotPacket(
                            -1, menu.getStateId(), -1, sp.containerMenu.getCarried()
                    ));
                }
                ci.cancel();
            }
        }
    }
}
package com.maxonaddon.client.mixin;

import com.maxonaddon.network.OdmSupplyInteractPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InventoryScreen.class, CreativeModeInventoryScreen.class})
public abstract class InventoryScreenMixin extends AbstractContainerScreen {

    @Unique private int fakeSlotX = 0;
    @Unique private int fakeSlotY = 0;
    @Unique private boolean isShowingFakeSlots = false;

    public InventoryScreenMixin(net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.entity.player.Inventory playerInventory, net.minecraft.network.chat.Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderMaxonDropdownSlots(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Slot legSlot = null;
        for (Slot slot : this.menu.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() == 37) {
                legSlot = slot;
                break;
            }
        }

        if (legSlot == null || !legSlot.hasItem() || !legSlot.isActive()) {
            isShowingFakeSlots = false;
            return;
        }

        ItemStack legItem = legSlot.getItem();
        boolean isOdm = legItem.getItem() instanceof daot.ODMGearItem;
        boolean isApg = legItem.getItem() instanceof daot.ODMAPGItem;

        if (isOdm || isApg) {
            int legX = this.leftPos + legSlot.x;
            int legY = this.topPos + legSlot.y;

            // Возвращаем супер-эстетичное расположение СЛЕВА от поножей
            fakeSlotX = legX - 42;
            fakeSlotY = legY - 1;

            boolean hoverLeg = (mouseX >= legX && mouseX < legX + 16 && mouseY >= legY && mouseY < legY + 16);
            boolean hoverFake = (mouseX >= fakeSlotX && mouseX < fakeSlotX + 38 && mouseY >= fakeSlotY && mouseY < fakeSlotY + 18);

            isShowingFakeSlots = hoverLeg || hoverFake;

            if (isShowingFakeSlots) {
                guiGraphics.fill(fakeSlotX, fakeSlotY, fakeSlotX + 38, fakeSlotY + 18, 0xFF8B8B8B);
                guiGraphics.fill(fakeSlotX + 1, fakeSlotY + 1, fakeSlotX + 17, fakeSlotY + 17, 0xFF373737);
                guiGraphics.fill(fakeSlotX + 20, fakeSlotY + 1, fakeSlotX + 36, fakeSlotY + 17, 0xFF373737);

                ItemContainerContents contents = legItem.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY);
                contents.copyInto(items);

                if (!items.get(0).isEmpty()) {
                    guiGraphics.renderItem(items.get(0), fakeSlotX + 1, fakeSlotY + 1);
                    guiGraphics.renderItemDecorations(this.font, items.get(0), fakeSlotX + 1, fakeSlotY + 1);
                }
                if (!items.get(1).isEmpty()) {
                    guiGraphics.renderItem(items.get(1), fakeSlotX + 20, fakeSlotY + 1);
                    guiGraphics.renderItemDecorations(this.font, items.get(1), fakeSlotX + 20, fakeSlotY + 1);
                }

                if (mouseX >= fakeSlotX + 1 && mouseX < fakeSlotX + 17 && mouseY >= fakeSlotY + 1 && mouseY < fakeSlotY + 17) {
                    guiGraphics.fillGradient(fakeSlotX + 1, fakeSlotY + 1, fakeSlotX + 17, fakeSlotY + 17, -2130706433, -2130706433);
                } else if (mouseX >= fakeSlotX + 20 && mouseX < fakeSlotX + 36 && mouseY >= fakeSlotY + 1 && mouseY < fakeSlotY + 17) {
                    guiGraphics.fillGradient(fakeSlotX + 20, fakeSlotY + 1, fakeSlotX + 36, fakeSlotY + 17, -2130706433, -2130706433);
                }
            }
        } else {
            isShowingFakeSlots = false;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMaxonMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (isShowingFakeSlots) {
            // Если клик пришелся на нашу левую область, полностью гасим его, исключая выброс вещей
            if (mouseX >= fakeSlotX && mouseX < fakeSlotX + 38 && mouseY >= fakeSlotY && mouseY < fakeSlotY + 18) {
                int targetSlot = (mouseX < fakeSlotX + 19) ? 0 : 1;
                ClientPlayNetworking.send(new OdmSupplyInteractPayload(targetSlot));
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMaxonMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (isShowingFakeSlots) {
            if (mouseX >= fakeSlotX && mouseX < fakeSlotX + 38 && mouseY >= fakeSlotY && mouseY < fakeSlotY + 18) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
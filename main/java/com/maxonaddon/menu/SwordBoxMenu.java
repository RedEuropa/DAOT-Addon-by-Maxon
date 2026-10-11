package com.maxonaddon.menu;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SwordBoxMenu extends AbstractContainerMenu {
    private final Container container;

    public SwordBoxMenu(int id, Inventory playerInv) {
        this(id, playerInv, new SimpleContainer(11));
    }

    public SwordBoxMenu(int id, Inventory playerInv, Container container) {
        super(MaxonDaotAddon.SWORD_BOX_MENU, id);
        this.container = container;
        container.startOpen(playerInv.player);

        // 4 слота для мечей (Верхний ряд, слева)
        for (int i = 0; i < 4; i++) this.addSlot(new Slot(container, i, 26 + i * 18, 20));
        // 2 слота газа (Верхний ряд, справа)
        for (int i = 0; i < 2; i++) this.addSlot(new Slot(container, 4 + i, 116 + i * 18, 20));
        // 5 слотов для копий (Нижний ряд, по центру)
        for (int i = 0; i < 5; i++) this.addSlot(new Slot(container, 6 + i, 44 + i * 18, 48));

        // Инвентарь игрока
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) { return this.container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();

            if (index < 11) {
                if (!this.moveItemStackTo(itemStack2, 11, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(itemStack2, 0, 11, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return itemStack;
    }
}
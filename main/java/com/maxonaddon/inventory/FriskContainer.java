package com.maxonaddon.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;

public class FriskContainer implements Container {
    private final Inventory targetInv;
    private final boolean[] revealed = new boolean[41];

    public FriskContainer(Player target) {
        this.targetInv = target.getInventory();
    }

    private int mapSlot(int i) {
        if (i < 36) return i;
        if (i < 40) return 36 + (i - 36);
        if (i == 40) return 40;
        return -1;
    }

    @Override
    public int getContainerSize() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        return targetInv.isEmpty();
    }

    @Override
    public ItemStack getItem(int i) {
        int mapped = mapSlot(i);

        if (mapped == -1) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.maxonaddon.frisk.locked_slot").withStyle(ChatFormatting.DARK_GRAY));
            return pane;
        }

        if (!revealed[i]) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.maxonaddon.frisk.click_to_search").withStyle(ChatFormatting.RED));
            return barrier;
        }

        return targetInv.getItem(mapped);
    }

    @Override
    public ItemStack removeItem(int i, int count) {
        if (!revealed[i]) return ItemStack.EMPTY;
        int mapped = mapSlot(i);
        if (mapped == -1) return ItemStack.EMPTY;
        return targetInv.removeItem(mapped, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        if (!revealed[i]) return ItemStack.EMPTY;
        int mapped = mapSlot(i);
        if (mapped == -1) return ItemStack.EMPTY;
        return targetInv.removeItemNoUpdate(mapped);
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        // Убрали "черную дыру"! Теперь мы просто перекладываем предмет.
        // А блокировать неверные предметы будет ванильная игра через canPlaceItem.
        int mapped = mapSlot(i);
        if (mapped != -1) {
            targetInv.setItem(mapped, stack);
        }
    }

    @Override
    public boolean canPlaceItem(int i, ItemStack stack) {
        int mapped = mapSlot(i);
        if (mapped == -1) return false;
        if (!revealed[i]) return false;

        if (i >= 36 && i <= 39) {
            if (stack.getItem() instanceof Equipable equipable) {
                EquipmentSlot slot = equipable.getEquipmentSlot();
                if (i == 36 && slot == EquipmentSlot.FEET) return true;
                if (i == 37 && slot == EquipmentSlot.LEGS) return true;
                if (i == 38 && slot == EquipmentSlot.CHEST) return true;
                if (i == 39 && slot == EquipmentSlot.HEAD) return true;
            }
            return false;
        }

        return true;
    }

    public void reveal(int i) {
        if (i >= 0 && i <= 40) {
            revealed[i] = true;
            setChanged();
        }
    }

    public boolean isRevealed(int i) {
        if (i < 0 || i > 40) return true;
        return revealed[i];
    }

    @Override
    public void setChanged() {
        targetInv.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return targetInv.stillValid(player);
    }

    @Override
    public void clearContent() {
    }
}
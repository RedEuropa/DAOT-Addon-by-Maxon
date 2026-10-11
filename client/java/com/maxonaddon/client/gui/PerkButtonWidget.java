package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Кнопка открытия меню перков в инвентаре — по образцу TraumaButtonWidget. */
public class PerkButtonWidget extends Button {
    private final ItemStack iconStack;

    public PerkButtonWidget(int x, int y, OnPress onPress) {
        super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
        this.iconStack = new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.renderItem(this.iconStack, this.getX() + 2, this.getY() + 2);
    }
}

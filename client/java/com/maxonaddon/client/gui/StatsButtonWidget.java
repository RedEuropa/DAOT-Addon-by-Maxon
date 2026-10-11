package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class StatsButtonWidget extends Button {
    private final ItemStack iconStack;

    public StatsButtonWidget(int x, int y, OnPress onPress) {
        // Делаем квадратную кнопку 20x20
        super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);

        // Получаем предмет из мода DAoT
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "bloodline_reroll"));
        this.iconStack = new ItemStack(item);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick); // Рисуем стандартный фон кнопки

        // Рисуем предмет со смещением, чтобы он был точно по центру кнопки (20x20)
        guiGraphics.renderItem(this.iconStack, this.getX() + 2, this.getY() + 2);
    }
}
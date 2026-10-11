package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TraumaButtonWidget extends Button {
    private final ItemStack iconStack;

    public TraumaButtonWidget(int x, int y, OnPress onPress) {
        // Делаем квадратную кнопку 20x20, без текста
        super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);

        // В качестве иконки берем стандартную кость из майнкрафта
        this.iconStack = new ItemStack(Items.BONE);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Сначала рисуем стандартный фон кнопки (серый квадрат, который реагирует на наведение)
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        // Затем рисуем предмет косточки поверх кнопки.
        // Предмет имеет размер 16x16, кнопка 20x20. Смещение +2 ставит иконку ровно по центру.
        guiGraphics.renderItem(this.iconStack, this.getX() + 2, this.getY() + 2);
    }
}
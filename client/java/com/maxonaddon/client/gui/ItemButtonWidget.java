package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Кнопка, подписанная предметом вместо текста: изумруд — согласие, барьер — отказ.
 * Смысл действия читается по иконке, а словами он продублирован в подсказке при наведении.
 */
public class ItemButtonWidget extends AbstractButton {

    private static final int SIZE = 20;

    private static final int BG_NORMAL = 0xFF2B2B2B;
    private static final int BG_HOVER = 0xFF4A4A4A;
    private static final int BORDER = 0xFF000000;

    private final ItemStack icon;
    private final OnPress action;

    public ItemButtonWidget(int x, int y, ItemStack icon, Component label, OnPress action) {
        super(x, y, SIZE, SIZE, label);
        this.icon = icon;
        this.action = action;
        this.setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress() {
        this.action.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();

        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, BORDER);
        graphics.fill(x, y, x + SIZE, y + SIZE, this.isHovered() ? BG_HOVER : BG_NORMAL);
        // Предмет 16x16 в кнопке 20x20 — отступ по 2 пикселя с каждой стороны.
        graphics.renderItem(this.icon, x + 2, y + 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    /** Свой интерфейс, чтобы не тянуть Button.OnPress с его типом параметра. */
    @FunctionalInterface
    public interface OnPress {
        void onPress(ItemButtonWidget button);
    }
}

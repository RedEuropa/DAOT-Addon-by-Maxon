package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class QuestJournalButton extends Button {
    private static final int SIZE = 20;
    private static final int BG = 0xFF373737;
    private static final int BORDER = 0xFF8B8B8B;

    private static final ItemStack ICON = new ItemStack(Items.WRITABLE_BOOK);

    public QuestJournalButton(int x, int y, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();

        graphics.fill(x, y, x + SIZE, y + SIZE, BG);
        graphics.renderOutline(x, y, SIZE, SIZE, this.isHoveredOrFocused() ? 0xFFFFFFFF : BORDER);

        // Рендерим иконку книги с пером в центре кнопки
        graphics.renderItem(ICON, x + 2, y + 2);

        if (this.isHoveredOrFocused()) {
            graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, Component.literal("Журнал заданий"), mouseX, mouseY);
        }
    }
}
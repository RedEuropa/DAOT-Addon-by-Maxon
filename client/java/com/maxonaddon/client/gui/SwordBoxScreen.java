package com.maxonaddon.client.gui;

import com.maxonaddon.menu.SwordBoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Полностью своя разметка, без ванильной текстуры сундука — просто белая панель с рамкой
 * и нарисованными "ячейками" под каждый слот (все взяты прямо из меню, так что автоматически
 * покрывает и наши 11 слотов, и инвентарь игрока, без ручного дублирования координат).
 */
public class SwordBoxScreen extends AbstractContainerScreen<SwordBoxMenu> {

    private static final int PANEL_COLOR = 0xFFF5F5F5;      // почти белый фон панели
    private static final int PANEL_BORDER = 0xFF404040;     // тёмная рамка панели
    private static final int SLOT_COLOR = 0xFF8B8B8B;       // светло-серая "ячейка" (как в ваниле)
    private static final int SLOT_BORDER = 0xFF373737;      // рамка ячейки

    public SwordBoxScreen(SwordBoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Белая панель с тонкой рамкой вместо текстуры сундука.
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, PANEL_BORDER);

        // Разметка под каждый слот — берём координаты прямо из меню (наши 11 + инвентарь
        // игрока), поэтому не нужно дублировать позиции вручную и рисовать лишние ячейки
        // там, где слотов на самом деле нет (как было бы с чужой текстурой сундука).
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            guiGraphics.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
            guiGraphics.renderOutline(sx, sy, 18, 18, SLOT_BORDER);
        }
    }
}

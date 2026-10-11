package com.maxonaddon.client.gui;

import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import com.maxonaddon.menu.GasCisternaMenu;
import daot.GasCanisterItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Свой экран цистерны — белая панель, 2 слота, и надпись о заполненности бака (плюс баллона,
 * если он стоит), нарисованная как полоса-индикатор — тем же принципом, что полоса прочности
 * у предметов: заполненная часть залита цветом, рядом точное число.
 */
public class GasCisternaScreen extends AbstractContainerScreen<GasCisternaMenu> {

    private static final int PANEL_COLOR = 0xFFF5F5F5;
    private static final int PANEL_BORDER = 0xFF404040;
    private static final int SLOT_COLOR = 0xFF8B8B8B;
    private static final int SLOT_BORDER = 0xFF373737;

    private static final int BAR_BG = 0xFF2B2B2B;
    private static final int BAR_BORDER = 0xFF141414;
    private static final int BAR_FILL_TANK = 0xFF35C9F1;
    private static final int BAR_FILL_CANISTER = 0xFF57D66B;

    public GasCisternaScreen(GasCisternaMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // super.render() (стандартная реализация Screen) сама вызывает renderBackground()
        // первым делом — повторный явный вызов здесь удвоил бы размытие фона (та же причина,
        // что уже чинили в CarriageScreen/PerksScreen в этой сессии). Полагаемся на один вызов.
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
        graphics.renderOutline(x, y, this.imageWidth, this.imageHeight, PANEL_BORDER);

        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
            graphics.renderOutline(sx, sy, 18, 18, SLOT_BORDER);
        }

        this.renderTankBar(graphics, x, y);
        this.renderCanisterBar(graphics, x, y);
    }

    /** Полоса заполненности внутреннего бака цистерны — видна всегда, независимо от того, стоит ли баллон. */
    private void renderTankBar(GuiGraphics graphics, int panelX, int panelY) {
        GasCisternaBlockEntity cisterna = this.getCisterna();
        if (cisterna == null) return;

        int stored = cisterna.getStoredGas();
        int capacity = GasCisternaBlockEntity.TANK_CAPACITY;

        int barX = panelX + 26;
        int barY = panelY + 22;
        int barWidth = 124;
        int barHeight = 8;

        graphics.drawString(this.font, Component.translatable("gui.maxonaddon.gas_cisterna.tank"), barX, barY - 10, 0xFF404040, false);
        this.drawBar(graphics, barX, barY, barWidth, barHeight, stored, capacity, BAR_FILL_TANK);

        Component amount = Component.literal(stored + " / " + capacity);
        int textWidth = this.font.width(amount);
        graphics.drawString(this.font, amount, barX + barWidth / 2 - textWidth / 2, barY + 1, 0xFFFFFFFF, false);
    }

    /** Полоса заполненности баллона — только если он реально стоит во втором слоте. */
    private void renderCanisterBar(GuiGraphics graphics, int panelX, int panelY) {
        ItemStack canister = this.menu.getSlot(1).getItem();
        if (!(canister.getItem() instanceof GasCanisterItem)) return;

        int gas = GasCanisterItem.getGas(canister);
        int maxGas = GasCanisterItem.MAX_GAS;

        int barX = panelX + 96;
        int barY = panelY + 64;
        int barWidth = 60;
        int barHeight = 6;

        this.drawBar(graphics, barX, barY, barWidth, barHeight, gas, maxGas, BAR_FILL_CANISTER);
    }

    private void drawBar(GuiGraphics graphics, int x, int y, int width, int height, int value, int max, int fillColor) {
        graphics.fill(x, y, x + width, y + height, BAR_BG);

        if (max > 0 && value > 0) {
            int filled = Math.max(1, (int) ((long) width * value / max));
            graphics.fill(x, y, x + Math.min(filled, width), y + height, fillColor);
        }

        graphics.renderOutline(x, y, width, height, BAR_BORDER);
    }

    private GasCisternaBlockEntity getCisterna() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        BlockEntity be = this.minecraft.level.getBlockEntity(this.menu.getBlockPos());
        return be instanceof GasCisternaBlockEntity cisterna ? cisterna : null;
    }
}

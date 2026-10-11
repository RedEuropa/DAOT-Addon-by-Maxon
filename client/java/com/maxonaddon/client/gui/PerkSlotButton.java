package com.maxonaddon.client.gui;

import com.maxonaddon.perk.ModPerk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Один из 5 слотов перка. Рисуется как ячейка инвентаря (тёмный квадрат с рамкой) — так же,
 * как слоты в SwordBoxScreen, без текстур. Если в слот вставлен перк, поверх ячейки
 * рисуется его иконка (предмет своего перка, см. ModPerk#getIconItem, или спрайт эффекта).
 */
public class PerkSlotButton extends Button {

    private static final int SIZE = 20;
    private static final int BG_EMPTY = 0xFF373737;
    private static final int BG_FILLED = 0xFF3B3423;
    private static final int BORDER = 0xFF8B8B8B;

    private ModPerk equipped;

    public PerkSlotButton(int x, int y, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    public void setEquipped(ModPerk perk) {
        this.equipped = perk;
    }

    public ModPerk getEquipped() {
        return this.equipped;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();

        graphics.fill(x, y, x + SIZE, y + SIZE, this.equipped != null ? BG_FILLED : BG_EMPTY);
        graphics.renderOutline(x, y, SIZE, SIZE, this.isHoveredOrFocused() ? 0xFFFFFFFF : BORDER);

        if (this.equipped != null) {
            PerkIconRenderer.render(graphics, this.equipped, x, y, SIZE, new ItemStack(this.equipped.getIconItem()));
        }
    }
}

package com.maxonaddon.client.gui;

import com.maxonaddon.perk.ModPerk;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Плитка одного перка (или "снять перк") в PerkListScreen — всегда показывает иконку
 * (предмета или эффекта, см. PerkIconRenderer), без текстовой подписи на самой кнопке.
 * Название и описание — в тултипе при наведении, который рисует сам PerkListScreen
 * (тултип должен лечь НАД соседними плитками, поэтому решение "рисовать поверх всего"
 * отдаём экрану, а не кнопке).
 *
 * Если перк ещё не разблокирован, экран выставляет this.active = false (унаследованное
 * поле AbstractWidget) — это само по себе блокирует клик, а здесь мы дополнительно
 * затемняем плитку, чтобы разница была видна на глаз.
 *
 * perk == null — плитка "снять перк": иконка всегда fallbackIcon (барьер), эффекта нет.
 */
public class PerkChoiceButton extends Button {

    private static final int SIZE = 20;
    private static final int LOCKED_OVERLAY = 0xB0000000;

    @Nullable private final ModPerk perk;
    private final ItemStack fallbackIcon;

    public PerkChoiceButton(int x, int y, @Nullable ModPerk perk, ItemStack fallbackIcon, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
        this.perk = perk;
        this.fallbackIcon = fallbackIcon;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();

        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF373737);
        graphics.renderOutline(x, y, SIZE, SIZE, this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF8B8B8B);

        if (this.perk != null) {
            PerkIconRenderer.render(graphics, this.perk, x, y, SIZE, this.fallbackIcon);
        } else {
            graphics.renderItem(this.fallbackIcon, x + 2, y + 2);
        }

        if (!this.active) {
            graphics.fill(x, y, x + SIZE, y + SIZE, LOCKED_OVERLAY);
        }
    }
}

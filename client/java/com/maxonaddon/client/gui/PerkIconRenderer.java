package com.maxonaddon.client.gui;

import com.maxonaddon.perk.ModPerk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

/**
 * Общая отрисовка плитки перка — используется в PerkSlotButton (слоты PerksScreen) и
 * PerkChoiceButton (список выбора в PerkListScreen), чтобы не дублировать одну и ту же
 * развилку "спрайт эффекта или иконка-предмет" в двух местах.
 *
 * Перки без своей иконки (ModPerk.getEffectIcon() == null) рисуются как обычная плитка-предмет
 * (нетер-звезда, см. вызовы) — так исторически показывались вообще все перки. Перки со своим
 * эффектом (например, "Боец Человечества" — иконка статус-эффекта "Герой деревни") рисуются
 * тем же спрайтом, что и в инвентарной панели эффектов зелий (Minecraft.getMobEffectTextures()).
 */
final class PerkIconRenderer {

    /** Размер спрайта эффекта в атласе — как в EffectRenderingInventoryScreen. */
    private static final int EFFECT_ICON_SIZE = 18;

    private PerkIconRenderer() {}

    static void render(GuiGraphics graphics, ModPerk perk, int slotX, int slotY, int slotSize, ItemStack fallbackIcon) {
        Holder<MobEffect> effect = perk.getEffectIcon();

        if (effect != null) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
            int pad = (slotSize - EFFECT_ICON_SIZE) / 2;
            graphics.blit(slotX + pad, slotY + pad, 0, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE, sprite);
        } else {
            int itemPad = (slotSize - 16) / 2;
            graphics.renderItem(fallbackIcon, slotX + itemPad, slotY + itemPad);
        }
    }
}

package com.maxonaddon.client.gui;

import com.maxonaddon.network.EquipPerkPayload;
import com.maxonaddon.perk.ModPerk;
import com.maxonaddon.util.IPlayerStats;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Прозрачное окно со списком перков для одного слота — открывается кликом по слоту
 * в PerksScreen. Фон полупрозрачный (0x66), легче непрозрачного фона PerksScreen (0xB3):
 * родительское меню и персонаж за ним остаются видны.
 *
 * Перки показаны плитками-предметами (как иконка на кнопке в инвентаре), а не текстовыми
 * кнопками на всю ширину — так и компактнее, и не приходится придумывать, куда девать длинный
 * текст названия. Название и описание — в тултипе при наведении.
 *
 * НЕполученные перки в списке вообще не показываются — их получение задумано как загадка
 * (какой-то challenge/секрет), а не как список целей с подсказками. Показываем только
 * плитки уже разблокированных перков + плитку "снять".
 *
 * Ряд плиток стоит внизу экрана, а не по центру: персонаж в PerksScreen рисуется примерно
 * посередине экрана через 3D-рендер модели со своим тестом глубины — 2D-виджеты, нарисованные
 * в той же области, рискуют быть перекрыты моделью независимо от порядка вызовов в коде.
 * Расположив плитки заведомо ниже области персонажа, избегаем этого пересечения совсем,
 * а не боремся с порядком отрисовки.
 */
public class PerkListScreen extends Screen {

    private static final int TILE_SIZE = 20;
    private static final int GAP = 6;

    private final PerksScreen parent;
    private final int slot;

    private final List<Row> rows = new ArrayList<>();

    /** perk == null — это плитка "снять перк", у неё есть название, но нет описания. */
    private record Row(PerkChoiceButton button, ModPerk perk) {}

    public PerkListScreen(PerksScreen parent, int slot) {
        super(Component.translatable("gui.maxonaddon.perks.choose"));
        this.parent = parent;
        this.slot = slot;
    }

    @Override
    protected void init() {
        this.buildTiles();
    }

    /** Пересобирает ряд плиток по текущему списку разблокированных перков игрока. */
    private void buildTiles() {
        this.clearWidgets();
        this.rows.clear();

        if (this.minecraft == null || this.minecraft.player == null) return;
        IPlayerStats stats = (IPlayerStats) this.minecraft.player;

        List<ModPerk> unlocked = new ArrayList<>();
        for (ModPerk perk : ModPerk.values()) {
            if (stats.hasUnlockedPerk(perk.getId())) unlocked.add(perk);
        }

        // +1 плитка на "снять перк" в конце ряда.
        int tileCount = unlocked.size() + 1;
        int totalWidth = tileCount * TILE_SIZE + (tileCount - 1) * GAP;
        int startX = this.width / 2 - totalWidth / 2;
        int y = this.height - 60;

        for (int i = 0; i < unlocked.size(); i++) {
            ModPerk perk = unlocked.get(i);
            int x = startX + i * (TILE_SIZE + GAP);

            PerkChoiceButton button = this.addRenderableWidget(
                    new PerkChoiceButton(x, y, perk, new ItemStack(perk.getIconItem()), b -> this.choose(perk.getId()))
            );
            this.rows.add(new Row(button, perk));
        }

        // Плитка "снять перк" — отдельным барьером в конце ряда.
        int clearX = startX + unlocked.size() * (TILE_SIZE + GAP);
        PerkChoiceButton clearButton = this.addRenderableWidget(
                new PerkChoiceButton(clearX, y, null, new ItemStack(Items.BARRIER), b -> this.choose(null))
        );
        this.rows.add(new Row(clearButton, null));
    }

    private void choose(String perkId) {
        ClientPlayNetworking.send(new EquipPerkPayload(this.slot, perkId == null ? "" : perkId));
        this.parent.refreshSlotIcons();
        this.minecraft.setScreen(this.parent);
    }

    /**
     * Пересобирает ряд плиток заново — вызывается клиентским обработчиком
     * SyncUnlockedPerksPayload, если это окно уже открыто в момент разблокировки нового
     * перка (редкий случай, но плитка должна появиться сразу, не только при переоткрытии).
     */
    public void refreshLockedState() {
        this.buildTiles();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — реальное затемнение рисуется вручную в render(), см. PerksScreen.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Родительский экран — полностью сам себя рисует (фон, слоты, персонаж, всё в
        // правильном порядке внутри), и только ПОСЛЕ этого мы накладываем своё затемнение
        // и рисуем плитки поверх. Мышь передаём РЕАЛЬНУЮ: раньше здесь стояло -1,-1 (чтобы
        // кнопки родителя не подсвечивались), но renderEntityInInventoryFollowsMouse() эти же
        // координаты использует, чтобы поворачивать модель персонажа вслед за курсором —
        // фиксированная точка (-1,-1) держала её неподвижной, отсюда "персонаж перестаёт
        // двигаться". Подсветка неактивных кнопок родителя — не проблема, кликнуть по ним
        // всё равно нельзя, пока активен этот экран.
        this.parent.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x66000000);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height - 78, 0xFFFFFF);

        // Виджеты — последними, поверх затемнения.
        super.render(graphics, mouseX, mouseY, partialTick);

        // Все плитки здесь — уже разблокированные перки (или "снять"), локи и подсказки
        // не нужны: неполученные перки просто не попадают в этот список.
        for (Row row : this.rows) {
            if (!row.button().isHovered()) continue;

            List<Component> tooltip = new ArrayList<>();
            if (row.perk() != null) {
                tooltip.add(Component.translatable(row.perk().getNameKey()));
                tooltip.add(Component.translatable(row.perk().getDescKey()));
            } else {
                tooltip.add(Component.translatable("gui.maxonaddon.perks.clear"));
            }
            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

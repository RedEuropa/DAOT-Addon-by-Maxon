package com.maxonaddon.client.gui;

import com.maxonaddon.perk.ModPerk;
import com.maxonaddon.perk.PerkManager;
import com.maxonaddon.util.IPlayerStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню перков — визуально копирует TraumaScreen (тот же тёмный фон и персонаж в рамке),
 * но вместо 4 конечностей — 5 слотов перка справа от персонажа. Клик по слоту открывает
 * PerkListScreen с прозрачным списком доступных перков для этого слота.
 *
 * === ПОРЯДОК ОТРИСОВКИ (важно, наступили на грабли) ===
 * renderBackground() здесь НАРОЧНО пустой. Стандартный Screen.render() сам вызывает
 * renderBackground() первым делом — если оставить в нём реальную закраску, она наложится
 * ДВАЖДЫ за кадр (once явно, once изнутри super.render()), экран темнеет сильнее задуманного.
 * Настоящий фон закрашиваем один раз вручную в самом render().
 *
 * Персонаж рисуется ДО финального super.render() (то есть до кнопок-слотов), а не после:
 * InventoryScreen.renderEntityInInventoryFollowsMouse() рендерит 3D-модель со своим тестом
 * глубины, и если что-то из 2D-интерфейса нарисовать ПОСЛЕ неё в одном кадре — это что-то
 * рискует быть перекрыто моделью независимо от порядка вызовов в коде. Виджеты должны
 * оставаться последними — так же, как в TraumaScreen, откуда и скопирована эта структура.
 */
public class PerksScreen extends Screen {

    private final List<PerkSlotButton> slotButtons = new ArrayList<>(PerkManager.SLOTS);

    public PerksScreen() {
        super(Component.translatable("gui.maxonaddon.perks.title"));
    }

    @Override
    protected void init() {
        this.slotButtons.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int slotsX = centerX + 70;
        int firstSlotY = centerY - 55;

        for (int i = 0; i < PerkManager.SLOTS; i++) {
            final int slot = i;
            int y = firstSlotY + i * 22;

            PerkSlotButton button = this.addRenderableWidget(new PerkSlotButton(slotsX, y, b ->
                    this.minecraft.setScreen(new PerkListScreen(this, slot))
            ));
            this.slotButtons.add(button);
        }

        this.refreshSlotIcons();
    }

    /** Перечитывает текущие перки игрока и обновляет иконки на кнопках. Вызывается при возврате из списка. */
    public void refreshSlotIcons() {
        if (this.minecraft == null || this.minecraft.player == null) return;

        String[] equipped = ((IPlayerStats) this.minecraft.player).getEquippedPerks();
        for (int i = 0; i < this.slotButtons.size() && i < equipped.length; i++) {
            this.slotButtons.get(i).setEquipped(ModPerk.byId(equipped[i]));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — см. пояснение в шапке класса.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Настоящий фон — ровно один раз.
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, centerY - 100, 0xFFFFFF);

        if (this.minecraft != null && this.minecraft.player != null) {
            Player player = this.minecraft.player;

            int boxLeft = centerX - 50;
            int boxTop = centerY - 50;
            int boxRight = centerX + 50;
            int boxBottom = centerY + 70;
            int scale = 55;

            // Прячем снаряжение на время рендера — так же, как делает TraumaScreen,
            // чтобы шлем/нагрудник не перекрывали модель персонажа в рамке.
            ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack legsItem = player.getItemBySlot(EquipmentSlot.LEGS);
            ItemStack feetItem = player.getItemBySlot(EquipmentSlot.FEET);
            ItemStack mainHand = player.getItemBySlot(EquipmentSlot.MAINHAND);
            ItemStack offHand = player.getItemBySlot(EquipmentSlot.OFFHAND);

            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, boxLeft, boxTop, boxRight, boxBottom, scale, 0.0f, (float) mouseX, (float) mouseY, player
            );

            player.setItemSlot(EquipmentSlot.HEAD, headItem);
            player.setItemSlot(EquipmentSlot.CHEST, chestItem);
            player.setItemSlot(EquipmentSlot.LEGS, legsItem);
            player.setItemSlot(EquipmentSlot.FEET, feetItem);
            player.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
            player.setItemSlot(EquipmentSlot.OFFHAND, offHand);
        }

        // Виджеты — строго последними. renderBackground() у этого класса пустой,
        // так что здесь ничего не закрасится повторно, только кнопки слотов.
        super.render(graphics, mouseX, mouseY, partialTick);

        for (PerkSlotButton button : this.slotButtons) {
            if (button.isHovered() && button.getEquipped() != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(button.getEquipped().getNameKey()));
                tooltip.add(Component.translatable(button.getEquipped().getDescKey()));
                graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

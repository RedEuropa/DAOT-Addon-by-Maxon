package com.maxonaddon.client.gui;

import com.maxonaddon.network.BuySkillPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class SkillBuyScreen extends Screen {
    private final Screen parent;
    private final String nameKey;
    private final String descKey;
    private final int cost;
    private final int currentSp;
    private final String skillId; // Чтобы сервер знал, что мы покупаем

    public SkillBuyScreen(Screen parent, String nameKey, String descKey, int cost, int currentSp, String skillId) {
        super(Component.translatable(nameKey));
        this.parent = parent;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.cost = cost;
        this.currentSp = currentSp;
        this.skillId = skillId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = this.height / 2 + 30;

        boolean canAfford = this.currentSp >= this.cost;

        // Кнопка Изучить
        Button learnBtn = Button.builder(Component.translatable("gui.maxonaddon.skills.learn"), button -> {
            ClientPlayNetworking.send(new BuySkillPayload(this.skillId));
            this.minecraft.setScreen(null); // Закрываем меню после покупки
        }).bounds(centerX - 105, y, 100, 20).build();

        learnBtn.active = canAfford; // Нельзя нажать, если нет SP
        this.addRenderableWidget(learnBtn);

        // Кнопка Отмена
        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.skills.cancel"), button -> {
            this.minecraft.setScreen(this.parent); // Возвращаемся в список
        }).bounds(centerX + 5, y, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;

        // Название
        graphics.drawCenteredString(this.font, Component.translatable(this.nameKey).withStyle(ChatFormatting.GOLD), centerX, 50, 0xFFFFFF);

        // Описание
        graphics.drawCenteredString(this.font, Component.translatable(this.descKey), centerX, 80, 0xAAAAAA);

        // Стоимость
        Component costComp = Component.translatable("gui.maxonaddon.skills.cost", this.cost)
                .withStyle(this.currentSp >= this.cost ? ChatFormatting.GREEN : ChatFormatting.RED);
        graphics.drawCenteredString(this.font, costComp, centerX, 110, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
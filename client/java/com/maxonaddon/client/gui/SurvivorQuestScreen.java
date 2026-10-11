package com.maxonaddon.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * Разговор с раненым выжившим на аванпосте разбойников (см. SurvivorQuestHandler).
 * В отличие от QuestScreen тут нет списка заданий — только один вопрос "помочь или нет",
 * причём отказ не бесплатный (штраф репутацией считает сервер).
 */
public class SurvivorQuestScreen extends Screen {
    private final int survivorId;

    public SurvivorQuestScreen(int survivorId) {
        super(Component.translatable("gui.maxonaddon.survivor.title"));
        this.survivorId = survivorId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.survivor.help").withStyle(ChatFormatting.GREEN), button -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.maxonaddon.network.SurvivorActionPayload(0, survivorId)
            );
            this.onClose();
        }).bounds(centerX - 105, centerY + 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.survivor.refuse").withStyle(ChatFormatting.RED), button -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.maxonaddon.network.SurvivorActionPayload(1, survivorId)
            );
            this.onClose();
        }).bounds(centerX + 5, centerY + 60, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — затемнение рисуется вручную в render(), см. QuestScreen (двойной
        // вызов renderBackground удвоил бы затемнение).
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Сам раненый — слева, чтобы было видно, о ком речь.
        if (this.minecraft != null && this.minecraft.level != null) {
            net.minecraft.world.entity.Entity entity = this.minecraft.level.getEntity(survivorId);
            if (entity instanceof LivingEntity survivor) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        graphics, centerX - 150, centerY - 50, centerX - 50, centerY + 40,
                        45, 0.0f, (float) mouseX, (float) mouseY, survivor
                );
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, centerY - 90, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.line1"), centerX + 40, centerY - 55, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.line2"), centerX + 40, centerY - 42, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.line3"), centerX + 40, centerY - 29, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.reward").withStyle(ChatFormatting.AQUA), centerX, centerY + 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.survivor.penalty").withStyle(ChatFormatting.RED), centerX, centerY + 26, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

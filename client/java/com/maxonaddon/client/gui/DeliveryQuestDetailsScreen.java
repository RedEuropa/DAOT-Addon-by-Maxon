package com.maxonaddon.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Детали квеста "Доставить припасы" — в отличие от QuestDetailsScreen, тут нет заранее
 * известной цели: получателя (случайный ДРУГОЙ командир) выбирает сервер прямо в момент
 * принятия (см. DeliveryQuestHandler.startDeliveryQuest), клиент узнаёт его только из
 * последующего SyncQuestPayload/выданного компаса.
 */
public class DeliveryQuestDetailsScreen extends Screen {
    private final Screen parentScreen;
    private final int commanderId;

    public DeliveryQuestDetailsScreen(Screen parentScreen, int commanderId) {
        super(Component.literal("Детали задания"));
        this.parentScreen = parentScreen;
        this.commanderId = commanderId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.accept").withStyle(ChatFormatting.GREEN), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.maxonaddon.network.QuestActionPayload(0, "delivery_quest", "", commanderId)
                );
                this.minecraft.player.sendSystemMessage(Component.translatable("message.maxonaddon.quest.accepted_check_journal").withStyle(ChatFormatting.GREEN));
                this.onClose();
            }
        }).bounds(centerX - 105, centerY + 80, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.decline").withStyle(ChatFormatting.RED), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }).bounds(centerX + 5, centerY + 80, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — см. QuestDetailsScreen, та же схема двойного затемнения.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.details_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, centerY - 90, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.details_line1"), centerX, centerY - 60, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.details_line2"), centerX, centerY - 45, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.conditions").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE), centerX, centerY - 15, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.condition_seal1").withStyle(ChatFormatting.RED), centerX, centerY + 5, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.condition_seal2").withStyle(ChatFormatting.RED), centerX, centerY + 18, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.condition_timer").withStyle(ChatFormatting.GREEN), centerX, centerY + 33, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.delivery.reward_line").withStyle(ChatFormatting.AQUA), centerX, centerY + 50, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

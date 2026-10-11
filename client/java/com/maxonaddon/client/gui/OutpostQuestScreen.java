package com.maxonaddon.client.gui;

import com.maxonaddon.network.OutpostActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Разговор с кадетом на сторожевой вышке.
 * <p>
 * Окно одно, а вида два: пока перерыв не вышел, кадеты благодарят за прошлую вылазку и
 * показывают, сколько ждать до следующей; иначе предлагают зачистку. Что именно показывать,
 * решает сервер (см. OutpostHuntQuestHandler) — он присылает остаток перерыва.
 */
public class OutpostQuestScreen extends Screen {

    private final int cadetId;
    private final int titansRequired;

    /** Остаток перерыва в тиках на момент открытия окна; 0 — задание доступно. */
    private final int cooldownTicks;

    /** Тик клиента, на котором окно открыли — от него отсчитываем обратный отсчёт. */
    private int openedAtTick;

    public OutpostQuestScreen(int cadetId, int titansRequired, String wallName, int cooldownTicks) {
        super(Component.translatable("gui.maxonaddon.outpost.title", wallDisplayName(wallName)));
        this.cadetId = cadetId;
        this.titansRequired = titansRequired;
        this.cooldownTicks = cooldownTicks;
    }

    /** Сервер шлёт стену как enum-имя — в текст переводим уже здесь, под язык этого клиента. */
    private static Component wallDisplayName(String wallName) {
        com.maxonaddon.util.Wall wall = com.maxonaddon.util.Wall.fromNameSafe(wallName);
        return wall != null ? Component.translatable(wall.getTranslationKey()) : Component.literal("?");
    }

    private boolean onCooldown() {
        return this.cooldownTicks > 0;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.openedAtTick = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.tickCount : 0;

        if (onCooldown()) {
            // Брать нечего — только попрощаться.
            this.addRenderableWidget(Button.builder(
                    Component.translatable("gui.maxonaddon.outpost.close"),
                    button -> this.onClose()).bounds(centerX - 50, centerY + 60, 100, 20).build());
            return;
        }

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.maxonaddon.outpost.accept").withStyle(ChatFormatting.GREEN),
                button -> {
                    ClientPlayNetworking.send(new OutpostActionPayload(0, cadetId));
                    this.onClose();
                }).bounds(centerX - 105, centerY + 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.maxonaddon.outpost.refuse").withStyle(ChatFormatting.RED),
                button -> {
                    ClientPlayNetworking.send(new OutpostActionPayload(1, cadetId));
                    this.onClose();
                }).bounds(centerX + 5, centerY + 60, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — Screen.render() сам вызывает renderBackground(), повторный вызов
        // удваивал бы затемнение (как и в остальных наших экранах).
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD9000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font,
                this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX, centerY - 80, 0xFFFFFF);

        if (onCooldown()) {
            renderCooldown(graphics, centerX, centerY);
            return;
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.line1"), centerX, centerY - 50, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.line2"), centerX, centerY - 37, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.task", titansRequired)
                        .withStyle(ChatFormatting.YELLOW),
                centerX, centerY - 12, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.reward").withStyle(ChatFormatting.AQUA),
                centerX, centerY + 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.reward_note").withStyle(ChatFormatting.GRAY),
                centerX, centerY + 26, 0xFFFFFF);
    }

    private void renderCooldown(GuiGraphics graphics, int centerX, int centerY) {
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.thanks").withStyle(ChatFormatting.GREEN),
                centerX, centerY - 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.thanks_note").withStyle(ChatFormatting.GRAY),
                centerX, centerY - 24, 0xFFFFFF);

        graphics.drawCenteredString(this.font,
                Component.translatable("gui.maxonaddon.outpost.next_in", formatRemaining())
                        .withStyle(ChatFormatting.YELLOW),
                centerX, centerY + 20, 0xFFFFFF);
    }

    /**
     * Обратный отсчёт. Считаем от тика игрока, а не от игрового времени мира: сервер прислал
     * остаток один раз, и пересчитывать его нужно локально, иначе строка застыла бы на месте
     * всё время, пока окно открыто.
     */
    private String formatRemaining() {
        int elapsed = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.tickCount - this.openedAtTick : 0;
        int left = Math.max(0, this.cooldownTicks - Math.max(0, elapsed));

        int totalSeconds = left / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

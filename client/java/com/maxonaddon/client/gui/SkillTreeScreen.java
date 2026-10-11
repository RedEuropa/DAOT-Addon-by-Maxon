package com.maxonaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Set;

public class SkillTreeScreen extends Screen {
    private final Screen parent;
    private final int level, exp, requiredExp, skillPoints, bloodlinePoints, titanPoints;
    private final String bloodlineStr, shifterStatus;
    private final Set<String> unlockedSkills;

    public SkillTreeScreen(Screen parent, int level, int exp, int requiredExp, int skillPoints,
                           int bloodlinePoints, int titanPoints,
                           String bloodlineStr, String shifterStatus, Set<String> unlockedSkills) {
        super(Component.translatable("gui.maxonaddon.skills.title"));
        this.parent = parent;
        this.level = level; this.exp = exp; this.requiredExp = requiredExp;
        this.skillPoints = skillPoints; this.bloodlinePoints = bloodlinePoints; this.titanPoints = titanPoints;
        this.bloodlineStr = bloodlineStr; this.shifterStatus = shifterStatus;
        this.unlockedSkills = unlockedSkills;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Передаем список навыков в каждое меню
        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.skills.soldier"), button -> {
            this.minecraft.setScreen(new SoldierSkillsScreen(this, this.skillPoints, this.unlockedSkills));
        }).bounds(centerX - 100, startY, 200, 20).build());

        boolean isShifter = !this.shifterStatus.equals("HIDDEN") && !this.shifterStatus.equals("Нет") && !this.shifterStatus.equals("None");
        if (isShifter) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.skills.titan"), button -> {
                this.minecraft.setScreen(new TitanSkillsScreen(this, this.titanPoints, this.unlockedSkills));
            }).bounds(centerX - 100, startY + 25, 200, 20).build());
        }

        boolean hasSpecialBloodline = this.bloodlineStr.equalsIgnoreCase("ACKERMAN") || this.bloodlineStr.equalsIgnoreCase("ROYAL");
        if (hasSpecialBloodline) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.skills.bloodline"), button -> {
                this.minecraft.setScreen(new BloodlineSkillsScreen(this, this.bloodlinePoints, this.bloodlineStr, this.unlockedSkills));
            }).bounds(centerX - 100, startY + 50, 200, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.skills.reset"), button -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.maxonaddon.network.ResetSkillsPayload());
            this.minecraft.setScreen(null);
        }).bounds(centerX + 110, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ... (ваш старый код отрисовки полосок опыта, без изменений)
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);
        Component spText = Component.translatable("gui.maxonaddon.skills.sp", this.skillPoints).withStyle(ChatFormatting.GOLD);
        graphics.drawCenteredString(this.font, spText, centerX, 35, 0xFFFFFF);

        int barWidth = 182;
        int barHeight = 5;
        int barX = centerX - (barWidth / 2);
        int barY = this.height - 45;
        int barY2 = barY - 15;
        int barY3 = barY - 30;

        // --- 1. ОПЫТ ---
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);
        float progress = this.requiredExp > 0 ? (float) this.exp / this.requiredExp : 0;
        int filledWidth = (int) (barWidth * progress);
        graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFF00FF00);
        graphics.drawCenteredString(this.font, this.exp + " / " + this.requiredExp, centerX, barY - 10, 0x80FF20);

        // --- 2. РОДОСЛОВНАЯ ---
        graphics.fill(barX, barY2, barX + barWidth, barY2 + barHeight, 0xFF555555);
        float progressB = Math.min(1.0f, (float) this.bloodlinePoints / 1000);
        graphics.fill(barX, barY2, barX + (int)(barWidth * progressB), barY2 + barHeight, 0xFF00AAFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.skills.bloodline_points", this.bloodlinePoints), centerX, barY2 - 10, 0x00AAFF);

        // --- 3. ТИТАН ---
        graphics.fill(barX, barY3, barX + barWidth, barY3 + barHeight, 0xFF555555);
        float progressT = Math.min(1.0f, (float) this.titanPoints / 500);
        graphics.fill(barX, barY3, barX + (int)(barWidth * progressT), barY3 + barHeight, 0xFFFF0000);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.skills.titan_points_bar", this.titanPoints), centerX, barY3 - 10, 0xFFFF0000);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
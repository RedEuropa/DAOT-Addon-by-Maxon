package com.maxonaddon.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import daot.BloodlineType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import com.maxonaddon.util.Wall;
import com.maxonaddon.util.ReputationHelper;

import java.util.Set;

public class StatsScreen extends Screen {
    private final int killed, fearResist;
    private final double totalHours, hoursWithoutDeath;
    private final String bloodlineStr, shifterStatus, ymirCurseTime;
    private final int level, exp, requiredExp, skillPoints, bloodlinePoints, titanPoints;
    private final int questsCompleted;
    private final int repMaria, repRose, repSina;

    // ЕДИНОЕ ХРАНИЛИЩЕ НАВЫКОВ
    private final Set<String> unlockedSkills;

    public StatsScreen(int killed, int fearResist, double totalHours, double hoursWithoutDeath,
                       String bloodlineStr, String shifterStatus, String ymirCurseTime,
                       int level, int exp, int requiredExp, int skillPoints,
                       int bloodlinePoints, int titanPoints, int questsCompleted,
                       Set<String> unlockedSkills,
                       int repMaria, int repRose, int repSina) {
        super(Component.translatable("gui.maxonaddon.stats.title"));
        this.killed = killed; this.fearResist = fearResist;
        this.totalHours = totalHours; this.hoursWithoutDeath = hoursWithoutDeath;
        this.bloodlineStr = bloodlineStr; this.shifterStatus = shifterStatus; this.ymirCurseTime = ymirCurseTime;
        this.level = level; this.exp = exp; this.requiredExp = requiredExp;
        this.skillPoints = skillPoints; this.bloodlinePoints = bloodlinePoints; this.titanPoints = titanPoints;
        this.questsCompleted = questsCompleted;
        this.unlockedSkills = unlockedSkills;
        this.repMaria = repMaria; this.repRose = repRose; this.repSina = repSina;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
    }

    private int computeBgHeight() {
        boolean isShifter = !this.shifterStatus.equals("HIDDEN");
        boolean hasCurse = ymirCurseTime != null && !ymirCurseTime.isEmpty();
        int bgHeight = 110; // 95 + 15 на строку "квестов выполнено"
        if (isShifter) bgHeight += 15;
        if (isShifter && hasCurse) bgHeight += 15;
        // Три строки репутации стен — только если система включена (см. render()).
        if (com.maxonaddon.client.ClientModState.isReputationEnabled()) bgHeight += 15 * 3;
        return bgHeight;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;
        int bgHeight = computeBgHeight();

        // Передаем ЕДИНЫЙ список навыков дальше
        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.stats.skills_button"), button -> {
            this.minecraft.setScreen(new SkillTreeScreen(
                    this, this.level, this.exp, this.requiredExp, this.skillPoints,
                    this.bloodlinePoints, this.titanPoints,
                    this.bloodlineStr, this.shifterStatus, this.unlockedSkills
            ));
        }).bounds(centerX - 100, startY + bgHeight + 5, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        boolean isShifter = !this.shifterStatus.equals("HIDDEN");
        boolean hasCurse = ymirCurseTime != null && !ymirCurseTime.isEmpty();
        int bgHeight = computeBgHeight();

        graphics.fill(centerX - 130, startY - 10, centerX + 130, startY + bgHeight, 0x80000000);

        // === Персонаж — слева от панели с надписями, по центру её высоты ===
        if (this.minecraft != null && this.minecraft.player != null) {
            int pLeft = centerX - 200, pTop = startY - 10, pRight = centerX - 135, pBottom = startY + bgHeight;
            graphics.fill(pLeft, pTop, pRight, pBottom, 0x80000000);

            int scale = Math.min(45, (pBottom - pTop) / 3);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, pLeft, pTop, pRight, pBottom, scale, 0.0f, (float) mouseX, (float) mouseY, this.minecraft.player
            );
        }

        Component titleComp = Component.translatable("gui.maxonaddon.stats.title").withStyle(Style.EMPTY.withBold(true).withColor(0xFFFFFF));
        graphics.drawCenteredString(this.font, titleComp, centerX, startY, 0xFFFFFF);

        Component bloodlineComp;
        if ("ACKERMAN".equalsIgnoreCase(bloodlineStr)) {
            bloodlineComp = Component.literal("Ackerman").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true));
        } else {
            BloodlineType type = BloodlineType.fromName(bloodlineStr);
            bloodlineComp = type != null ? type.getStyledName() : Component.translatable("gui.maxonaddon.stats.unknown");
        }

        Component fullBloodlineComp = Component.translatable("gui.maxonaddon.stats.bloodline").append(bloodlineComp);
        graphics.drawCenteredString(this.font, fullBloodlineComp, centerX, startY + 20, 0xFFFFFF);

        int yOffset = 35;

        if (isShifter) {
            Component shifterTextComp;
            if (this.shifterStatus.equals("Нет") || this.shifterStatus.equals("None")) {
                shifterTextComp = Component.translatable("gui.maxonaddon.stats.none").withStyle(ChatFormatting.GRAY);
            } else {
                shifterTextComp = Component.literal(this.shifterStatus).withStyle(ChatFormatting.GOLD);
            }

            Component shifterLine = Component.translatable("gui.maxonaddon.stats.shifter", shifterTextComp);
            graphics.drawCenteredString(this.font, shifterLine, centerX, startY + yOffset, 0xFFFFFF);
            yOffset += 15;

            if (hasCurse) {
                Component curseComp;
                if (ymirCurseTime.equals("EXPIRED")) {
                    curseComp = Component.translatable("gui.maxonaddon.stats.ymir_curse.expired").withStyle(ChatFormatting.DARK_RED);
                } else {
                    Component timeValue = Component.literal(ymirCurseTime).withStyle(ChatFormatting.RED);
                    curseComp = Component.translatable("gui.maxonaddon.stats.ymir_curse", timeValue);
                }
                graphics.drawCenteredString(this.font, curseComp, centerX, startY + yOffset, 0xFFFFFF);
                yOffset += 15;
            }
        }

        Component fearValue = Component.literal(fearResist + "%").withStyle(ChatFormatting.RED);
        Component fearLine = Component.translatable("gui.maxonaddon.stats.fear_resist", fearValue);
        graphics.drawCenteredString(this.font, fearLine, centerX, startY + yOffset, 0xFFFFFF);
        yOffset += 15;

        Component killedValue = Component.literal(String.valueOf(killed)).withStyle(ChatFormatting.GRAY);
        Component killedLine = Component.translatable("gui.maxonaddon.stats.titans_killed", killedValue);
        graphics.drawCenteredString(this.font, killedLine, centerX, startY + yOffset, 0xFFFFFF);
        yOffset += 15;

        Component hoursNoDeathValue = Component.literal(String.format("%.2f", hoursWithoutDeath)).withStyle(ChatFormatting.GREEN);
        Component hoursNoDeathLine = Component.translatable("gui.maxonaddon.stats.hours_without_death", hoursNoDeathValue);
        graphics.drawCenteredString(this.font, hoursNoDeathLine, centerX, startY + yOffset, 0xFFFFFF);
        yOffset += 15;

        Component totalHoursValue = Component.literal(String.format("%.2f", totalHours)).withStyle(ChatFormatting.GRAY);
        Component totalHoursLine = Component.translatable("gui.maxonaddon.stats.total_hours", totalHoursValue);
        graphics.drawCenteredString(this.font, totalHoursLine, centerX, startY + yOffset, 0xFFFFFF);
        yOffset += 15;

        Component questsValue = Component.literal(String.valueOf(questsCompleted)).withStyle(ChatFormatting.YELLOW);
        Component questsLine = Component.translatable("gui.maxonaddon.stats.quests_completed", questsValue);
        graphics.drawCenteredString(this.font, questsLine, centerX, startY + yOffset, 0xFFFFFF);
        yOffset += 20;

        // === Репутация у трёх стен (скрыта, если система выключена на сервере) ===
        if (com.maxonaddon.client.ClientModState.isReputationEnabled()) {
            yOffset = drawReputationLine(graphics, centerX, startY + yOffset, Wall.MARIA, repMaria) - startY;
            yOffset = drawReputationLine(graphics, centerX, startY + yOffset, Wall.ROSE, repRose) - startY;
            drawReputationLine(graphics, centerX, startY + yOffset, Wall.SINA, repSina);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Рисует одну строку "Имя стены: [полоска] rep/100" и возвращает Y для следующей строки. */
    private int drawReputationLine(GuiGraphics graphics, int centerX, int y, Wall wall, int rep) {
        boolean russian = this.minecraft != null
                && "ru_ru".equals(this.minecraft.getLanguageManager().getSelected());

        int color = ReputationHelper.getColor(rep);
        Component nameComp = Component.literal(wall.displayName(russian)).withStyle(Style.EMPTY.withColor(color).withBold(true));
        graphics.drawCenteredString(this.font, nameComp, centerX - 60, y, 0xFFFFFF);

        int barWidth = 90, barHeight = 8;
        int barX = centerX - 5, barY = y - 1;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x66404040);
        int filled = (int) (barWidth * (rep / (float) ReputationHelper.MAX));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, color);
        }
        graphics.renderOutline(barX, barY, barWidth, barHeight, 0xFF000000);

        graphics.drawString(this.font, String.valueOf(rep), barX + barWidth + 6, y, 0xFFFFFF, false);

        return y + 15;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

package com.maxonaddon.client.gui;

import com.maxonaddon.skills.ModSkill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Set;

public class BloodlineSkillsScreen extends Screen {
    private final Screen parent;
    private final int bloodlinePoints;
    private final String bloodlineType;
    private final Set<String> unlockedSkills;

    public BloodlineSkillsScreen(Screen parent, int bloodlinePoints, String bloodlineType, Set<String> unlockedSkills) {
        super(Component.translatable("gui.maxonaddon.skills.bloodline"));
        this.parent = parent;
        this.bloodlinePoints = bloodlinePoints;
        this.bloodlineType = bloodlineType;
        this.unlockedSkills = unlockedSkills;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = this.height / 4;

        List<ModSkill> bloodlineSkills = ModSkill.getByCategory(ModSkill.Category.BLOODLINE);

        // Проверяем, изучено ли базовое пробуждение в зависимости от типа крови
        // Для Аккермана это "bloodline_awakening", для Короля — "royal_control_1"
        String awakeningId = bloodlineType.equalsIgnoreCase("ACKERMAN") ? "bloodline_awakening" : "bloodline_awakening";
        boolean hasAwakened = unlockedSkills.contains(awakeningId) || unlockedSkills.contains("royal_control_1");

        int visibleCount = 0;
        for (ModSkill skill : bloodlineSkills) {
            // Пропускаем навыки чужой крови
            if (!skill.bloodlineReq.equals("ANY") && !skill.bloodlineReq.equalsIgnoreCase(this.bloodlineType)) {
                continue;
            }

            boolean isUnlocked = unlockedSkills.contains(skill.id);

            // Базовый навык пробуждения можно учить сразу, остальные — только после пробуждения
            boolean isAwakeningSkill = skill.id.equals("bloodline_awakening");
            boolean baseRequirementsMet = isAwakeningSkill || hasAwakened;

            boolean canUnlock = (skill.parent == null || unlockedSkills.contains(skill.parent.id)) && baseRequirementsMet;

            // Мультиязычная замена ключа для общего навыка пробуждения
            String translationKey = skill.translationKey;
            if (skill.id.equals("bloodline_awakening")) {
                translationKey = bloodlineType.equalsIgnoreCase("ACKERMAN") ? "gui.maxonaddon.skills.ackerman_awakening" : "gui.maxonaddon.skills.royal_roar";
            }

            // Используем уже существующий в ваших lang файлах ключ "gui.maxonaddon.skills.already_learned"[cite: 48, 49]
            MutableComponent text = Component.translatable(translationKey).copy();
            if (isUnlocked) {
                text.append(" (").append(Component.translatable("gui.maxonaddon.skills.already_learned")).append(")");
            }

            final String finalKey = translationKey;
            Button btn = Button.builder(text, button -> {
                if (!isUnlocked && canUnlock) {
                    this.minecraft.setScreen(new SkillBuyScreen(
                            this, Component.translatable(finalKey).getString(),
                            Component.translatable(finalKey + ".desc").getString(),
                            skill.cost, this.bloodlinePoints, skill.id
                    ));
                }
            }).bounds(centerX - 100, startY + (visibleCount * 25), 200, 20).build();

            btn.active = !isUnlocked && canUnlock;

            // Тултипы блокировки с использованием ваших ключей локализации
            if (!isUnlocked && !baseRequirementsMet) {
                btn.setTooltip(Tooltip.create(Component.translatable("message.maxonaddon.skills.not_awakened").withStyle(ChatFormatting.RED)));
            } else if (!isUnlocked && !canUnlock && skill.parent != null) {
                btn.setTooltip(Tooltip.create(Component.translatable("gui.maxonaddon.skills.need_parent", Component.translatable(skill.parent.translationKey)).withStyle(ChatFormatting.RED)));
            }

            this.addRenderableWidget(btn);
            visibleCount++;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        Component pointsText = Component.translatable("gui.maxonaddon.skills.bloodline_points", this.bloodlinePoints);
        graphics.drawCenteredString(this.font, pointsText, this.width / 2, 35, 0xFF00AAFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
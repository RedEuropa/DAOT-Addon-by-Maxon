package com.maxonaddon.client.gui;

import com.maxonaddon.skills.ModSkill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;

import java.util.List;
import java.util.Set;

public class TitanSkillsScreen extends Screen {
    private final Screen parent;
    private final int titanPoints;
    private final Set<String> unlockedSkills;

    // Постраничный вывод — тот же приём, что и в SoldierSkillsScreen: список навыков
    // может вырасти больше, чем помещается на экране без наслоения на "Назад".
    private int page = 0;
    private int perPage = 1;
    private int totalPages = 1;

    private static final int ROW_HEIGHT = 25;
    private static final int LIST_TOP = 45;
    private static final int BOTTOM_RESERVED = 65;

    public TitanSkillsScreen(Screen parent, int titanPoints, Set<String> unlockedSkills) {
        super(Component.translatable("gui.maxonaddon.skills.titan"));
        this.parent = parent;
        this.titanPoints = titanPoints;
        this.unlockedSkills = unlockedSkills;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        List<ModSkill> titanSkills = ModSkill.getByCategory(ModSkill.Category.TITAN);

        int available = this.height - LIST_TOP - BOTTOM_RESERVED;
        this.perPage = Math.max(1, available / ROW_HEIGHT);
        this.totalPages = Math.max(1, (int) Math.ceil(titanSkills.size() / (double) this.perPage));
        this.page = Math.max(0, Math.min(this.page, this.totalPages - 1));

        int from = this.page * this.perPage;
        int to = Math.min(titanSkills.size(), from + this.perPage);

        for (int i = from; i < to; i++) {
            ModSkill skill = titanSkills.get(i);
            boolean isUnlocked = unlockedSkills.contains(skill.id);
            boolean canUnlock = skill.parent == null || unlockedSkills.contains(skill.parent.id);

            Component text = Component.translatable(skill.translationKey);
            if (isUnlocked) {
                text = text.copy().append(" (").append(Component.translatable("gui.maxonaddon.skills.already_learned")).append(")");
            }

            Button btn = Button.builder(text, button -> {
                if (!isUnlocked && canUnlock) {
                    this.minecraft.setScreen(new SkillBuyScreen(
                            this, skill.translationKey, skill.translationKey + ".desc",
                            skill.cost, this.titanPoints, skill.id
                    ));
                }
            }).bounds(centerX - 100, LIST_TOP + ((i - from) * ROW_HEIGHT), 200, 20).build();

            btn.active = !isUnlocked && canUnlock;
            if (!isUnlocked && canUnlock && this.titanPoints < skill.cost) {
                btn.setTooltip(Tooltip.create(Component.translatable("gui.maxonaddon.skills.need_titan_points", skill.cost).withStyle(ChatFormatting.RED)));
            }

            this.addRenderableWidget(btn);
        }

        if (this.totalPages > 1) {
            int navY = this.height - 55;

            Button prev = Button.builder(Component.literal("◀"), b -> {
                this.page = (this.page - 1 + this.totalPages) % this.totalPages;
                this.rebuildWidgets();
            }).bounds(centerX - 100, navY, 40, 20).build();

            Button next = Button.builder(Component.literal("▶"), b -> {
                this.page = (this.page + 1) % this.totalPages;
                this.rebuildWidgets();
            }).bounds(centerX + 60, navY, 40, 20).build();

            this.addRenderableWidget(prev);
            this.addRenderableWidget(next);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.totalPages > 1 && scrollY != 0) {
            if (scrollY < 0) {
                this.page = (this.page + 1) % this.totalPages;
            } else {
                this.page = (this.page - 1 + this.totalPages) % this.totalPages;
            }
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        Component pointsText = Component.translatable("gui.maxonaddon.skills.titan_points", this.titanPoints);
        graphics.drawCenteredString(this.font, pointsText, this.width / 2, 35, 0xFFFF5555);

        if (this.totalPages > 1) {
            graphics.drawCenteredString(this.font,
                    Component.literal((this.page + 1) + " / " + this.totalPages),
                    this.width / 2, this.height - 49, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}

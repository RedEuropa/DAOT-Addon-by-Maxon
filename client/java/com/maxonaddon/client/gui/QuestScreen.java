package com.maxonaddon.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

public class QuestScreen extends Screen {
    private final int commanderId;

    public QuestScreen(int commanderId) {
        super(Component.translatable("gui.maxonaddon.quest.menu_title"));
        this.commanderId = commanderId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (ClientQuestManager.hasQuest()) {
            // Квест уже взят (не обязательно у ЭТОГО НПС, но раз квест сейчас один на игрока —
            // защита от спама "взять ещё одно задание, пока текущее не закрыто" сразу тут).
            this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.turn_in"), button -> {
                // TODO: здесь пока нет проверки, что условия задания реально выполнены
                // (убито 3 титана, собрано 2 пшеницы) — см. TODO в обработчике
                // QuestActionPayload на сервере (action == 2).
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.maxonaddon.network.QuestActionPayload(2, "", "", commanderId) // 2 = Сдать
                );
                this.onClose();
            }).bounds(centerX - 75, centerY - 20, 150, 20).build());
        } else {
            // Нет активного квеста. Вводное берётся у каждого командира по разу (у этого
            // уже сдано — кнопка неактивна, иди к следующему, там планка будет выше), а
            // доставка и спасение открываются после первой сдачи вводного вообще.
            net.minecraft.world.entity.Entity commanderEntity =
                    (this.minecraft != null && this.minecraft.level != null) ? this.minecraft.level.getEntity(commanderId) : null;
            boolean testDoneHere = ClientQuestManager.testQuestDoneAt(commanderEntity);

            Button testButton = Button.builder(
                    testDoneHere
                            ? Component.translatable("gui.maxonaddon.test_quest.done_here").withStyle(ChatFormatting.GRAY)
                            : Component.translatable("gui.maxonaddon.test_quest.button"),
                    button -> {
                        if (this.minecraft != null) this.minecraft.setScreen(new QuestDetailsScreen(this, commanderId));
                    }).bounds(centerX - 75, centerY - 20, 150, 20).build();
            testButton.active = !testDoneHere;
            this.addRenderableWidget(testButton);

            boolean unlocked = ClientQuestManager.otherQuestsUnlocked();

            Button deliveryButton = Button.builder(Component.translatable("gui.maxonaddon.delivery.button"), button -> {
                if (this.minecraft != null) this.minecraft.setScreen(new DeliveryQuestDetailsScreen(this, commanderId));
            }).bounds(centerX - 75, centerY + 5, 150, 20).build();
            deliveryButton.active = unlocked;
            this.addRenderableWidget(deliveryButton);

            Button rescueButton = Button.builder(Component.translatable("gui.maxonaddon.rescue.button"), button -> {
                if (this.minecraft != null) this.minecraft.setScreen(new RescueQuestDetailsScreen(this, commanderId));
            }).bounds(centerX - 75, centerY + 30, 150, 20).build();
            rescueButton.active = unlocked;
            this.addRenderableWidget(rescueButton);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.close"), button -> this.onClose())
                .bounds(centerX - 50, centerY + 80, 100, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — реальное затемнение и персонажи рисуются вручную в render(), см.
        // ниже. Двойной вызов renderBackground() (явно + изнутри super.render()) удвоил бы
        // затемнение — та же причина, что уже чинили в других экранах в этой сессии.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.quest.available"), centerX, centerY - 100, 0xFFD700);

        // Подсказка, почему часть заданий недоступна — иначе серые кнопки выглядят как баг.
        if (!ClientQuestManager.hasQuest() && !ClientQuestManager.otherQuestsUnlocked()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.maxonaddon.quest.locked_hint").withStyle(ChatFormatting.GRAY),
                    centerX, centerY + 55, 0xFFFFFF);
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            // Отрисовка игрока (справа)
            int pLeft = centerX + 50, pTop = centerY - 50, pRight = centerX + 150, pBottom = centerY + 70;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics, pLeft, pTop, pRight, pBottom, 55, 0.0f, (float) mouseX, (float) mouseY, this.minecraft.player
            );

            // Отрисовка командира (слева)
            if (this.minecraft.level != null) {
                net.minecraft.world.entity.Entity entity = this.minecraft.level.getEntity(commanderId);
                if (entity instanceof LivingEntity commander) {
                    int cLeft = centerX - 150, cTop = centerY - 50, cRight = centerX - 50, cBottom = centerY + 70;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics, cLeft, cTop, cRight, cBottom, 55, 0.0f, (float) mouseX, (float) mouseY, commander
                    );
                }
            }
        }

        // Виджеты — последними: рендер персонажей (3D со своим тестом глубины) должен
        // отрисоваться ДО кнопок, иначе кнопки рискуют быть перекрыты моделью независимо
        // от порядка вызовов в коде (см. подробное объяснение в PerksScreen — там ровно
        // такая же схема "персонаж, потом виджеты").
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

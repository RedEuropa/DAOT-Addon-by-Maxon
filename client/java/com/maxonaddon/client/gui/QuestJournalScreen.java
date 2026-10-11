package com.maxonaddon.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuestJournalScreen extends Screen {

    public QuestJournalScreen() {
        super(Component.translatable("gui.maxonaddon.journal.title"));
    }

    /** Совпадает со startY в render() — кнопка отказа рисуется вровень с заголовком квеста. */
    private static final int QUEST_CARD_Y = 60;

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.close"), button -> this.onClose())
                .bounds(centerX - 50, this.height - 30, 100, 20).build());

        if (ClientQuestManager.hasQuest()) {
            // Кнопка отказа привязана к конкретной карточке квеста (а не общая внизу
            // журнала), и шлёт её questId — сервер отказывается ровно от ЭТОГО квеста,
            // а не от "какой квест сейчас активен".
            this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.decline").withStyle(ChatFormatting.RED), button -> {
                if (this.minecraft != null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new com.maxonaddon.network.QuestActionPayload(1, ClientQuestManager.currentQuestId, "", -1)
                    );
                    this.onClose();
                }
            }).bounds(centerX + 30, QUEST_CARD_Y - 4, 90, 16).build());

            // "Обновить" — только у спасения. Там прогресс держится на событиях смерти титанов
            // и кадетов, а они иногда не приходят (титан-скелет успел исчезнуть сам) — и квест
            // виснет с недобитым титаном. Кнопка просит сервер пересчитать всё по факту.
            if ("rescue_quest".equals(ClientQuestManager.currentQuestId)) {
                this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.refresh").withStyle(ChatFormatting.AQUA), button -> {
                    if (this.minecraft != null) {
                        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                new com.maxonaddon.network.QuestActionPayload(3, ClientQuestManager.currentQuestId, "", -1)
                        );
                        this.onClose();
                    }
                }).bounds(centerX + 30, QUEST_CARD_Y + 16, 90, 16).build());
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — Screen.render() (через super.render() ниже) сам вызывает
        // renderBackground() первым делом; явный вызов здесь удвоил бы затемнение
        // (та же причина, что уже чинили в других экранах в этой сессии).
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD9000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.journal.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, 20, 0xFFFFFF);

        if (!ClientQuestManager.hasQuest()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.journal.empty"), centerX, 60, 0x888888);
            return;
        }

        int startY = QUEST_CARD_Y;

        if (ClientQuestManager.currentQuestId.equals("delivery_quest")) {
            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.delivery.details_title").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), centerX - 120, startY, 0xFFFFFF);

            // currentTarget хранит просто ИМЯ стены (см. DeliveryQuestHandler) — переводим сами,
            // под язык именно этого клиента, а не то, что сервер решил бы за нас.
            com.maxonaddon.util.Wall targetWall = com.maxonaddon.util.Wall.fromNameSafe(ClientQuestManager.currentTarget);
            Component targetName = targetWall != null ? Component.translatable(targetWall.getTranslationKey()) : Component.literal("?");
            Component targetLine = Component.translatable("gui.maxonaddon.delivery.target", targetName).withStyle(ChatFormatting.GRAY);
            graphics.drawString(this.font, targetLine, centerX - 120, startY + 12, 0xFFFFFF);

            long remainingTicks = ClientQuestManager.deliveryDeadlineTick
                    - (this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L);
            Component timerText;
            if (remainingTicks <= 0) {
                timerText = Component.translatable("gui.maxonaddon.delivery.time_up").withStyle(ChatFormatting.DARK_RED);
            } else {
                long totalSeconds = remainingTicks / 20;
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;
                ChatFormatting color = totalSeconds <= 60 ? ChatFormatting.RED : ChatFormatting.GREEN;
                String timeStr = String.format("%d:%02d", minutes, seconds);
                timerText = Component.translatable("gui.maxonaddon.delivery.time_left", timeStr).withStyle(color);
            }
            graphics.drawString(this.font, timerText, centerX - 110, startY + 24, 0xFFFFFF);
            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.delivery.no_open_warning").withStyle(ChatFormatting.RED), centerX - 110, startY + 36, 0xFFFFFF);
            return;
        }

        if (ClientQuestManager.currentQuestId.equals("rescue_quest")) {
            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.rescue.details_title").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), centerX - 120, startY, 0xFFFFFF);

            if (!com.maxonaddon.client.RescueLocatorClient.arrived) {
                long remainingTicks = com.maxonaddon.client.RescueLocatorClient.deadlineTick
                        - (this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L);
                Component timerText;
                if (remainingTicks <= 0) {
                    timerText = Component.translatable("gui.maxonaddon.delivery.time_up").withStyle(ChatFormatting.DARK_RED);
                } else {
                    long totalSeconds = remainingTicks / 20;
                    long minutes = totalSeconds / 60;
                    long seconds = totalSeconds % 60;
                    ChatFormatting color = totalSeconds <= 60 ? ChatFormatting.RED : ChatFormatting.GREEN;
                    String timeStr = String.format("%d:%02d", minutes, seconds);
                    timerText = Component.translatable("gui.maxonaddon.rescue.time_left", timeStr).withStyle(color);
                }
                graphics.drawString(this.font, timerText, centerX - 110, startY + 12, 0xFFFFFF);
            } else {
                int titansKilled = Math.min(ClientQuestManager.questTitansKilled, com.maxonaddon.client.RescueLocatorClient.titansRequired);
                int titansRequired = com.maxonaddon.client.RescueLocatorClient.titansRequired;
                Component titanText = Component.translatable("gui.maxonaddon.rescue.condition_titans_progress", titansKilled, titansRequired);
                titanText = titanText.copy().withStyle(titansKilled >= titansRequired
                        ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withStrikethrough(true)
                        : net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.RED));
                graphics.drawString(this.font, titanText, centerX - 110, startY + 12, 0xFFFFFF);

                int cadetsAlive = com.maxonaddon.client.RescueLocatorClient.cadetsAlive;
                int cadetsTotal = com.maxonaddon.client.RescueLocatorClient.cadetsTotal;
                Component cadetText = Component.translatable("gui.maxonaddon.rescue.condition_cadets_progress", cadetsAlive, cadetsTotal)
                        .withStyle(cadetsAlive == cadetsTotal ? ChatFormatting.GREEN : (cadetsAlive == 0 ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW));
                graphics.drawString(this.font, cadetText, centerX - 110, startY + 24, 0xFFFFFF);

                if (titansKilled >= titansRequired) {
                    graphics.drawString(this.font, Component.translatable("gui.maxonaddon.rescue.ready_to_report").withStyle(ChatFormatting.GREEN), centerX - 110, startY + 40, 0xFFFFFF);
                }
            }
            return;
        }

        if (ClientQuestManager.currentQuestId.equals("outpost_hunt")) {
            // Имя стены переводим сами, под язык этого клиента, — как и в карточке доставки.
            com.maxonaddon.util.Wall outpostWall =
                    com.maxonaddon.util.Wall.fromNameSafe(ClientQuestManager.outpostWallName());
            Component outpostWallName = outpostWall != null
                    ? Component.translatable(outpostWall.getTranslationKey())
                    : Component.literal("?");
            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.outpost.title", outpostWallName)
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), centerX - 120, startY, 0xFFFFFF);

            int required = ClientQuestManager.outpostTitansRequired();
            int killed = Math.min(ClientQuestManager.questTitansKilled, required);
            boolean done = required > 0 && killed >= required;

            Component progress = Component.translatable("gui.maxonaddon.outpost.condition_titans", killed, required)
                    .copy().withStyle(done
                            ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withStrikethrough(true)
                            : net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.RED));
            graphics.drawString(this.font, progress, centerX - 110, startY + 12, 0xFFFFFF);

            graphics.drawString(this.font, Component.translatable(done
                            ? "gui.maxonaddon.outpost.ready_to_report"
                            : "gui.maxonaddon.outpost.step_hunt")
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY), centerX - 110, startY + 24, 0xFFFFFF);

            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.outpost.reward").withStyle(ChatFormatting.AQUA), centerX - 110, startY + 40, 0xFFFFFF);
            return;
        }

        if (ClientQuestManager.currentQuestId.equals("survivor_quest")) {
            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.survivor.title").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), centerX - 120, startY, 0xFFFFFF);

            boolean carried = com.maxonaddon.client.SurvivorLocatorClient.carried;
            Component step1 = Component.translatable("gui.maxonaddon.survivor.step_pickup")
                    .withStyle(carried
                            ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withStrikethrough(true)
                            : net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.RED));
            graphics.drawString(this.font, step1, centerX - 110, startY + 12, 0xFFFFFF);

            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.survivor.step_deliver")
                    .withStyle(carried ? ChatFormatting.GREEN : ChatFormatting.GRAY), centerX - 110, startY + 24, 0xFFFFFF);

            graphics.drawString(this.font, Component.translatable("gui.maxonaddon.survivor.reward").withStyle(ChatFormatting.AQUA), centerX - 110, startY + 40, 0xFFFFFF);
            return;
        }

        // --- ЛОГИКА СЧЕТЧИКОВ (тестовое задание) ---
        // Планка растёт с каждой сдачей, поэтому берём её из ClientQuestManager, а не 3/2.
        int titansRequired = ClientQuestManager.requiredTitans();
        int wheatRequired = ClientQuestManager.requiredWheat();
        int titans = Math.min(ClientQuestManager.questTitansKilled, titansRequired);

        // Считаем пшеницу прямо в инвентаре игрока
        int wheatCount = 0;
        if (this.minecraft != null && this.minecraft.player != null) {
            wheatCount = this.minecraft.player.getInventory().countItem(net.minecraft.world.item.Items.WHEAT);
        }

        // --- ФОРМАТИРОВАНИЕ ТЕКСТА ---
        Component titanText = Component.translatable("gui.maxonaddon.test_quest.progress_titans", titans, titansRequired)
                .withStyle(titans >= titansRequired
                        ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withStrikethrough(true)
                        : net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.RED));

        Component wheatText = Component.translatable("gui.maxonaddon.test_quest.progress_wheat", wheatCount, wheatRequired)
                .withStyle(wheatCount >= wheatRequired
                        ? net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withStrikethrough(true)
                        : net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GREEN));

        // --- ОТРИСОВКА ---
        graphics.drawString(this.font, Component.translatable("gui.maxonaddon.test_quest.title").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), centerX - 120, startY, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.maxonaddon.quest.turn_in_to", formatTurnInTarget()).withStyle(ChatFormatting.GRAY), centerX - 120, startY + 12, 0xFFFFFF);

        graphics.drawString(this.font, titanText, centerX - 110, startY + 24, 0xFFFFFF);
        graphics.drawString(this.font, wheatText, centerX - 110, startY + 36, 0xFFFFFF);
    }

    /**
     * currentTarget тестового задания хранится как язык-нейтральное "фракция|город"
     * (см. QuestDetailsScreen) — собираем читаемую фразу здесь, под язык ЭТОГО клиента.
     * Старые сохранения хранят уже готовую русскую строку без разделителя — её показываем
     * как есть, чтобы у тех, кто взял задание до обновления, журнал не сломался.
     */
    private Component formatTurnInTarget() {
        String raw = ClientQuestManager.currentTarget;
        int sep = raw.indexOf('|');
        if (sep < 0) return Component.literal(raw);

        String factionId = raw.substring(0, sep);
        String cityId = raw.substring(sep + 1);

        Component faction = Component.translatable("gui.maxonaddon.faction." + factionId);
        Component city = cityId.isEmpty()
                ? Component.translatable("gui.maxonaddon.quest.unknown_city")
                : Component.translatable("gui.maxonaddon.carriage.city." + cityId);
        return Component.translatable("gui.maxonaddon.quest.captain_in", faction, city);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
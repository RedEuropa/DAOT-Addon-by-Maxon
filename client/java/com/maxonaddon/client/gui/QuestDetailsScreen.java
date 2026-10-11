package com.maxonaddon.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

public class QuestDetailsScreen extends Screen {
    private final Screen parentScreen;
    private final int commanderId;

    public QuestDetailsScreen(Screen parentScreen, int commanderId) {
        super(Component.translatable("gui.maxonaddon.quest.details_title"));
        this.parentScreen = parentScreen;
        this.commanderId = commanderId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Кнопка "Принять"
        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.accept").withStyle(ChatFormatting.GREEN), button -> {
            if (this.minecraft != null && this.minecraft.player != null && this.minecraft.level != null) {
                net.minecraft.world.entity.Entity entity = this.minecraft.level.getEntity(commanderId);

                if (entity instanceof net.minecraft.world.entity.LivingEntity commander) {
                    // 1. Определяем фракцию по нагруднику
                    String factionId = "scout";
                    net.minecraft.world.item.ItemStack coat = commander.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                    String coatId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(coat.getItem()).getPath();

                    if (coatId.equals("trench_coat_military_police")) {
                        factionId = "police";
                    } else if (coatId.equals("trench_coat_garrison")) {
                        factionId = "garrison";
                    }

                    // 2. Определяем город
                    BlockPos npcPos = commander.blockPosition();
                    String cityId = com.maxonaddon.util.CarriagePricing.nearestDistrictId(npcPos);

                    // Собираем ЯЗЫК-НЕЙТРАЛЬНЫЙ идентификатор "фракция|город", а не готовую
                    // фразу: сервер хранит его как есть, а читаемую строку каждый клиент
                    // соберёт сам под свой язык (см. QuestJournalScreen) — тот же приём, что
                    // уже используется для стен в квесте доставки.
                    String turnInTarget = factionId + "|" + (cityId == null ? "" : cityId);

                    // 3. Отправляем серверу — сервер (IPlayerStats) единственный источник
                    // истины, клиент узнает, что квест реально принят, из ответного
                    // SyncQuestPayload (см. QuestActionPayload-обработчик в MaxonDaotAddon).
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new com.maxonaddon.network.QuestActionPayload(0, "test_quest", turnInTarget, commanderId)
                    );

                    this.minecraft.player.sendSystemMessage(Component.translatable("message.maxonaddon.quest.accepted_check_journal").withStyle(ChatFormatting.GREEN));
                    this.onClose(); // Закрываем все окна
                }
            }
        }).bounds(centerX - 105, centerY + 80, 100, 20).build());

        // Кнопка "Отказаться"
        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.quest.decline").withStyle(ChatFormatting.RED), button -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        }).bounds(centerX + 5, centerY + 80, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто нарочно — стандартный Screen.render() (вызывается через super.render() ниже)
        // сам вызывает renderBackground() первым делом; если оставить здесь явную закраску,
        // она наложится дважды за кадр (та же причина, что уже чинили в других экранах
        // в этой сессии — CarriageScreen, PerksScreen и т.д.). Реальное затемнение — ниже,
        // прямым вызовом graphics.fill() в render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB3000000);
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, centerY - 90, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.line1"), centerX, centerY - 60, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.line2"), centerX, centerY - 45, 0xFFFFFF);

        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.goals").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE), centerX, centerY - 15, 0xFFFFFF);

        // Планка растёт с каждой сдачей — показываем текущую, а не зашитые 3/2.
        int titans = ClientQuestManager.requiredTitans();
        int wheat = ClientQuestManager.requiredWheat();
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.goal_titans", titans).withStyle(ChatFormatting.RED), centerX, centerY + 10, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("gui.maxonaddon.test_quest.goal_wheat", wheat).withStyle(ChatFormatting.GREEN), centerX, centerY + 25, 0xFFFFFF);

        if (ClientQuestManager.testQuestCompletions > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.maxonaddon.test_quest.completions", ClientQuestManager.testQuestCompletions).withStyle(ChatFormatting.GRAY),
                    centerX, centerY + 45, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
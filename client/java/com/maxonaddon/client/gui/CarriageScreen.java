package com.maxonaddon.client.gui;

import com.maxonaddon.util.CarriagePricing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public class CarriageScreen extends Screen {
    private final List<String> unlockedCities;
    private final boolean forestUnlocked;
    private final String currentCityId;

    public CarriageScreen(List<String> unlockedCities, boolean forestUnlocked, String currentCityId) {
        super(Component.translatable("gui.maxonaddon.carriage.title"));
        this.unlockedCities = unlockedCities;
        this.forestUnlocked = forestUnlocked;
        this.currentCityId = currentCityId;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 100;

        com.maxonaddon.util.IPlayerStats stats = (com.maxonaddon.util.IPlayerStats) net.minecraft.client.Minecraft.getInstance().player;
        boolean isTraveler = stats != null && stats.hasUnlockedPerk("traveler");

        // Создаем 12 кнопок городов (3 столбца по 4 ряда)
        for (int i = 0; i < 12; i++) {
            String cityId = "district_" + (i + 1);
            boolean isUnlocked = unlockedCities.contains(cityId);
            boolean isCurrent = cityId.equals(this.currentCityId);

            int col = i % 3;
            int row = i / 3;

            int x = centerX - 120 + (col * 80);
            int y = startY + (row * 30);

            Component cityName = Component.translatable("gui.maxonaddon.carriage.city.district_" + (i + 1));
            Component label = cityName;

            // У текущего города (где стоит игрок) цену не показываем — ехать туда нет смысла.
            if (isUnlocked && !isCurrent) {
                int price = CarriagePricing.priceTo(this.currentCityId, cityId);
                if (isTraveler) {
                    // Если есть перк, показываем, что это бесплатно
                    label = cityName.copy().append(Component.literal(" (0)"));
                } else if (price > 0) {
                    label = cityName.copy().append(Component.literal(" (" + price + ")"));
                }
            }

            Button cityButton = Button.builder(label, (button) -> {
                        int price = isTraveler ? 0 : CarriagePricing.priceTo(this.currentCityId, cityId);
                        net.minecraft.client.Minecraft.getInstance().setScreen(
                                new CarriageConfirmScreen(this, cityId, cityName, Math.max(price, 0))
                        );
                    })
                    .bounds(x, y, 70, 20)
                    .build();

            cityButton.active = isUnlocked && !isCurrent;

            this.addRenderableWidget(cityButton);
        }

        // 13-я кнопка (Лес)
        Component forestName = Component.translatable("gui.maxonaddon.carriage.giant_forest");
        Component forestLabel = forestName;
        if (forestUnlocked) {
            int forestPrice = CarriagePricing.priceTo(this.currentCityId, "giant_forest");
            if (forestPrice > 0) {
                forestLabel = forestName.copy().append(Component.literal(" (" + forestPrice + ")"));
            }
        }

        Button forestButton = Button.builder(forestLabel, (button) -> {
                    int forestPrice = CarriagePricing.priceTo(this.currentCityId, "giant_forest");
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                            new CarriageConfirmScreen(this, "giant_forest", forestName, Math.max(forestPrice, 0))
                    );
                })
                .bounds(centerX - 35, startY + 130, 70, 20)
                .build();

        forestButton.active = forestUnlocked;
        this.addRenderableWidget(forestButton);

        // Изумруд в правом верхнем углу — вход в лавку лошадей. Запрашиваем предложение
        // сразу при открытии экрана, чтобы к моменту клика seed уже пришёл.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.maxonaddon.network.HorseActionPayload(com.maxonaddon.network.HorseActionPayload.OPEN, 0));

        this.addRenderableWidget(new ItemButtonWidget(
                this.width - 30, 10,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD),
                Component.translatable("gui.maxonaddon.horse.open"),
                b -> net.minecraft.client.Minecraft.getInstance().setScreen(new HorseShopScreen(this))));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // super.render() (стандартная реализация Screen) уже сама вызывает renderBackground()
        // самым первым делом — повторный вызов здесь запускал размытие фона ДВАЖДЫ за кадр,
        // отсюда и жалоба "очень сильно размылилось". Просто рисуем текст и делегируем остальное.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 120, 0xFFFFFF);
    }
}

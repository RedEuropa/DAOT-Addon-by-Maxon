package com.maxonaddon.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Подтверждение поездки у проводника — показывает город назначения и цену перед списанием. */
public class CarriageConfirmScreen extends Screen {
    private final Screen parent;
    private final String destinationId;
    private final Component destinationName;
    private final int price;

    public CarriageConfirmScreen(Screen parent, String destinationId, Component destinationName, int price) {
        super(Component.translatable("gui.maxonaddon.carriage.confirm.title"));
        this.parent = parent;
        this.destinationId = destinationId;
        this.destinationName = destinationName;
        this.price = price;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.carriage.confirm.yes"), b -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.maxonaddon.network.CarriageTeleportPayload(this.destinationId)
            );
            Minecraft.getInstance().setScreen(null);
        }).bounds(centerX - 105, centerY + 20, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.maxonaddon.carriage.confirm.no"), b -> {
            Minecraft.getInstance().setScreen(this.parent);
        }).bounds(centerX + 5, centerY + 20, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // НАСТОЯЩАЯ причина "очень сильно размылилось": стандартный Screen.render()
        // (вызывается через super.render() ниже) САМ уже вызывает renderBackground() первым
        // делом — проверено байткодом. Прошлый явный вызов renderBackground() здесь запускал
        // размытие фона ВТОРОЙ раз за тот же кадр поверх первого — отсюда двойной эффект.
        // Просто отдаём рисование фона стандартной реализации.
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);

        Component text = Component.translatable("gui.maxonaddon.carriage.confirm.text", this.destinationName, this.price);
        graphics.drawCenteredString(this.font, text, this.width / 2, this.height / 2 - 10, 0xFFFF55);
    }
}

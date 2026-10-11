package com.maxonaddon.client;

import com.maxonaddon.network.JawQteInputPayload;
import com.maxonaddon.network.JawQteStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class JawQteClient {
    public static boolean active = false;
    public static int progress = 0;
    public static int requiredKey = 0; // 0 = ЛКМ, 1 = ПКМ
    private static boolean wasAttackDown = false;
    private static boolean wasUseDown = false;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(JawQteStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                active = payload.active();
                progress = payload.progress();
                requiredKey = payload.requiredKey();
            });
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (!active) return;

            Minecraft client = Minecraft.getInstance();
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            int barWidth = 100;
            int barHeight = 10;
            int x = (screenWidth - barWidth) / 2;
            int y = (screenHeight / 2) + 30;

            guiGraphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);

            int filledWidth = (int) ((progress / 20.0F) * barWidth);
            guiGraphics.fill(x, y, x + filledWidth, y + barHeight, 0xFFA5260A);

            Component keyComponent = (requiredKey == 0)
                    ? Component.translatable("gui.maxonaddon.qte.lmb")
                    : Component.translatable("gui.maxonaddon.qte.rmb");

            int textWidth = client.font.width(keyComponent);
            guiGraphics.drawString(client.font, keyComponent, x + (barWidth - textWidth) / 2, y - 12, 0xFFFFFFFF);
        });
    }

    public static void tickInput(Minecraft client) {
        if (!active || client.player == null) return;

        boolean isAttackDown = client.options.keyAttack.isDown();
        if (isAttackDown && !wasAttackDown) {
            if (requiredKey == 0) {
                ClientPlayNetworking.send(new com.maxonaddon.network.JawQteInputPayload(0));
            }
            client.options.keyAttack.setDown(false);
            while(client.options.keyAttack.consumeClick()) {} // Съедаем клик
        }
        wasAttackDown = isAttackDown;

        boolean isUseDown = client.options.keyUse.isDown();
        if (isUseDown && !wasUseDown) {
            if (requiredKey == 1) {
                ClientPlayNetworking.send(new com.maxonaddon.network.JawQteInputPayload(1));
            }
            client.options.keyUse.setDown(false);
            while(client.options.keyUse.consumeClick()) {} // Съедаем клик
        }
        wasUseDown = isUseDown;
    }
}
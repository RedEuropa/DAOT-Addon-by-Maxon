package com.maxonaddon.client.mixin;

import com.maxonaddon.client.FearManager;
import com.maxonaddon.client.HeadTraumaClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderMaxonOverlays(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // 1. Отрисовка страха (красная виньетка)
        if (FearManager.currentFearLevel > 0.05) {
            // Увеличиваем коэффициент, чтобы потемнение было более глубоким и заметным
            int alpha = (int) (FearManager.currentFearLevel * 200);
            int color = (alpha << 24) | 0x000000;

            RenderSystem.enableBlend();
            guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color);
            RenderSystem.disableBlend();
        }

        // 1.5. Тёмно-красная виньетка "сердцебиения" во время берсерка (потери контроля) — НЕ
        // плоская заливка на весь экран (это выглядело как "прямая пульсация" — всё одинаково
        // темнеет/светлеет), а настоящая виньетка: тёмные края по бокам/сверху/снизу, прозрачно
        // к центру. Два слоя друг на друге, а не один резкий:
        //  - широкий и мягкий (getSoftPulse — растянутый, квадрат синуса) снизу, задаёт плавное
        //    "дыхание" фона и смягчает восприятие;
        //  - узкий и резкий (getPulse — короткий "лаб-даб") сверху, даёт ощущение самого удара.
        // Раньше был только резкий слой — граница дёргалась туда-сюда быстро и глазам было
        // неприятно; мягкий слой снизу растягивает переход, резкий сверху уже не так режет глаз.
        if (com.maxonaddon.client.BerserkClientState.controlLossActive) {
            int screenW = guiGraphics.guiWidth();
            int screenH = guiGraphics.guiHeight();

            RenderSystem.enableBlend();

            float softPulse = com.maxonaddon.client.BerserkVignette.getSoftPulse();
            renderVignetteLayer(guiGraphics, screenW, screenH, softPulse, 0.14F, 0.22F, 0.11F, 0.20F, 130, 16);

            float sharpPulse = com.maxonaddon.client.BerserkVignette.getPulse();
            renderVignetteLayer(guiGraphics, screenW, screenH, sharpPulse, 0.10F, 0.16F, 0.08F, 0.14F, 190, 20);

            RenderSystem.disableBlend();
        }

        // 2. Отрисовка контузии (серая пелена / потемнение в глазах)
        if (HeadTraumaClient.currentIntensity > 0.01f) {
            // Альфа от 0 до 160 (из 255) для создания густой пелены
            int alpha = (int) (HeadTraumaClient.currentIntensity * 160);
            // Темно-серый цвет (имитация потери фокуса и потемнения)
            int color = (alpha << 24) | 0x1A1A1A;

            RenderSystem.enableBlend();
            guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color);
            RenderSystem.disableBlend();
        }

        // 3. Патроны марлийской винтовки — ряд из 8 "патрончиков" прямо над полоской опыта
        // (та рисуется на y = guiHeight - 32, шириной 182 — см. Gui.renderExperienceBar).
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
            net.minecraft.world.item.ItemStack offHand = player.getOffhandItem();
            net.minecraft.world.item.ItemStack rifle = mainHand.getItem() instanceof com.maxonaddon.item.MarleyanRifleItem ? mainHand
                    : offHand.getItem() instanceof com.maxonaddon.item.MarleyanRifleItem ? offHand
                    : net.minecraft.world.item.ItemStack.EMPTY;

            if (!rifle.isEmpty()) {
                int loaded = com.maxonaddon.item.MarleyanRifleItem.getLoadedRounds(rifle);
                int max = com.maxonaddon.item.MarleyanRifleItem.MAX_LOADED_ROUNDS;

                int pipWidth = 14;
                int pipHeight = 5;
                int gap = 2;
                int totalWidth = max * pipWidth + (max - 1) * gap;
                int startX = guiGraphics.guiWidth() / 2 - totalWidth / 2;
                int y = guiGraphics.guiHeight() - 44;

                RenderSystem.enableBlend();
                for (int i = 0; i < max; i++) {
                    int x = startX + i * (pipWidth + gap);
                    boolean filled = i < loaded;
                    guiGraphics.fill(x, y, x + pipWidth, y + pipHeight, filled ? 0xFFFFD24A : 0x66404040);
                    guiGraphics.renderOutline(x, y, pipWidth, pipHeight, 0xFF000000);
                }
                RenderSystem.disableBlend();
            }
        }

        // 4. Текст "внутреннего голоса" во время сильного страха рядом с титаном
        // (см. FearTextManager — считается и выбирает случайные параметры на клиенте).
        String fearText = com.maxonaddon.client.FearTextManager.getCurrentText();
        if (fearText != null) {
            float alpha = com.maxonaddon.client.FearTextManager.getAlphaProgress();
            float scale = com.maxonaddon.client.FearTextManager.getSizeMultiplier() * com.maxonaddon.client.FearTextManager.getScaleProgress();

            int x = (int) (guiGraphics.guiWidth() * com.maxonaddon.client.FearTextManager.getPosXFrac());
            int y = (int) (guiGraphics.guiHeight() * com.maxonaddon.client.FearTextManager.getPosYFrac());

            int colorAlpha = Math.max(0, Math.min(255, (int) (alpha * 255)));
            int color = (colorAlpha << 24) | 0xFFFFFF;

            RenderSystem.enableBlend();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(com.maxonaddon.client.FearTextManager.getAngleDeg()));
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, fearText, 0, 0, color);
            guiGraphics.pose().popPose();
            RenderSystem.disableBlend();
        }

        // 5. Текст "внутреннего голоса" титана во время потери контроля (берсерк) — см.
        // BerserkTextManager. Устроено один в один как блок 4 выше, только цвет зависит от
        // того, спокойная фраза попалась (белая) или агрессивная (красная).
        String berserkText = com.maxonaddon.client.BerserkTextManager.getCurrentText();
        if (berserkText != null) {
            float alpha = com.maxonaddon.client.BerserkTextManager.getAlphaProgress();
            float scale = com.maxonaddon.client.BerserkTextManager.getSizeMultiplier() * com.maxonaddon.client.BerserkTextManager.getScaleProgress();

            int x = (int) (guiGraphics.guiWidth() * com.maxonaddon.client.BerserkTextManager.getPosXFrac());
            int y = (int) (guiGraphics.guiHeight() * com.maxonaddon.client.BerserkTextManager.getPosYFrac());

            int colorAlpha = Math.max(0, Math.min(255, (int) (alpha * 255)));
            int rgb = com.maxonaddon.client.BerserkTextManager.isCurrentRed() ? 0xFF5555 : 0xFFFFFF;
            int color = (colorAlpha << 24) | rgb;

            RenderSystem.enableBlend();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0);
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(com.maxonaddon.client.BerserkTextManager.getAngleDeg()));
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, berserkText, 0, 0, color);
            guiGraphics.pose().popPose();
            RenderSystem.disableBlend();
        }

        // 6. Локатор квеста "Доставить припасы" — своя мини-версия ванильного Player Locator
        // Bar (в 1.21.1 самого Locator Bar нет — проверено по джарам), над полоской опыта.
        // Полоска-трек + маркер, скользящий по ней в зависимости от угла между взглядом
        // игрока и направлением на получателя; при точном наведении маркер зеленеет.
        if (com.maxonaddon.client.DeliveryLocatorClient.active) {
            renderDeliveryLocator(guiGraphics);
        }
        // Тот же трек для "Спасение застрявших" — сервер сам прячет его (active=false), как
        // только игрок доберётся до точки (см. RescueQuestHandler.checkArrivals), так что
        // отдельно проверять arrived тут не нужно.
        if (com.maxonaddon.client.RescueLocatorClient.active) {
            renderRescueLocator(guiGraphics);
        }
        // Маркер на раненого выжившего. Пока он на спине, стрелка не нужна — вместо неё
        // короткая подсказка, куда его нести.
        if (com.maxonaddon.client.SurvivorLocatorClient.active) {
            renderSurvivorLocator(guiGraphics);
        }
    }

    private static void renderDeliveryLocator(GuiGraphics guiGraphics) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        String currentDim = mc.level.dimension().location().toString();
        if (!currentDim.equals(com.maxonaddon.client.DeliveryLocatorClient.dimension)) return;

        double dx = com.maxonaddon.client.DeliveryLocatorClient.x - player.getX();
        double dz = com.maxonaddon.client.DeliveryLocatorClient.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double bearing = net.minecraft.util.Mth.wrapDegrees(angleToTarget - player.getYRot());

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() - 40;
        int trackHalfWidth = 50;

        RenderSystem.enableBlend();
        guiGraphics.fill(centerX - trackHalfWidth, y, centerX + trackHalfWidth, y + 1, 0x88FFFFFF);
        guiGraphics.fill(centerX - 1, y - 2, centerX + 1, y + 3, 0x88FFFFFF); // центральная риска "прямо по курсу"

        double maxBearing = 60.0;
        double clamped = Math.max(-maxBearing, Math.min(maxBearing, bearing));
        int markerX = centerX + (int) (clamped / maxBearing * trackHalfWidth);

        boolean aligned = Math.abs(bearing) < 5.0;
        int color = aligned ? 0xFF55FF55 : 0xFFFFD24A;

        guiGraphics.drawCenteredString(mc.font, "▲", markerX, y - 9, color);

        String distText = (int) distance + "m";
        guiGraphics.drawCenteredString(mc.font, distText, centerX, y + 4, 0xAAFFFFFF);
        RenderSystem.disableBlend();
    }

    /** То же самое, что renderDeliveryLocator, но на состояние RescueLocatorClient — см. комментарий там. */
    private static void renderRescueLocator(GuiGraphics guiGraphics) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        String currentDim = mc.level.dimension().location().toString();
        if (!currentDim.equals(com.maxonaddon.client.RescueLocatorClient.dimension)) return;

        double dx = com.maxonaddon.client.RescueLocatorClient.x - player.getX();
        double dz = com.maxonaddon.client.RescueLocatorClient.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double bearing = net.minecraft.util.Mth.wrapDegrees(angleToTarget - player.getYRot());

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() - 40;
        int trackHalfWidth = 50;

        RenderSystem.enableBlend();
        guiGraphics.fill(centerX - trackHalfWidth, y, centerX + trackHalfWidth, y + 1, 0x88FFFFFF);
        guiGraphics.fill(centerX - 1, y - 2, centerX + 1, y + 3, 0x88FFFFFF);

        double maxBearing = 60.0;
        double clamped = Math.max(-maxBearing, Math.min(maxBearing, bearing));
        int markerX = centerX + (int) (clamped / maxBearing * trackHalfWidth);

        boolean aligned = Math.abs(bearing) < 5.0;
        // titanMode=true — идём убивать (см. RescueLocatorClient) — трек красный, чтобы визуально
        // отличался от жёлто-зелёного "иди сюда" на здание: aligned тут тоже не зелёный, а
        // ярко-красный, чтобы не путать с "цель достигнута" у обычного локатора.
        boolean titanMode = com.maxonaddon.client.RescueLocatorClient.titanMode;
        int color = titanMode
                ? (aligned ? 0xFFFF3333 : 0xFFCC1111)
                : (aligned ? 0xFF55FF55 : 0xFFFFD24A);

        guiGraphics.drawCenteredString(mc.font, "▲", markerX, y - 9, color);

        String distText = (int) distance + "m";
        guiGraphics.drawCenteredString(mc.font, distText, centerX, y + 4, 0xAAFFFFFF);
        RenderSystem.disableBlend();
    }

    /** Маркер на раненого выжившего — тот же трек; когда он уже на спине, стрелка заменяется подсказкой. */
    private static void renderSurvivorLocator(GuiGraphics guiGraphics) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() - 40;

        RenderSystem.enableBlend();

        if (com.maxonaddon.client.SurvivorLocatorClient.carried) {
            guiGraphics.drawCenteredString(mc.font,
                    net.minecraft.network.chat.Component.translatable("gui.maxonaddon.survivor.carrying"),
                    centerX, y - 9, 0xFF9BE8A0);
            RenderSystem.disableBlend();
            return;
        }

        String currentDim = mc.level.dimension().location().toString();
        if (!currentDim.equals(com.maxonaddon.client.SurvivorLocatorClient.dimension)) {
            RenderSystem.disableBlend();
            return;
        }

        double dx = com.maxonaddon.client.SurvivorLocatorClient.x - player.getX();
        double dz = com.maxonaddon.client.SurvivorLocatorClient.z - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double bearing = net.minecraft.util.Mth.wrapDegrees(angleToTarget - player.getYRot());

        int trackHalfWidth = 50;
        guiGraphics.fill(centerX - trackHalfWidth, y, centerX + trackHalfWidth, y + 1, 0x88FFFFFF);
        guiGraphics.fill(centerX - 1, y - 2, centerX + 1, y + 3, 0x88FFFFFF);

        double maxBearing = 60.0;
        double clamped = Math.max(-maxBearing, Math.min(maxBearing, bearing));
        int markerX = centerX + (int) (clamped / maxBearing * trackHalfWidth);

        boolean aligned = Math.abs(bearing) < 5.0;
        int color = aligned ? 0xFF55FF55 : 0xFFB48CFF; // сиреневый — под цвет его сигнальной ракеты

        guiGraphics.drawCenteredString(mc.font, "▲", markerX, y - 9, color);

        String distText = (int) distance + "m";
        guiGraphics.drawCenteredString(mc.font, distText, centerX, y + 4, 0xAAFFFFFF);
        RenderSystem.disableBlend();
    }

    /**
     * Один слой виньетки берсерка — тёмная кромка по всем четырём краям экрана, глубина
     * (reach) которой растёт от pulse (0..1). Вызывается дважды в блоке 1.5: с
     * BerserkVignette.getSoftPulse() (широкий, мягкий фон) и с getPulse() (узкий, резкий
     * акцент удара) — второй поверх первого.
     */
    private static void renderVignetteLayer(GuiGraphics guiGraphics, int screenW, int screenH, float pulse,
                                             float baseFracX, float pulseFracX, float baseFracY, float pulseFracY,
                                             int maxAlpha, int bands) {
        int reachX = (int) (screenW * (baseFracX + pulse * pulseFracX));
        int reachY = (int) (screenH * (baseFracY + pulse * pulseFracY));
        int rgb = 0x330000;

        // Верх/низ — честный плавный градиент, тут fillGradient умеет сам (интерполирует по Y
        // от первого цвета ко второму).
        int edgeColor = (maxAlpha << 24) | rgb;
        int clearColor = rgb; // альфа 0 — прозрачный
        guiGraphics.fillGradient(0, 0, screenW, reachY, edgeColor, clearColor);
        guiGraphics.fillGradient(0, screenH - reachY, screenW, screenH, clearColor, edgeColor);

        // Левый/правый край — fillGradient умеет только сверху вниз, поэтому кромку по X
        // собираем узкими полосами с квадратично убывающей альфой (чем ближе к центру, тем
        // прозрачнее) — на глаз это уже гладкий градиент.
        for (int i = 0; i < bands; i++) {
            float t0 = (float) i / bands;
            float t1 = (float) (i + 1) / bands;
            int x0 = (int) (reachX * t0);
            int x1 = (int) (reachX * t1) + 1; // +1 нахлёст, чтобы полосы не оставляли щелей

            float falloff = 1.0F - t0;
            int alpha = (int) (maxAlpha * falloff * falloff);
            int bandColor = (alpha << 24) | rgb;

            guiGraphics.fill(x0, 0, x1, screenH, bandColor);
            guiGraphics.fill(screenW - x1, 0, screenW - x0, screenH, bandColor);
        }
    }
}
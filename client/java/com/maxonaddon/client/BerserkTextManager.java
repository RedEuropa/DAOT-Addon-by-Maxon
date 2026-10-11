package com.maxonaddon.client;

import net.minecraft.client.Minecraft;

/**
 * Короткие вспышки "внутреннего голоса" титана на экране во время потери контроля (берсерк) —
 * целиком считается и рисуется на клиенте (см. tick() ниже и рендер в GuiMixin), у каждого
 * игрока свой независимый случайный набор. Устроено по образцу FearTextManager, включая уже
 * исправленный там баг: раз начатая фраза ВСЕГДА доигрывает свой цикл появления/удержания/
 * исчезновения до конца, а active только решает, можно ли запустить новую — иначе колебания
 * состояния около границы обрывали бы и "оживляли" текст рывками.
 *
 * Два набора фраз: спокойные/завлекающие (белые) и агрессивные/зовущие сопротивляться (красные) —
 * цвет выбирается вместе с фразой при спавне и не меняется до её исчезновения.
 */
public class BerserkTextManager {

    private static final String[] PHRASES_WHITE_RU = {
            "Как хорошо",
            "Тут так тепло",
            "Какой прекрасный сон"
    };
    private static final String[] PHRASES_WHITE_EN = {
            "How nice...",
            "It's so warm here",
            "What a beautiful dream"
    };

    private static final String[] PHRASES_RED_RU = {
            "Я их всех уничтожу!",
            "Не уходите!",
            "Борись"
    };
    private static final String[] PHRASES_RED_EN = {
            "I'll destroy them all!",
            "Don't leave!",
            "Fight it"
    };

    private static final long INTERVAL_MS = 3000L;
    private static final long VISIBLE_MS = 1500L;
    private static final float FADE_IN_FRACTION = 0.25F;
    private static final float FADE_OUT_FRACTION = 0.35F;

    private static long lastSpawnTime = -INTERVAL_MS;
    private static String currentText = null;
    private static boolean currentIsRed = false;
    private static long showStartTime = 0L;

    private static float posXFrac;
    private static float posYFrac;
    private static float angleDeg;
    private static float sizeMultiplier;

    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

    private BerserkTextManager() {}

    /** Вызывать каждый клиентский тик. active — "игрок сейчас теряет контроль над берсерком". */
    public static void tick(boolean active) {
        long now = System.currentTimeMillis();

        // Уже показываем фразу — доигрываем цикл до конца, не обрываем резко (см. комментарий
        // класса — этот же баг уже ловился и чинился в FearTextManager).
        if (currentText != null) {
            if (now - showStartTime >= VISIBLE_MS) {
                currentText = null;
            }
            return;
        }

        if (active && now - lastSpawnTime >= INTERVAL_MS) {
            spawn(now);
        }
    }

    private static void spawn(long now) {
        lastSpawnTime = now;
        showStartTime = now;

        boolean russian = "ru_ru".equals(Minecraft.getInstance().getLanguageManager().getSelected());
        currentIsRed = RANDOM.nextBoolean();

        String[] pool;
        if (currentIsRed) {
            pool = russian ? PHRASES_RED_RU : PHRASES_RED_EN;
        } else {
            pool = russian ? PHRASES_WHITE_RU : PHRASES_WHITE_EN;
        }
        currentText = pool[RANDOM.nextInt(pool.length)];

        posXFrac = 0.25F + RANDOM.nextFloat() * 0.5F;
        posYFrac = 0.25F + RANDOM.nextFloat() * 0.45F;
        angleDeg = -22.0F + RANDOM.nextFloat() * 44.0F;
        sizeMultiplier = 1.1F + RANDOM.nextFloat() * 1.2F;
    }

    public static String getCurrentText() {
        return currentText;
    }

    public static boolean isCurrentRed() {
        return currentIsRed;
    }

    public static float getPosXFrac() { return posXFrac; }
    public static float getPosYFrac() { return posYFrac; }
    public static float getAngleDeg() { return angleDeg; }
    public static float getSizeMultiplier() { return sizeMultiplier; }

    public static float getScaleProgress() {
        return computeEnvelope(true);
    }

    public static float getAlphaProgress() {
        return computeEnvelope(false);
    }

    /**
     * ВАЖНО: масштаб на исчезновении намеренно НЕ уменьшается (в отличие от FearTextManager) —
     * одновременное изменение и размера, и прозрачности повёрнутого текста на пиксельном
     * шрифте Minecraft (без сглаживания при повороте) даёт заметное "дрожание"/мерцание кадр
     * от кадра, которое и было замечено как "мигание при пропадании". Размер меняется только
     * на появлении (zoom-in), а на исчезновении меняется исключительно альфа — geometry
     * перестаёт "гулять" по кадрам, и фейд выглядит гладким.
     */
    private static float computeEnvelope(boolean isScale) {
        if (currentText == null) return 0.0F;
        long elapsed = System.currentTimeMillis() - showStartTime;
        float progress = Math.min(1.0F, elapsed / (float) VISIBLE_MS);

        if (progress < FADE_IN_FRACTION) {
            float t = progress / FADE_IN_FRACTION;
            return isScale ? lerp(0.4F, 1.0F, t) : t;
        } else if (progress > 1.0F - FADE_OUT_FRACTION) {
            if (isScale) return 1.0F; // размер зафиксирован — плывёт только альфа
            float t = (progress - (1.0F - FADE_OUT_FRACTION)) / FADE_OUT_FRACTION;
            return 1.0F - t;
        } else {
            return 1.0F;
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}

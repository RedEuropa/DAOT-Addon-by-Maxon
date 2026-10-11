package com.maxonaddon.client;

import net.minecraft.client.Minecraft;

/**
 * Короткие вспышки текста "внутреннего голоса" на экране во время сильного страха рядом
 * с титаном — не сетевой пакет, целиком считается и рисуется на клиенте (см. tick() ниже
 * и рендер в GuiMixin), поэтому у каждого игрока свой независимый случайный набор.
 */
public class FearTextManager {

    private static final String[] PHRASES_RU = {
            "Я не хочу умирать",
            "Хочу домой",
            "Спасите",
            "Почему?",
            "Мамочка... Папочка...",
            "Кто нибудь...",
            "Я не могу...",
            "Не здесь",
            "Пощадите!"
    };

    private static final String[] PHRASES_EN = {
            "I don't want to die",
            "I want to go home",
            "Help!",
            "Why?",
            "Mommy... Daddy...",
            "Someone...",
            "I can't...",
            "Not here",
            "Have mercy!"
    };

    /** Раз в сколько мс МОЖЕТ появиться новая фраза (не "раз в 3с гарантированно", а не чаще). */
    private static final long INTERVAL_MS = 3000L;
    /** Сколько мс видна одна фраза (появление+удержание+исчезновение суммарно). */
    private static final long VISIBLE_MS = 1500L;
    /** Первые 25% времени показа — появление (зум + непрозрачность растут). */
    private static final float FADE_IN_FRACTION = 0.25F;
    /** Последние 35% времени показа — исчезновение (уменьшение + прозрачность). */
    private static final float FADE_OUT_FRACTION = 0.35F;

    private static long lastSpawnTime = -INTERVAL_MS;
    private static String currentText = null;
    private static long showStartTime = 0L;

    // Случайные параметры конкретно ЭТОЙ фразы — подбираются один раз при появлении и не
    // меняются, пока эта фраза не исчезнет (иначе бы "дёргалась" каждый кадр).
    private static float posXFrac;
    private static float posYFrac;
    private static float angleDeg;
    private static float sizeMultiplier;

    private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

    private FearTextManager() {}

    /** Вызывать каждый клиентский тик. active — "близко к титану и страх выше среднего". */
    public static void tick(boolean active) {
        long now = System.currentTimeMillis();

        // Уже показываем фразу — всегда доигрываем её цикл (появление/удержание/исчезновение)
        // до конца, не обрывая резко. currentFearLevel сглаживается плавно (лерп 10% за тик) и
        // около порога 0.5 может колебаться туда-сюда, пока игрок ходит на границе радиуса
        // титана — если тут же обнулять currentText при active=false, а потом (когда пройдут
        // 3с с прошлого спавна) запускать новую фразу заново при active=true, текст будет
        // "оживать" через fade-in (снова становясь непрозрачным) и тут же резко обрываться —
        // именно так выглядел баг с "перестаёт быть прозрачным перед исчезновением".
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
        String[] pool = russian ? PHRASES_RU : PHRASES_EN;
        currentText = pool[RANDOM.nextInt(pool.length)];

        // Не совсем по краям экрана, чтобы текст точно помещался целиком.
        posXFrac = 0.25F + RANDOM.nextFloat() * 0.5F;
        posYFrac = 0.25F + RANDOM.nextFloat() * 0.45F;
        angleDeg = -22.0F + RANDOM.nextFloat() * 44.0F;
        sizeMultiplier = 1.1F + RANDOM.nextFloat() * 1.2F; // 1.1x .. 2.3x обычного текста
    }

    /** null, если сейчас показывать нечего. */
    public static String getCurrentText() {
        return currentText;
    }

    public static float getPosXFrac() { return posXFrac; }
    public static float getPosYFrac() { return posYFrac; }
    public static float getAngleDeg() { return angleDeg; }
    public static float getSizeMultiplier() { return sizeMultiplier; }

    /** 0..1: масштаб текущей анимации (зум при появлении/исчезновении). */
    public static float getScaleProgress() {
        return computeEnvelope(true);
    }

    /** 0..1: непрозрачность текущей анимации. */
    public static float getAlphaProgress() {
        return computeEnvelope(false);
    }

    private static float computeEnvelope(boolean isScale) {
        if (currentText == null) return 0.0F;
        long elapsed = System.currentTimeMillis() - showStartTime;
        float progress = Math.min(1.0F, elapsed / (float) VISIBLE_MS);

        if (progress < FADE_IN_FRACTION) {
            float t = progress / FADE_IN_FRACTION;
            return isScale ? lerp(0.4F, 1.0F, t) : t;
        } else if (progress > 1.0F - FADE_OUT_FRACTION) {
            float t = (progress - (1.0F - FADE_OUT_FRACTION)) / FADE_OUT_FRACTION;
            return isScale ? lerp(1.0F, 0.5F, t) : (1.0F - t);
        } else {
            return 1.0F;
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}

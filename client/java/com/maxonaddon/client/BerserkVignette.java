package com.maxonaddon.client;

/**
 * Ритм "сердцебиения" для тёмно-красной виньетки во время потери контроля (берсерк) — см.
 * рендер в GuiMixin. Считает не уровень (как страх), а именно фазу пульса: два коротких удара
 * за цикл ("лаб-даб"), как настоящий пульс, а не плавное дыхание.
 */
public final class BerserkVignette {

    /** Длина одного цикла сердцебиения — ~67 ударов в минуту. */
    private static final long CYCLE_MS = 900L;

    private static final long BEAT1_START = 0L;
    private static final long BEAT1_DUR = 130L;
    private static final long BEAT2_START = 180L;
    private static final long BEAT2_DUR = 110L;

    // "Мягкий" пульс для второго, более широкого слоя виньетки — те же удары, но растянутые
    // почти втрое: он не рисует резкую границу, а плавно подсвечивает фон под ней, смягчая
    // ощущение от резкого/короткого пульса первого слоя (см. GuiMixin — рендерится ПОД ним).
    private static final long SOFT_BEAT1_DUR = 260L;
    private static final long SOFT_BEAT2_DUR = 220L;

    private BerserkVignette() {}

    /** 0..1 — текущая фаза пульса (пики на ударах, 0 между ними). Резкий, короткий — основной слой. */
    public static float getPulse() {
        long t = System.currentTimeMillis() % CYCLE_MS;
        float beat1 = beatEnvelope(t, BEAT1_START, BEAT1_DUR);
        float beat2 = beatEnvelope(t, BEAT2_START, BEAT2_DUR);
        return Math.max(beat1, beat2);
    }

    /** 0..1 — та же фаза удара, но растянутая и смягчённая (квадрат синуса) для второго слоя. */
    public static float getSoftPulse() {
        long t = System.currentTimeMillis() % CYCLE_MS;
        float beat1 = beatEnvelope(t, BEAT1_START, SOFT_BEAT1_DUR);
        float beat2 = beatEnvelope(t, BEAT2_START, SOFT_BEAT2_DUR);
        float raw = Math.max(beat1, beat2);
        return raw * raw; // квадрат — более плавные, "тающие" края, без резкого старта/финиша
    }

    private static float beatEnvelope(long t, long start, long dur) {
        if (t < start || t >= start + dur) return 0.0F;
        float p = (t - start) / (float) dur;
        return (float) Math.sin(Math.PI * p);
    }
}

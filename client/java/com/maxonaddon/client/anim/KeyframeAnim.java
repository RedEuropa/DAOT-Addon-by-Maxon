package com.maxonaddon.client.anim;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Простой сэмплер поворотов костей по ключевым кадрам, вручную перенесённым
 * из player_animations/odm.animation.json на плоский скелет HumanoidModel.
 * Bedrock-интерполяцию "catmullrom" аппроксимируем линейной интерполяцией
 * между соседними кадрами — так же, как уже сделано в
 * AnimatedPlayerVillagerModel#applyBiteAnimation.
 */
public final class KeyframeAnim {

    /** Один ключевой кадр: время в секундах + поворот в градусах (как в animation.json). */
    public record Frame(float time, float xDeg, float yDeg, float zDeg) {
        public static Frame of(float time, float xDeg, float yDeg, float zDeg) {
            return new Frame(time, xDeg, yDeg, zDeg);
        }
    }

    private KeyframeAnim() {
    }

    public static void apply(ModelPart part, Frame[] frames, float t) {
        if (part == null) return;
        float[] v = sample(frames, t);
        part.xRot = (float) Math.toRadians(v[0]);
        part.yRot = (float) Math.toRadians(v[1]);
        part.zRot = (float) Math.toRadians(v[2]);
    }

    /**
     * Складывает две дорожки (например "body" + "torso" из Bedrock-рига,
     * которым в плоском HumanoidModel соответствует одна кость) и применяет
     * итоговый поворот.
     */
    public static void applySum(ModelPart part, Frame[] primary, Frame[] secondary, float t) {
        if (part == null) return;
        float[] a = sample(primary, t);
        float[] b = sample(secondary, t);
        part.xRot = (float) Math.toRadians(a[0] + b[0]);
        part.yRot = (float) Math.toRadians(a[1] + b[1]);
        part.zRot = (float) Math.toRadians(a[2] + b[2]);
    }

    private static float[] sample(Frame[] frames, float t) {
        if (frames.length == 0) return new float[]{0F, 0F, 0F};
        if (t <= frames[0].time()) return deg(frames[0]);

        Frame last = frames[frames.length - 1];
        if (t >= last.time()) return deg(last);

        for (int i = 0; i < frames.length - 1; i++) {
            Frame a = frames[i];
            Frame b = frames[i + 1];
            if (t <= b.time()) {
                float span = b.time() - a.time();
                float p = span <= 1.0E-5F ? 1.0F : (t - a.time()) / span;
                return new float[]{
                        Mth.lerp(p, a.xDeg(), b.xDeg()),
                        Mth.lerp(p, a.yDeg(), b.yDeg()),
                        Mth.lerp(p, a.zDeg(), b.zDeg())
                };
            }
        }
        return deg(last);
    }

    private static float[] deg(Frame f) {
        return new float[]{f.xDeg(), f.yDeg(), f.zDeg()};
    }

    /** Заворачивает время в цикл длиной {@code length} секунд (для loop-анимаций). */
    public static float loop(float t, float length) {
        if (length <= 0F) return 0F;
        float m = t % length;
        return m < 0 ? m + length : m;
    }
}

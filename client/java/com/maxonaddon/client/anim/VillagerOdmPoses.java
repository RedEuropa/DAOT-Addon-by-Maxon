package com.maxonaddon.client.anim;

import com.maxonaddon.client.AnimatedPlayerVillagerModel;

/**
 * Позы жителей-солдат для ОДМ, вручную перенесённые из ключевых кадров
 * player_animations/odm.animation.json (double_hook, double_hook_flying,
 * down_slash, falling) на плоский скелет HumanoidModel/PlayerModel.
 * У ванильной модели нет отдельной кости "torso" (только "body"), поэтому
 * вращение "torso" из Bedrock-рига складывается с "body" и накладывается
 * на HumanoidModel#body — см. {@link KeyframeAnim#applySum}.
 */
public final class VillagerOdmPoses {
    private VillagerOdmPoses() {
    }

    public static final float DOUBLE_HOOK_LENGTH = 0.5F;
    public static final float DOUBLE_HOOK_FLYING_LENGTH = 3.0F;
    public static final float DOWN_SLASH_LENGTH = 0.3333F;
    public static final float FALLING_LENGTH = 3.0F;

    // ==================== double_hook (разовый захват, держит последний кадр) ====================
    private static final KeyframeAnim.Frame[] DH_BODY = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, -12.5F, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DH_TORSO = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.25F, 2.5F, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, 0, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DH_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.0417F, -18.69074F, 2.514F, 2.95058F),
            KeyframeAnim.Frame.of(0.0833F, -26.92902F, 5.48209F, 1.52297F),
            KeyframeAnim.Frame.of(0.125F, -49.94171F, 4.55035F, 1.54639F),
            KeyframeAnim.Frame.of(0.2083F, -49.94171F, 4.55035F, 1.54639F),
            KeyframeAnim.Frame.of(0.25F, -15.75656F, 4.51831F, 7.41636F),
            KeyframeAnim.Frame.of(0.3333F, 40.50903F, 2.64954F, 10.74469F),
            KeyframeAnim.Frame.of(0.5F, 28.00903F, 2.64954F, 10.74469F)
    };
    private static final KeyframeAnim.Frame[] DH_LEFT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.0417F, -19.15751F, -2.54942F, -2.95683F),
            KeyframeAnim.Frame.of(0.0833F, -29.93084F, -6.75843F, -3.6865F),
            KeyframeAnim.Frame.of(0.125F, -51.18531F, -4.59328F, -1.58055F),
            KeyframeAnim.Frame.of(0.2083F, -51.18531F, -4.59328F, -1.58055F),
            KeyframeAnim.Frame.of(0.3333F, 38.04043F, -2.90213F, -10.95149F),
            KeyframeAnim.Frame.of(0.5F, 25.54043F, -2.90213F, -10.95149F)
    };
    private static final KeyframeAnim.Frame[] DH_RIGHT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, 14.85934F, -2.46431F, 7.02766F)
    };
    private static final KeyframeAnim.Frame[] DH_LEFT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, 17.43745F, 1.50179F, -4.76968F)
    };

    // ==================== double_hook_flying (циклический полёт на двух тросах) ====================
    private static final KeyframeAnim.Frame[] DHF_BODY = {
            KeyframeAnim.Frame.of(0.0F, -25F, 0, 0),
            KeyframeAnim.Frame.of(3.0F, -25F, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DHF_TORSO = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(1.5F, 2.5F, 0, 0),
            KeyframeAnim.Frame.of(3.0F, 0, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DHF_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 40.50903F, 2.64954F, 10.74469F),
            KeyframeAnim.Frame.of(0.5F, 40.55759F, 2.86777F, 12.02893F),
            KeyframeAnim.Frame.of(1.5F, 40.56997F, 1.02446F, 12.6455F),
            KeyframeAnim.Frame.of(2.25F, 40.5395F, 1.837F, 11.6951F),
            KeyframeAnim.Frame.of(3.0F, 40.50903F, 2.64954F, 10.74469F)
    };
    private static final KeyframeAnim.Frame[] DHF_LEFT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 38.04043F, -2.90213F, -10.95149F),
            KeyframeAnim.Frame.of(0.5F, 38.09481F, -3.1753F, -12.22539F),
            KeyframeAnim.Frame.of(1.5F, 38.11367F, -1.36017F, -12.92075F),
            KeyframeAnim.Frame.of(2.25F, 37.07705F, -2.13115F, -11.93612F),
            KeyframeAnim.Frame.of(3.0F, 38.04043F, -2.90213F, -10.95149F)
    };
    private static final KeyframeAnim.Frame[] DHF_RIGHT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 42.35934F, -2.46431F, 7.02766F),
            KeyframeAnim.Frame.of(0.5F, 43.22583F, -1.95362F, 6.16726F),
            KeyframeAnim.Frame.of(1.5F, 44.85934F, -2.46431F, 7.02766F),
            KeyframeAnim.Frame.of(3.0F, 42.35934F, -2.46431F, 7.02766F)
    };
    private static final KeyframeAnim.Frame[] DHF_LEFT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 29.93745F, 1.50179F, -4.76968F),
            KeyframeAnim.Frame.of(0.5F, 30.79308F, 1.18809F, -3.81989F),
            KeyframeAnim.Frame.of(1.5F, 32.43745F, 1.50179F, -4.76968F),
            KeyframeAnim.Frame.of(3.0F, 29.93745F, 1.50179F, -4.76968F)
    };

    // ==================== down_slash (удар сверху вниз, держит последний кадр) ====================
    private static final KeyframeAnim.Frame[] DS_BODY = {
            KeyframeAnim.Frame.of(0.0F, -10F, 0, 0),
            KeyframeAnim.Frame.of(0.25F, 42.5F, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, 35F, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DS_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0.0F, -217.56397F, 7.43547F, -0.98448F),
            KeyframeAnim.Frame.of(0.125F, -70.00464F, -10.18922F, 22.18654F),
            KeyframeAnim.Frame.of(0.25F, 77.38058F, 2.3706F, -7.57632F),
            KeyframeAnim.Frame.of(0.3333F, 63.74297F, 9.19752F, 5.85693F)
    };
    private static final KeyframeAnim.Frame[] DS_LEFT_ARM = {
            KeyframeAnim.Frame.of(0.0F, -217.56397F, -7.43547F, 0.98448F),
            KeyframeAnim.Frame.of(0.125F, -70.04462F, 10.30517F, -22.61241F),
            KeyframeAnim.Frame.of(0.25F, 77.41364F, -2.12793F, 6.73602F),
            KeyframeAnim.Frame.of(0.3333F, 63.71842F, -8.95381F, -6.69207F)
    };
    private static final KeyframeAnim.Frame[] DS_RIGHT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 17.5F, 0, 0),
            KeyframeAnim.Frame.of(0.25F, -37.5F, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, -30F, 0, 0)
    };
    private static final KeyframeAnim.Frame[] DS_LEFT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 25F, 0, 0),
            KeyframeAnim.Frame.of(0.25F, -50F, 0, 0),
            KeyframeAnim.Frame.of(0.3333F, -42.5F, 0, 0)
    };

    // ==================== falling (свободное падение без крюков, циклическая) ====================
    private static final KeyframeAnim.Frame[] FALL_BODY = {
            KeyframeAnim.Frame.of(0.0F, 22.5F, 0, 0),
            KeyframeAnim.Frame.of(3.0F, 22.5F, 0, 0)
    };
    private static final KeyframeAnim.Frame[] FALL_TORSO = {
            KeyframeAnim.Frame.of(0.0F, 0, 0, 0),
            KeyframeAnim.Frame.of(1.5F, 2.5F, 0, 0),
            KeyframeAnim.Frame.of(3.0F, 0, 0, 0)
    };
    private static final KeyframeAnim.Frame[] FALL_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 54.87345F, 5.85807F, 36.14328F),
            KeyframeAnim.Frame.of(0.75F, 53.47158F, 3.41906F, 34.7802F),
            KeyframeAnim.Frame.of(1.5F, 51.88213F, -1.99965F, 29.39749F),
            KeyframeAnim.Frame.of(2.25F, 53.29446F, 3.74832F, 31.02451F),
            KeyframeAnim.Frame.of(3.0F, 54.87345F, 5.85807F, 36.14328F)
    };
    private static final KeyframeAnim.Frame[] FALL_LEFT_ARM = {
            KeyframeAnim.Frame.of(0.0F, 52.89082F, -8.47167F, -37.72789F),
            KeyframeAnim.Frame.of(0.75F, 51.35181F, -5.12413F, -35.7976F),
            KeyframeAnim.Frame.of(1.5F, 49.51655F, 1.35144F, -29.95526F),
            KeyframeAnim.Frame.of(2.25F, 51.06613F, -5.31105F, -32.0236F),
            KeyframeAnim.Frame.of(3.0F, 52.89082F, -8.47167F, -37.72789F)
    };
    private static final KeyframeAnim.Frame[] FALL_RIGHT_LEG = {
            KeyframeAnim.Frame.of(0.0F, -14.98409F, 0.08038F, 7.44473F),
            KeyframeAnim.Frame.of(0.75F, -14.87203F, 2.20423F, 6.7542F),
            KeyframeAnim.Frame.of(1.5F, -9.75997F, 4.32807F, 6.06368F),
            KeyframeAnim.Frame.of(2.25F, -12.14244F, 1.68006F, 4.30836F),
            KeyframeAnim.Frame.of(3.0F, -14.98409F, 0.08038F, 7.44473F)
    };
    private static final KeyframeAnim.Frame[] FALL_LEFT_LEG = {
            KeyframeAnim.Frame.of(0.0F, 15.01661F, 2.35791F, -2.41948F),
            KeyframeAnim.Frame.of(0.75F, 12.4891F, 1.18372F, -3.92909F),
            KeyframeAnim.Frame.of(1.5F, 14.96158F, 0.00953F, -5.4387F),
            KeyframeAnim.Frame.of(2.25F, 15.33752F, 0.52311F, -1.51767F),
            KeyframeAnim.Frame.of(3.0F, 15.01661F, 2.35791F, -2.41948F)
    };

    public static void applyDoubleHook(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, DOUBLE_HOOK_LENGTH);
        KeyframeAnim.applySum(model.body, DH_BODY, DH_TORSO, t);
        KeyframeAnim.apply(model.rightArm, DH_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, DH_LEFT_ARM, t);
        KeyframeAnim.apply(model.rightLeg, DH_RIGHT_LEG, t);
        KeyframeAnim.apply(model.leftLeg, DH_LEFT_LEG, t);
    }

    public static void applyDoubleHookFlying(AnimatedPlayerVillagerModel model, float t) {
        t = KeyframeAnim.loop(t, DOUBLE_HOOK_FLYING_LENGTH);
        KeyframeAnim.applySum(model.body, DHF_BODY, DHF_TORSO, t);
        KeyframeAnim.apply(model.rightArm, DHF_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, DHF_LEFT_ARM, t);
        KeyframeAnim.apply(model.rightLeg, DHF_RIGHT_LEG, t);
        KeyframeAnim.apply(model.leftLeg, DHF_LEFT_LEG, t);
    }

    public static void applyDownSlash(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, DOWN_SLASH_LENGTH);
        KeyframeAnim.apply(model.body, DS_BODY, t);
        KeyframeAnim.apply(model.rightArm, DS_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, DS_LEFT_ARM, t);
        KeyframeAnim.apply(model.rightLeg, DS_RIGHT_LEG, t);
        KeyframeAnim.apply(model.leftLeg, DS_LEFT_LEG, t);
    }

    /**
     * Винтовка в левой руке вне боя: рука опущена и слегка отведена назад, кисть развёрнута —
     * оружие лежит вдоль бедра, а не болтается стволом в землю.
     * <p>
     * Поза задана числами прямо здесь, а не перенесена из player_animations: анимации именно
     * для этого случая в наборе пока нет, а библиотека анимаций игрока к Villager всё равно
     * не применяется — жителям позы собираются вручную, как и всё остальное в этом классе.
     * Появится ключевой кадр в JSON — перенесём его сюда тем же способом, что и ОДМ.
     */
    public static void applyRifleAtSide(AnimatedPlayerVillagerModel model) {
        model.leftArm.xRot = (float) Math.toRadians(-12.0);
        model.leftArm.yRot = (float) Math.toRadians(-8.0);
        model.leftArm.zRot = (float) Math.toRadians(6.0);
    }

    public static void applyFalling(AnimatedPlayerVillagerModel model, float t) {
        t = KeyframeAnim.loop(t, FALLING_LENGTH);
        KeyframeAnim.applySum(model.body, FALL_BODY, FALL_TORSO, t);
        KeyframeAnim.apply(model.rightArm, FALL_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, FALL_LEFT_ARM, t);
        KeyframeAnim.apply(model.rightLeg, FALL_RIGHT_LEG, t);
        KeyframeAnim.apply(model.leftLeg, FALL_LEFT_LEG, t);
    }
}

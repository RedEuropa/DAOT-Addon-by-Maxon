package com.maxonaddon.client.anim;

import com.maxonaddon.client.AnimatedPlayerVillagerModel;

/**
 * Позы полицейского с марлийской винтовкой.
 * <p>
 * Кадры автоматически перенесены из player_animations/marleyan_rifle_hand.animation.json
 * на плоский скелет PlayerModel — тем же способом, что и ОДМ-позы в VillagerOdmPoses:
 * библиотека анимаций игрока к Villager не применяется, поэтому кадры живут в коде.
 * Кость Bedrock-рига "torso" отдельной пары в HumanoidModel не имеет и складывается
 * с body — см. KeyframeAnim#applySum.
 */
public final class VillagerRiflePoses {

    private VillagerRiflePoses() {
    }

    public static final float IDLE_LENGTH = 4F;
    public static final float AIM_LENGTH = 0.7333F;
    public static final float SHOOT_LENGTH = 1.2667F;
    public static final float BOLT_CYCLE_LENGTH = 1.5F;
    public static final float RIFLE_RELOAD_LENGTH = 3.2F;

    private static final KeyframeAnim.Frame[] IDLE_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0F, -82F, -7F, 1F),
            KeyframeAnim.Frame.of(4F, -82F, -7F, 1F)
    };
    private static final KeyframeAnim.Frame[] IDLE_LEFT_ARM = {
            KeyframeAnim.Frame.of(0F, -86F, 44F, 0F),
            KeyframeAnim.Frame.of(4F, -86F, 44F, 0F)
    };

    private static final KeyframeAnim.Frame[] AIM_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0F, -82F, -7F, 1F),
            KeyframeAnim.Frame.of(0.4667F, -81.4414F, -21.8456F, -1.2242F)
    };
    private static final KeyframeAnim.Frame[] AIM_LEFT_ARM = {
            KeyframeAnim.Frame.of(0F, -86F, 44F, 0F),
            KeyframeAnim.Frame.of(0.4667F, -70.1193F, 41.4841F, -2.8225F)
    };
    private static final KeyframeAnim.Frame[] AIM_HEAD = {
            KeyframeAnim.Frame.of(0F, 0F, 0F, 0F),
            KeyframeAnim.Frame.of(0.3333F, 0F, 0F, -7.5F)
    };

    private static final KeyframeAnim.Frame[] SHOOT_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0F, -82F, -7F, 1F)
    };
    private static final KeyframeAnim.Frame[] SHOOT_LEFT_ARM = {
            KeyframeAnim.Frame.of(0F, -86F, 44F, 0F)
    };
    private static final KeyframeAnim.Frame[] SHOOT_TORSO = {
            KeyframeAnim.Frame.of(0F, 0F, 0F, 0F),
            KeyframeAnim.Frame.of(0.1333F, 0F, 0F, 0F),
            KeyframeAnim.Frame.of(0.2F, 0F, 2.5F, 0F),
            KeyframeAnim.Frame.of(0.3F, 0F, 0F, 0F)
    };

    private static final KeyframeAnim.Frame[] BOLT_CYCLE_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0F, -82F, -7F, 1F)
    };
    private static final KeyframeAnim.Frame[] BOLT_CYCLE_LEFT_ARM = {
            KeyframeAnim.Frame.of(0F, -86F, 44F, 0F)
    };
    private static final KeyframeAnim.Frame[] BOLT_CYCLE_TORSO = {
            KeyframeAnim.Frame.of(0F, 0F, 0F, 0F)
    };

    private static final KeyframeAnim.Frame[] RIFLE_RELOAD_RIGHT_ARM = {
            KeyframeAnim.Frame.of(0F, -82F, -7F, 1F),
            KeyframeAnim.Frame.of(0.6667F, -82.237F, -5.5263F, 0.7895F),
            KeyframeAnim.Frame.of(1.3667F, -70.6509F, -4.0949F, 0.585F),
            KeyframeAnim.Frame.of(1.6333F, -80.4842F, -3.6306F, 0.5188F),
            KeyframeAnim.Frame.of(1.8333F, -79.0325F, -3.1162F, 0.4452F)
    };
    private static final KeyframeAnim.Frame[] RIFLE_RELOAD_LEFT_ARM = {
            KeyframeAnim.Frame.of(0F, -86F, 44F, 0F)
    };

    public static void applyIdle(AnimatedPlayerVillagerModel model, float t) {
        t = KeyframeAnim.loop(t, IDLE_LENGTH);
        KeyframeAnim.apply(model.rightArm, IDLE_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, IDLE_LEFT_ARM, t);
    }

    public static void applyAim(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, AIM_LENGTH);
        KeyframeAnim.apply(model.head, AIM_HEAD, t);
        KeyframeAnim.apply(model.rightArm, AIM_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, AIM_LEFT_ARM, t);
    }

    public static void applyShoot(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, SHOOT_LENGTH);
        KeyframeAnim.apply(model.body, SHOOT_TORSO, t);
        KeyframeAnim.apply(model.rightArm, SHOOT_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, SHOOT_LEFT_ARM, t);
    }

    public static void applyBoltCycle(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, BOLT_CYCLE_LENGTH);
        KeyframeAnim.apply(model.body, BOLT_CYCLE_TORSO, t);
        KeyframeAnim.apply(model.rightArm, BOLT_CYCLE_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, BOLT_CYCLE_LEFT_ARM, t);
    }

    public static void applyRifleReload(AnimatedPlayerVillagerModel model, float t) {
        t = Math.min(t, RIFLE_RELOAD_LENGTH);
        KeyframeAnim.apply(model.rightArm, RIFLE_RELOAD_RIGHT_ARM, t);
        KeyframeAnim.apply(model.leftArm, RIFLE_RELOAD_LEFT_ARM, t);
    }

}

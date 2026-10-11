package com.maxonaddon.client;

import com.maxonaddon.client.anim.VillagerOdmPoses;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;

public class AnimatedPlayerVillagerModel extends PlayerModel<Villager> {

    /** Длина перезарядки в секундах — ровно RELOAD_TICKS (70) из PoliceRifleAttackGoal. */
    private static final float RELOAD_SECONDS = 3.5F;
    /** Сколько держится отдача после выстрела. */
    private static final float RECOIL_SECONDS = 0.25F;

    public AnimatedPlayerVillagerModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(Villager entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Сначала вызываем стандартную логику
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // 1. Анимация укуса (шифтер-превращение) имеет наивысший приоритет
        float biteTime = VillagerAnimationClientTracker.getBiteTime(entity.getId());
        if (biteTime >= 0.0f) {
            applyBiteAnimation(biteTime);
            return;
        }

        // 2. Полёт на тросах ОДМ (double_hook -> double_hook_flying)
        VillagerHookTracker.HookData hook = VillagerHookTracker.getHook(entity.getId());
        if (hook != null && hook.active) {
            if (hook.activeSeconds < VillagerOdmPoses.DOUBLE_HOOK_LENGTH) {
                VillagerOdmPoses.applyDoubleHook(this, hook.activeSeconds);
            } else {
                VillagerOdmPoses.applyDoubleHookFlying(this, hook.activeSeconds - VillagerOdmPoses.DOUBLE_HOOK_LENGTH);
            }
            return;
        }

        // 3. Винтовка в правой руке — полицейский в бою. Фазу присылает сервер, время
        // внутри фазы клиент отсчитывает сам (см. VillagerRifleAnimTracker).
        // Проверяем ДО удара клинком: у стрелка attackAnim может подняться от любого
        // ванильного взмаха, а замах мечом стрелку в принципе не подходит.
        if (entity.getMainHandItem().is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) {
            applyRifleCombatPose(entity);
            return;
        }

        // 4. Удар клинком (down_slash) — используем ванильную фазу взмаха руки (scout.swing(...)),
        // отдельный пакет не нужен: attackAnim уже реплицируется клиенту автоматически.
        if (entity.attackAnim > 0.0F) {
            VillagerOdmPoses.applyDownSlash(this, entity.attackAnim * VillagerOdmPoses.DOWN_SLASH_LENGTH);
            return;
        }

        // 5. Свободное падение без тросов
        if (!entity.onGround() && !entity.isInWater() && entity.getDeltaMovement().y < -0.08) {
            VillagerOdmPoses.applyFalling(this, ageInTicks / 20.0F);
            return;
        }

        // 6. Винтовка в левой руке — тот же полицейский вне боя: оружие «на плече».
        if (entity.getOffhandItem().is(com.maxonaddon.MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) {
            VillagerOdmPoses.applyRifleAtSide(this);
        }
    }

    /**
     * Выбирает позу по фазе, которую прислал сервер. Пока фазы нет (пакет ещё не дошёл или
     * житель только попал в зону видимости) — показываем idle, а не застывшую руку.
     */
    private void applyRifleCombatPose(Villager entity) {
        VillagerRifleAnimTracker.State state = VillagerRifleAnimTracker.get(entity.getId());
        int phase = state == null
                ? com.maxonaddon.network.RifleAnimPayload.IDLE
                : state.phase;

        // Перезарядка = натягивание арбалета: одна рука держит оружие, вторая уходит вперёд.
        if (phase == com.maxonaddon.network.RifleAnimPayload.RELOAD) {
            float seconds = state == null ? 0.0F : state.seconds;
            animateReloadLikeCrossbow(Mth.clamp(seconds / RELOAD_SECONDS, 0.0F, 1.0F));
            return;
        }

        // Всё остальное — ванильная стойка «целюсь из арбалета»: оружие у плеча, руки
        // ведут за головой. Она же служит и позой ожидания между выстрелами.
        AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);

        // Отдача: короткий толчок рук вверх-назад сразу после выстрела.
        if (phase == com.maxonaddon.network.RifleAnimPayload.SHOOT && state != null) {
            float kick = Math.max(0.0F, 1.0F - state.seconds / RECOIL_SECONDS);
            this.rightArm.xRot -= 0.35F * kick;
            this.leftArm.xRot -= 0.25F * kick;
        }
    }

    /**
     * Ванильная поза натягивания арбалета (AnimationUtils#animateCrossbowCharge), но прогресс
     * берём свой: ванильная версия читает getTicksUsingItem(), а житель предмет «не использует» —
     * там всегда был бы ноль, и рука просто застыла бы в начальном положении.
     */
    private void animateReloadLikeCrossbow(float progress) {
        this.rightArm.yRot = -0.8F;
        this.rightArm.xRot = -0.97079635F;
        this.leftArm.xRot = Mth.lerp(progress, -0.97079635F, -((float) Math.PI / 2F));
        this.leftArm.yRot = Mth.lerp(progress, 0.4F, 0.85F);
    }

    private void applyBiteAnimation(float t) {
        t = Math.min(t, 1.5417f);

        // --- ТОРСО (BODY) ---
        if (t < 0.6667f) {
            float p = t / 0.6667f;
            this.body.xRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-10.66f));
            this.body.yRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-16.62f));
            this.body.zRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-2.55f));
        } else if (t < 0.75f) {
            float p = (t - 0.6667f) / 0.0833f;
            this.body.xRot = Mth.lerp(p, (float)Math.toRadians(-10.66f), (float)Math.toRadians(-12.5f));
            this.body.yRot = Mth.lerp(p, (float)Math.toRadians(-16.62f), (float)Math.toRadians(-32.5f));
            this.body.zRot = 0.0f;
        } else if (t < 0.9167f) {
            float p = (t - 0.75f) / 0.1667f;
            this.body.xRot = Mth.lerp(p, (float)Math.toRadians(-12.5f), (float)Math.toRadians(12.59f));
            this.body.yRot = Mth.lerp(p, (float)Math.toRadians(-32.5f), (float)Math.toRadians(5.04f));
            this.body.zRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(5.20f));
        } else {
            this.body.xRot = (float)Math.toRadians(12.12f);
            this.body.yRot = (float)Math.toRadians(6.11f);
            this.body.zRot = (float)Math.toRadians(0.3f);
        }

        // --- ГОЛОВА (HEAD) ---
        if (t < 0.6667f) {
            float p = t / 0.6667f;
            this.head.xRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-23.37f));
            this.head.yRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-11.53f));
            this.head.zRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(8.68f));
        } else if (t < 0.75f) {
            float p = (t - 0.6667f) / 0.0833f;
            this.head.xRot = Mth.lerp(p, (float)Math.toRadians(-23.37f), (float)Math.toRadians(-32.11f));
            this.head.yRot = Mth.lerp(p, (float)Math.toRadians(-11.53f), (float)Math.toRadians(-24.92f));
            this.head.zRot = Mth.lerp(p, (float)Math.toRadians(8.68f), (float)Math.toRadians(12.28f));
        } else if (t < 0.9167f) {
            float p = (t - 0.75f) / 0.1667f;
            this.head.xRot = Mth.lerp(p, (float)Math.toRadians(-32.11f), (float)Math.toRadians(38.54f));
            this.head.yRot = Mth.lerp(p, (float)Math.toRadians(-24.92f), (float)Math.toRadians(8.50f));
            this.head.zRot = Mth.lerp(p, (float)Math.toRadians(12.28f), (float)Math.toRadians(-0.23f));
        } else {
            this.head.xRot = (float)Math.toRadians(43.54f);
            this.head.yRot = (float)Math.toRadians(8.5f);
            this.head.zRot = (float)Math.toRadians(-0.23f);
        }

        // --- ПРАВАЯ РУКА (RIGHT ARM) ---
        if (t < 0.75f) {
            float p = t / 0.75f;
            this.rightArm.xRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(14.45f));
            this.rightArm.yRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(10.16f));
            this.rightArm.zRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(82.42f));
        } else if (t < 0.8333f) {
            float p = (t - 0.75f) / 0.0833f;
            this.rightArm.xRot = Mth.lerp(p, (float)Math.toRadians(14.45f), (float)Math.toRadians(-168.05f));
            this.rightArm.yRot = (float)Math.toRadians(10.16f);
            this.rightArm.zRot = (float)Math.toRadians(82.42f);
        } else {
            this.rightArm.xRot = (float)Math.toRadians(-171.04f);
            this.rightArm.yRot = (float)Math.toRadians(18.38f);
            this.rightArm.zRot = (float)Math.toRadians(88.98f);
        }

        // --- ЛЕВАЯ РУКА (LEFT ARM) ---
        if (t < 0.2917f) {
            float p = t / 0.2917f;
            this.leftArm.xRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-58.05f));
            this.leftArm.yRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(-56.59f));
            this.leftArm.zRot = Mth.lerp(p, 0.0f, (float)Math.toRadians(24.42f));
        } else if (t < 0.75f) {
            float p = (t - 0.2917f) / 0.4583f;
            this.leftArm.xRot = Mth.lerp(p, (float)Math.toRadians(-58.05f), (float)Math.toRadians(-121.94f));
            this.leftArm.yRot = Mth.lerp(p, (float)Math.toRadians(-56.59f), (float)Math.toRadians(-20.98f));
            this.leftArm.zRot = Mth.lerp(p, (float)Math.toRadians(24.42f), (float)Math.toRadians(65.06f));
        } else if (t < 0.9167f) {
            float p = (t - 0.75f) / 0.1667f;
            this.leftArm.xRot = Mth.lerp(p, (float)Math.toRadians(-121.94f), (float)Math.toRadians(-71.14f));
            this.leftArm.yRot = Mth.lerp(p, (float)Math.toRadians(-20.98f), (float)Math.toRadians(-11.64f));
            this.leftArm.zRot = Mth.lerp(p, (float)Math.toRadians(65.06f), (float)Math.toRadians(-13.17f));
        } else {
            this.leftArm.xRot = (float)Math.toRadians(-82.11f);
            this.leftArm.yRot = (float)Math.toRadians(-20.66f);
            this.leftArm.zRot = (float)Math.toRadians(-24.83f);
        }
    }
}
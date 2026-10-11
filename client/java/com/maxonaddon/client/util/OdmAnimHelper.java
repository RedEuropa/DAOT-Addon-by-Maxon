package com.maxonaddon.client.util;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OdmAnimHelper {

    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "injure_controller");
    // ЕДИНЫЙ слой под ВСЮ анимацию рук марлийской винтовки (idle/idle2/draw/shoot/aim_shoot/
    // aim/reload) — НАРОЧНО один слой на всё, а не "базовый idle-слой + отдельный слой под
    // одноразовые действия": раньше idle и одноразовые действия (draw и т.д.) были на РАЗНЫХ
    // слоях (activeTraumas/LAYER_ID и activeReloadAnims/RELOAD_LAYER_ID) и должны были сами
    // синхронизироваться по таймеру, кто из них сейчас главный — из-за гонки между
    // "одноразовый слой ещё не отпустил управление" и "idle-слой уже должен был перехватить"
    // винтовка иногда так и оставалась висеть не в idle. Здесь вместо этого КАЖДЫЙ тик сам
    // код в MaxonDaotAddonClient явно решает, какая ИМЕННО анимация должна идти прямо сейчас
    // (см. setRifleAnim ниже) — никакой гонки между слоями просто не может возникнуть.
    public static final ResourceLocation RIFLE_LAYER_ID = ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "rifle_controller");
    public static final ResourceLocation ACTION_LAYER_ID = ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "action_controller");
    // Отдельный слой под ОДНОРАЗОВЫЕ анимации нашего мода (не травмы — те зациклены,
    // и не dannys-aot-экшены — те завязаны на чужой неймспейс "dannys-aot").
    // Нужен для "gas_fill.animation.json" (odm_reload/apg_reload) и подобного.
    public static final ResourceLocation RELOAD_LAYER_ID = ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "reload_controller");

    private static final Map<UUID, String> activeTraumas = new HashMap<>();
    private static final Map<UUID, String> activeRifleAnim = new HashMap<>();
    private static final Map<UUID, Boolean> rifleAnimLoop = new HashMap<>();
    private static final Map<UUID, String> activeActions = new HashMap<>();
    private static final Map<UUID, Long> actionEndTimes = new HashMap<>(); // Таймер для снятия зависания!
    private static final Map<UUID, String> activeReloadAnims = new HashMap<>();
    private static final Map<UUID, Long> reloadEndTimes = new HashMap<>();

    // === ФЛАГИ ПРИНУДИТЕЛЬНОГО ПЕРЕЗАПУСКА ===
    // AnimationController.setAnimation(...) внутри себя сравнивает новый RawAnimation
    // с уже проигранным (currentRawAnimation) через equals() и, если они СОВПАДАЮТ,
    // просто ничего не делает — не сбрасывает tick и не возвращает состояние в RUNNING.
    // А stop() НЕ очищает currentRawAnimation. Поэтому повторный запуск ОДНОЙ И ТОЙ ЖЕ
    // одноразовой анимации (например, второй odm_reload подряд) молча игнорировался —
    // отсюда "после первого проигрывания анимация не повторяется". Лечится явным
    // controller.forceAnimationReset() перед повторной установкой той же анимации.
    private static final java.util.Set<UUID> actionNeedsReset = new java.util.HashSet<>();
    private static final java.util.Set<UUID> reloadNeedsReset = new java.util.HashSet<>();
    private static final java.util.Set<UUID> rifleAnimNeedsReset = new java.util.HashSet<>();

    public static void registerLayer() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 110, (player) ->
                new PlayerAnimationController(player, OdmAnimHelper::traumaPredicate)
        );
        // Приоритет выше травм (110) — оружие в руках визуально важнее позы травмы для тех же
        // костей рук (тот же принцип, что уже даёт ACTION_LAYER_ID/RELOAD_LAYER_ID выше травм).
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(RIFLE_LAYER_ID, 115, (player) ->
                new PlayerAnimationController(player, OdmAnimHelper::riflePredicate)
        );
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ACTION_LAYER_ID, 120, (player) ->
                new PlayerAnimationController(player, OdmAnimHelper::actionPredicate)
        );
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(RELOAD_LAYER_ID, 130, (player) ->
                new PlayerAnimationController(player, OdmAnimHelper::reloadPredicate)
        );
    }

    private static PlayState traumaPredicate(AnimationController controller, AnimationData data, AnimationController.AnimationSetter setter) {
        return playFromMap(controller, setter, activeTraumas, null, null, "maxon-daot-addon", true);
    }

    private static PlayState riflePredicate(AnimationController controller, AnimationData data, AnimationController.AnimationSetter setter) {
        if (!(controller instanceof PlayerAnimationController pac)) return PlayState.STOP;
        UUID uuid = pac.getPlayer().getUUID();
        String targetAnim = activeRifleAnim.get(uuid);
        if (targetAnim == null || targetAnim.isEmpty()) return PlayState.STOP;

        Animation anim = PlayerAnimResources.getAnimation(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", targetAnim));
        if (anim == null) return PlayState.STOP;

        boolean loop = Boolean.TRUE.equals(rifleAnimLoop.get(uuid));
        if (rifleAnimNeedsReset.remove(uuid)) {
            controller.forceAnimationReset();
        }

        setter.setAnimation(loop ? RawAnimation.begin().thenLoop(anim) : RawAnimation.begin().thenPlayAndHold(anim));
        return PlayState.CONTINUE;
    }

    private static PlayState actionPredicate(AnimationController controller, AnimationData data, AnimationController.AnimationSetter setter) {
        return playFromMap(controller, setter, activeActions, actionEndTimes, actionNeedsReset, "dannys-aot", false);
    }

    private static PlayState reloadPredicate(AnimationController controller, AnimationData data, AnimationController.AnimationSetter setter) {
        return playFromMap(controller, setter, activeReloadAnims, reloadEndTimes, reloadNeedsReset, "maxon-daot-addon", false);
    }

    private static PlayState playFromMap(AnimationController controller, AnimationController.AnimationSetter setter, Map<UUID, String> map, Map<UUID, Long> endTimes, java.util.Set<UUID> needsReset, String namespace, boolean loop) {
        if (controller instanceof PlayerAnimationController pac) {
            AbstractClientPlayer player = pac.getPlayer();
            String targetAnim = map.get(player.getUUID());

            if (targetAnim == null || targetAnim.isEmpty()) {
                return PlayState.STOP;
            }

            // === ЖЕСТКИЙ СБРОС ЗАВИСАНИЯ ===
            // Если анимация разовая (не loop), проверяем таймер. Если время вышло - вырубаем.
            if (!loop && endTimes != null) {
                Long endTime = endTimes.get(player.getUUID());
                if (endTime != null && System.currentTimeMillis() > endTime) {
                    map.remove(player.getUUID());
                    endTimes.remove(player.getUUID());
                    if (needsReset != null) needsReset.remove(player.getUUID());
                    return PlayState.STOP;
                }
            }

            ResourceLocation animId = ResourceLocation.fromNamespaceAndPath(namespace, targetAnim);
            Animation anim = PlayerAnimResources.getAnimation(animId);

            if (anim == null) {
                return PlayState.STOP;
            }

            // Свежий запрос на проигрывание (см. playReload()/playAction()) — форсируем
            // пересборку очереди анимации, иначе контроллер молча проигнорирует установку
            // ТОЙ ЖЕ анимации, что уже когда-то проигрывалась (см. комментарий у полей выше).
            if (needsReset != null && needsReset.remove(player.getUUID())) {
                controller.forceAnimationReset();
            }

            // thenPlayAndHold, а не thenPlay: для одноразовых анимаций (reload-слой) держим
            // последний кадр, пока сам вызывающий код явно не переключит/остановит анимацию
            // (см. playReload()/stopReload()) — раньше проигрывалось один раз и "отвисало" в
            // непредсказуемое положение. Для существующих вызовов (заправка газа и т.п.)
            // поведение не меняется: там durationTicks и так совпадает с длиной ролика, и
            // слой сам обрывается по таймеру раньше, чем это стало бы заметно.
            setter.setAnimation(loop ? RawAnimation.begin().thenLoop(anim) : RawAnimation.begin().thenPlayAndHold(anim));
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    public static void play(Player player, String animName) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        String current = activeTraumas.get(player.getUUID());
        if (animName.equals(current)) return;
        activeTraumas.put(player.getUUID(), animName);
    }

    public static void stop(Player player) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        activeTraumas.remove(player.getUUID());
    }

    /**
     * Единая точка управления анимацией рук под винтовку (см. RIFLE_LAYER_ID выше) — вызывающий
     * код (MaxonDaotAddonClient) каждый тик сам решает, ЧТО ИМЕННО должно идти прямо сейчас
     * (idle/idle2/draw/shoot/aim_shoot/aim/reload), и просто сообщает это сюда. Де-дублирует
     * повторные запросы той же анимации (не дёргает/не перезапускает, если ничего не изменилось).
     *
     * loop=true — обычный цикл (idle/idle2, у них "loop": true в самом animation.json).
     * loop=false — играть один раз и ЗАСТЫТЬ на последнем кадре (thenPlayAndHold), пока сюда
     * не придёт следующий вызов с ДРУГИМ именем — то есть держится ровно до тех пор, пока
     * вызывающий код сам не решит, что пора сменить анимацию (по своим тик-счётчикам).
     */
    public static void setRifleAnim(Player player, String animName, boolean loop) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        UUID uuid = player.getUUID();
        String current = activeRifleAnim.get(uuid);
        if (animName.equals(current)) return;
        activeRifleAnim.put(uuid, animName);
        rifleAnimLoop.put(uuid, loop);
        rifleAnimNeedsReset.add(uuid);
    }

    public static void clearRifleAnim(Player player) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        UUID uuid = player.getUUID();
        activeRifleAnim.remove(uuid);
        rifleAnimLoop.remove(uuid);
        rifleAnimNeedsReset.remove(uuid);
    }

    /**
     * Разовая анимация под наш неймспейс (заправка газа и подобное) — играет ОДИН РАЗ и сама
     * останавливается через durationTicks (переводим в тики Minecraft, 50 мс за тик), в отличие
     * от play()/activeTraumas, который зациклен намертво и не предназначен для one-shot действий.
     */
    public static void playReload(Player player, String animName, int durationTicks) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        activeReloadAnims.put(player.getUUID(), animName);
        reloadEndTimes.put(player.getUUID(), System.currentTimeMillis() + durationTicks * 50L);
        reloadNeedsReset.add(player.getUUID());
    }

    /**
     * Досрочно снимает анимацию reload-слоя (см. playReload()) — например, отпустили ПКМ
     * прицела винтовки раньше, чем истёк переданный туда durationTicks. Без явной остановки
     * зафиксированная thenPlayAndHold-поза висела бы до истечения таймера.
     */
    public static void stopReload(Player player) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        activeReloadAnims.remove(player.getUUID());
        reloadEndTimes.remove(player.getUUID());
        reloadNeedsReset.remove(player.getUUID());
    }

    public static void playAction(Player player, String animName) {
        if (!player.level().isClientSide() || !(player instanceof AbstractClientPlayer)) return;
        activeActions.put(player.getUUID(), animName);
        // Засекаем ровно 500 миллисекунд на анимацию выстрела. После этого она сама исчезнет!
        actionEndTimes.put(player.getUUID(), System.currentTimeMillis() + 420);
        actionNeedsReset.add(player.getUUID());
    }
}
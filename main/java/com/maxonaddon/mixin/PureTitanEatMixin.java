package com.maxonaddon.mixin;

import com.maxonaddon.HostileQteHandler;
import com.maxonaddon.RoyalTitanInteractHandler;
import daot.ShifterTitan;
import daot.BladeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LivingEntity.class)
public abstract class PureTitanEatMixin {

    private static final Map<UUID, Long> grabStartTicks = new ConcurrentHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void interceptEatingLogic(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.level().isClientSide()) return;

        if (self instanceof ShifterTitan) return;

        String name = self.getClass().getName();
        // TitanDummyEntity (тренировочный манекен) не умеет есть — раньше сюда всё равно
        // заходили каждый тик, getMethod("getEatingTarget") кидал NoSuchMethodException,
        // и он молча глотался. Работало, но впустую жгло рефлексию 20 раз в секунду на манекен.
        if (name.startsWith("daot.") && (name.contains("Titan") || name.contains("ConnieFather")) && !name.contains("Grab") && !name.contains("Eye") && !name.contains("Nape") && !name.contains("Dummy")) {

            if (RoyalTitanInteractHandler.sacrificingTitans.containsKey(self.getUUID())) {
                return;
            }

            try {
                Method getEatingTargetMethod = self.getClass().getMethod("getEatingTarget");
                Object targetObj = getEatingTargetMethod.invoke(self);

                if (targetObj instanceof ServerPlayer victim) {
                    UUID victimUuid = victim.getUUID();
                    // Ключ grabStartTicks — UUID ТИТАНА ("этот титан начал держать кого-то в тик X").
                    // Раньше запись велась по UUID жертвы, а удалялась везде по UUID титана, поэтому
                    // не удалялась никогда: задержка в 1 секунду перед QTE срабатывала только при
                    // первом в жизни захвате, дальше QTE стартовало мгновенно, а карта текла.
                    UUID titanUuid = self.getUUID();

                    if (HostileQteHandler.hasGrabImmunity(victim)) {
                        Method cancelEatingMethod = self.getClass().getMethod("cancelEating");
                        cancelEatingMethod.invoke(self);
                        grabStartTicks.remove(titanUuid);
                        return;
                    }

                    // Мёртвый/умирающий титан не должен держать игрока.
                    //
                    // Раньше мы тут просто выходили, считая, что он "вот-вот отпустит". Но
                    // отпускать некому: вся штатная очистка захвата (снятие тега being_grabbed,
                    // setEating(false), высадка) живёт в TitanEntity.die(), а для этого титана
                    // die() уже отработал и второй раз не вызовется. При этом скелет висит в
                    // мире ещё 200 тиков (см. UniversalTitanDeathMixin), а performEat в daot не
                    // проверяет, жив ли титан, — поэтому труп успевает схватить заново. Дальше
                    // тупик: QTE мы не поднимаем, фаза поедания у мёртвого не идёт, урона нет,
                    // и игрок просто висит в пасти, а после исчезновения скелета остаётся с
                    // тегом being_grabbed навсегда.
                    //
                    // Поэтому не ждём у моря погоды, а разжимаем пасть сами — каждый тик, пока
                    // труп пытается удерживать жертву.
                    if (self.isDeadOrDying()) {
                        grabStartTicks.remove(titanUuid);
                        maxon$releaseVictim(self, victim);
                        return;
                    }

                    long currentTick = self.level().getGameTime();
                    grabStartTicks.putIfAbsent(titanUuid, currentTick);

                    // Ждем 1 секунду (20 тиков) перед запуском QTE
                    long grabStart = grabStartTicks.get(titanUuid);
                    if (currentTick - grabStart < 20L) {
                        return;
                    }

                    // === ПРОВЕРКА НАЛИЧИЯ ЦЕЛЫХ МЕЧЕЙ В РУКАХ ===
                    ItemStack mainHand = victim.getMainHandItem();
                    ItemStack offHand = victim.getOffhandItem();
                    boolean hasValidBlades = false;

                    if (mainHand.getItem() instanceof BladeItem && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY) {
                        hasValidBlades = true;
                    } else if (offHand.getItem() instanceof BladeItem && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY) {
                        hasValidBlades = true;
                    }

                    // Если мечей нет или они пустые/ломаные — QTE не стартует, титан спокойно ест
                    if (!hasValidBlades) {
                        return;
                    }

                    boolean isQteAlreadyActive = HostileQteHandler.activeEscapes.containsKey(victimUuid);

                    if (!isQteAlreadyActive) {
                        HostileQteHandler.startEscapeQte(victim, (Monster) self);
                        isQteAlreadyActive = HostileQteHandler.activeEscapes.containsKey(victimUuid);
                    }

                    if (isQteAlreadyActive) {
                        Method isEatingMethod = self.getClass().getMethod("isEating");
                        if ((boolean) isEatingMethod.invoke(self)) {
                            // getDeclaredField на self.getClass() ищет ТОЛЬКО в конечном классе.
                            // У CrawlerTitanEntity своего поля eatTicks нет (унаследовано выше
                            // по иерархии) — раньше это кидало NoSuchFieldException и ускорение
                            // анимации поедания для криперов молча не работало. Ищем по иерархии.
                            Field eatTicksField = maxon$findEatTicksField(self.getClass());
                            if (eatTicksField != null) {
                                eatTicksField.setAccessible(true);
                                int currentEatTicks = eatTicksField.getInt(self);

                                if (self.tickCount % 2 == 0) {
                                    eatTicksField.setInt(self, currentEatTicks + 1);
                                }
                            }
                        }
                    }
                } else {
                    grabStartTicks.remove(self.getUUID());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Освобождает жертву из пасти.
     * <p>
     * Основной путь — cancelEating() самого титана: он снимает тег, на время высадки
     * поднимает setDismountAllowed(true) и сбрасывает фазу поедания. Вручную дёргать
     * stopRiding() бесполезно — вне этого окна daot высадку блокирует. Снятие тега
     * продублировано на случай, когда захват уже развалился наполовину: isEating() при
     * этом false, cancelEating() выходит вхолостую, а тег на жертве остаётся висеть.
     */
    private static void maxon$releaseVictim(LivingEntity titan, ServerPlayer victim) {
        try {
            titan.getClass().getMethod("cancelEating").invoke(titan);
        } catch (Exception ignored) {
            // У этого титана нет cancelEating — обойдёмся снятием тега.
        }
        victim.removeTag("dannys-aot:being_grabbed");
    }

    /** Ищет поле eatTicks по всей иерархии классов — у части титанов оно объявлено выше. */
    private static Field maxon$findEatTicksField(Class<?> cls) {
        for (; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                return cls.getDeclaredField("eatTicks");
            } catch (NoSuchFieldException ignored) {
                // ищем выше
            }
        }
        return null;
    }
}
package com.maxonaddon.mixin;

import com.maxonaddon.util.LegCutState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Mixin(Entity.class)
public class TitanDummyHitboxMixin {

    // ThreadLocal защищает от StackOverflow (бесконечной рекурсии),
    // когда мы сами вызываем setPos внутри перехватчика setPos.
    private static final ThreadLocal<Boolean> IS_OFFSETTING = ThreadLocal.withInitial(() -> false);

    // Кэшируем методы рефлексии, чтобы не искать их каждый тик и не нагружать сервер
    private static final Map<Class<?>, Method> METHOD_CACHE = new HashMap<>();

    // === КОЭФФИЦИЕНТЫ СМЕЩЕНИЯ (доля от bbHeight) ===
    // Раньше тут были угаданные 0.45 (вниз) и bbWidth*0.8 (вперёд) ОДИНАКОВЫЕ для шеи и глаз.
    // Пересчитано честной прямой кинематикой (FK) по настоящим данным мода: цепочка костей
    // main -> upbody -> up -> head -> {nape_hitbox, eye_hitbox} из puretitan.geo.json
    // (пивоты костей) + держащаяся поза анимации "legcut" из puretitan.animation.json
    // (main опускается на 85 единиц, upbody поворачивается на 85°, up почти гасит это
    // обратно до ~1.2° остатка, head ещё -10°). Смещение вычислено как разница мировых
    // координат кости между позой T=0 (стоя) и полностью удержанной позой legcut,
    // в блоках (16 единиц = 1 блок), отнесённое к visible_bounds_height модели (16.5):
    //   шея (nape_hitbox):  вниз 0.74 * bbHeight, вперёд 0.33 * bbHeight
    //   глаза (eye_hitbox): вниз 0.67 * bbHeight, вперёд 0.49 * bbHeight
    // Раньше bbWidth (заметно меньше bbHeight у этого рига — 7 против 16.5) занижал
    // "вперёд" для ГЛАЗ почти вдвое относительно правильного — из-за этого глаза не
    // долетали до своей настоящей точки и визуально стягивались к шее ("в одну точку"),
    // а шея и глаза одинаково недодвигались вниз-вперёд и оставались там, где их уже
    // накрывало наклонившееся вперёд туловище ("проваливается в текстуры").
    private static final double NAPE_DOWN_RATIO = 0.74D;
    private static final double NAPE_FORWARD_RATIO = 0.33D;
    private static final double EYE_DOWN_RATIO = 0.67D;
    private static final double EYE_FORWARD_RATIO = 0.49D;

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void interceptSetPos(double x, double y, double z, CallbackInfo ci) {
        // Если смещение уже применяется нами же — пропускаем, чтобы не зациклить
        if (IS_OFFSETTING.get()) return;

        Entity self = (Entity) (Object) this;

        // Быстрый фильтр: применяем логику только к хитбоксам мода, отсекая обычные энтити
        String className = self.getClass().getSimpleName();
        boolean isNape = className.contains("TitanNape");
        boolean isEye = className.contains("TitanEye");
        if (!isNape && !isEye) {
            return;
        }

        try {
            Class<?> clazz = self.getClass();
            Method getParentMethod = METHOD_CACHE.get(clazz);

            // Если метода нет в кэше, пытаемся его найти
            if (getParentMethod == null && !METHOD_CACHE.containsKey(clazz)) {
                try {
                    getParentMethod = clazz.getMethod("getParentTitan");
                    METHOD_CACHE.put(clazz, getParentMethod);
                } catch (NoSuchMethodException e) {
                    METHOD_CACHE.put(clazz, null); // Запоминаем, что метода нет, чтобы не искать снова
                }
            }

            if (getParentMethod != null) {
                Object parentObj = getParentMethod.invoke(self);

                if (parentObj instanceof LivingEntity parent && parent instanceof LegCutState legState) {
                    if (legState.isLegCut()) {
                        int timer = legState.getLegCutTimer();
                        float progress = 0.0F;

                        // Воспроизводим тайминги анимации
                        if (timer >= 80) {
                            progress = (100 - timer) / 20.0F;
                        } else if (timer > 20) {
                            progress = 1.0F;
                        } else {
                            progress = timer / 20.0F;
                        }

                        progress = progress * progress * (3 - 2 * progress);

                        if (progress > 0) {
                            double downRatio = isEye ? EYE_DOWN_RATIO : NAPE_DOWN_RATIO;
                            double forwardRatio = isEye ? EYE_FORWARD_RATIO : NAPE_FORWARD_RATIO;

                            double dropOffset = parent.getBbHeight() * downRatio * progress;
                            float yaw = (float) Math.toRadians(parent.getYRot());
                            double forwardOffset = parent.getBbHeight() * forwardRatio * progress;

                            // ВАЖНО: x, y и z здесь — это координаты, на которые родитель ПЫТАЕТСЯ поставить шею.
                            // Мы просто прибавляем к ним наше смещение:
                            double targetX = x - Math.sin(yaw) * forwardOffset;
                            double targetY = y - dropOffset;
                            double targetZ = z + Math.cos(yaw) * forwardOffset;

                            // Включаем флаг защиты, устанавливаем новые координаты и выключаем флаг
                            IS_OFFSETTING.set(true);
                            self.setPos(targetX, targetY, targetZ);
                            IS_OFFSETTING.set(false);

                            // Отменяем стандартный вызов, не давая моду затереть наши расчеты
                            ci.cancel();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Безопасно игнорируем ошибки рефлексии
        }
    }
}

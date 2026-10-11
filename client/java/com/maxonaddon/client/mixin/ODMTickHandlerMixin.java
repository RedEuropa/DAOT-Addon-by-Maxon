package com.maxonaddon.client.mixin;

import daot.HookPoint;
import daot.ODMTickHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ODMTickHandler.class, remap = false)
public class ODMTickHandlerMixin {

    // ВАЖНО: цель поля указана БЕЗ владельца ("isExtending:Z", а не "Ldaot/HookPoint;isExtending:Z").
    // Sinytra Connector переписывает строки-цели миксинов и, встретив владельца, пытается
    // отобразить имя поля через таблицу интермедиари; для чужого модового поля отображения нет,
    // и он подставляет литерал "null" -> "Ldaot/HookPoint;null:Z", инъекция падает и игра не стартует.
    // Без владельца эта ветка Connector'а не срабатывает, а Mixin спокойно ищет поле по имени и типу.
    @Redirect(method = "applyHookMovement", at = @At(value = "FIELD", target = "isExtending:Z"))
    private static boolean redirectIsExtending(HookPoint instance) {
        if (!com.maxonaddon.MaxonConfig.get().enableAdvancedHookMechanics) {
            return instance.isExtending; // Возвращаем ванильную логику
        }
        if (instance.startPosition != null && instance.position != null) {
            double distance = instance.startPosition.distanceTo(instance.position);
            // Та же формула, что и у визуала крюка, иначе физика и картинка разъедутся.
            double durationMs = com.maxonaddon.util.HookSpeedHelper.flightDurationMs(distance);
            long elapsed = System.currentTimeMillis() - instance.shootTime;

            // Физика идёт ровно по нашему таймеру: пока не истёк — крюк ещё летит, истёк —
            // уже долетел. Раньше здесь стояло "return true, иначе как в ванили", и вторая
            // половина была потолком для навыка "Ускоренный крюк": быстрее ванильных
            // SHOOT_DURATION (150 мс) крюк зацепиться не мог, сколько его ни ускоряй.
            return elapsed < durationMs;
        }
        return instance.isExtending;
    }
}
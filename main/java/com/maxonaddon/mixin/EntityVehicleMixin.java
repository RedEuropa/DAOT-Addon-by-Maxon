package com.maxonaddon.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Позволяет пользоваться ОДМ, сидя верхом.
 * <p>
 * Базовый мод отключает снаряжение всаднику: почти везде в daot стоят проверки вида «игрок
 * пассажир — значит не летает». Поэтому для вызовов, пришедших из daot, мы отвечаем, что
 * игрок ни на ком не едет, — а всем остальным (ванили, рендеру, другим модам) говорим правду,
 * иначе игрока бы просто ссадило с лошади.
 * <p>
 * Настоящий транспорт при этом остаётся доступен через EntityVehicleAccessor — им и
 * пользуется наш код, которому нужна правда.
 * <p>
 * Отличить «вызов из daot» можно только по стеку — интерфейса для этого базовый мод не даёт.
 * Стек снимается ТОЛЬКО когда сущность реально едет на лошади, поэтому обычные мобы за это не
 * платят ничего; всадников в мире единицы. Порядок проверок здесь важен и трогать его не
 * стоит: сначала дешёвая проверка транспорта, только потом стек.
 * <p>
 * Врём НЕ всему базовому моду, а только его коду про ОДМ (см. ODM_CALLERS). Это не
 * перестраховка: ваниль снимает пассажира с транспорта, опираясь на isPassenger(), и если
 * соврать в этот момент, игрок останется в списке пассажиров и будет снят с чанк-трекинга
 * дважды — сервер падает с NPE в DistanceManager.removePlayer. Ровно тот же вылет уже чинили
 * для выхода из мира верхом, см. PlayerDisconnectDismountMixin; здесь он приходил через смерть
 * игрока, потому что daot слушает и её тоже.
 */
@Mixin(Entity.class)
public abstract class EntityVehicleMixin {

    @Shadow
    private Entity vehicle;

    @Inject(method = "isPassenger", at = @At("HEAD"), cancellable = true)
    private void maxon$hideHorseFromDaot(CallbackInfoReturnable<Boolean> cir) {
        if (maxon$shouldHideHorse()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getVehicle", at = @At("HEAD"), cancellable = true)
    private void maxon$hideHorseVehicleFromDaot(CallbackInfoReturnable<Entity> cir) {
        if (maxon$shouldHideHorse()) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Скрывать ли лошадь от базового мода.
     * <p>
     * Только для игрока с навыком «Всадник»: именно этим враньём daot и разрешает пользоваться
     * снаряжением верхом, поэтому без навыка крюки в седле просто не вылетают — базовый мод
     * видит обычного пассажира и отключает ОДМ сам. Проверка навыка стоит здесь, а не в физике
     * полёта: выстрел крюка живёт в daot, до нашей физики дело доходит уже после него.
     */
    @Unique
    private boolean maxon$shouldHideHorse() {
        // Своё УПМ выключено — не мешаем базовому моду: он снова сам решает, что всадник
        // снаряжением не пользуется.
        if (!com.maxonaddon.MaxonConfig.get().enableOdmPhysics) return false;
        if (!(this.vehicle instanceof AbstractHorse)) return false;
        if (!(((Object) this) instanceof net.minecraft.world.entity.player.Player player)) return false;
        if (!((com.maxonaddon.util.IPlayerStats) player).hasRiderHooks()) return false;
        return maxon$isOdmCaller();
    }

    /**
     * Классы daot, которым мы отвечаем «игрок не пассажир». Только логика снаряжения: она и
     * отключает ОДМ всаднику. Всё остальное в базовом моде — смерть, удаление, команды —
     * должно видеть настоящее положение дел.
     */
    @Unique
    private static final String[] ODM_CALLERS = {
            "daot.ODM", "daot.HookPoint", "daot.Blade", "daot.Gas", "daot.APG"
    };

    /** Пришёл ли вызов из кода ОДМ базового мода. Смотрим неглубоко — свои кадры daot кладёт рядом. */
    @Unique
    private boolean maxon$isOdmCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int limit = Math.min(15, stack.length);
        for (int i = 2; i < limit; i++) {
            String name = stack[i].getClassName();
            if (!name.startsWith("daot.")) continue;
            for (String prefix : ODM_CALLERS) {
                if (name.startsWith(prefix)) return true;
            }
        }
        return false;
    }
}

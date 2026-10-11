package com.maxonaddon.util;

import com.google.common.collect.ImmutableList;
import com.maxonaddon.mixin.EntityVehicleAccessor;
import net.minecraft.world.entity.Entity;

/**
 * Принудительное спешивание в обход {@code stopRiding()}.
 * <p>
 * Нужно там, где игрок обязан покинуть транспорт, а базовый мод спешивание запрещает:
 * daot отменяет stopRiding(), пока титан «не отпускает» («Sneak to dismount»). Если оставить
 * игрока пассажиром, ваниль снимет его с чанк-трекинга дважды — сначала в обходе
 * getPassengersAndSelf(), потом отдельно как игрока, — и сервер падает с
 * NullPointerException в DistanceManager.removePlayer.
 */
public final class RideBreaker {

    private RideBreaker() {}

    /**
     * Ссаживает сущность любой ценой: сперва штатно, а если это отменили — разрывая связь
     * напрямую через поля.
     *
     * @return true, если сущность действительно ехала и была ссажена
     */
    public static boolean forceDismount(Entity rider) {
        if (!rider.isPassenger()) return false;

        rider.stopRiding();
        if (rider.isPassenger()) {
            breakLink(rider);
        }
        return true;
    }

    /** Рвёт связь пассажир-транспорт напрямую через поля, минуя stopRiding(). */
    private static void breakLink(Entity rider) {
        EntityVehicleAccessor riderAccess = (EntityVehicleAccessor) rider;
        Entity vehicle = riderAccess.maxon$getVehicleField();
        riderAccess.maxon$setVehicleField(null);
        if (vehicle == null) return;

        EntityVehicleAccessor vehicleAccess = (EntityVehicleAccessor) vehicle;
        ImmutableList<Entity> current = vehicleAccess.maxon$getPassengersField();
        if (current == null || !current.contains(rider)) return;

        ImmutableList.Builder<Entity> rest = ImmutableList.builder();
        for (Entity passenger : current) {
            if (passenger != rider) rest.add(passenger);
        }
        vehicleAccess.maxon$setPassengersField(rest.build());
    }
}

package com.maxonaddon.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Чинит вылет сервера при выходе из мира верхом на титане, который не отпускает.
 *
 * Ваниль в PlayerList.remove делает так:
 *
 *     if (player.isPassenger()) {
 *         Entity vehicle = player.getRootVehicle();
 *         if (vehicle.hasExactlyOnePlayerPassenger()) {
 *             player.stopRiding();
 *             vehicle.getPassengersAndSelf().forEach(e -> e.setRemoved(UNLOADED_WITH_PLAYER));
 *         }
 *     }
 *     ...
 *     serverLevel.removePlayerImmediately(player, UNLOADED_WITH_PLAYER);
 *
 * Здесь критично, что stopRiding() ДОЛЖЕН отцепить игрока: иначе он остаётся в
 * getPassengersAndSelf(), получает setRemoved(...) в этом самом forEach, а следом
 * removePlayerImmediately снимает его ВТОРОЙ раз. Второе снятие уходит в
 * DistanceManager.removePlayer, где playersPerChunk.get(...) уже пуст — и сервер падает с
 * NullPointerException "Cannot invoke ObjectSet.remove(Object) because $$4 is null".
 *
 * А dannys-aot как раз запрещает спешивание: daot.mixin.EntityDismountMixin инжектится в
 * Entity.stopRiding() (HEAD, cancellable) и отменяет его, пока титан не разрешает высадку —
 * это та самая "Your titan has been defeated! Sneak to dismount". Выходишь из мира, не слезая
 * с побеждённого титана, — получаешь вылет.
 *
 * Поэтому отцепляем игрока сами ещё до ванильной ветки: сначала штатным stopRiding(), а если
 * его отменили — рвём связь напрямую через поля (EntityVehicleAccessor). Удаление транспорта
 * при этом повторяем ровно по ванильному условию, чтобы поведение не изменилось.
 */
@Mixin(PlayerList.class)
public abstract class PlayerDisconnectDismountMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void maxon$forceDismountBeforeLeaving(ServerPlayer player, CallbackInfo ci) {
        if (!player.isPassenger()) return;

        Entity vehicle = player.getRootVehicle();
        // Условие считаем ДО отцепления — после него оно уже не сработает.
        boolean shouldRemoveVehicle = vehicle.hasExactlyOnePlayerPassenger();

        player.stopRiding();
        if (player.isPassenger()) {
            // stopRiding() отменён сторонним кодом — разрываем связь напрямую.
            maxon$forceUnride(player);
        }

        if (shouldRemoveVehicle) {
            // Игрока в этом списке уже нет, так что второго setRemoved по нему не будет.
            vehicle.getPassengersAndSelf().forEach(e -> e.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
        }
    }

    /** Рвёт связь пассажир-транспорт напрямую через поля, минуя stopRiding(). */
    @Unique
    private static void maxon$forceUnride(ServerPlayer player) {
        EntityVehicleAccessor playerAccess = (EntityVehicleAccessor) player;
        Entity vehicle = playerAccess.maxon$getVehicleField();
        playerAccess.maxon$setVehicleField(null);
        if (vehicle == null) return;

        EntityVehicleAccessor vehicleAccess = (EntityVehicleAccessor) vehicle;
        ImmutableList<Entity> current = vehicleAccess.maxon$getPassengersField();
        if (current != null && current.contains(player)) {
            ImmutableList.Builder<Entity> rest = ImmutableList.builder();
            for (Entity passenger : current) {
                if (passenger != player) rest.add(passenger);
            }
            vehicleAccess.maxon$setPassengersField(rest.build());
        }
    }
}

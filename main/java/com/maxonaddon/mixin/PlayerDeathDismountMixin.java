package com.maxonaddon.mixin;

import com.maxonaddon.util.RideBreaker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Не даёт серверу упасть, если игрок погиб верхом.
 * <p>
 * Через 20 тиков после смерти ваниль вызывает игроку {@code remove(KILLED)}, а тот снимает его
 * с чанк-трекинга. Если к этому моменту игрок всё ещё числится пассажиром, снятие происходит
 * дважды: сначала вместе с транспортом через getPassengersAndSelf(), потом отдельно — и второй
 * заход падает с NullPointerException в DistanceManager.removePlayer, потому что в
 * playersPerChunk его уже нет.
 * <p>
 * Это та же поломка, что чинится в PlayerDisconnectDismountMixin для выхода из мира, только
 * приходит она через смерть: базовый мод запрещает спешивание, пока титан «не отпускает», и
 * ванильное stopRiding() в этот момент отменяется. Поэтому ссаживаем сами, а если запретили —
 * рвём связь напрямую (RideBreaker).
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDeathDismountMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void maxon$dismountBeforeDeath(DamageSource source, CallbackInfo ci) {
        RideBreaker.forceDismount((ServerPlayer) (Object) this);
    }
}

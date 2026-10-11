package com.maxonaddon.mixin;

import daot.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class TitanRemovalBlockerMixin {

    // Внедряемся в самый корень удаления сущностей
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void blockEarlyRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        boolean isOurTitan = self instanceof TitanBeardEntity ||
                self instanceof CrawlerTitanEntity ||
                self instanceof YellowTitanEntity ||
                self instanceof ConnieFatherEntity ||
                self instanceof SmallTitan2Entity ||
                self instanceof SmallTitanEntity ||
                self instanceof SadTitanEntity ||
                self instanceof TitanEntity ||
                self instanceof FritzTitanEntity;

        // Держим мёртвого титана в мире 200 тиков (10 сек) вместо ванильных ~20, чтобы
        // доиграла анимация смерти.
        //
        // ВАЖНО: перехватываем ТОЛЬКО причину KILLED. Раньше отменялось удаление по любой
        // причине, включая UNLOADED_TO_CHUNK и CHANGED_DIMENSION — то есть титана нельзя было
        // выгрузить вместе с чанком, и он оставался висеть в уже выгруженном чанке.
        if (reason == Entity.RemovalReason.KILLED
                && isOurTitan
                && self instanceof LivingEntity living
                && living.isDeadOrDying()
                && living.deathTime < 200) {
            ci.cancel();
        }
    }
}
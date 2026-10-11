package com.maxonaddon.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ВРЕМЕННЫЙ ОТЛАДОЧНЫЙ МИКСИН. Удалить, когда баг будет найден.
 *
 * Ловит момент выдачи тега weakened_shifter_vessel и печатает стек вызова —
 * то есть точное имя класса и строку, которые этот тег ставят.
 * Не забудь добавить "MaxonTagDebugMixin" в maxon-daot-addon.mixins.json
 */
@Mixin(Entity.class)
public abstract class MaxonTagDebugMixin {

    @Unique
    private static final Logger MAXON$LOG = LogUtils.getLogger();

    @Inject(method = "addTag", at = @At("HEAD"))
    private void maxon$logTagSource(String tag, CallbackInfoReturnable<Boolean> cir) {
        if (!"weakened_shifter_vessel".equals(tag)) return;

        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n================ [MaxonAddon/TAG] КТО СТАВИТ weakened_shifter_vessel ================\n");
        sb.append("Сущность: ").append(self.getType().toShortString())
                .append("  uuid=").append(self.getStringUUID(), 0, 8)
                .append("  pos=").append(self.position()).append('\n');

        if (self instanceof Mob mob) {
            sb.append("NoAI=").append(mob.isNoAi())
                    .append("  NoGravity=").append(mob.isNoGravity())
                    .append("  noPhysics=").append(mob.noPhysics)
                    .append("  vehicle=").append(mob.getVehicle() == null
                            ? "НЕТ"
                            : mob.getVehicle().getClass().getName())
                    .append('\n');
        }

        sb.append("--- СТЕК ВЫЗОВА ---\n");
        for (StackTraceElement el : new Throwable().getStackTrace()) {
            String line = el.toString();
            // Ванильные внутренности пропускаем, оставляем только моды
            if (line.startsWith("net.minecraft.") || line.startsWith("java.")) continue;
            sb.append("    ").append(line).append('\n');
        }
        sb.append("=====================================================================================");

        MAXON$LOG.info(sb.toString());
    }
}
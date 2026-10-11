package com.maxonaddon.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ПРИЧИНА БАГА С ЛЕВИТАЦИЕЙ:
 * после discard() титана кто-то каждый тик заново сажает ослабленного жителя
 * на уже удалённого титана (поле vehicle снова заполняется, а stopRiding()
 * при этом ещё и телепортирует жителя к точке высадки титана — то есть в воздух).
 *
 * Здесь мы просто запрещаем саму посадку: житель с тегом weakened_shifter_vessel
 * не может сесть ни на кого, кроме игрока (переноска на руках), если транспорт
 * удалён или мёртв. Пока титан живой (анимация смерти 60 тиков) — не мешаем.
 */
@Mixin(Entity.class)
public abstract class VesselRideBlockerMixin {

    @Unique
    private static final Logger MAXON$LOG = LogUtils.getLogger();

    @Unique
    private static int maxon$logged = 0;

    @Inject(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void maxon$blockGhostRemount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        if (self.level().isClientSide()) return;
        if (!(self instanceof Villager)) return;
        if (!self.getTags().contains("weakened_shifter_vessel")) return;

        // Переноска игроком на руках — разрешена всегда
        if (vehicle instanceof Player) return;

        // Живой титан во время анимации смерти — не трогаем, это задумка
        if (!vehicle.isRemoved() && vehicle.isAlive()) return;

        // Осталось только одно: попытка посадить жителя на труп. Запрещаем.
        if (maxon$logged < 3) {
            maxon$logged++;
            StringBuilder sb = new StringBuilder();
            sb.append("\n=========== [MaxonAddon/RIDE] ЗАБЛОКИРОВАНА ПОСАДКА НА ТРУП ===========\n");
            sb.append("Транспорт: ").append(vehicle.getClass().getName())
                    .append(" removed=").append(vehicle.isRemoved())
                    .append(" alive=").append(vehicle.isAlive()).append('\n');
            sb.append("--- КТО САЖАЕТ ---\n");
            for (StackTraceElement el : new Throwable().getStackTrace()) {
                String line = el.toString();
                if (line.startsWith("java.")) continue;
                sb.append("    ").append(line).append('\n');
            }
            sb.append("=======================================================================");
            MAXON$LOG.info(sb.toString());
        }

        cir.setReturnValue(false);
    }
}
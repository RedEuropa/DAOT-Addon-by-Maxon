package com.maxonaddon.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лошадь, с которой спрыгнули на тросах, ещё какое-то время скачет дальше сама.
 * <p>
 * Без этого спрыг выглядел нелепо: всадник улетает на крюке, а лошадь застывает на месте как
 * вкопанная. Направление берётся с момента прыжка, скорость — ровно галоп, а через
 * RUN_TICKS лошадь возвращается под управление обычного ИИ.
 */
public final class RunawayHorseTracker {

    /** Сколько тиков лошадь скачет по инерции — пять секунд. */
    private static final int RUN_TICKS = 100;
    /** Горизонтальная скорость галопа. */
    private static final double GALLOP_SPEED = 0.6;

    private record Run(Vec3 direction, int ticksLeft) {}

    private static final Map<LivingEntity, Run> RUNNING = new ConcurrentHashMap<>();

    private RunawayHorseTracker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (RUNNING.isEmpty()) return;

            RUNNING.replaceAll((horse, run) -> {
                if (!horse.isAlive() || run.ticksLeft() <= 0) return run;
                Vec3 velocity = horse.getDeltaMovement();
                // По вертикали не трогаем — пусть падает и прыгает своими силами.
                horse.setDeltaMovement(run.direction().x * GALLOP_SPEED, velocity.y,
                        run.direction().z * GALLOP_SPEED);
                return new Run(run.direction(), run.ticksLeft() - 1);
            });
            RUNNING.entrySet().removeIf(e -> e.getValue().ticksLeft() <= 0 || !e.getKey().isAlive());
        });

        // Мапа статическая и переживает смену мира: без очистки в ней остались бы висеть
        // ссылки на лошадей предыдущего мира — тот же класс утечки, что чинили в генераторах
        // построек.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RUNNING.clear());
    }

    /**
     * Спешивает игрока перед рывком и отправляет лошадь скакать дальше.
     *
     * @return true, если игрок действительно сидел верхом
     */
    public static boolean dismountAndRun(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractHorse horse)) return false;

        // Направление берём от самой лошади: её собственная скорость, а если стояла на
        // месте — то куда смотрела. Иначе стоящая лошадь получила бы нулевой вектор и
        // осталась бы на месте вопреки смыслу.
        Vec3 velocity = horse.getDeltaMovement();
        Vec3 direction = new Vec3(velocity.x, 0, velocity.z);
        if (direction.lengthSqr() < 0.01) {
            Vec3 look = horse.getLookAngle();
            direction = new Vec3(look.x, 0, look.z);
        }

        if (direction.lengthSqr() > 0.0) {
            RUNNING.put(horse, new Run(direction.normalize(), RUN_TICKS));
        }

        player.stopRiding();
        // Приподнимаем импульсом, а НЕ setPos: прямая правка координат ServerPlayer обходит
        // обновление чанк-трекинга, и если игрок при этом пересечёт границу секции, его потом
        // снимут с учёта в секции, где он не числился — сервер падает с NPE в
        // DistanceManager.removePlayer (тот же вылет, что в PlayerDisconnectDismountMixin).
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.4, 0.0));
        player.hurtMarked = true;
        return true;
    }
}

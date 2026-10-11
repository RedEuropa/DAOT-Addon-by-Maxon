package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.RoyalTitanInteractHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Чистильщик застрявших чистых титанов.
 *
 * Титаны проваливаются в пещеры (особенно после добавления пещерной генерации), сами оттуда не
 * выбираются и не деспавнятся — а лимит спавна они при этом занимают, поэтому новые перестают
 * появляться. Раз в CLEANUP_INTERVAL проходим по загруженным сущностям и убираем тех, кто
 * заведомо никому не нужен.
 *
 * Как отличаем чистого титана от шифтера (проверено по иерархии классов в dannys-aot 2.4.3):
 * интерфейс daot.GrabbingTitan реализуют РОВНО чистые титаны — TitanEntity, SmallTitan,
 * SmallTitan2, Crawler, Yellow, Sad, Fritz (и наследники Beard/Tropical/Abnormal/CrawlingAbnormal),
 * ConnieFather, OgreTitan. Все шифтеры (Attack, Armored, Female, Beast, Colossal, Warhammer, а
 * также наследники AttackTitanEntity — Founding, TripleT, TestShifter/Jaw, Cart, OgreShifter)
 * реализуют daot.ShifterTitan и GrabbingTitan НЕ реализуют.
 *
 * Проверка по именам классов для этого не годится: "FoundingTitanEntity" и "TripleTTitanEntity"
 * содержат "Titan", но не содержат ни "Shifter", ни имён из списка исключений — то есть наивный
 * фильтр по строкам удалял бы чужие силы шифтеров. Наоборот, "ConnieFatherEntity" слова "Titan"
 * не содержит вовсе, и такой фильтр его бы никогда не тронул.
 */
public final class StuckTitanCleaner {

    private static int tickCounter = 0;

    private StuckTitanCleaner() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!MaxonConfig.get().stuckTitanCleanup) return;

            int interval = Math.max(20, MaxonConfig.get().stuckTitanCleanupIntervalSeconds * 20);
            if (++tickCounter < interval) return;
            tickCounter = 0;

            for (ServerLevel level : server.getAllLevels()) {
                cleanupStuckTitans(level);
            }
        });
    }

    private static void cleanupStuckTitans(ServerLevel level) {
        double keepRadius = MaxonConfig.get().stuckTitanKeepRadius;
        double caveRadius = MaxonConfig.get().stuckTitanCaveRadius;

        // Сначала собираем список, и только потом удаляем: discard() прямо во время обхода
        // getAllEntities() меняет коллекцию под итератором.
        List<Entity> doomed = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (!isCleanablePureTitan(entity)) continue;

            Player nearestPlayer = level.getNearestPlayer(entity, keepRadius);
            boolean canSeeSky = level.canSeeSky(entity.blockPosition());

            // Удаляем, если:
            // 1) в радиусе keepRadius вообще нет игроков;
            // 2) титан сидит без неба над головой (пещера) и ближайший игрок дальше caveRadius.
            if (nearestPlayer == null || (!canSeeSky && entity.distanceTo(nearestPlayer) > caveRadius)) {
                doomed.add(entity);
            }
        }

        for (Entity entity : doomed) {
            entity.discard();
        }
    }

    private static boolean isCleanablePureTitan(Entity entity) {
        if (!(entity instanceof daot.GrabbingTitan)) return false;
        // Страховка на случай, если будущая версия daot повесит GrabbingTitan и на шифтера.
        if (entity instanceof daot.ShifterTitan) return false;

        // Именная бирка — титан явно чей-то ручной.
        if (entity.hasCustomName()) return false;

        // Кто-то верхом (например, королевская кровь управляет титаном).
        if (entity.isVehicle()) return false;

        // Босс-берсерк.
        if (entity.getTags().contains("berserk_boss")) return false;

        // Титан, созданный игроком (королевская кровь) — не наш мусор.
        if (daot.VillagerTransformTracker.getOwnerName(entity) != null) return false;

        // Идёт жертвоприношение — трогать нельзя.
        if (RoyalTitanInteractHandler.sacrificingTitans.containsKey(entity.getUUID())) return false;

        if (entity instanceof Mob mob) {
            // Заспавнен яйцом/командой либо иначе помечен как постоянный — ваниль такого
            // тоже никогда не деспавнит.
            if (mob.isPersistenceRequired()) return false;
            // Умирающему даём доиграть смерть — он и так исчезнет сам.
            if (mob.isDeadOrDying()) return false;
        }

        return true;
    }
}

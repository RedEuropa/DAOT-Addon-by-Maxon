package com.maxonaddon.world;

import com.maxonaddon.util.PoliceAlertTracker;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Если игрок бьёт мирного жителя, все полицейские ("police_hunter") поблизости
 * получают тревогу (см. PoliceAlertTracker) и бросаются защищать пострадавшего —
 * см. PoliceDefendVillagersGoal. Контрабандистов и самих полицейских это не касается —
 * не мирные жители в этом смысле.
 */
public final class PoliceDefendVillagersHandler {

    private static final double ALERT_RADIUS = 24.0;
    private static final long ALERT_LIFETIME_TICKS = 100L; // 5 секунд на то, чтобы полицейский подхватил тревогу

    private PoliceDefendVillagersHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
            if (blocked || damageTaken <= 0.0F) return;
            if (!(victim instanceof Villager villager)) return;
            if (villager.getTags().contains("police_hunter")) return;
            if (villager.getTags().contains("smuggler") && !villager.getTags().contains("smuggler_caught")) return;

            Entity attackerEntity = source.getEntity();
            if (!(attackerEntity instanceof Player attacker)) return;
            if (!(villager.level() instanceof ServerLevel level)) return;

            AABB box = villager.getBoundingBox().inflate(ALERT_RADIUS);
            List<Villager> officers = level.getEntitiesOfClass(Villager.class, box,
                    v -> v.isAlive() && v.getTags().contains("police_hunter"));

            long expireAt = level.getGameTime() + ALERT_LIFETIME_TICKS;
            for (Villager officer : officers) {
                PoliceAlertTracker.alert(officer, attacker.getUUID(), expireAt);
            }
        });
    }
}

package com.maxonaddon.world;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.event.CarriageSpawner;
import com.maxonaddon.util.DistrictHelper;
import com.maxonaddon.util.PoliceGearHelper;
import com.maxonaddon.util.SmugglerTradeHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Гарантирует, что в каждом реальном округе (см. CarriageSpawner.SPAWN_POINTS —
 * "город" в этом мире НЕ около (0,0), там ближайший округ на ~1300 блоков дальше)
 * всегда есть 3-4 полицейских и хотя бы один контрабандист. Обычный шанс при спавне
 * жителя ("smugglerSpawnChance"/"policeSpawnChance") это НЕ гарантирует — это просто шанс.
 * Периодически проверяет каждый округ и, если нужного числа не хватает (никого не
 * заспавнило, или кого-то убили), "повышает" случайных обычных жителей этого округа
 * до нужной роли — за один проход может повысить сразу нескольких.
 */
public final class CityFactionGuard {

    private static final ResourceKey<Level> PARADIS_KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));

    private static final int CHECK_INTERVAL_TICKS = 1200; // раз в минуту
    private static final int MIN_POLICE_PER_DISTRICT = 3;
    private static final int MAX_POLICE_PER_DISTRICT = 4;
    private static final int MIN_SMUGGLERS_PER_DISTRICT = 1;

    private CityFactionGuard() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

            ServerLevel level = server.getLevel(PARADIS_KEY);
            if (level == null) return;
            if (!level.getGameRules().getBoolean(MaxonConfig.DO_SOLDIER_SPAWN)) return;

            for (BlockPos center : CarriageSpawner.SPAWN_POINTS) {
                ensurePresence(level, center);
            }
        });
    }

    private static void ensurePresence(ServerLevel level, BlockPos districtCenter) {
        AABB cityBox = new AABB(districtCenter).inflate(DistrictHelper.CITY_RADIUS, 200.0, DistrictHelper.CITY_RADIUS);
        List<Villager> villagersInCity = level.getEntitiesOfClass(Villager.class, cityBox, Villager::isAlive);

        // Округ сейчас не загружен (никто рядом не стоит) — ждём следующей проверки.
        if (villagersInCity.isEmpty()) return;

        long policeCount = villagersInCity.stream().filter(v -> v.getTags().contains("police_hunter")).count();
        long smugglerCount = villagersInCity.stream().filter(v ->
                v.getTags().contains("smuggler") && !v.getTags().contains("smuggler_caught")).count();

        int targetPolice = MIN_POLICE_PER_DISTRICT + level.getRandom().nextInt(MAX_POLICE_PER_DISTRICT - MIN_POLICE_PER_DISTRICT + 1);
        int policeNeeded = (int) Math.max(0, targetPolice - policeCount);
        int smugglersNeeded = (int) Math.max(0, MIN_SMUGGLERS_PER_DISTRICT - smugglerCount);

        List<Villager> eligible = eligibleCandidates(villagersInCity);

        for (int i = 0; i < policeNeeded && !eligible.isEmpty(); i++) {
            Villager candidate = takeRandom(eligible, level.getRandom());
            PoliceGearHelper.equipAsPolice(candidate);
            System.out.println("[MaxonAddon] Округ у " + districtCenter + ": назначен новый полицейский ("
                    + candidate.getUUID() + ")");
        }

        for (int i = 0; i < smugglersNeeded && !eligible.isEmpty(); i++) {
            Villager candidate = takeRandom(eligible, level.getRandom());
            SmugglerTradeHelper.markAsSmuggler(candidate);
            System.out.println("[MaxonAddon] Округ у " + districtCenter + ": назначен новый контрабандист ("
                    + candidate.getUUID() + ")");
        }
    }

    private static List<Villager> eligibleCandidates(List<Villager> villagers) {
        return villagers.stream()
                .filter(v -> !v.isBaby()
                        && !v.getTags().contains("carriage_driver")
                        // Точки квестовых командиров (QuestCommanderSpawner) стоят практически
                        // в тех же координатах, что и центры округов (CarriageSpawner —
                        // ~1300/7300/13300, то же кольцо) — то есть физически ВНУТРИ
                        // CITY_RADIUS, который сканирует ensurePresence(). Без этого
                        // исключения командира могло случайно "повысить" до полицейского
                        // или контрабандиста: PoliceGearHelper.equipAsPolice/markAsSmuggler
                        // затирают его форменный плащ и профессию своей экипировкой.
                        && !v.getTags().contains("quest_commander")
                        && !v.getTags().contains("garrison_soldier")
                        && !v.getTags().contains("scout_legion")
                        && !v.getTags().contains("police_hunter")
                        && !v.getTags().contains("smuggler")
                        && !v.getTags().contains("smuggler_caught"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Достаёт случайный элемент из списка и сразу убирает его оттуда, чтобы не выбрать дважды. */
    private static Villager takeRandom(List<Villager> pool, RandomSource random) {
        return pool.remove(random.nextInt(pool.size()));
    }
}

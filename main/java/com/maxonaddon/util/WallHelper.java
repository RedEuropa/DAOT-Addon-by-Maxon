package com.maxonaddon.util;

import com.maxonaddon.event.CarriageSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Определяет, к какой стене (Мария/Роза/Сина) относится конкретное место или житель.
 * "Стена = весь округ" — все 12 округов из CarriageSpawner.SPAWN_POINTS уже стоят тремя
 * кольцами от центра карты (~1300 / ~7300 / ~13300 блоков), поэтому берём готовую геометрию,
 * а не выдумываем новую: округа 1-4 (внутреннее кольцо) = Сина, 5-8 (среднее) = Роза,
 * 9-12 (внешнее) = Мария. То же кольцо, на котором стоят точки квестовых командиров
 * (см. QuestCommanderSpawner) — Полиция = Сина, Гарнизон = Роза, Скауты = Мария.
 */
public final class WallHelper {

    private WallHelper() {}

    /** Индекс (1-based) ближайшего округа из CarriageSpawner.SPAWN_POINTS, или -1, если (x,z) не в радиусе ни одного. */
    public static int nearestDistrictIndex(double x, double z) {
        BlockPos[] points = CarriageSpawner.SPAWN_POINTS;
        double radiusSq = DistrictHelper.CITY_RADIUS * DistrictHelper.CITY_RADIUS;

        int best = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            double dx = x - points[i].getX();
            double dz = z - points[i].getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq <= radiusSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = i + 1;
            }
        }
        return best;
    }

    /** Стена по индексу округа (1-based), см. схему деления в шапке класса. Null для неизвестного индекса. */
    public static Wall wallForDistrictIndex(int districtIndex) {
        if (districtIndex < 1 || districtIndex > 12) return null;
        if (districtIndex <= 4) return Wall.SINA;
        if (districtIndex <= 8) return Wall.ROSE;
        return Wall.MARIA;
    }

    /** Стена по позиции — null, если позиция не внутри радиуса ни одного из 12 округов. */
    public static Wall getWallAt(double x, double z) {
        return wallForDistrictIndex(nearestDistrictIndex(x, z));
    }

    public static Wall getWallAt(Entity entity) {
        return getWallAt(entity.getX(), entity.getZ());
    }

    /**
     * Стена квестового командира: сперва по тегу (см. QuestCommanderSpawner — расставляется
     * прямо при спавне), а если тега нет (командир заспавнен ДО этого обновления, на старом
     * сохранении) — запасной вариант по чистому радиусу от центра карты, т.к. точки командиров
     * стоят ровно на тех же трёх кольцах, что и округа, но сами вне радиуса CITY_RADIUS
     * округов (это отдельные "монументы", getWallAt(Entity) для них всегда вернёт null).
     */
    public static Wall getWallForCommander(Entity commander) {
        for (Wall wall : Wall.values()) {
            if (commander.getTags().contains(wall.tag)) return wall;
        }

        double distFromOrigin = Math.sqrt(commander.getX() * commander.getX() + commander.getZ() * commander.getZ());
        if (distFromOrigin < 4000.0) return Wall.SINA;
        if (distFromOrigin < 10000.0) return Wall.ROSE;
        return Wall.MARIA;
    }

    /**
     * Стена военного НПС (police_hunter/garrison_soldier/scout_legion) — НАПРЯМУЮ по его же
     * тегу ветки, а не по позиции. Это принципиально: getWallAt(Entity) работает только в
     * радиусе CITY_RADIUS от одного из 12 округов, а garrison_soldier/scout_legion (в отличие
     * от police_hunter) выставляются ЛЮБОМУ жителю при спавне вообще где угодно на карте
     * (см. VillagerEntityMixin#onFinalizeSpawn — 40% шанс безо всякой привязки к округу).
     * Раньше WallReputationTargetMixin/WallReputationGoal звали getWallAt(self) и для такого
     * "дикого" гарнизона/скаута всегда получали null — иммунитет друга/вражда по репутации
     * просто никогда не срабатывали. Соответствие веток стенам то же самое, что уже
     * используется для точек квестовых командиров: Полиция = Сина, Гарнизон = Роза,
     * Скауты = Мария.
     */
    public static Wall getWallForSoldier(Entity soldier) {
        if (soldier.getTags().contains("police_hunter")) return Wall.SINA;
        if (soldier.getTags().contains("garrison_soldier")) return Wall.ROSE;
        if (soldier.getTags().contains("scout_legion")) return Wall.MARIA;
        return null;
    }
}

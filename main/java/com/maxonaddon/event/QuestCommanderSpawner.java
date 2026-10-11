package com.maxonaddon.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class QuestCommanderSpawner {

    private static final BlockPos[] POLICE_POINTS = {
            new BlockPos(1318, 80, 10), new BlockPos(-1281, 87, 10),
            new BlockPos(18, 94, 1310), new BlockPos(18, 85, -1289)
    };

    private static final BlockPos[] GARRISON_POINTS = {
            new BlockPos(18, 95, 7310), new BlockPos(18, 83, -7289),
            new BlockPos(-7281, 93, 10), new BlockPos(7318, 91, 10)
    };

    private static final BlockPos[] SCOUT_POINTS = {
            new BlockPos(13318, 92, 10), new BlockPos(-13281, 80, 10),
            new BlockPos(18, 96, 13310), new BlockPos(18, 92, -13289)
    };

    /**
     * Все 12 точек разом — используется DeliveryQuestHandler, чтобы принудительно подгрузить
     * чанки всех командиров перед поиском случайного получателя. Без этого level.getEntities()
     * находит только того командира, у которого чанк и так загружен (игрок стоит рядом с ним) —
     * все остальные 11 точек за сотни/тысячи блоков давно выгружены, если игроки туда не ходили,
     * и случайный получатель getEntities() просто не мог найти никого, КРОМЕ исходного
     * командира — отсюда баг "компас указывает на того же, кто выдал квест".
     */
    public static final BlockPos[] ALL_POINTS = concat(POLICE_POINTS, GARRISON_POINTS, SCOUT_POINTS);

    /** Стена по индексу в ALL_POINTS (0-3 = Полиция/Сина, 4-7 = Гарнизон/Роза, 8-11 = Разведка/Мария) — тот же порядок, что и в concat() ниже. */
    public static com.maxonaddon.util.Wall wallForPointIndex(int index) {
        if (index < 4) return com.maxonaddon.util.Wall.SINA;
        if (index < 8) return com.maxonaddon.util.Wall.ROSE;
        return com.maxonaddon.util.Wall.MARIA;
    }

    /** Индекс в ALL_POINTS ближайшей (по горизонтали) к (x,z) точки. */
    public static int nearestPointIndex(double x, double z) {
        int best = 0;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < ALL_POINTS.length; i++) {
            double dx = x - ALL_POINTS[i].getX();
            double dz = z - ALL_POINTS[i].getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = i;
            }
        }
        return best;
    }

    private static BlockPos[] concat(BlockPos[]... arrays) {
        int total = 0;
        for (BlockPos[] arr : arrays) total += arr.length;
        BlockPos[] result = new BlockPos[total];
        int offset = 0;
        for (BlockPos[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ResourceKey<Level> paradisKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));
            ServerLevel level = server.getLevel(paradisKey);
            if (level == null) return;

            spawnForFaction(level, POLICE_POINTS, "trench_coat_military_police", com.maxonaddon.util.Wall.SINA);
            spawnForFaction(level, GARRISON_POINTS, "trench_coat_garrison", com.maxonaddon.util.Wall.ROSE);
            spawnForFaction(level, SCOUT_POINTS, "trench_coat_scout", com.maxonaddon.util.Wall.MARIA);
        });
    }

    private static void spawnForFaction(ServerLevel level, BlockPos[] points, String coatId, com.maxonaddon.util.Wall wall) {
        for (BlockPos originalPos : points) {
            level.getChunk(originalPos.getX() >> 4, originalPos.getZ() >> 4, ChunkStatus.FULL, true);
            BlockPos groundPos = findGround(level, originalPos);

            AABB box = new AABB(groundPos).inflate(10.0);
            List<Villager> existing = level.getEntitiesOfClass(Villager.class, box, v -> v.getTags().contains("quest_commander"));

            if (existing.isEmpty()) {
                Villager npc = EntityType.VILLAGER.create(level);
                if (npc != null) {
                    npc.setPos(groundPos.getX() + 0.5, groundPos.getY(), groundPos.getZ() + 0.5);
                    npc.setInvulnerable(true);
                    npc.setNoAi(true);
                    npc.setPersistenceRequired();

                    float yaw = (float) (Math.atan2(groundPos.getZ(), groundPos.getX()) * (180.0 / Math.PI)) - 90.0F;
                    npc.setYRot(yaw);
                    npc.yHeadRot = yaw;
                    npc.yBodyRot = yaw;

                    // Экипировка командира
                    ItemStack coat = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", coatId)));
                    ItemStack boots = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "odm_boots")));

                    npc.setItemSlot(EquipmentSlot.CHEST, coat);
                    npc.setItemSlot(EquipmentSlot.FEET, boots);

                    npc.addTag("quest_commander");
                    npc.addTag(wall.tag);
                    level.addFreshEntity(npc);
                }
            }
        }
    }

    private static BlockPos findGround(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos mut = startPos.mutable();
        while (mut.getY() < 320 && !level.getBlockState(mut).getCollisionShape(level, mut).isEmpty()) mut.move(Direction.UP);
        while (mut.getY() > -64 && level.getBlockState(mut.below()).getCollisionShape(level, mut.below()).isEmpty()) mut.move(Direction.DOWN);
        return mut.immutable();
    }
}
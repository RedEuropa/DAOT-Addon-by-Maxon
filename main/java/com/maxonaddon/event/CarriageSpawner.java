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

public class CarriageSpawner {

    // Твои координаты
    // Публичный доступ — используется DistrictHelper (полиция/контрабандисты определяют
    // "это город" по этим же 12 точкам, а не по (0,0), где реальных округов нет).
    public static final BlockPos[] SPAWN_POINTS = {
            new BlockPos(1295, 0, 13),
            new BlockPos(-1307, 0, 25),
            new BlockPos(-7, 0, 1331),
            new BlockPos(-9, 0, -1274),
            new BlockPos(0, 0, 7325),
            new BlockPos(0, 0, -7274),
            new BlockPos(-7299, 0, 25),
            new BlockPos(7299, 0, 25),
            new BlockPos(13326, 0, -7),
            new BlockPos(-13305, 0, 25),
            new BlockPos(0, 0, 13330),
            new BlockPos(0, 0, -13273),
    };

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ResourceKey<Level> paradisKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));
            ServerLevel level = server.getLevel(paradisKey);
            if (level == null) return;

            for (int i = 0; i < SPAWN_POINTS.length; i++) {
                BlockPos originalPos = SPAWN_POINTS[i];
                String cityId = "district_" + (i + 1);

                // ПРИНУДИТЕЛЬНО ЗАГРУЖАЕМ ЧАНК, ИНАЧЕ БУДЕТ ПУСТОТА
                level.getChunk(originalPos.getX() >> 4, originalPos.getZ() >> 4, ChunkStatus.FULL, true);

                // Ищем точную землю (пропускаем траву, воздух и вылезаем из блоков)
                BlockPos groundPos = findGround(level, originalPos);

                AABB box = new AABB(groundPos).inflate(10.0);
                List<Villager> existing = level.getEntitiesOfClass(Villager.class, box, v -> v.getTags().contains("carriage_driver"));

                if (existing.isEmpty()) {
                    spawnNPC(level, groundPos, cityId);
                }
            }
        });
    }

    // Умный поиск пола
    private static BlockPos findGround(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos mut = startPos.mutable();

        // Если стартовая точка внутри твердого блока (в земле), поднимаемся вверх
        while (mut.getY() < 320 && !level.getBlockState(mut).getCollisionShape(level, mut).isEmpty()) {
            mut.move(Direction.UP);
        }

        // Спускаемся вниз до ближайшего твердого блока (пропуская воздух, траву, цветы без коллизии)
        while (mut.getY() > -64 && level.getBlockState(mut.below()).getCollisionShape(level, mut.below()).isEmpty()) {
            mut.move(Direction.DOWN);
        }

        return mut.immutable();
    }

    private static void spawnNPC(ServerLevel level, BlockPos pos, String cityId) {
        Villager npc = EntityType.VILLAGER.create(level);
        if (npc != null) {
            // Ставим НПС ровно на найденный блок
            npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            npc.setInvulnerable(true);
            npc.setNoAi(true);
            npc.setPersistenceRequired();

            // Задаем поворот, чтобы он смотрел от центра города
            float yaw = (float) (Math.atan2(pos.getZ(), pos.getX()) * (180.0 / Math.PI)) - 90.0F;
            npc.setYRot(yaw);
            npc.yHeadRot = yaw;
            npc.yBodyRot = yaw;

            ItemStack cloak = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "royal_cloak")));
            npc.setItemSlot(EquipmentSlot.HEAD, cloak);

            npc.addTag("carriage_driver");
            npc.addTag("daot_city:" + cityId);

            level.addFreshEntity(npc);
            System.out.println("[MaxonAddon] Заспавнен " + cityId + " на " + pos);
        }
    }
}
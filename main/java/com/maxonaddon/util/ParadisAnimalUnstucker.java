package com.maxonaddon.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Чинит зависших в воздухе, неподвижных животных в измерении Paradis.
 *
 * Ранее WorldBoundaryMixin#onSpawnOriginalMobs спавнил декоративных животных прямо во время
 * генерации чанка через WorldGenRegion#addFreshEntity(MobSpawnType.CHUNK_GENERATION). Такие
 * сущности создавались вне обычного тика сервера и получались "мёртвыми": без гравитации,
 * без нормально работающего ИИ, накладывались друг на друга и зависали в воздухе там же, где
 * их изначально поставили (см. комментарий "ВРЕМЕННО ОТКЛЮЧЕНО" в WorldBoundaryMixin — сам
 * спавн уже выключен, но animalи, уже осевшие в старых сгенерированных чанках, никуда не делись).
 *
 * Этот класс не трогает причину (спавнер уже отключён), а лечит следствие: раз в CHECK_INTERVAL
 * проходит по загруженным сущностям Paradis, находит животных, которые не стоят на земле и
 * практически не двигаются дольше времени "осадки" после спавна, опускает их на ближайшую твёрдую
 * поверхность снизу и придаёт лёгкий импульс + сбрасывает зависшую навигацию, чтобы ИИ снова
 * начал работать.
 *
 * Фильтр по классу Animal НЕ трогает капитанов/перевозчиков квестов — это Villager-сущности
 * (AbstractVillager), а Animal и AbstractVillager — сестринские классы (оба наследуют AgeableMob
 * напрямую), то есть villager instanceof Animal всегда false.
 */
public final class ParadisAnimalUnstucker {

    /** Раз во сколько тиков проверяем (100 тиков = 5 секунд — не горит, лишний раз лазить по всем сущностям незачем). */
    private static final int CHECK_INTERVAL_TICKS = 100;
    /** Сколько тиков должно пройти с появления сущности, прежде чем её вообще рассматриваем (даём осесть после обычного спавна/загрузки чанка). */
    private static final int MIN_AGE_TICKS = 40;
    /** Если скорость выше этого порога — животное реально падает/идёт, а не зависло; не трогаем. */
    private static final double STUCK_VELOCITY_SQR_THRESHOLD = 0.0009;

    private static int tickCounter = 0;

    private ParadisAnimalUnstucker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < CHECK_INTERVAL_TICKS) return;
            tickCounter = 0;

            ResourceKey<Level> paradisKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("dannys-aot", "paradis"));
            ServerLevel paradisLevel = server.getLevel(paradisKey);
            if (paradisLevel == null) return;

            fixStuckAnimals(paradisLevel);
        });
    }

    private static void fixStuckAnimals(ServerLevel level) {
        // Очень широкая коробка безопасна и дёшева — getEntities и так фильтрует только уже
        // загруженные сущности, а не сканирует весь мир заново.
        AABB scanBox = new AABB(-30000, level.getMinBuildHeight(), -30000, 30000, level.getMaxBuildHeight(), 30000);

        List<Animal> animals = level.getEntities(EntityTypeTest.forClass(Animal.class), scanBox, animal -> true);

        for (Animal animal : animals) {
            if (animal.tickCount < MIN_AGE_TICKS) continue;
            if (animal.onGround()) continue;
            if (animal.getDeltaMovement().lengthSqr() > STUCK_VELOCITY_SQR_THRESHOLD) continue;

            BlockPos.MutableBlockPos cursor = animal.blockPosition().mutable();
            boolean foundGround = false;
            int minY = level.getMinBuildHeight();

            while (cursor.getY() > minY) {
                cursor.move(Direction.DOWN);
                if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                    foundGround = true;
                    break;
                }
            }
            if (!foundGround) continue;

            double groundY = cursor.getY() + 1.0;
            // Уже стоит (или почти стоит) на найденной земле — просто нужно разбудить, телепорт не нужен.
            if (Math.abs(animal.getY() - groundY) >= 0.1) {
                animal.teleportTo(animal.getX(), groundY, animal.getZ());
            }

            // Лёгкий случайный толчок, чтобы физика и навигация снова "зацепились".
            animal.setDeltaMovement(
                    (animal.getRandom().nextDouble() - 0.5) * 0.3,
                    0.05,
                    (animal.getRandom().nextDouble() - 0.5) * 0.3
            );
            animal.hurtMarked = true;

            if (animal instanceof Mob mob) {
                if (mob.isNoAi()) mob.setNoAi(false);
                mob.getNavigation().stop();
            }
        }
    }
}

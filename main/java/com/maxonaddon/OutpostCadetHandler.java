package com.maxonaddon;

import com.maxonaddon.item.MarleyanRifleItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Кадеты-стрелки на сторожевых вышках (см. PillagerOutpostGenerator): расхаживают по
 * балкону и бьют по глазам подходящих титанов.
 * <p>
 * Ходьбу ведёт CadetPatrolGoal, а стрельбу — этот обработчик. Разделение не от хорошей
 * жизни: кадет остаётся обычным Villager, и его брейн со своим расписанием утащил бы пост
 * куда угодно, поэтому брейну перекрывают цель ходьбы (см. VillagerEntityMixin), а
 * патрулирование берёт на себя наша задача.
 * <p>
 * Кадетов ищем вокруг игроков, а не перебором всех жителей мира: без игрока рядом чанк
 * выгружен, титанов там нет и стрелять всё равно не по кому.
 * <p>
 * Цель — сущность-глаз ("eye"), а не туша титана: по корпусу стрельба была бы холостой,
 * урон в daot засчитывается по уязвимым точкам (тот же принцип, что в ScoutOdmCombatGoal).
 * Попадание в глаз титана ослепляет и разворачивает — для прикрытия поста это ровно то,
 * что нужно.
 */
public final class OutpostCadetHandler {

    public static final String CADET_TAG = "outpost_cadet";

    /** Дальность огня кадета. */
    private static final double FIRE_RANGE = 40.0;
    /**
     * В каком радиусе вокруг игрока искать кадетов.
     * <p>
     * Это перебор ВСЕХ жителей в радиусе, а в городе их сотни — поэтому берём чуть больше
     * дальности огня, а не «с запасом». Вышки стоят вдали от округов, так что до города
     * этот скан всё равно не дотянется.
     */
    private static final double CADET_SEARCH_RANGE = 56.0;

    private static final float DAMAGE = 10.0F;
    /** Пауза между выстрелами: выстрел плюс перезарядка затвора. */
    private static final int SHOT_INTERVAL_TICKS = 50;
    /** Пауза после опустевшего магазина. */
    private static final int RELOAD_TICKS = 80;
    // Стрельба и так реже раза в две секунды, поэтому чаще, чем раз в полсекунды, перебирать
    // сущности незачем.
    private static final int CHECK_INTERVAL = 10;

    /** Игровое время, раньше которого кадет не стреляет. Ключ — UUID кадета. */
    private static final Map<UUID, Long> nextShotAt = new HashMap<>();

    private static int tickCounter = 0;

    private OutpostCadetHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(OutpostCadetHandler::tick);
    }

    /**
     * Ставит на крышу вооружённого кадета.
     *
     * @param groupTag общий тег вышки — по нему кадеты одного поста узнают друг друга
     *                 (задание у них общее, см. OutpostHuntQuestHandler)
     */
    public static void spawnCadet(ServerLevel level, BlockPos pos, String groupTag) {
        Villager cadet = EntityType.VILLAGER.create(level);
        if (cadet == null) return;

        cadet.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        cadet.setVillagerData(cadet.getVillagerData().setProfession(VillagerProfession.NITWIT));
        cadet.setPersistenceRequired();
        cadet.addTag(CADET_TAG);
        cadet.addTag(groupTag);

        cadet.setItemSlot(EquipmentSlot.CHEST, new ItemStack(daot.DannysAot.UNIFORM));
        cadet.setItemSlot(EquipmentSlot.FEET, new ItemStack(daot.DannysAot.ODM_BOOTS));

        ItemStack rifle = new ItemStack(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM);
        MarleyanRifleItem.setLoadedRounds(rifle, MarleyanRifleItem.MAX_LOADED_ROUNDS);
        cadet.setItemSlot(EquipmentSlot.MAINHAND, rifle);

        // Снаряжение с них не падает — иначе вышку выгоднее было бы вырезать, чем оборонять.
        cadet.setDropChance(EquipmentSlot.CHEST, 0.0F);
        cadet.setDropChance(EquipmentSlot.FEET, 0.0F);
        cadet.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        cadet.setYRot(level.getRandom().nextFloat() * 360.0F);
        level.addFreshEntity(cadet);
    }

    private static void tick(MinecraftServer server) {
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Мапа кулдаунов не должна расти вечно: кадеты гибнут, миры меняются.
        if (nextShotAt.size() > 512) nextShotAt.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;

            AABB box = player.getBoundingBox().inflate(CADET_SEARCH_RANGE);
            List<Villager> cadets = level.getEntitiesOfClass(Villager.class, box,
                    v -> v.isAlive() && v.getTags().contains(CADET_TAG));

            for (Villager cadet : cadets) {
                tickCadet(level, cadet);
            }
        }
    }

    private static void tickCadet(ServerLevel level, Villager cadet) {
        ItemStack rifle = cadet.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!rifle.is(MaxonDaotAddon.MARLEYAN_RIFLE_ITEM)) return;

        long now = level.getGameTime();
        Long readyAt = nextShotAt.get(cadet.getUUID());
        if (readyAt != null && now < readyAt) return;

        Entity eye = findEye(level, cadet);
        if (eye == null) return;

        // Разворачиваем корпус и голову к цели: патрульная задача крутит кадета по ходу
        // движения, и без доворота стрелок палил бы себе за спину.
        lookAt(cadet, eye);

        if (MarleyanRifleItem.getLoadedRounds(rifle) <= 0) {
            MarleyanRifleItem.setLoadedRounds(rifle, MarleyanRifleItem.MAX_LOADED_ROUNDS);
            playSound(level, cadet, com.maxonaddon.sound.ModSounds.RIFLE_FULL_RELOAD, 1.0F);
            nextShotAt.put(cadet.getUUID(), now + RELOAD_TICKS);
            return;
        }

        MarleyanRifleItem.setLoadedRounds(rifle, MarleyanRifleItem.getLoadedRounds(rifle) - 1);
        eye.hurt(level.damageSources().mobAttack(cadet), DAMAGE);

        Vec3 impact = eye.position().add(0.0, eye.getBbHeight() * 0.5, 0.0);
        playSound(level, cadet, com.maxonaddon.sound.ModSounds.RIFLE_SHOOT, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                impact.x, impact.y, impact.z, 3, 0.05, 0.05, 0.05, 0.01);

        nextShotAt.put(cadet.getUUID(), now + SHOT_INTERVAL_TICKS);
    }

    /** Ближайший глаз титана, до которого есть свободная линия огня. */
    private static Entity findEye(ServerLevel level, Villager cadet) {
        AABB box = cadet.getBoundingBox().inflate(FIRE_RANGE);
        List<Entity> around = level.getEntities(cadet, box, e -> {
            if (!e.isAlive()) return false;
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            return key != null && key.getPath().contains("eye");
        });

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity candidate : around) {
            double dist = cadet.distanceToSqr(candidate);
            if (dist >= bestDist) continue;
            if (!hasClearShot(level, cadet, candidate)) continue;
            bestDist = dist;
            best = candidate;
        }
        return best;
    }

    private static boolean hasClearShot(ServerLevel level, Villager cadet, Entity target) {
        Vec3 from = cadet.getEyePosition();
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cadet));
        return hit.getType() != HitResult.Type.BLOCK;
    }

    private static void lookAt(Villager cadet, Entity target) {
        double dx = target.getX() - cadet.getX();
        double dz = target.getZ() - cadet.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        cadet.setYRot(yaw);
        cadet.setYBodyRot(yaw);
        cadet.setYHeadRot(yaw);
    }

    private static void playSound(ServerLevel level, Villager cadet, SoundEvent sound, float volume) {
        level.playSound(null, cadet.getX(), cadet.getY(), cadet.getZ(),
                sound, SoundSource.HOSTILE, volume, 1.0F);
    }
}

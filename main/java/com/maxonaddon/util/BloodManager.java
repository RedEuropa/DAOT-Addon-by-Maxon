package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.network.SyncBloodPayload;
import daot.BladeItem;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Кровь титана на снаряжении: копится за убийства титанов КЛИНКОМ (dannys-aot:blade),
 * со временем испаряется обратно по одной стадии со звуком шипения и паром.
 *
 * Стадии: 0 — чисто, 1 — первое убийство, 2 — bloodStage2Kills убийств, 3 — bloodStage3Kills.
 * Куда рисуется кровь (плащ вместо скина и т.д.) решает уже клиент — см. BloodRenderHelper.
 */
public final class BloodManager {

    private BloodManager() {}

    /** true, если игрок держит в любой руке клинок из dannys-aot. */
    public static boolean isHoldingBlade(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof BladeItem
                || player.getOffhandItem().getItem() instanceof BladeItem;
    }

    /** Вызывается при убийстве титана. Кровь пачкает игрока только если убийство было клинком. */
    public static void onTitanKilledByBlade(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;

        int kills = stats.getBladeTitanKills() + 1;
        stats.setBladeTitanKills(kills);

        MaxonConfig config = MaxonConfig.get();
        int newStage;
        if (kills >= config.bloodStage3Kills) {
            newStage = 3;
        } else if (kills >= config.bloodStage2Kills) {
            newStage = 2;
        } else {
            newStage = 1;
        }

        // Каждое убийство заново запускает таймер высыхания.
        stats.setBloodDryTicks(config.bloodDrySeconds * 20);

        if (newStage != stats.getBloodStage()) {
            stats.setBloodStage(newStage);
            sync(player);
        }
    }

    /** Серверный тик: испарение крови по стадиям. */
    public static void serverTick(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        int stage = stats.getBloodStage();
        if (stage <= 0) return;

        int remaining = stats.getBloodDryTicks() - 1;
        if (remaining > 0) {
            stats.setBloodDryTicks(remaining);
            return;
        }

        // Стадия испарилась — откатываемся на одну ступень вниз.
        int newStage = stage - 1;
        stats.setBloodStage(newStage);

        // Счётчик убийств тоже откатываем к порогу новой стадии, чтобы следующее
        // убийство не перепрыгивало обратно на максимум.
        MaxonConfig config = MaxonConfig.get();
        int cappedKills = switch (newStage) {
            case 2 -> config.bloodStage3Kills - 1;
            case 1 -> config.bloodStage2Kills - 1;
            default -> 0;
        };
        stats.setBladeTitanKills(Math.min(stats.getBladeTitanKills(), Math.max(0, cappedKills)));

        stats.setBloodDryTicks(newStage > 0 ? config.bloodDrySeconds * 20 : 0);

        playEvaporationEffect(player);
        sync(player);
    }

    /**
     * Шипение + пар в том месте, где кровь исчезала: у клинка (если он в руках),
     * иначе у груди игрока.
     */
    private static void playEvaporationEffect(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        boolean fromBlade = isHoldingBlade(player);

        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.7;
        double z = player.getZ();

        if (fromBlade) {
            // Смещаем облачко пара к руке с клинком.
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x).normalize().scale(0.4);
            x += look.x * 0.6 + side.x;
            y = player.getY() + player.getBbHeight() * 0.6;
            z += look.z * 0.6 + side.z;
        }

        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 12, 0.15, 0.2, 0.15, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    /** Рассылает стадию крови всем, кто видит игрока, и ему самому. */
    public static void sync(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        SyncBloodPayload payload = new SyncBloodPayload(player.getId(), stats.getBloodStage());

        ServerPlayNetworking.send(player, payload);
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            if (viewer != player) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    /** true, если в слоте головы надет любой плащ (cloak) — тогда кровь идёт на плащ, а не на скин. */
    public static boolean hasCloak(net.minecraft.world.entity.LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty()) return false;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(head.getItem());
        return key != null && key.getPath().contains("cloak");
    }
}

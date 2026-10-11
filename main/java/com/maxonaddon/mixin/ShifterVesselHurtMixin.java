package com.maxonaddon.mixin;

import daot.AttackTitanEntity;
import daot.ArmoredTitanEntity;
import daot.FemaleTitanEntity;
import daot.BeastTitanEntity;
import daot.ColossalTitanEntity;
import daot.WarhammerTitanEntity;
import daot.DannysAot;
import daot.TitanPowerData;
import daot.TitanPowerType;
import daot.TitanEntity;
import com.maxonaddon.network.VillagerPreshiftPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.List;

@Mixin(LivingEntity.class)
public abstract class ShifterVesselHurtMixin {

    @Unique
    private int maxon$preshiftTimer = -1;

    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$onTickPreshift(CallbackInfo ci) {
        if (this.maxon$preshiftTimer > 0) {
            if ((Object) this instanceof Villager villager && !villager.level().isClientSide()) {

                villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0);
                ServerLevel serverLevel = (ServerLevel) villager.level();

                if (this.maxon$preshiftTimer % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, villager.getX(), villager.getY(), villager.getZ(), 2, 0.3, 0.3, 0.3, 0.05);
                }

                this.maxon$preshiftTimer--;

                if (this.maxon$preshiftTimer == 0) {
                    this.maxon$preshiftTimer = -1;
                    this.maxon$executeTransform(villager, serverLevel);
                }
            } else {
                this.maxon$preshiftTimer = -1;
            }
        }
    }

    @Unique
    private int maxon$storedTitanType = 1; // Сохраняем тип титана заранее

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void maxon$transformOnHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Villager villager) {
            if (!villager.level().isClientSide()) {

                boolean ruleAllowed = villager.level().getGameRules().getBoolean(com.maxonaddon.BerserkConfig.VILLAGER_SHIFTER_TRANSFORM);
                if (!ruleAllowed) return;

                com.maxonaddon.MaxonConfig config = com.maxonaddon.MaxonConfig.get();
                if (!config.enableVillagerShifterTransformation) return;

                if (villager.getTags().contains("weakened_shifter_vessel")) {
                    if (source.is(DamageTypes.FALL)) {
                        cir.setReturnValue(false);
                        return;
                    }
                    return;
                }

                if (villager.isPassenger()) {
                    Entity vehicle = villager.getVehicle();
                    if (vehicle != null && vehicle.getTags().contains("berserk_boss")) {
                        if (vehicle instanceof daot.ShifterTitan shifter && (shifter.isIncapacitated() || shifter.isDismounting())) {
                            Mob nape = null;
                            try {
                                java.lang.reflect.Method m = vehicle.getClass().getMethod("getNapeEntity");
                                nape = (Mob) m.invoke(vehicle);
                            } catch (Exception ignored) {}

                            if (nape != null && nape.isAlive()) {
                                nape.hurt(source, amount);
                            } else {
                                vehicle.hurt(source, amount);
                            }
                        }
                        cir.setReturnValue(false);
                        return;
                    }
                }

                if (villager.getTags().contains("is_transforming_shifter")) {
                    cir.setReturnValue(false);
                    return;
                }

                if (config.villagerTransformationChance < 100.0 && villager.getRandom().nextDouble() * 100.0 > config.villagerTransformationChance) {
                    return;
                }

                Entity attacker = source.getEntity();
                boolean isPlayerOrTitan = attacker instanceof Player || attacker instanceof Monster;
                if (!isPlayerOrTitan) return;

                ServerLevel serverLevel = (ServerLevel) villager.level();
                TitanPowerData powerData = TitanPowerData.get(serverLevel);
                TitanPowerType villagerPower = powerData.getPower(villager.getUUID());
                boolean hasPowerTag = villager.getTags().stream().anyMatch(tag -> tag.startsWith("titan_power_"));

                if (villagerPower != null || hasPowerTag) {
                    cir.setReturnValue(false);
                    villager.addTag("is_transforming_shifter");

                    // Определяем тип титана заранее, чтобы клиент знал, какой рев готовить
                    if (villagerPower != null) {
                        switch (villagerPower) {
                            case ARMORED -> this.maxon$storedTitanType = 2;
                            case FEMALE -> this.maxon$storedTitanType = 3;
                            case BEAST -> this.maxon$storedTitanType = 4;
                            case WARHAMMER -> this.maxon$storedTitanType = 5;
                            case COLOSSAL -> this.maxon$storedTitanType = 6;
                            default -> this.maxon$storedTitanType = 1;
                        }
                    }

                    // ОТПРАВЛЯЕМ ПАКЕТ СТАРТА (Искры, анимация укуса и звук грома/подготовки)
                    try {
                        VillagerPreshiftPayload payload = new VillagerPreshiftPayload(villager.getId());
                        for (ServerPlayer p : PlayerLookup.tracking(villager)) {
                            ServerPlayNetworking.send(p, payload);
                        }
                    } catch (Exception ignored) {}

                    this.maxon$preshiftTimer = 50; // 2.5 секунды таймер
                }
            }
        }
    }

    @Unique
    private void maxon$executeTransform(Villager villager, ServerLevel serverLevel) {
        villager.addTag("shifter_boss_vessel");

        TitanPowerData powerData = TitanPowerData.get(serverLevel);
        TitanPowerType villagerPower = powerData.getPower(villager.getUUID());

        Monster bossTitan;
        if (villagerPower != null) {
            switch (villagerPower) {
                case ARMORED -> bossTitan = new ArmoredTitanEntity(DannysAot.ARMORED_TITAN, serverLevel);
                case FEMALE -> bossTitan = new FemaleTitanEntity(DannysAot.FEMALE_TITAN, serverLevel);
                case BEAST -> bossTitan = new BeastTitanEntity(DannysAot.BEAST_TITAN, serverLevel);
                case COLOSSAL -> bossTitan = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, serverLevel);
                case WARHAMMER -> bossTitan = new WarhammerTitanEntity(DannysAot.WARHAMMER_TITAN, serverLevel);
                case JAW -> bossTitan = new daot.TestShifterTitanEntity(DannysAot.TEST_SHIFTER_TITAN, serverLevel); // Добавлен Челюсти[cite: 13]
                default -> bossTitan = new AttackTitanEntity(DannysAot.ATTACK_TITAN, serverLevel);
            }
        } else {
            if (villager.getTags().contains("titan_power_armored")) bossTitan = new ArmoredTitanEntity(DannysAot.ARMORED_TITAN, serverLevel);
            else if (villager.getTags().contains("titan_power_female")) bossTitan = new FemaleTitanEntity(DannysAot.FEMALE_TITAN, serverLevel);
            else if (villager.getTags().contains("titan_power_beast")) bossTitan = new BeastTitanEntity(DannysAot.BEAST_TITAN, serverLevel);
            else if (villager.getTags().contains("titan_power_colossal")) bossTitan = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, serverLevel);
            else if (villager.getTags().contains("titan_power_warhammer")) bossTitan = new WarhammerTitanEntity(DannysAot.WARHAMMER_TITAN, serverLevel);
            else if (villager.getTags().contains("titan_power_jaw")) bossTitan = new daot.TestShifterTitanEntity(DannysAot.TEST_SHIFTER_TITAN, serverLevel); // Добавлен Челюсти[cite: 13]
            else bossTitan = new AttackTitanEntity(DannysAot.ATTACK_TITAN, serverLevel);
        }

        bossTitan.setPos(villager.getX(), villager.getY(), villager.getZ());
        bossTitan.addTag("berserk_boss");
        serverLevel.addFreshEntity(bossTitan);

        if (bossTitan instanceof daot.WarhammerTitanEntity warhammer) {
            try {
                java.lang.reflect.Method spawnCrystal = warhammer.getClass().getMethod(
                        "spawnCrystalShell", double.class, double.class, double.class, java.util.UUID.class
                );
                spawnCrystal.invoke(warhammer, villager.getX(), villager.getY(), villager.getZ(), villager.getUUID());

                java.lang.reflect.Method getCrystal = warhammer.getClass().getMethod("getCrystalShellEntity");
                Entity crystal = (Entity) getCrystal.invoke(warhammer);

                if (crystal != null) {
                    villager.startRiding(crystal, true);
                } else {
                    villager.setPos(villager.getX(), villager.getY(), villager.getZ());
                }
            } catch (Exception e) {
                e.printStackTrace();
                villager.startRiding(bossTitan, true);
            }
        } else {
            // Стандартная посадка для остальных титанов[cite: 13]
            villager.startRiding(bossTitan, true);
        }

        // ВНИМАНИЕ: Дублирующийся блок с повторным startRiding(bossTitan, true) УДАЛЕН отсюда![cite: 13]

        villager.setInvisible(true);

        try {
            daot.DismountSmokeHelper.onShifterSpawn(bossTitan);
        } catch (Exception ignored) {}

        double centerX = bossTitan.getX();
        double centerY = bossTitan.getY() + 3.0D;
        double centerZ = bossTitan.getZ();

        boolean isColossal = bossTitan instanceof ColossalTitanEntity;
        boolean isGriefing = DannysAot.isTitanGriefingEnabled(serverLevel) && DannysAot.isShifterExplosionDamageEnabled(serverLevel);

        Level.ExplosionInteraction interaction = (isGriefing && !isColossal) ?
                Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;

        serverLevel.explode(bossTitan, centerX, centerY, centerZ, 24.0F, false, interaction);
        serverLevel.explode(bossTitan, centerX, centerY + 8.0D, centerZ, 16.0F, false, interaction);
        serverLevel.explode(bossTitan, centerX, centerY + 15.0D, centerZ, 12.0F, false, interaction);

        // Определяем точный тип титана для правильного звука рева на клиенте
        int titanType = 1; // Атакующий по умолчанию
        if (bossTitan instanceof ArmoredTitanEntity) titanType = 2;
        else if (bossTitan instanceof FemaleTitanEntity) titanType = 3;
        else if (bossTitan instanceof BeastTitanEntity) titanType = 4;
        else if (bossTitan instanceof WarhammerTitanEntity) titanType = 5;
        else if (bossTitan instanceof ColossalTitanEntity) titanType = 6;

        // Отправляем пакет ВЗРЫВА (молния + звук + рев) ВСЕМ игрокам поблизости РОВНО ОДИН РАЗ
        try {
            com.maxonaddon.network.VillagerTransformPayload transformPayload =
                    new com.maxonaddon.network.VillagerTransformPayload(bossTitan.getId(), this.maxon$storedTitanType, centerX, centerY, centerZ);
            for (ServerPlayer p : PlayerLookup.tracking(bossTitan)) {
                ServerPlayNetworking.send(p, transformPayload);
            }
        } catch (Exception ignored) {}

        double maxRange = 20.0D;
        AABB explosionBox = bossTitan.getBoundingBox().inflate(maxRange);
        List<Entity> entitiesInRadius = serverLevel.getEntities(bossTitan, explosionBox);
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.setPos(centerX, centerY, centerZ);
            lightning.setVisualOnly(true); // Делаем молнию визуальной, чтобы она не убивала игроков и не поджигала мир
            serverLevel.addFreshEntity(lightning);
        }
        // Добавление красивых жёлтых искр и вспышки молнии в точке трансформации
        serverLevel.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                centerX, centerY, centerZ,
                50, // количество частиц
                1.5, 3.0, 1.5, // разброс по осям X, Y, Z
                0.5 // скорость
        );
        for (Entity target : entitiesInRadius) {
            if (target != bossTitan && target != villager && target.getVehicle() != bossTitan) {

                if (target.getClass().getName().contains("Titan") || target.getClass().getName().contains("Nape") || target.getClass().getName().contains("Eye")) {
                    continue;
                }

                Vec3 toEntity = target.position().subtract(bossTitan.position());
                double distance = toEntity.length();

                if (distance > 0.0D && distance < maxRange) {
                    double linearIntensity = (maxRange - distance) / maxRange;

                    if (target instanceof LivingEntity livingTarget) {
                        float damage = (float) (15.0F * linearIntensity);
                        if (livingTarget instanceof Player) {
                            damage *= 0.3F;
                        }
                        livingTarget.hurt(serverLevel.damageSources().explosion(bossTitan, bossTitan), damage);
                    }

                    double knockbackStrength = linearIntensity * 4.0D;
                    double upwardStrength = linearIntensity * 1.5D;
                    Vec3 knockback = toEntity.normalize().scale(knockbackStrength).add(0.0D, upwardStrength, 0.0D);

                    target.setDeltaMovement(target.getDeltaMovement().add(knockback));

                    if (target instanceof LivingEntity livingTarget) {
                        livingTarget.hurtMarked = true;
                        if (!(livingTarget instanceof Player) && distance < 12.0D) {
                            livingTarget.setRemainingFireTicks(100);
                        }
                    }
                }
            }
        }
    }
}
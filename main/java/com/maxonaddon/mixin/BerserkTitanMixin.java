package com.maxonaddon.mixin;

import com.maxonaddon.BerserkBossHandler;
import com.maxonaddon.ai.TitanBerserkMeleeGoal;
import daot.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.maxonaddon.BerserkConfig;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
        AttackTitanEntity.class, ArmoredTitanEntity.class, FemaleTitanEntity.class,
        BeastTitanEntity.class, ColossalTitanEntity.class, WarhammerTitanEntity.class
})
public abstract class BerserkTitanMixin extends Monster {

    @Unique private ServerBossEvent maxon$bossBar;
    @Unique private int maxon$berserkDeathTimer = 0;
    @Unique private boolean maxon$berserkIsDead = false;
    @Unique private int maxon$abilityCooldown = 0;

    protected BerserkTitanMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void maxon$onRegisterGoals(CallbackInfo ci) {

        if ((Object) this instanceof daot.ColossalTitanEntity colossal) {
            this.goalSelector.addGoal(0, new com.maxonaddon.ai.RumblingWalkGoal(colossal) {
                @Override
                public boolean canUse() {
                    if (!colossal.getTags().contains("rumbling_boss")) return false;
                    return super.canUse();
                }
                @Override
                public boolean canContinueToUse() {
                    if (!colossal.getTags().contains("rumbling_boss")) return false;
                    return super.canContinueToUse();
                }
            });
        }

        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
        });

        this.goalSelector.addGoal(1, new TitanBerserkMeleeGoal(this));

        this.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 1.0D) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canContinueToUse();
            }
        });

        HurtByTargetGoal hurtGoal = new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canContinueToUse();
            }
        };
        hurtGoal.setAlertOthers();
        this.targetSelector.addGoal(1, hurtGoal);

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, true, entity -> {
            if (entity instanceof Player p) {
                String ownerTag = "berserk_owner_" + p.getUUID();
                if (daot.TranslucentTracker.isToggled(p.getUUID())) return false;
                return !BerserkTitanMixin.this.hasPassenger(p) && !BerserkTitanMixin.this.getTags().contains(ownerTag) && !p.isCreative() && !p.isSpectator();
            }
            return false;
        }) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canContinueToUse();
            }
        });

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<LivingEntity>(this, LivingEntity.class, true, e -> {
            return e instanceof daot.ShifterTitan && e != BerserkTitanMixin.this;
        }) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canContinueToUse();
            }
        });

        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<LivingEntity>(this, LivingEntity.class, true, e -> {
            if (e instanceof daot.ShifterTitan) return false;
            String name = e.getClass().getName();
            return name.startsWith("daot.") && name.contains("Titan") && e != BerserkTitanMixin.this;
        }) {
            @Override
            public boolean canUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                if (!BerserkTitanMixin.this.getTags().contains("berserk_boss")) return false;
                return super.canContinueToUse();
            }
        });
    }

    @Unique
    private void maxon$restoreBerserkState() {
        if (this.level().isClientSide()) return;

        for (ServerPlayer player : ((ServerLevel) this.level()).players()) {
            if (this.getTags().contains("berserk_owner_" + player.getUUID())) {
                BerserkBossHandler.setTitanUUID(this, player.getUUID());
                daot.TranslucentTracker.setToggled(player, false);
                daot.TranslucentTracker.clear(player);

                if (!player.getTags().contains("has_shifted_before")) {
                    player.addTag("has_shifted_before");
                    player.displayClientMessage(Component.translatable("message.maxonaddon.berserk_restored"), true);
                }

                this.removeTag("berserk_boss");
                break;
            }
        }
    }

    @Unique
    private void maxon$triggerTitanAbilities() {
        if (this.getTarget() == null || this.distanceTo(this.getTarget()) > 20.0D) return;

        if (this.maxon$abilityCooldown > 0) {
            this.maxon$abilityCooldown--;
            return;
        }

        if (this.random.nextInt(40) == 0) {
            try {
                Object self = (Object) this;
                if (self instanceof AttackTitanEntity attackTitan) {
                    attackTitan.triggerAbility(this.random.nextBoolean() ? 1 : 2);
                    this.maxon$abilityCooldown = 60;
                } else if (self instanceof ArmoredTitanEntity armored) {
                    armored.triggerAbility(1);
                    this.maxon$abilityCooldown = 80;
                } else if (self instanceof FemaleTitanEntity female) {
                    if (this.random.nextInt(3) == 0) {
                        female.getClass().getMethod("triggerRoar").invoke(female);
                        this.maxon$abilityCooldown = 200;
                    } else {
                        female.triggerAbility(1);
                        this.maxon$abilityCooldown = 50;
                    }
                } else if (self instanceof BeastTitanEntity beast) {
                    beast.getClass().getMethod("triggerRoar").invoke(beast);
                    this.maxon$abilityCooldown = 300;
                } else if (self instanceof WarhammerTitanEntity warhammer) {
                    int roll = this.random.nextInt(3);
                    if (roll == 0) {
                        warhammer.getClass().getMethod("triggerSpikeField").invoke(warhammer);
                        this.maxon$abilityCooldown = 150;
                    } else if (roll == 1) {
                        warhammer.triggerAbility(2);
                        this.maxon$abilityCooldown = 80;
                    } else {
                        warhammer.triggerAbility(1);
                        this.maxon$abilityCooldown = 60;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void maxon$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (this.maxon$bossBar != null) {
            this.maxon$bossBar.removeAllPlayers();
            this.maxon$bossBar.setVisible(false);
            this.maxon$bossBar = null;
        }
    }

    // ========================================================
    // ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ ВЫСАДКИ ЖИТЕЛЯ
    // ========================================================
    @Unique
    private void maxon$ejectAndWeakenVillagers() {
        java.util.List<Entity> vesselsToEject = new java.util.ArrayList<>();

        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("shifter_boss_vessel")) {
                vesselsToEject.add(villager);
            }
        }

        if ((Object) this instanceof daot.WarhammerTitanEntity warhammer) {
            try {
                java.lang.reflect.Method getCrystal = warhammer.getClass().getMethod("getCrystalShellEntity");
                Entity crystal = (Entity) getCrystal.invoke(warhammer);
                if (crystal != null) {
                    for (Entity passenger : crystal.getPassengers()) {
                        if (passenger instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("shifter_boss_vessel")) {
                            vesselsToEject.add(villager);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Entity entity : vesselsToEject) {
            net.minecraft.world.entity.npc.Villager villager = (net.minecraft.world.entity.npc.Villager) entity;
            villager.removeTag("shifter_boss_vessel");
            villager.addTag("weakened_shifter_vessel");
            villager.removeTag("is_transforming_shifter");

            // ВАЖНО: Никаких setInvisible(false) здесь нет!
            // Житель сидит невидимым в шее всё время, пока проигрывается анимация смерти.
            if (villager instanceof com.maxonaddon.util.VillagerSkinAccessor accessor) {
                accessor.maxon$setTransformTimer(6000);
            }
        }
    }

    // ========================================================
    // ИСТИННАЯ СМЕРТЬ ТИТАНА
    // ========================================================
    @Inject(method = "die", at = @At("HEAD"))
    private void maxon$onDie(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        if (!this.level().isClientSide()
                && (this.getTags().contains("berserk_boss") || this.maxon$berserkIsDead)) {
            if (!this.maxon$berserkIsDead) {
                this.maxon$restoreBerserkState();
                this.maxon$ejectAndWeakenVillagers();
                // УБРАНО this.ejectPassengers();
            }
            this.maxon$berserkIsDead = true;
        }
    }

    // ========================================================
    // ТИК: ДИНАМИЧЕСКИЙ ИНВИЗ И ИСЦЕЛЕНИЕ
    // ========================================================
    @Inject(method = "tick", at = @At("HEAD"))
    private void maxon$onTickBerserkHead(CallbackInfo ci) {
        if (!this.level().isClientSide() && this.getTags().contains("berserk_boss")) {

            if ((Object) this instanceof daot.WarhammerTitanEntity warhammer) {
                for (Entity passenger : this.getPassengers()) {
                    if (passenger instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("shifter_boss_vessel")) {
                        try {
                            java.lang.reflect.Method getCrystal = warhammer.getClass().getMethod("getCrystalShellEntity");
                            Entity crystal = (Entity) getCrystal.invoke(warhammer);

                            if (crystal == null) {
                                java.lang.reflect.Method spawnCrystal = warhammer.getClass().getMethod(
                                        "spawnCrystalShell", double.class, double.class, double.class, java.util.UUID.class
                                );
                                spawnCrystal.invoke(warhammer, this.getX(), this.getY(), this.getZ(), villager.getUUID());
                                crystal = (Entity) getCrystal.invoke(warhammer);
                            }

                            if (crystal != null) {
                                villager.stopRiding();
                                villager.startRiding(crystal, true);
                                villager.setInvisible(true);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            Entity vessel = null;
            if ((Object) this instanceof daot.WarhammerTitanEntity warhammer) {
                try {
                    java.lang.reflect.Method getCrystal = warhammer.getClass().getMethod("getCrystalShellEntity");
                    Entity crystal = (Entity) getCrystal.invoke(warhammer);
                    if (crystal != null && !crystal.getPassengers().isEmpty()) {
                        vessel = crystal.getPassengers().get(0);
                    }
                } catch (Exception ignored) {}
            }

            if (vessel == null) {
                for (Entity passenger : this.getPassengers()) {
                    if (passenger.getTags().contains("shifter_boss_vessel")) {
                        vessel = passenger;
                        break;
                    }
                }
            }

            if (vessel instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("shifter_boss_vessel")) {
                if (villager.getHealth() < villager.getMaxHealth()) {
                    villager.setHealth(villager.getMaxHealth());
                }
            }

            if (this.getHealth() <= 0.0F || this.maxon$berserkIsDead) {
                if (!this.maxon$berserkIsDead) {
                    this.maxon$restoreBerserkState();
                    this.maxon$ejectAndWeakenVillagers();
                }
                this.maxon$berserkIsDead = true;
                this.setTarget(null);
                this.getNavigation().stop();
                this.setSprinting(false);
                this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            }
        }
    }

    @Inject(method = "onPlayerShift", at = @At("HEAD"), cancellable = true)
    private void maxon$preventPlayerShift(CallbackInfo ci) {
        if (this.getTags().contains("berserk_boss")) {
            ci.cancel();
        }
    }

    @Inject(method = "startDismounting", at = @At("HEAD"), cancellable = true)
    private void maxon$preventTitanShiftDismount(CallbackInfo ci) {
        if (this.getTags().contains("berserk_boss")) {
            ci.cancel();
        }
    }

    @Inject(method = "isDismountAllowed", at = @At("HEAD"), cancellable = true)
    private void maxon$preventDismountCheck(CallbackInfoReturnable<Boolean> cir) {
        if (this.getTags().contains("berserk_boss")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "removePassenger", at = @At("HEAD"), cancellable = true)
    private void maxon$preventVanillaDismount(Entity passenger, CallbackInfo ci) {
        if (this.getTags().contains("berserk_boss") && passenger instanceof Player) {
            if (!this.maxon$berserkIsDead) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void maxon$onTickBerserkTail(CallbackInfo ci) {
        // Тег сняли, а титан жив — значит игрок вырвался через QTE (см. BerserkEscapeQteHandler).
        // Убрать полоску нужно именно здесь: весь код ниже, включая ветку её очистки, лежит
        // внутри проверки на "berserk_boss", и без тега до него уже не дойти — полоска так и
        // висела бы над экраном до самой смерти титана.
        if (!this.level().isClientSide() && !this.getTags().contains("berserk_boss") && this.maxon$bossBar != null) {
            this.maxon$bossBar.removeAllPlayers();
            this.maxon$bossBar.setVisible(false);
            this.maxon$bossBar = null;
        }

        if (!this.level().isClientSide() && this.getTags().contains("berserk_boss")) {

            boolean showBossBar = this.level().getGameRules().getBoolean(BerserkConfig.BERSERK_TITAN_BAR);

            // ПОКАЗЫВАЕМ БОССБАР: только если включено правило И титан ЖИВ
            if (showBossBar && !this.maxon$berserkIsDead) {
                if (this.maxon$bossBar == null) {
                    net.minecraft.network.chat.Component bossName = this.getDisplayName();

                    if ((Object) this instanceof daot.TestShifterTitanEntity) {
                        bossName = net.minecraft.network.chat.Component.translatable("entity.maxonaddon.jaw_titan");
                    }

                    this.maxon$bossBar = new ServerBossEvent(
                            net.minecraft.network.chat.Component.translatable("bossbar.maxonaddon.berserk", bossName).withStyle(net.minecraft.ChatFormatting.RED),
                            BossEvent.BossBarColor.RED,
                            BossEvent.BossBarOverlay.PROGRESS
                    );
                    this.maxon$bossBar.setVisible(true);
                }

                // Обновляем здоровье
                this.maxon$bossBar.setProgress(this.getHealth() / this.getMaxHealth());

                // 1. ДОБАВЛЯЕМ игроков, которые подошли близко (64 блока)
                for (ServerPlayer player : this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(64.0D))) {
                    if (!this.maxon$bossBar.getPlayers().contains(player)) {
                        this.maxon$bossBar.addPlayer(player);
                    }
                }

                // 2. УДАЛЯЕМ игроков: проверяем сам список боссбара.
                // Так мы точно поймаем тех, кто резко улетел, умер или телепортировался
                java.util.List<ServerPlayer> playersToRemove = new java.util.ArrayList<>();
                for (ServerPlayer player : this.maxon$bossBar.getPlayers()) {
                    if (player.distanceTo(this) > 64.0D || !player.isAlive() || player.level() != this.level()) {
                        playersToRemove.add(player);
                    }
                }
                // Безопасно удаляем найденных
                for (ServerPlayer player : playersToRemove) {
                    this.maxon$bossBar.removePlayer(player);
                }

            } else {
                // УБИРАЕМ БОССБАР СРАЗУ: если правило выключили ИЛИ если титан мертв
                if (this.maxon$bossBar != null) {
                    this.maxon$bossBar.removeAllPlayers();
                    this.maxon$bossBar.setVisible(false);
                    this.maxon$bossBar = null;
                }
            }

            // Способности работают только у живого титана
            if (!this.maxon$berserkIsDead) {
                this.maxon$triggerTitanAbilities();
            }

            if (this.maxon$berserkIsDead) {
                this.maxon$berserkDeathTimer++;

                // === КОНЕЦ АНИМАЦИИ: 60 ТИКОВ ===
                if (this.maxon$berserkDeathTimer >= 60) {
                    // (Удаление боссбара отсюда убрано, так как теперь он пропадает в момент смерти)

                    // НАДЕЖНЫЙ ПОИСК (не по радиусу, а прямо из списка пассажиров титана)
                    java.util.List<Entity> vesselsToDrop = new java.util.ArrayList<>();

                    for (Entity passenger : this.getPassengers()) {
                        if (passenger instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("weakened_shifter_vessel")) {
                            vesselsToDrop.add(villager);
                        }
                    }

                    // Если это Молотобоец, проверяем кристалл
                    if ((Object) this instanceof daot.WarhammerTitanEntity warhammer) {
                        try {
                            java.lang.reflect.Method getCrystal = warhammer.getClass().getMethod("getCrystalShellEntity");
                            Entity crystal = (Entity) getCrystal.invoke(warhammer);
                            if (crystal != null) {
                                for (Entity passenger : crystal.getPassengers()) {
                                    if (passenger instanceof net.minecraft.world.entity.npc.Villager villager && villager.getTags().contains("weakened_shifter_vessel")) {
                                        vesselsToDrop.add(villager);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    // ПРИМЕНЯЕМ СТАТУСЫ К НАЙДЕННЫМ ЖИТЕЛЯМ
                    for (Entity entity : vesselsToDrop) {
                        net.minecraft.world.entity.npc.Villager villager = (net.minecraft.world.entity.npc.Villager) entity;
                        villager.stopRiding();

                        // Делаем видимым
                        villager.setInvisible(false);
                        villager.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);

                        // ВЫКЛЮЧАЕМ НЕВЕСОМОСТЬ (Это то, из-за чего он не падал!)
                        villager.setNoGravity(false);

                        // ВЫКЛЮЧАЕМ ИИ, чтобы он лежал смирно и чтобы сработал наш гравитационный миксин
                        villager.setNoAi(true);

                        villager.onSyncedDataUpdated(net.minecraft.world.entity.Entity.DATA_SHARED_FLAGS_ID);
                    }

                    this.ejectPassengers();
                    this.discard();
                }
                return; // Возврат, чтобы мертвый титан не атаковал
            }

            // ... логика урона титанам-союзникам ...
            if (this.tickCount % 10 == 0) {
                AABB searchBox = this.getBoundingBox().inflate(64.0D);
                AABB meleeBox = this.getBoundingBox().inflate(3.5D);

                for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
                    if (entity == this || this.hasPassenger(entity) || entity instanceof ShifterTitan) continue;

                    String name = entity.getClass().getName();
                    if (name.startsWith("daot.") && name.contains("Titan")) {
                        if (entity instanceof Mob pureTitan) {
                            if (pureTitan.getTarget() == null || pureTitan.getTarget() instanceof Player) {
                                pureTitan.setTarget(this);
                            }
                            if (meleeBox.intersects(pureTitan.getBoundingBox())) {
                                pureTitan.hurt(this.damageSources().mobAttack(this), 15.0F);
                            }
                        }
                    }
                }
            }
        }
    }
}
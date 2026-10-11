package com.maxonaddon.mixin;

import com.maxonaddon.network.LegCutSyncPayload;
import com.maxonaddon.util.LegCutState;
import daot.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import daot.ArmoredTitanEntity;

@Mixin(value = {
        TitanEntity.class, FritzTitanEntity.class, OgreTitanEntity.class, YellowTitanEntity.class,TitanEntity.class, ArmoredTitanEntity.class
})
public class TitanLegWeakPointMixin implements LegCutState {

    @Unique
    private static final RawAnimation dassodm$LEG_CUT_ANIM = RawAnimation.begin().thenPlayAndHold("legcut");
    @Unique
    private static final RawAnimation dassodm$LEG_REGEN_ANIM = RawAnimation.begin().thenPlay("legcut_regen");

    @Unique
    private boolean dassodm$isLegCut = false;
    @Unique
    private int dassodm$legCutTimer = 0;

    @Override
    public boolean isLegCut() {
        return this.dassodm$isLegCut;
    }
    @Override
    public int getLegCutTimer() {
        return this.dassodm$legCutTimer;
    }

    @Override
    public void setLegCut(boolean value) {
        this.dassodm$isLegCut = value;
        Entity target = (Entity) (Object) this;

        if (value) {
            this.dassodm$legCutTimer = 100; // 5 секунд стана
        } else {
            this.dassodm$legCutTimer = 0;

            // Если стан закончился, возвращаем Огру его родную скорость ходьбы
            if (target instanceof OgreTitanEntity mob) {
                var speedAttribute = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    speedAttribute.setBaseValue(0.27D); // Дефолтная скорость погони/ходьбы Огра
                }
            }
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void onLegCut(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity target = (Entity) (Object) this;
        if (target.level().isClientSide()) return;

        // БЛОКИРОВКА СПАМА: Если ноги УЖЕ подрезаны, выходим!
        if (this.dassodm$isLegCut) return;

        if (target instanceof ArmoredTitanEntity armored) {
            // Если броня на ногах НЕ разбита (скидки нет), подрезать нельзя
            if (!armored.isLegsShattered()) {
                return;
            }
        }

        if (source.getEntity() instanceof Player player) {
            Item mainWeapon = player.getMainHandItem().getItem();
            Item offWeapon = player.getOffhandItem().getItem();

            boolean isVanillaSword = mainWeapon instanceof SwordItem || offWeapon instanceof SwordItem;
            boolean isModBlade = mainWeapon instanceof BladeItem || mainWeapon instanceof BladeComponentItem ||
                    offWeapon instanceof BladeItem || offWeapon instanceof BladeComponentItem;

            if (isVanillaSword || isModBlade) {
                double titanBottom = target.getY();
                boolean isOgre = target instanceof OgreTitanEntity;

                // Для огра делаем порог выше, для остальных стандартные 40%
                double legHeightLimit = isOgre ? target.getBbHeight() * 0.50 : target.getBbHeight() * 0.40;
                double bottomOffset = isOgre ? 3.0 : 2.0;
                double playerY = player.getY();

                if (playerY >= titanBottom - bottomOffset && playerY <= titanBottom + legHeightLimit) {
                    try {
                        this.setLegCut(true);

                        if (target.level() instanceof ServerLevel serverLevel) {
                            for (ServerPlayer tracker : PlayerLookup.tracking(target)) {
                                LegCutSyncPayload.send(tracker, target.getId(), true);
                            }

                            // 1. Партиклы крови
                            BlockParticleOption bloodParticle = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
                            serverLevel.sendParticles(bloodParticle, target.getX(), titanBottom + 1.0, target.getZ(), 60, 0.7, 0.5, 0.7, 0.15);

                            // 2. Партиклы гравия (пыль от падения туши)
                            BlockParticleOption gravelParticle = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState());
                            // Спавним их пошире (разлет 1.5 блока) и у самой земли
                            serverLevel.sendParticles(gravelParticle, target.getX(), titanBottom + 0.2, target.getZ(), 100, 1.5, 0.5, 1.5, 0.15);
                        }

                        // Стан и остановка для всех (на всякий случай глушим дефолтный ИИ)
                        if (target instanceof net.minecraft.world.entity.Mob mob) {
                            mob.getNavigation().stop();
                            mob.setTarget(null);
                        }
                        target.setDeltaMovement(0, target.getDeltaMovement().y, 0);

                        // Звук среза мечом
                        player.level().playSound(null, target.getX(), titanBottom, target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0F, 1.0F);

                        // Звук падения (гравий) с низким питчем (0.6F) для эффекта массивного удара о землю
                        player.level().playSound(null, target.getX(), titanBottom, target.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 3.0F, 0.6F);

                        player.displayClientMessage(Component.translatable("message.maxonaddon.leg_cut.stunned"), true);

                    } catch (Exception e) {
                        System.err.println("Ошибка при подрезе ног: " + e.getMessage());
                    }
                }
            }
        }
    }    @Inject(method = "tick", at = @At("TAIL"))
    private void onTitanTick(CallbackInfo ci) {
        Entity target = (Entity) (Object) this;
        boolean isOgre = target instanceof OgreTitanEntity;

        if (this.dassodm$isLegCut) {
            if (this.dassodm$legCutTimer > 0) {
                this.dassodm$legCutTimer--;

                // 1. Спавним пар у ног во время подреза
                if (!target.level().isClientSide() && target.level() instanceof ServerLevel serverLevel) {
                    if (target.tickCount % 3 == 0) {
                        serverLevel.sendParticles(
                                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                target.getX(), target.getY() + 0.3, target.getZ(),
                                5, 0.8, 0.2, 0.8, 0.02
                        );
                    }
                }

                // 2. Если это Огр — зануляем ему атрибут скорости и навигацию
                if (isOgre && target instanceof net.minecraft.world.entity.Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);

                    var speedAttribute = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                    if (speedAttribute != null) {
                        speedAttribute.setBaseValue(0.0D); // ВЫКЛЮЧАЕМ СКОРОСТЬ
                    }
                    target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
                }
            } else {
                // Время стана вышло — снимаем подрез
                this.setLegCut(false);
                if (!target.level().isClientSide() && target.level() instanceof ServerLevel) {
                    for (ServerPlayer tracker : PlayerLookup.tracking(target)) {
                        LegCutSyncPayload.send(tracker, target.getId(), false);
                    }
                }
            }
        }
    }

    @Inject(method = "registerControllers", at = @At("TAIL"), remap = false, require = 0)
    private void addLegCutController(AnimatableManager.ControllerRegistrar controllers, CallbackInfo ci) {
        if (!(this instanceof GeoEntity geoEntity)) return;

        controllers.add(new AnimationController<>(geoEntity, "legcut_controller", 5, state -> {
            if (this.dassodm$isLegCut) {
                // Если таймер подходит к концу (например, осталось меньше секунды), играем регенерацию
                if (this.dassodm$legCutTimer <= 20 && this.dassodm$legCutTimer > 0) {
                    return state.setAndContinue(dassodm$LEG_REGEN_ANIM);
                } else {
                    return state.setAndContinue(dassodm$LEG_CUT_ANIM);
                }
            }
            return PlayState.STOP;
        }));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveLegCut(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("IsLegCut", this.dassodm$isLegCut);
        tag.putInt("LegCutTimer", this.dassodm$legCutTimer);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void loadLegCut(CompoundTag tag, CallbackInfo ci) {
        this.dassodm$isLegCut = tag.getBoolean("IsLegCut");
        this.dassodm$legCutTimer = tag.getInt("LegCutTimer");
    }
}
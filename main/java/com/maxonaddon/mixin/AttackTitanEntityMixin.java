package com.maxonaddon.mixin;

import com.maxonaddon.entity.TitanBoulderEntity;
import daot.AttackTitanEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

@SuppressWarnings({"resource", "unused"})
@Mixin(AttackTitanEntity.class)
public abstract class AttackTitanEntityMixin extends Monster {

    protected AttackTitanEntityMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract void setGrabbedEntityId(int id);
    @Shadow public abstract int getGrabbedEntityId();
    @Shadow public abstract void setGrabPhase(int phase);
    @Shadow public abstract int getGrabPhase();
    @Shadow public abstract boolean isDefeated();
    @Shadow public abstract boolean isGrabbing();
    @Shadow public abstract boolean isThrowing();
    @Shadow public abstract boolean isEating();
    @Shadow public abstract void releaseGrabbedEntity();
    @Shadow public abstract void startThrow();
    @Shadow public abstract void startEat();

    @Shadow public abstract void setTitanAttacking(boolean attacking);
    @Shadow public abstract boolean isTitanAttacking();
    @Shadow public abstract void setAttackNumber(int number);
    @Shadow public abstract int getAttackNumber();
    @Shadow private int attackAnimationTicks;
    @Shadow private int attackCooldown;

    @Inject(method = "tryGrabNearest", at = @At("HEAD"), cancellable = true)
    private void onTryGrabNearestBoulder(CallbackInfo ci) {
        AABB searchBox = this.getBoundingBox().inflate(6.0D);
        List<TitanBoulderEntity> boulders = this.level().getEntitiesOfClass(TitanBoulderEntity.class, searchBox);

        if (!boulders.isEmpty()) {
            TitanBoulderEntity targetBoulder = boulders.getFirst();
            this.setGrabbedEntityId(targetBoulder.getId());
            this.setGrabPhase(1);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void syncBoulderPositionToCenter(CallbackInfo ci) {
        int grabbedId = this.getGrabbedEntityId();
        if (grabbedId != -1) {
            Entity grabbed = this.level().getEntity(grabbedId);
            if (grabbed instanceof TitanBoulderEntity boulder) {

                float yaw = (float)Math.toRadians(this.getYRot());
                double forwardX = -Math.sin(yaw);
                double forwardZ = Math.cos(yaw);

                if (this.isTitanAttacking() && this.getAttackNumber() == 99) {
                    if (this.attackAnimationTicks <= 1) {
                        // 1. Отпускаем камень
                        this.releaseGrabbedEntity();

                        double dropX = this.getX() + forwardX * 5.0D;
                        double dropZ = this.getZ() + forwardZ * 5.0D;

                        boulder.setPos(dropX, this.getY() + 4.0D, dropZ);
                        boulder.setNoGravity(false);

                        this.setTitanAttacking(false);
                        this.setAttackNumber(0);
                    } else {
                        // 2. Плавное опускание: с 19.5 вниз до 4.0 (разница 15.5 блоков)
                        float progress = (20.0F - this.attackAnimationTicks) / 20.0F;

                        double currentYOffset = 19.5D - (15.5D * progress);
                        double currentDist = 1.5D + (3.5D * progress);

                        boulder.setPos(this.getX() + forwardX * currentDist, this.getY() + currentYOffset, this.getZ() + forwardZ * currentDist);
                        boulder.setYRot(this.getYRot());
                        boulder.setDeltaMovement(0, 0, 0);
                    }
                } else {
                    // 3. Удержание: подняли высоту до 19.5 блоков
                    boulder.setPos(this.getX() + forwardX * 1.0D, this.getY() + 19.5D, this.getZ() + forwardZ * 1.0D);
                    boulder.setYRot(this.getYRot());
                    boulder.setDeltaMovement(0, 0, 0);
                }
            }
        }
    }

    @Inject(method = "grabArmPredicate", at = @At("HEAD"), cancellable = true, remap = false)
    private void fixHoldAnimationLoop(AnimationState<AttackTitanEntity> state, CallbackInfoReturnable<PlayState> cir) {
        int grabbedId = this.getGrabbedEntityId();
        if (grabbedId != -1) {
            Entity grabbed = this.level().getEntity(grabbedId);
            if (grabbed instanceof TitanBoulderEntity) {
                if (this.isTitanAttacking() && this.getAttackNumber() == 99) {
                    cir.setReturnValue(PlayState.STOP);
                } else {
                    state.getController().setAnimation(RawAnimation.begin().thenPlayAndHold("hold_rock"));
                    cir.setReturnValue(PlayState.CONTINUE);
                }
            }
        }
    }

    @Inject(method = "triggerAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private void startLoweringOnLeftClick(CallbackInfo ci) {
        if (!this.isDefeated() && this.isGrabbing() && !this.isThrowing() && !this.isEating()) {

            int grabbedId = this.getGrabbedEntityId();
            Entity grabbed = this.level().getEntity(grabbedId);

            if (grabbed instanceof TitanBoulderEntity) {
                this.setAttackNumber(99);
                this.setTitanAttacking(true);
                this.attackAnimationTicks = 20;
                this.attackCooldown = 20;
                ci.cancel();
                return;
            }

            if (this.getGrabPhase() == 2) {
                this.startThrow();
            } else {
                this.startEat();
            }
            ci.cancel();
        }
    }
}
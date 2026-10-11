package com.maxonaddon.mixin;

import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;

import daot.*;

@Mixin(value = {
        TitanEyeEntity.class, TitanNapeEntity.class,
        ArmoredTitanEyeEntity.class, ArmoredTitanNapeEntity.class,
        AttackTitanEyeEntity.class, AttackTitanNapeEntity.class,
        BeastTitanEyeEntity.class, BeastTitanNapeEntity.class,
        ColossalTitanEyeEntity.class, ColossalTitanNapeEntity.class,
        CrawlerTitanEyeEntity.class, CrawlerTitanNapeEntity.class,
        FemaleTitanEyeEntity.class, FemaleTitanNapeEntity.class,
        FritzTitanEyeEntity.class, FritzTitanNapeEntity.class,
        OgreTitanEyeEntity.class, OgreTitanNapeEntity.class,
        SadTitanEyeEntity.class, SadTitanNapeEntity.class,
        SmallTitanEyeEntity.class, SmallTitanNapeEntity.class,
        SmallTitan2EyeEntity.class, SmallTitan2NapeEntity.class,
        TitanDummyEyeEntity.class, TitanDummyNapeEntity.class,
        YellowTitanEyeEntity.class, YellowTitanNapeEntity.class,
        WarhammerTitanEyeEntity.class, CrystalShellWarhammerEntity.class,
        ConnieFatherEyeEntity.class, ConnieFatherNapeEntity.class,
})
public class UniversalTitanWeakPointMixin {

    @Unique private int maxonaddon$apgHitCount = 0;
    @Unique private long maxonaddon$lastApgHitTime = 0;

    @Unique
    private void maxonaddon$forceDamage(LivingEntity titan, DamageSource source, float amount) {
        float newHealth = titan.getHealth() - amount;
        titan.setHealth(Math.max(newHealth, 0.0F));

        titan.hurtMarked = true;
        titan.invulnerableTime = 0;

        if (titan.getHealth() <= 0.0F) {
            titan.die(source);
        } else {
            try {
                Method triggerHit = titan.getClass().getMethod("triggerHitReaction", Entity.class, boolean.class, boolean.class);
                triggerHit.invoke(titan, source.getEntity(), true, false);
            } catch (Exception ignored) {}
        }
    }

    /**
     * ДОБИВАНИЕ УСТАВШЕГО (isIncapacitated) БЕРСЕРК-БОССА КЛИНКОМ В ШЕЮ.
     * <p>
     * Разобрано по байткоду daot: AttackTitanEntity#hurtFromNape (и аналоги у остальных
     * шифтеров) считает урон как min(maxHealth/3 * заряд_клинка, health - 1) — то есть,
     * как только здоровье titan'а уже около 1.0 (а именно туда его и приводит incapacitate(),
     * см. "if (getHealth() <= 1.01F) incapacitate()"), health - 1 ≈ 0, и дальнейшие удары в
     * шею почти не наносят урона. В ВАНИЛЬНОМ daot это не баг: у шифтера всегда есть
     * управляющий игрок, и "добивание" происходит по ДРУГОЙ ветке — если titan.isDismounting(),
     * hurtFromNape сразу зовёт die() в обход этой формулы (эта ветка проверена тем же
     * разбором байткода). isDismounting() выставляется по решению самого игрока (bail out).
     * <p>
     * У наших берсерк-боссов (см. BerserkBossHandler — setShifterUUID(null), реального
     * игрока-водителя нет вообще) этот "bail out" некому инициировать, поэтому titan навечно
     * зависает на ~1 HP, сколько его ни бей клинком в шею — снаружи это выглядит как
     * "хитбокс сломан/урон не проходит". Раз спускать игрока некому — добиваем сами: реальный
     * удар клинком по шее уставшего берсерк-босса сразу завершает его тем же die(source),
     * которым в оригинале заканчивается штатный bail-out-путь.
     */
    @Unique
    private boolean maxonaddon$finishIncapacitatedBerserkNape(Entity weakPoint, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!weakPoint.getClass().getSimpleName().contains("Nape")) return false; // добиваем только через шею

        if (!(source.getEntity() instanceof Player attacker)) return false;
        net.minecraft.world.item.ItemStack weapon = attacker.getMainHandItem();
        boolean hasBlade = weapon.getItem() instanceof daot.BladeItem
                && daot.BladeItem.getBladeState(weapon) != daot.BladeItem.BladeState.EMPTY;
        if (!hasBlade) return false;

        try {
            Method getParentMethod = weakPoint.getClass().getMethod("getParentTitan");
            Object parentObj = getParentMethod.invoke(weakPoint);

            if (parentObj instanceof LivingEntity parentTitan
                    && parentTitan instanceof daot.ShifterTitan shifter
                    && parentTitan.isAlive()
                    && parentTitan.getTags().contains("berserk_boss")
                    && shifter.isIncapacitated()) {

                parentTitan.die(source);
                cir.setReturnValue(true);
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHitWeakPoint(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity maxonaddon$weakPoint = (Entity) (Object) this;
        if (maxonaddon$finishIncapacitatedBerserkNape(maxonaddon$weakPoint, source, cir)) return;

        // ВАЖНО: Пропускаем взрывы. Это позволяет нашему трюку сработать!
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return;

        Entity directEntity = source.getDirectEntity();
        boolean isApg = directEntity instanceof daot.APGProjectileEntity;

        if (isApg) {
            Entity weakPoint = (Entity) (Object) this;
            ServerLevel serverLevel = weakPoint.level() instanceof ServerLevel sl ? sl : null;
            String weakPointName = weakPoint.getClass().getSimpleName();

            boolean isNape = weakPointName.contains("Nape");
            boolean isEye = weakPointName.contains("Eye") || weakPointName.contains("Grab");

            try {
                Method getParentMethod = weakPoint.getClass().getMethod("getParentTitan");
                Object parentObj = getParentMethod.invoke(weakPoint);

                if (parentObj instanceof LivingEntity parentTitan && parentTitan.isAlive()) {
                    String className = parentTitan.getClass().getSimpleName();

                    if (className.contains("WarhammerTitan") || className.contains("ArmoredTitan")) {
                        cir.setReturnValue(false);
                        return;
                    }

                    if (className.contains("FemaleTitan") && isNape) {
                        try {
                            Method isHardenedNapeMethod = parentTitan.getClass().getMethod("isHardenedNape");
                            boolean isHardenedNape = (Boolean) isHardenedNapeMethod.invoke(parentTitan);
                            if (isHardenedNape) {
                                cir.setReturnValue(false);
                                return;
                            }
                        } catch (Exception ignored) {}
                    }

                    boolean isShifter = className.contains("Attack") || className.contains("Female") ||
                            className.contains("Beast") || className.contains("Colossal") ||
                            className.contains("Cart") || className.contains("OgreShifter") ||
                            className.contains("TestShifter") || className.contains("Founding");

                    // ==============================
                    // ЛОГИКА ШЕИ (Двойное попадание APG)
                    // ==============================
                    if (isNape) {
                        long currentTime = serverLevel != null ? serverLevel.getGameTime() : 0;

                        if (currentTime - this.maxonaddon$lastApgHitTime > 15) {
                            this.maxonaddon$apgHitCount = 0;
                        }

                        this.maxonaddon$apgHitCount++;
                        this.maxonaddon$lastApgHitTime = currentTime;

                        if (this.maxonaddon$apgHitCount >= 2) {
                            if (isShifter) {
                                // Шифтерам просто сносим кучу ХП
                                this.maxonaddon$forceDamage(parentTitan, source, 40.0F);
                            } else {
                                // ОБЫЧНЫМ ТИТАНАМ ОТПРАВЛЯЕМ ВЗРЫВ ПРЯМО В ШЕЮ!
                                // Создаем взрыв, автором которого числится игрок (чтобы дали опыт и килл)
                                DamageSource explosion = weakPoint.damageSources().explosion(source.getDirectEntity(), source.getEntity());

                                // Вызываем hurt() с типом IS_EXPLOSION. Наш миксин его проигнорирует (строка 67),
                                // а мод DAoT поймает, оторвет шею и убьет родительского титана.
                                weakPoint.hurt(explosion, Float.MAX_VALUE);
                            }

                            if (source.getEntity() instanceof Player player) {
                                player.displayClientMessage(Component.translatable("message.maxonaddon.apg_perfect_hit").withStyle(ChatFormatting.DARK_RED), true);
                            }
                            this.maxonaddon$apgHitCount = 0;
                        } else {
                            if (serverLevel != null) {
                                BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
                                serverLevel.sendParticles(blood, weakPoint.getX(), weakPoint.getY(), weakPoint.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.1D);
                            }
                        }

                        // Отменяем базовый урон от стрелы, чтобы не спамило "USE A BLADE"
                        cir.setReturnValue(true);
                        return;
                    }

                    // ==============================
                    // ЛОГИКА ГЛАЗ (Ослепление APG)
                    // ==============================
                    if (isEye) {
                        this.maxonaddon$forceDamage(parentTitan, source, amount);

                        if (parentTitan.isAlive()) {
                            boolean isAlreadyBlinded = false;

                            try {
                                Method isEyeHurtMethod = parentTitan.getClass().getMethod("isEyeHurt");
                                isAlreadyBlinded = (Boolean) isEyeHurtMethod.invoke(parentTitan);
                            } catch (Exception e1) {
                                try {
                                    Method isBlindedMethod = parentTitan.getClass().getMethod("isBlinded");
                                    isAlreadyBlinded = (Boolean) isBlindedMethod.invoke(parentTitan);
                                } catch (Exception e2) {}
                            }

                            if (!isAlreadyBlinded) {
                                boolean triggered = false;

                                try {
                                    Method triggerMethod = parentTitan.getClass().getMethod("triggerEyeHurt");
                                    triggerMethod.invoke(parentTitan);
                                    triggered = true;
                                } catch (Exception e1) {
                                    try {
                                        Method triggerBlindnessMethod = parentTitan.getClass().getMethod("triggerBlindness");
                                        triggerBlindnessMethod.invoke(parentTitan);
                                        triggered = true;
                                    } catch (Exception e2) {}
                                }

                                if (triggered && source.getEntity() instanceof Player player) {
                                    player.displayClientMessage(Component.translatable("message.maxonaddon.apg_blinded").withStyle(ChatFormatting.RED), true);
                                }
                            }
                        }

                        cir.setReturnValue(true);
                        return;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки рефлексии
            }

            cir.setReturnValue(false);
        }
    }
}
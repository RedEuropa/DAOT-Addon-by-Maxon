package com.maxonaddon.mixin;

import com.maxonaddon.MaxonConfig; // Не забудь импорт конфига
import com.maxonaddon.network.TitanHitSoundPayload;
import daot.network.EffectPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = {
        daot.TitanNapeEntity.class,
        daot.CrawlerTitanNapeEntity.class,
        daot.SadTitanNapeEntity.class,
        daot.SmallTitanNapeEntity.class,
        daot.SmallTitan2NapeEntity.class,
        daot.YellowTitanNapeEntity.class,
        daot.ConnieFatherNapeEntity.class,
        daot.FritzTitanNapeEntity.class,
        daot.AbnormalTitanNapeEntity.class,
        daot.CrawlingAbnormalTitanNapeEntity.class,
        daot.TitanBeardNapeEntity.class,
        daot.TitanTropicalNapeEntity.class,
        daot.OgreTitanNapeEntity.class
})
public class TitanMeleeNapeMixin {

    /** Нижняя граница урона крита по титану — крит обязан ощутимо превосходить обычный удар. */
    private static final float MIN_CRIT_DAMAGE = 50.0f;

    // require = 0: в dannys-aot 2.4.3 часть классов шеи (TitanBeardNapeEntity,
    // TitanTropicalNapeEntity) больше НЕ переопределяет hurt(), а наследует его —
    // для них инжектить нечего, и без require = 0 запуск падает с
    // InvalidInjectionException. Классы, где hurt() есть, работают как прежде.
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void onMeleeHitNape(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {

        // --- ПРОВЕРКА КОНФИГА ---
        // Если в maxon_addon.json параметр pureTitanRandomHp отключен (false),
        // то миксин полностью отключается и работает оригинальный мод.
        if (!MaxonConfig.get().pureTitanRandomHp) {
            return;
        }

        Entity weakPoint = (Entity) (Object) this;
        Entity directEntity = source.getDirectEntity();

        // Кто ударил и чем. Обычный клинок бьёт лично (directEntity == сам игрок).
        // Громовое копьё — снаряд (directEntity == ThunderSpearEntity), стрелявшего игрока
        // достаём через source.getEntity() (владелец снаряда для начисления килла — тот же
        // приём, что уже используется в UniversalTitanWeakPointMixin для APG).
        //
        // Копьё пока разбираем ТОЛЬКО у затылка Огра (weakPoint instanceof OgreTitanNapeEntity):
        // именно для Огра урон от игрока напрямую жёстко блокируется его собственным hurt()
        // (см. комментарий у смертельной ветки ниже), и наш миксин — единственный надёжный путь
        // довести копьё до реального убийства. Для остальных титанов нативный путь копья не
        // трогаем, чтобы не менять то, что не жаловались как сломанное.
        Player player = null;
        boolean isSpear = false;

        if (directEntity instanceof Player p) {
            player = p;
        } else if (weakPoint instanceof daot.OgreTitanNapeEntity
                && directEntity instanceof daot.ThunderSpearEntity
                && source.getEntity() instanceof Player p) {
            player = p;
            isSpear = true;
        }

        if (player != null && !player.level().isClientSide()) {
            boolean hasBlade = !isSpear && (player.getMainHandItem().getItem() instanceof daot.BladeItem ||
                    player.getOffhandItem().getItem() instanceof daot.BladeItem);

            // Клинок в руке сам по себе ещё не удар: ванильный клик мышью по хитбоксу шеи
            // приходит сюда с тем же playerAttack, что и настоящий взмах из dannys-aot.
            // Раньше мы этого не различали, и любой такой клик превращался в удар с базой 30,
            // а на скорости выше 3.0 (полёт на тросе) — с гарантированным критом. Отсюда и
            // "просто цепляюсь крюком в шею, а титан получает урон и иногда умирает".
            // Копьё проверять не нужно — оно прилетает снарядом и своей механикой взмаха
            // не пользуется.
            if (hasBlade && !isSpear && !com.maxonaddon.util.BladeAttackWindow.isSwinging(player)) {
                return;
            }

            if (hasBlade || isSpear) {
                try {
                    Method getParentMethod = weakPoint.getClass().getMethod("getParentTitan");
                    Object parentObj = getParentMethod.invoke(weakPoint);

                    if (parentObj instanceof LivingEntity parentTitan && parentTitan.isAlive()) {

                        // --- 0. БОНУСЫ ОТ НАВЫКОВ (суммируются) ---
                        com.maxonaddon.util.IPlayerStats skills = (com.maxonaddon.util.IPlayerStats) player;

                        // Мастерство: +10% и +15% урона по титанам + бонус урона от перков
                        // (PerkManager.attackDamageBonus — тот же % урона, что перк вешает
                        // на Attributes.ATTACK_DAMAGE, но этот удар считает урон от своей
                        // фиксированной базы 30.0f, а не от ванильного атрибута, поэтому
                        // бонус перка нужно приплюсовать сюда явно).
                        float damageBonus = 0.0f;
                        if (skills.hasSkill("titan_mastery_1")) damageBonus += 0.10f;
                        if (skills.hasSkill("titan_mastery_2")) damageBonus += 0.15f;
                        damageBonus += (float) com.maxonaddon.perk.PerkManager.attackDamageBonus(player);

                        // Твёрдая рука: +5% и +10% к шансу крита + бонус крита от перков
                        float critChanceBonus = 0.0f;
                        if (skills.hasSkill("steady_hand_1")) critChanceBonus += 0.05f;
                        if (skills.hasSkill("steady_hand_2")) critChanceBonus += 0.10f;
                        critChanceBonus += (float) com.maxonaddon.perk.PerkManager.critChanceBonus(player);

                        // Палач: +5% и +10% к урону крита + бонус крит-урона от перков ("Герой Человечества")
                        float critDamageBonus = 0.0f;
                        if (skills.hasSkill("executioner_1")) critDamageBonus += 0.05f;
                        if (skills.hasSkill("executioner_2")) critDamageBonus += 0.10f;
                        critDamageBonus += (float) com.maxonaddon.perk.PerkManager.critDamageBonus(player);

                        // --- 1. ШАНС КРИТА ---
                        // Крит — механика клинка (разгон, "твёрдая рука"/"палач" — про фехтование
                        // в упор). У копья нет вложенной руки, критов не считаем — просто сильный
                        // фиксированный урон снаряда.
                        double speed = player.getDeltaMovement().length();
                        float critChance = 0.10f; // Базовые 10%

                        if (speed > 1.5) critChance += 0.20f;
                        if (speed > 3.0) critChance = 1.0f;

                        // Навыки добавляются и к разогнанному криту тоже, но потолок — 100%.
                        critChance = Math.min(1.0f, critChance + critChanceBonus);

                        boolean isCritical = !isSpear && player.getRandom().nextFloat() < critChance;

                        // --- 2. ВЛИЯНИЕ СЛОЖНОСТИ ---
                        float diffMult = 1.0f;
                        Difficulty difficulty = player.level().getDifficulty();

                        if (difficulty == Difficulty.EASY || difficulty == Difficulty.PEACEFUL) {
                            diffMult = 10.0f;
                        } else if (difficulty == Difficulty.NORMAL) {
                            diffMult = 1.0f;
                        } else if (difficulty == Difficulty.HARD) {
                            diffMult = 0.55f;
                        }

                        float localDiff = player.level().getCurrentDifficultyAt(weakPoint.blockPosition()).getEffectiveDifficulty();
                        diffMult *= (1.0f - (Math.min(localDiff, 4.0f) * 0.075f));

                        // --- 3. ВЫЧИСЛЕНИЕ УРОНА ---
                        // Мастерство увеличивает базовый урон, Палач — только множитель крита.
                        // Копьё: берём РЕАЛЬНЫЙ урон снаряда (amount — то, что уже посчитал daot
                        // для взрыва копья), а не выдумываем число — так не трогаем существующий
                        // баланс копья, только доводим его до титана тем же путём, что клинок.
                        float baseBladeDamage = isSpear
                                ? amount * (1.0f + damageBonus) * diffMult
                                : 30.0f * (1.0f + damageBonus) * diffMult;
                        float critMultiplier = 1.5f + critDamageBonus;
                        float finalDamage = baseBladeDamage;

                        if (isCritical) {
                            // Крит НИКОГДА не бьёт слабее MIN_CRIT_DAMAGE. На нормальной сложности
                            // множители сложности сжимают базовый удар примерно до 25, и крит x1.5
                            // давал жалкие ~38 — крит переставал ощущаться как крит. Это именно ПОЛ,
                            // а не фикс: на лёгкой сложности и с навыками "Палач" крит спокойно уходит
                            // выше, Math.max ничего не срезает.
                            //
                            // Пол масштабируется тем же (1 + damageBonus), что и базовый урон: если
                            // навык/перк даёт +X% урона по титанам, минимальный крит тоже должен
                            // вырасти на X%, а не оставаться жёстко прибитым к 50 — иначе с сильными
                            // бонусами урона "пол" крита со временем становится СЛАБЕЕ обычного удара.
                            float scaledMinCrit = MIN_CRIT_DAMAGE * (1.0f + damageBonus);
                            finalDamage = Math.max(baseBladeDamage * critMultiplier, scaledMinCrit);
                        }

                        float currentHealth = parentTitan.getHealth();

                        // Спавн крови при ударе
                        spawnAllBloodEffects(weakPoint, isCritical);

                        // --- 4. НАНЕСЕНИЕ УРОНА И ЭФФЕКТЫ ---
                        if (currentHealth - finalDamage <= 0) {
                            // --- СМЕРТЕЛЬНЫЙ УДАР ---
                            // ВАЖНО: раньше здесь только проигрывался звук смерти, а сам "добивающий"
                            // удар отдавался оригинальному hurt() затылка (мы не отменяли инжект).
                            // Для большинства титанов это работало, но у daot.OgreTitanEntity СВОЙ
                            // hurt(), который безусловно блокирует ЛЮБОЙ урон напрямую от игрока
                            // (return false, если source.getEntity() instanceof Player) — урон Огру
                            // разрешён только через отдельный счётчик onNapeHit() у затылка, а у ТОГО,
                            // в свою очередь, добивающий удар клинком должен ещё пройти угол атаки
                            // (≤60° от направления взгляда Огра), дистанцию (≤6 блоков) и не попасть
                            // в окно парирования — в реальном бою это неслучайно срывается на
                            // последнем ударе, и здоровье просто зависает у нуля без смерти.
                            // Поэтому завершаем убийство САМИ, минуя hurt() совсем — тот же приём,
                            // что уже проверенно работает в UniversalTitanWeakPointMixin.
                            parentTitan.setHealth(0.0F);
                            parentTitan.hurtMarked = true;
                            sendSoundPacket(player, weakPoint, 1); // 1 = СМЕРТЬ
                            parentTitan.die(source);
                            cir.setReturnValue(true);

                            // Проверка перка — вызываем ЗДЕСЬ и напрямую, а не только через общее
                            // событие AFTER_KILLED_OTHER_ENTITY в MaxonDaotAddon.java: здесь оружие
                            // известно ТОЧНО (isSpear), а не угадывается постфактум по
                            // getLastDamageSource() — у Огра этот путь ненадёжен, поскольку
                            // parentTitan.die(source) вызван в обход штатного hurt(), и
                            // lastDamageSource у родителя мог остаться не выставлен. Общий
                            // обработчик всё равно тоже сработает следом — это безвредно,
                            // hasUnlockedPerk-проверка внутри идемпотентна.
                            com.maxonaddon.perk.PerkUnlockHandler.onWeaponTitanKill(player, parentTitan, isSpear);
                        } else {
                            // --- ВЫЖИВАНИЕ ТИТАНА ---
                            parentTitan.setHealth(currentHealth - finalDamage);

                            if (isCritical) {
                                sendSoundPacket(player, weakPoint, 2); // 2 = ЗВУК КРИТА
                                spawnCritParticles(weakPoint);
                                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.maxonaddon.crit.hit", String.format("%.1f", finalDamage)).withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD), true);
                            } else {
                                sendSoundPacket(player, weakPoint, 0); // 0 = ОБЫЧНЫЙ ЗВУК
                                spawnSweepParticle(weakPoint);
                            }

                            parentTitan.hurtMarked = true;
                            cir.setReturnValue(true);
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки рефлексии
                }
            }
        }
    }

    private void sendSoundPacket(Player attacker, Entity weakPoint, int soundType) {
        TitanHitSoundPayload payload = new TitanHitSoundPayload(weakPoint.getX(), weakPoint.getY(), weakPoint.getZ(), soundType);

        if (attacker.level() instanceof ServerLevel serverLevel) {
            List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                    p.distanceToSqr(weakPoint) < 4096.0
            );

            for (ServerPlayer p : nearbyPlayers) {
                ServerPlayNetworking.send(p, payload);
            }

            net.minecraft.sounds.SoundEvent[] slashSounds = new net.minecraft.sounds.SoundEvent[]{
                    daot.ModSounds.SLASH1, daot.ModSounds.SLASH2, daot.ModSounds.SLASH3
            };

            net.minecraft.sounds.SoundEvent slash = slashSounds[attacker.getRandom().nextInt(3)];
            float pitch = 0.9F + attacker.getRandom().nextFloat() * 0.1F;

            serverLevel.playSound(null, weakPoint.getX(), weakPoint.getY(), weakPoint.getZ(), slash, net.minecraft.sounds.SoundSource.PLAYERS, 8.0F, pitch);
        }
    }

    private void spawnAllBloodEffects(Entity weakPoint, boolean isCritical) {
        if (weakPoint.level() instanceof ServerLevel serverLevel) {
            double x = weakPoint.getX();
            double yOffset = weakPoint.getY() + (double)weakPoint.getBbHeight() * 0.5D;
            double z = weakPoint.getZ();

            float scale = isCritical ? 2.5F : 1.5F;
            EffectPayload bloodPayload = new EffectPayload("blood", x, yOffset, z, scale);

            for (ServerPlayer p : PlayerLookup.tracking(weakPoint)) {
                ServerPlayNetworking.send(p, bloodPayload);
            }

            BlockParticleOption bloodParticle = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
            int count = isCritical ? 80 : 30;
            serverLevel.sendParticles(bloodParticle, x, yOffset, z, count, 0.4D, 0.4D, 0.4D, 0.15D);
        }
    }

    private void spawnSweepParticle(Entity weakPoint) {
        if (weakPoint.level() instanceof ServerLevel serverLevel) {
            double yOffset = weakPoint.getY() + (double)weakPoint.getBbHeight() * 0.5D;
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, weakPoint.getX(), yOffset, weakPoint.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    private void spawnCritParticles(Entity weakPoint) {
        if (weakPoint.level() instanceof ServerLevel serverLevel) {
            double yOffset = weakPoint.getY() + (double)weakPoint.getBbHeight() * 0.5D;

            serverLevel.sendParticles(ParticleTypes.CRIT, weakPoint.getX(), yOffset, weakPoint.getZ(), 15, 0.4D, 0.4D, 0.4D, 0.2D);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, weakPoint.getX(), yOffset, weakPoint.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.1D);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, weakPoint.getX(), yOffset, weakPoint.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }
}
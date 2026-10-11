package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.UUID;

/**
 * "Стальной затылок" — снижает урон по затылку СВОЕГО титана-шифтера.
 *
 * Уровень 1 — минус 25% урона, уровень 2 — минус 40% (не суммируются, берётся лучший).
 *
 * Точка входа именно hurtFromNape(DamageSource, float): её объявляют все шесть базовых семейств
 * шифтеров, а Челюсть, Повозка, Огр, Основатель и Тройной наследуют её от AttackTitanEntity,
 * поэтому отдельно их перечислять не нужно.
 *
 * Скил личный: ищем владельца титана через getShifterUUID() и проверяем навык у него. Чужие
 * титаны и титаны офлайн-игроков остаются с обычным уроном.
 *
 * Про совместное действие с затвердеванием: AttackTitanDamageMixin при активном затвердевании
 * оставляет от удара 20%. Приоритет этого миксина выше, поэтому урон сначала режется навыком,
 * а затвердевание применяется уже к уменьшенному числу.
 */
@Mixin(value = {
        daot.AttackTitanEntity.class,
        daot.ArmoredTitanEntity.class,
        daot.FemaleTitanEntity.class,
        daot.BeastTitanEntity.class,
        daot.WarhammerTitanEntity.class,
        daot.ColossalTitanEntity.class
}, priority = 1500)
public abstract class SteelNapeMixin {

    @Unique private static final float MAXON$REDUCTION_1 = 0.25F;
    @Unique private static final float MAXON$REDUCTION_2 = 0.40F;

    @ModifyVariable(method = "hurtFromNape", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float maxon$softenNapeDamage(float amount) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() || amount <= 0.0F) return amount;

        MinecraftServer server = self.level().getServer();
        if (server == null) return amount;

        UUID shifterUuid = maxon$shifterUuid(self);
        if (shifterUuid == null) return amount;

        ServerPlayer owner = server.getPlayerList().getPlayer(shifterUuid);
        if (owner == null) return amount;

        IPlayerStats stats = (IPlayerStats) owner;
        if (stats.hasSkill("steel_nape_2")) return amount * (1.0F - MAXON$REDUCTION_2);
        if (stats.hasSkill("steel_nape_1")) return amount * (1.0F - MAXON$REDUCTION_1);
        return amount;
    }

    /**
     * getShifterUUID() объявлен в каждом из шести классов-целей, но не через общий интерфейс
     * (daot.ShifterTitan содержит только isDismounting/isIncapacitated), поэтому @Shadow тут не
     * подходит и приходится разбирать типы вручную. Наследники AttackTitanEntity ловятся первой
     * же веткой.
     */
    @Unique
    private static UUID maxon$shifterUuid(Entity self) {
        if (self instanceof daot.AttackTitanEntity t) return t.getShifterUUID();
        if (self instanceof daot.ArmoredTitanEntity t) return t.getShifterUUID();
        if (self instanceof daot.FemaleTitanEntity t) return t.getShifterUUID();
        if (self instanceof daot.BeastTitanEntity t) return t.getShifterUUID();
        if (self instanceof daot.WarhammerTitanEntity t) return t.getShifterUUID();
        if (self instanceof daot.ColossalTitanEntity t) return t.getShifterUUID();
        return null;
    }
}

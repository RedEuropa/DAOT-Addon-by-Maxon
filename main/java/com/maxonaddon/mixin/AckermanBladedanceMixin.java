package com.maxonaddon.mixin;

import com.maxonaddon.util.IPlayerStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * "Танец Клинков" — во время пробуждения Аккермана снижает расход прочности клинков на 25%.
 *
 * daot.BladeItem.damageBladeByAmount(stack, amount, wielder) уже содержит готовый механизм
 * подстройки урона прочности под конкретного владельца — adjustWearForOgre(wielder, amount)
 * делает ровно это для Огра. Мы встраиваемся в тот же вход (третий аргумент — тот же wielder,
 * что получает adjustWearForOgre), уменьшая ВХОДНОЙ amount ДО того, как метод его обработает —
 * так наша скидка складывается с любой другой корректировкой (в т.ч. огровской), а не спорит с ней.
 */
@Mixin(targets = "daot.BladeItem", remap = false)
public abstract class AckermanBladedanceMixin {

    // ВАЖНО: у @ModifyVariable обработчик обязан содержать ВЕСЬ список параметров целевого
    // метода как контекст (проверено логом ошибки Mixin) — сама захватываемая переменная
    // просто дублируется первым аргументом. Дескриптор damageBladeByAmount подтверждён
    // байткодом: (ItemStack, int, LivingEntity) -> void, поэтому здесь 4 параметра:
    // (int-первый — то, что реально меняем и возвращаем) + (ItemStack, int, LivingEntity —
    // полный исходный список для контекста, второй int тут не используется).
    @ModifyVariable(method = "damageBladeByAmount", at = @At("HEAD"), argsOnly = true)
    private static int maxon$reduceBladedanceWear(int amount, net.minecraft.world.item.ItemStack stack, int amountContext, LivingEntity wielder) {
        if (!(wielder instanceof Player player)) return amount;

        float reduction = 0.0F;

        // "Танец Клинков" — только во время пробуждения Аккермана.
        if (player.getTags().contains("dannys-aot:awakened_power")) {
            IPlayerStats stats = (IPlayerStats) player;
            if (stats.hasSkill("bladedance")) {
                reduction = 0.25F;
            }
        }

        // Перк "Герой Человечества" — не привязан к пробуждению Аккермана, действует всегда.
        float perkBonus = (float) com.maxonaddon.perk.PerkManager.bladeDurabilityBonus(player);
        if (perkBonus > 0.0F) {
            reduction = 1.0F - (1.0F - reduction) * (1.0F - perkBonus);
        }

        if (reduction <= 0.0F) return amount;

        // Округляем в пользу игрока (Math.round, а не floor), но не даём уйти в 0 при
        // однократных маленьких повреждениях — иначе клинок вообще перестанет тупиться.
        int reduced = Math.round(amount * (1.0F - reduction));
        return Math.max(reduced, amount > 0 ? 1 : 0);
    }
}

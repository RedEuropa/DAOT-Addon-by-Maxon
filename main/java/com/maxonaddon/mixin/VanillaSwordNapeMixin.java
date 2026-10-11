package com.maxonaddon.mixin;

import daot.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Целимся во все классы шеи, которые есть в моде
@Mixin(value = {
        TitanNapeEntity.class,
        CrawlerTitanNapeEntity.class,
        FemaleTitanNapeEntity.class,
        FritzTitanNapeEntity.class,
        OgreTitanNapeEntity.class,
        SadTitanNapeEntity.class,
        SmallTitanNapeEntity.class,
        SmallTitan2NapeEntity.class,
        TitanDummyNapeEntity.class,
        YellowTitanNapeEntity.class,
        ConnieFatherNapeEntity.class
})
public class VanillaSwordNapeMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onSwordHitNape(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {

        // 1. Защита от рекурсии: если урон УЖЕ от взрыва, не трогаем его
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        Entity weakPoint = (Entity) (Object) this;

        // 2. Логика строго на сервере
        if (!weakPoint.level().isClientSide() && source.getDirectEntity() instanceof Player player) {
            Item weapon = player.getMainHandItem().getItem();

            // 3. Проверяем, держит ли игрок ванильный меч (железный, алмазный, незеритовый)
            if (weapon == Items.IRON_SWORD || weapon == Items.DIAMOND_SWORD || weapon == Items.NETHERITE_SWORD) {

                // 4. Генерируем фейковый урон от взрыва (автором взрыва записываем игрока, чтобы дали опыт/килл)
                DamageSource explosionDamage = weakPoint.damageSources().explosion(player, player);

                // 5. Повторно вызываем hurt, но уже со взрывом.
                // Мод dannys-aot увидит IS_EXPLOSION, сам нарисует кровь и убьет титана!
                weakPoint.hurt(explosionDamage, amount);

                // 6. Отменяем изначальный "слабый" удар ванильным мечом
                cir.setReturnValue(true);
            }
        }
    }
}
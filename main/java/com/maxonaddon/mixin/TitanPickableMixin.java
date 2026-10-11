package com.maxonaddon.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Целимся во всех чистых титанов из мода
@Mixin(targets = {
        "daot.TitanEntity",
        "daot.SmallTitanEntity",
        "daot.SmallTitan2Entity",
        "daot.FritzTitanEntity",
        "daot.AbnormalTitanEntity",
        "daot.CrawlingAbnormalTitanEntity",
        "daot.ConnieFatherEntity",
        "daot.OgreTitanEntity",
        "daot.SadTitanEntity",
        "daot.YellowTitanEntity",
        "daot.CrawlerTitanEntity",
        "daot.TitanBeardEntity",
        "daot.TitanTropicalEntity"
})
public abstract class TitanPickableMixin extends Monster {

    // Обязательный конструктор для наследования от Monster
    protected TitanPickableMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    // require = 0 говорит миксину: "Если метода нет у конкретного титана, просто пропусти"
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true, require = 0)
    private void makeAlwaysPickable(CallbackInfoReturnable<Boolean> cir) {
        if (!this.isDeadOrDying()) {
            cir.setReturnValue(true);
        }
    }
}
package com.maxonaddon.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Прямой доступ к приватным полям Entity.vehicle и Entity.passengers.
 *
 * Нужен потому, что stopRiding() для ослабленного жителя перехватывается
 * и отменяется сторонним кодом (судя по всему, самим dannys-aot).
 * Через аксессор мы рвём связь напрямую с обеих сторон.
 *
 * ВАЖНО: строку "EntityVehicleAccessor" ОБЯЗАТЕЛЬНО добавить в массив "mixins"
 * файла maxon-daot-addon.mixins.json — без этого игра падает при первом
 * обращении к классу (IllegalClassLoadError).
 */
@Mixin(Entity.class)
public interface EntityVehicleAccessor {

    @Accessor("vehicle")
    Entity maxon$getVehicleField();

    @Accessor("vehicle")
    void maxon$setVehicleField(Entity vehicle);

    @Accessor("passengers")
    ImmutableList<Entity> maxon$getPassengersField();

    @Accessor("passengers")
    void maxon$setPassengersField(ImmutableList<Entity> passengers);
}
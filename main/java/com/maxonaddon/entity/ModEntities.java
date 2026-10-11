package com.maxonaddon.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

    public class ModEntities {
        public static final EntityType<TitanBoulderEntity> TITAN_BOULDER = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                // ИЗМЕНЕНИЕ ЗДЕСЬ:
                ResourceLocation.fromNamespaceAndPath("dassodm", "titan_boulder"),
                FabricEntityTypeBuilder.create(MobCategory.MISC, TitanBoulderEntity::new)
                        .dimensions(EntityDimensions.fixed(4.0f, 4.0f))
                        .trackRangeBlocks(64)
                        .trackedUpdateRate(2)
                        .build()
        );
// ...

    public static void register() {
        // Вызов этого метода заставит класс загрузиться и зарегистрировать энтити
        System.out.println("Регистрация энтити dassodm завершена!");
    }
}
package com.maxonaddon.client.model;

import com.maxonaddon.block.entity.GasCisternaBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import daot.GasCanisterItem;
import net.minecraft.world.item.ItemStack;


public class GasCisternaModel extends GeoModel<GasCisternaBlockEntity> {

    @Override
    public ResourceLocation getModelResource(GasCisternaBlockEntity animatable) {
        // Проверяем 1-й слот (слот канистры)
        ItemStack canisterSlot = animatable.getItem(1);

        // Если предмет есть и это канистра - возвращаем модель ON
        if (!canisterSlot.isEmpty() && canisterSlot.getItem() instanceof GasCanisterItem) {
            return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/gas_cisterna_on.geo.json");
        }

        // Иначе стандартная модель
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/gas_cisterna.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GasCisternaBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/block/gas_cisterna.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GasCisternaBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "animations/gas_cisterna.animation.json");
    }
}
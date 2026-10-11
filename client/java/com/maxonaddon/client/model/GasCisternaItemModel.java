package com.maxonaddon.client.model;

import com.maxonaddon.item.GasCisternaItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// Указываем правильный тип GasCisternaItem
public class GasCisternaItemModel extends GeoModel<GasCisternaItem> {

    @Override
    public ResourceLocation getModelResource(GasCisternaItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/gas_cisterna.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GasCisternaItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/block/gas_cisterna.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GasCisternaItem animatable) {
        return null;
    }
}
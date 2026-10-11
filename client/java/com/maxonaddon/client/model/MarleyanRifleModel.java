package com.maxonaddon.client.model;

import com.maxonaddon.item.MarleyanRifleItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MarleyanRifleModel extends GeoModel<MarleyanRifleItem> {

    @Override
    public ResourceLocation getModelResource(MarleyanRifleItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/rifle.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MarleyanRifleItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/weapon/marleyan_rifle.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MarleyanRifleItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "animations/marleyan_rifle.animation.json");
    }
}

package com.maxonaddon.client.model;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.item.SwordBoxItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SwordBoxItemModel extends GeoModel<SwordBoxItem> {
    @Override
    public ResourceLocation getModelResource(SwordBoxItem animatable) {
        return MaxonDaotAddon.id("geo/item_box.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SwordBoxItem animatable) {
        return MaxonDaotAddon.id("textures/block/item_box.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SwordBoxItem animatable) {
        return MaxonDaotAddon.id("animations/item_box.animation.json");
    }
}
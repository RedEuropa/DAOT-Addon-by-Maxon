package com.maxonaddon.client.model;

import com.maxonaddon.MaxonDaotAddon;
import com.maxonaddon.block.entity.SwordBoxBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SwordBoxModel extends GeoModel<SwordBoxBlockEntity> {
    @Override
    public ResourceLocation getModelResource(SwordBoxBlockEntity animatable) {
        return MaxonDaotAddon.id("geo/item_box.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SwordBoxBlockEntity animatable) {
        return MaxonDaotAddon.id("textures/block/item_box.png"); // Убедитесь в правильности пути
    }

    @Override
    public ResourceLocation getAnimationResource(SwordBoxBlockEntity animatable) {
        return MaxonDaotAddon.id("animations/item_box.animation.json");
    }
}
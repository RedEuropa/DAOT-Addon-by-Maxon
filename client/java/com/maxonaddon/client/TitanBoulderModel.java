package com.maxonaddon.client;

import com.maxonaddon.entity.TitanBoulderEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class TitanBoulderModel extends GeoModel<TitanBoulderEntity> {

    @Override
    public ResourceLocation getModelResource(TitanBoulderEntity animatable) {
        // Указываем точный путь к файлу модели
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "geo/rock.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TitanBoulderEntity animatable) {
        // Указываем точный путь к текстуре
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/entity/rock.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TitanBoulderEntity animatable) {
        // У камня нет анимаций, возвращаем null, чтобы GeckoLib даже не пытался их искать
        return null;
    }
}
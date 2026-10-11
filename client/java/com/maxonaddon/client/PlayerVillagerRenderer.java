package com.maxonaddon.client;

import com.maxonaddon.util.VillagerSkinAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public class PlayerVillagerRenderer extends MobRenderer<Villager, AnimatedPlayerVillagerModel> {

    public PlayerVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new AnimatedPlayerVillagerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);

        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Villager entity) {
        int skinId = ((VillagerSkinAccessor) entity).modid$getSkinIndex();
        return ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "textures/entity/villager_skins/skin_" + skinId + ".png");
    }
}
package com.maxonaddon.client.render;

import com.maxonaddon.block.entity.SwordBoxBlockEntity;
import com.maxonaddon.client.model.SwordBoxModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider; // <-- Новый импорт
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class SwordBoxRenderer extends GeoBlockRenderer<SwordBoxBlockEntity> {

    public SwordBoxRenderer(BlockEntityRendererProvider.Context context) {
        super(new SwordBoxModel());
    }

    @Override
    public void preRender(PoseStack poseStack, SwordBoxBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // 1. Считаем общее количество мечей в слотах 0, 1, 2, 3
        int totalBlades = 0;
        for (int i = 0; i <= 3; i++) {
            if (!animatable.getItem(i).isEmpty()) {
                totalBlades += animatable.getItem(i).getCount();
            }
        }

        // 2. Убедимся, что родительские группы мечей не скрыты (иначе мечи внутри них тоже пропадут)
        setBoneHidden(model, "blade_group_1", false);
        setBoneHidden(model, "blade_group_2", false);
        setBoneHidden(model, "blade_group_3", false);
        setBoneHidden(model, "blade_group_4", false);

        // 3. Динамически показываем от 1 до 16 мечей (balde_1 ... balde_16)
        for (int i = 1; i <= 16; i++) {
            setBoneHidden(model, "balde_" + i, i > totalBlades);
        }

        // Газ (Слоты 4-5)
        setBoneHidden(model, "Gas1", animatable.getItem(4).isEmpty());
        setBoneHidden(model, "Gas2", animatable.getItem(5).isEmpty());

        // Копья (Слоты 6-10)
        setBoneHidden(model, "thunder_spear", animatable.getItem(6).isEmpty());
        setBoneHidden(model, "thunder_spear2", animatable.getItem(7).isEmpty());
        setBoneHidden(model, "thunder_spear3", animatable.getItem(8).isEmpty());
        setBoneHidden(model, "thunder_spear4", animatable.getItem(9).isEmpty());
        setBoneHidden(model, "thunder_spear5", animatable.getItem(10).isEmpty());

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private void setBoneHidden(BakedGeoModel model, String boneName, boolean hidden) {
        model.getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
    }
}
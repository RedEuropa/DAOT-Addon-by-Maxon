package com.maxonaddon.client.render;

import com.maxonaddon.client.gui.TraumaScreen;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class TraumaLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public TraumaLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 1. Рендерим мигание ТОЛЬКО если сейчас открыт экран травм
        if (!(Minecraft.getInstance().screen instanceof TraumaScreen)) {
            return;
        }

        IPlayerStats stats = (IPlayerStats) player;
        boolean hasHead = stats.hasTrauma(TraumaType.HEAD);
        boolean hasBody = stats.hasTrauma(TraumaType.BODY);
        boolean hasArms = stats.hasTrauma(TraumaType.ARMS);
        boolean hasLegs = stats.hasTrauma(TraumaType.LEGS);

        // Если травм нет - ничего не делаем
        if (!hasHead && !hasBody && !hasArms && !hasLegs) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();

        // 2. Запоминаем, какие части тела сейчас были видимы (чтобы не сломать ванильный рендер)
        boolean headVis = model.head.visible;
        boolean hatVis = model.hat.visible;
        boolean bodyVis = model.body.visible;
        boolean jacketVis = model.jacket.visible;
        boolean rightArmVis = model.rightArm.visible;
        boolean rightSleeveVis = model.rightSleeve.visible;
        boolean leftArmVis = model.leftArm.visible;
        boolean leftSleeveVis = model.leftSleeve.visible;
        boolean rightLegVis = model.rightLeg.visible;
        boolean rightPantsVis = model.rightPants.visible;
        boolean leftLegVis = model.leftLeg.visible;
        boolean leftPantsVis = model.leftPants.visible;

        // 3. Скрываем ВСЁ
        model.setAllVisible(false);

        // 4. Включаем видимость ТОЛЬКО для сломанных конечностей
        if (hasHead) { model.head.visible = true; model.hat.visible = true; }
        if (hasBody) { model.body.visible = true; model.jacket.visible = true; }
        if (hasArms) {
            model.rightArm.visible = true; model.rightSleeve.visible = true;
            model.leftArm.visible = true; model.leftSleeve.visible = true;
        }
        if (hasLegs) {
            model.rightLeg.visible = true; model.rightPants.visible = true;
            model.leftLeg.visible = true; model.leftPants.visible = true;
        }

        // 5. Вычисляем пульсацию альфа-канала по синусоиде от системного времени
        // Math.sin дает от -1 до 1. Превращаем это в 0.0 - 1.0
        double time = System.currentTimeMillis() / 250.0; // Скорость мигания (чем меньше цифра, тем быстрее)
        float pulse = (float) ((Math.sin(time) + 1.0) / 2.0);

        // Ограничиваем прозрачность, чтобы она мигала от 10% до 60% плотности
        float alpha = pulse * 0.5f + 0.1f;

        // Собираем ARGB цвет (Красный с нашей пульсирующей альфой)
        int a = (int) (alpha * 255);
        int color = (a << 24) | (255 << 16) | (0 << 8) | 0;

        // 6. Отрисовываем выбранные конечности поверх основного скина
        poseStack.pushPose();

        // Увеличиваем модель на 2%, чтобы красная заливка была чуть больше самого скина
        // Это полностью решает проблему невидимости и мерцания текстур (Z-fighting)
        poseStack.scale(1.02f, 1.02f, 1.02f);
        // Смещаем чуть вниз, чтобы отцентровать увеличенную модель
        poseStack.translate(0.0f, -0.02f, 0.0f);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, color);

        poseStack.popPose();

        // 7. Возвращаем оригинальную видимость модели
        model.head.visible = headVis;
        model.hat.visible = hatVis;
        model.body.visible = bodyVis;
        model.jacket.visible = jacketVis;
        model.rightArm.visible = rightArmVis;
        model.rightSleeve.visible = rightSleeveVis;
        model.leftArm.visible = leftArmVis;
        model.leftSleeve.visible = leftSleeveVis;
        model.rightLeg.visible = rightLegVis;
        model.rightPants.visible = rightPantsVis;
        model.leftLeg.visible = leftLegVis;
        model.leftPants.visible = leftPantsVis;
    }
}
package com.maxonaddon.client.render;

import com.maxonaddon.client.VillagerHookTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class VillagerCableLayer<T extends Villager, M extends net.minecraft.client.model.EntityModel<T>> extends RenderLayer<T, M> {

    public VillagerCableLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        VillagerHookTracker.HookData hookData = VillagerHookTracker.getHook(entity.getId());
        if (hookData == null || !hookData.active || hookData.target == null) return;

        // ВАЖНО: не используем PoseStack сущности вообще. К этому моменту рендерер модели
        // уже применил к нему поворот по yBodyRot, зеркальный scale(-1,-1,1) и смещение вверх —
        // это НЕ ортогональный набор преобразований, который можно "отменить" обратным
        // поворотом (как было раньше): scale(-1,-1,1) стоит МЕЖДУ поворотом модели и нашей
        // попыткой его открутить, поэтому итоговая линия кренилась вместе с поворотом жителя.
        // Вместо борьбы с чужой матрицей просто считаем трос в мировых координатах напрямую
        // (позиция камеры -> точка) и рисуем его единичной матрицей — так он зависит только
        // от реальной мировой позиции жителя и точки зацепа, и ни от чего больше.
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        double entityX = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double entityY = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double entityZ = Mth.lerp(partialTicks, entity.zOld, entity.getZ());

        // Точка старта троса — пояс жителя.
        float startX = (float) (entityX - camPos.x);
        float startY = (float) (entityY - camPos.y + 0.8);
        float startZ = (float) (entityZ - camPos.z);

        // Точка зацепа, пришедшая с сервера, — уже в мировых координатах.
        float endX = (float) (hookData.target.x - camPos.x);
        float endY = (float) (hookData.target.y - camPos.y);
        float endZ = (float) (hookData.target.z - camPos.z);

        // RenderType.lines() здесь не подходил по двум причинам сразу:
        // 1) его формат вершин POSITION_COLOR_NORMAL требует нормаль, а лента её не писала —
        //    BufferBuilder падал с "Missing elements in vertex: Normal" при первой же отрисовке;
        // 2) его режим — Mode.LINES, то есть вершины читаются парами как отрезки, а мы строим
        //    по четыре вершины на сегмент, рассчитывая на квады.
        // debugQuads — это POSITION_COLOR + Mode.QUADS без отсечения граней: ровно тот формат,
        // под который написана лента.
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f identity = new Matrix4f();

// Создаем векторы для нашей утилиты
        Vec3 startVec = new Vec3(startX, startY, startZ);
        Vec3 endVec = new Vec3(endX, endY, endZ);

// Для теста зададим maxRopeLength искусственно больше текущего расстояния.
// В будущем это значение будет браться из HookPoint.maxRopeLength, когда мы свяжем это с ODM.
        double realDistance = startVec.distanceTo(endVec);
        double simulatedMaxRope = realDistance + 3.0; // Добавляем 3 блока длины для провисания
        float thickness = 0.05f;
        Vec3 playerVelocity = new Vec3(0, 0, 0);
// Вызываем отрисовку Безье вместо прямой линии
        com.maxonaddon.client.render.RopeRendererUtils.renderSaggingRope(
                vertexConsumer,
                identity,
                startVec,
                endVec,
                simulatedMaxRope,
                new int[]{40, 40, 40, 255}, // Цвет
                camPos,                     // Позиция камеры (она уже есть в VillagerCableLayer)
                thickness,
                playerVelocity// Толщина
        );
    }
}

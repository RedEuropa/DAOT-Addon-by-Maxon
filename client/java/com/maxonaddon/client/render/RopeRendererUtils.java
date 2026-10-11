package com.maxonaddon.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class RopeRendererUtils {

    public static void renderSaggingRope(VertexConsumer vertexConsumer, Matrix4f matrix, Vec3 start, Vec3 end, double maxRopeLength, int[] color, Vec3 camPos, float thickness, Vec3 playerVelocity) {
        double currentDistance = start.distanceTo(end);
        if (currentDistance < 0.01) return;

        double sagAmount = Math.max(0.0, maxRopeLength - currentDistance);

        // Фактор горизонтальности
        double dy = Math.abs(end.y - start.y);
        double horizontalDistSqr = currentDistance * currentDistance - dy * dy;
        double horizontalFactor = horizontalDistSqr > 0 ? Math.sqrt(horizontalDistSqr) / currentDistance : 0.0;

        double visualSag = Math.min(sagAmount * 0.7, 10.0) * horizontalFactor;

        Vec3 dir = end.subtract(start);
        Vec3 airDrag = playerVelocity.scale(-0.8);

        // Вычисляем контрольные точки 1 раз за кадр (это нормально)
        Vec3 control1 = start.add(dir.scale(0.333)).subtract(0.0, visualSag * 0.4, 0.0).add(airDrag);
        Vec3 control2 = start.add(dir.scale(0.666)).subtract(0.0, visualSag, 0.0).add(airDrag.scale(0.2));

        int segments = 12; // Снизили для идеального FPS

        // Стартовые координаты в примитивах
        double prevX = start.x;
        double prevY = start.y;
        double prevZ = start.z;

        double halfThick = thickness / 2.0;

        // ЦИКЛ БЕЗ ВЫДЕЛЕНИЯ ПАМЯТИ (Zero-Allocation Loop)
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double u = 1.0 - t;

            // Текущая точка Безье
            double x = u*u*u * start.x + 3*u*u*t * control1.x + 3*u*t*t * control2.x + t*t*t * end.x;
            double y = u*u*u * start.y + 3*u*u*t * control1.y + 3*u*t*t * control2.y + t*t*t * end.y;
            double z = u*u*u * start.z + 3*u*u*t * control1.z + 3*u*t*t * control2.z + t*t*t * end.z;

            // Математика векторов "вручную"
            double dirX = x - prevX;
            double dirY = y - prevY;
            double dirZ = z - prevZ;
            double dirLen = Math.sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ);
            if (dirLen > 0) { dirX /= dirLen; dirY /= dirLen; dirZ /= dirLen; }

            double camX = camPos.x - prevX;
            double camY = camPos.y - prevY;
            double camZ = camPos.z - prevZ;
            double camLen = Math.sqrt(camX*camX + camY*camY + camZ*camZ);
            if (camLen > 0) {
                camX /= camLen; camY /= camLen; camZ /= camLen;
            } else {
                camX = 0.0; camY = 1.0; camZ = 0.0;
            }

            // Лента развёрнута к камере, поэтому направление на камеру и есть её нормаль.
            // На формате POSITION_COLOR (debugQuads) setNormal тихо игнорируется — BufferBuilder
            // просто не находит такой элемент, — но если слой когда-нибудь переедет на формат
            // с нормалью, вершины уже будут заполнены полностью и отрисовка не упадёт.
            float nx = (float) camX;
            float ny = (float) camY;
            float nz = (float) camZ;

            // Cross Product (Векторное произведение)
            double rightX = dirY * camZ - dirZ * camY;
            double rightY = dirZ * camX - dirX * camZ;
            double rightZ = dirX * camY - dirY * camX;
            double rightLen = Math.sqrt(rightX*rightX + rightY*rightY + rightZ*rightZ);

            if (rightLen > 0) {
                rightX = (rightX / rightLen) * halfThick;
                rightY = (rightY / rightLen) * halfThick;
                rightZ = (rightZ / rightLen) * halfThick;
            }

            // Отрисовываем 4 точки полигона
            vertexConsumer.addVertex(matrix, (float)(prevX + rightX), (float)(prevY + rightY), (float)(prevZ + rightZ))
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(nx, ny, nz);

            vertexConsumer.addVertex(matrix, (float)(prevX - rightX), (float)(prevY - rightY), (float)(prevZ - rightZ))
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(nx, ny, nz);

            vertexConsumer.addVertex(matrix, (float)(x - rightX), (float)(y - rightY), (float)(z - rightZ))
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(nx, ny, nz);

            vertexConsumer.addVertex(matrix, (float)(x + rightX), (float)(y + rightY), (float)(z + rightZ))
                    .setColor(color[0], color[1], color[2], color[3])
                    .setNormal(nx, ny, nz);

            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }
}
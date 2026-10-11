package com.maxonaddon.client.mixin;

import com.maxonaddon.client.render.RopeRendererUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import daot.HookPoint;
import daot.ODMTickHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = daot.HookLineRenderer.class, remap = false)
public class HookLineRendererMixin {

    @Inject(method = "renderHookLines", at = @At("HEAD"), cancellable = true)
    private static void onRenderHookLines(WorldRenderContext context, CallbackInfo ci) {
        if (!com.maxonaddon.MaxonConfig.get().enableAdvancedHookMechanics) return;
        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        HookPoint leftHook = ODMTickHandler.getLeftHook(player.getUUID());
        HookPoint rightHook = ODMTickHandler.getRightHook(player.getUUID());

        PoseStack poseStack = context.matrixStack();
        Camera camera = context.camera();
        Vec3 camPos = camera.getPosition();

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.debugQuads());

        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        double smoothX = Mth.lerp(partialTick, player.xo, player.getX());
        double smoothY = Mth.lerp(partialTick, player.yo, player.getY());
        double smoothZ = Mth.lerp(partialTick, player.zo, player.getZ());
        Vec3 smoothPlayerPos = new Vec3(smoothX, smoothY, smoothZ);

        float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float yawRad = (float) Math.toRadians(-bodyYaw);

        double offsetX = 0.38;
        double offsetY = 0.6;

        Vec3 leftHipOffset = new Vec3(offsetX, offsetY, 0.0).yRot(yawRad);
        Vec3 rightHipOffset = new Vec3(-offsetX, offsetY, 0.0).yRot(yawRad);

        Vec3 leftStartPos = smoothPlayerPos.add(leftHipOffset);
        Vec3 rightStartPos = smoothPlayerPos.add(rightHipOffset);

        // Получаем вектор скорости игрока для инерции троса
        Vec3 playerVelocity = player.getDeltaMovement();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        int[] blackColor = new int[]{0, 0, 0, 255};
        float thickness = 0.05f; // 5 пикселей

        // ЛЕВЫЙ ТРОС
        if (leftHook != null && (leftHook.active || leftHook.isExtending || leftHook.isRetracting)) {
            Vec3 endPos = leftHook.getCurrentPosition();
            double currentDist = leftStartPos.distanceTo(endPos);

            boolean isFlying = leftHook.isExtending;
            if (leftHook.startPosition != null && leftHook.position != null) {
                double expectedDuration = Math.max(leftHook.startPosition.distanceTo(leftHook.position) * 16.66, 50.0);
                isFlying = (System.currentTimeMillis() - leftHook.shootTime) < expectedDuration;
            }

            double ropeLength = maxon$ropeLength(leftHook, currentDist, isFlying);
            RopeRendererUtils.renderSaggingRope(buffer, poseStack.last().pose(), leftStartPos, endPos, ropeLength, blackColor, camPos, thickness, playerVelocity);
        }

        // ПРАВЫЙ ТРОС
        if (rightHook != null && (rightHook.active || rightHook.isExtending || rightHook.isRetracting)) {
            Vec3 endPos = rightHook.getCurrentPosition();
            double currentDist = rightStartPos.distanceTo(endPos);

            boolean isFlying = rightHook.isExtending;
            if (rightHook.startPosition != null && rightHook.position != null) {
                double expectedDuration = Math.max(rightHook.startPosition.distanceTo(rightHook.position) * 16.66, 50.0);
                isFlying = (System.currentTimeMillis() - rightHook.shootTime) < expectedDuration;
            }

            double ropeLength = maxon$ropeLength(rightHook, currentDist, isFlying);
            RopeRendererUtils.renderSaggingRope(buffer, poseStack.last().pose(), rightStartPos, endPos, ropeLength, blackColor, camPos, thickness, playerVelocity);
        }

        poseStack.popPose();
    }

    /**
     * Сколько троса стравлено. По разнице между этой длиной и расстоянием до крюка
     * RopeRendererUtils и рисует провисание.
     * <p>
     * Раньше здесь стояло само расстояние — то есть длина всегда равнялась натянутой, разница
     * выходила нулевой, и трос рисовался идеально прямым в любой ситуации. Настоящую длину
     * знает физика (см. OdmPhysicsMixin): пробел её сматывает, шифт стравливает. Берём её —
     * и трос провисает, когда его отпустили, и струной вытягивается, когда игрок подтянулся
     * к пределу.
     * <p>
     * В полёте крюка длины ещё нет, там прежняя прибавка к расстоянию; со старой физикой
     * базового мода поведение тоже оставлено как было.
     */
    @Unique
    private static double maxon$ropeLength(HookPoint hook, double currentDist, boolean isFlying) {
        if (isFlying) return currentDist + 3.0;
        if (!com.maxonaddon.MaxonConfig.get().enableOdmPhysics) return currentDist;

        double spooled = hook.maxRopeLength;
        // Ноль означает, что длину ещё никто не выставил.
        return spooled > 0.0 ? Math.max(spooled, currentDist) : currentDist;
    }
}
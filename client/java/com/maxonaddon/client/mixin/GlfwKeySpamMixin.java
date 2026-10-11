package com.maxonaddon.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Window.defaultErrorCallback — это тот самый метод, который печатает
 * "########## GL ERROR ##########" / "@ Pre render" / "65539: Invalid key 3".
 *
 * Колбэк GLFW вызывается синхронно изнутри плохого вызова, поэтому стек
 * показывает точного виновника. Печатаем его 3 раза, дальше глушим спам.
 *
 * remap = false — класс com.mojang.blaze3d не обфусцирован.
 * require = 0 — если имя метода вдруг не совпадёт, игра не упадёт, миксин просто не применится.
 */
@Mixin(value = Window.class, remap = false)
public abstract class GlfwKeySpamMixin {

    @Unique
    private static final Logger MAXON$LOG = LogUtils.getLogger();

    @Unique
    private static int maxon$printed = 0;

    @Unique
    private static long maxon$suppressed = 0L;

    @Inject(method = "defaultErrorCallback", at = @At("HEAD"), cancellable = true, require = 0)
    private void maxon$catchInvalidKey(int error, long description, CallbackInfo ci) {
        String message;
        try {
            message = MemoryUtil.memUTF8(description);
        } catch (Throwable t) {
            return;
        }

        if (message == null || !message.contains("Invalid key")) return;

        if (maxon$printed < 3) {
            maxon$printed++;
            StringBuilder sb = new StringBuilder();
            sb.append("\n=========== [MaxonAddon/GLFW] КТО ДЁРГАЕТ НЕВЕРНУЮ КЛАВИШУ ===========\n");
            sb.append("GLFW error ").append(error).append(": ").append(message).append('\n');
            for (StackTraceElement el : new Throwable().getStackTrace()) {
                sb.append("    ").append(el).append('\n');
            }
            sb.append("======================================================================");
            MAXON$LOG.info(sb.toString());
        } else {
            maxon$suppressed++;
            if (maxon$suppressed % 6000L == 0L) {
                MAXON$LOG.info("[MaxonAddon/GLFW] Подавлено сообщений 'Invalid key': {}", maxon$suppressed);
            }
        }

        ci.cancel(); // спам в консоль не пускаем
    }
}
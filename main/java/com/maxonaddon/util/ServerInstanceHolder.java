package com.maxonaddon.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/** Хранит ссылку на текущий MinecraftServer — нужна там, где под рукой есть только UUID. */
public final class ServerInstanceHolder {

    private static volatile MinecraftServer instance;

    private ServerInstanceHolder() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> instance = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> instance = null);
    }

    public static MinecraftServer get() {
        return instance;
    }
}

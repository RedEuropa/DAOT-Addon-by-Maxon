package com.maxonaddon.client;

import com.maxonaddon.network.ForceDetachPayload;
import com.maxonaddon.network.RequestJamPayload;
import daot.HookPoint;
import daot.ODMTickHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.player.Player;

public class DashOdmClient {

	public static void register() {
		// Регистрируем приемник пакета от сервера
		ClientPlayNetworking.registerGlobalReceiver(ForceDetachPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				Player localPlayer = context.client().player;
				if (localPlayer == null) return;

				// Получаем НАШИ хуки на клиенте
				HookPoint leftHook = ODMTickHandler.getLeftHook(localPlayer.getUUID());
				HookPoint rightHook = ODMTickHandler.getRightHook(localPlayer.getUUID());

				boolean wasDetached = false;

				// Проверяем левый хук: активен ли он и зацеплен ли за того, кто сделал рывок
				if (leftHook != null && leftHook.active && leftHook.hookedEntity != null) {
					if (leftHook.hookedEntity.getId() == payload.targetEntityId()) {
						leftHook.release();
						wasDetached = true;
					}
				}

				// Проверяем правый хук
				if (rightHook != null && rightHook.active && rightHook.hookedEntity != null) {
					if (rightHook.hookedEntity.getId() == payload.targetEntityId()) {
						rightHook.release();
						wasDetached = true;
					}
				}

				// Если мы действительно потеряли зацеп из-за деша — просим сервер заклинить УПМ!
				if (wasDetached) {
					ClientPlayNetworking.send(new RequestJamPayload());
				}
			});
		});
	}
}
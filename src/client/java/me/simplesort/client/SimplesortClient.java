package me.simplesort.client;

import me.simplesort.network.SortTrailPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class SimplesortClient implements ClientModInitializer {
	@SuppressWarnings("null")
	@Override
	public void onInitializeClient() {

		SortTrailRenderer.register();

		// Registers the client-side receiver for sort trail payloads
		ClientPlayNetworking.registerGlobalReceiver(SortTrailPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				SortTrailRenderer.addTrails(payload.successes(), payload.fails(), payload.partials());
			});
		});

	}
}
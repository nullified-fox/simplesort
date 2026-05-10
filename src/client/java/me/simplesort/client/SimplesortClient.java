/**
    Copyright (c) 2026 FanaticalFoxx. All Rights Reserved.

    You may NOT:
    - Redistribute, copy, or mirror the source code or compiled files
    - Modify or create derivative works
    - Reupload to any mod hosting platform

    You MAY:
    - Include this mod in modpacks (public or private) without prior permission
    - Share modpack links that reference the official download
 */

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
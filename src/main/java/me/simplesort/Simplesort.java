package me.simplesort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.simplesort.block.SortingHubBlock;
import me.simplesort.config.ModConfig;
import me.simplesort.network.SortTrailPayload;
import me.simplesort.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class Simplesort implements ModInitializer {
	public static final String MOD_ID = "simple-sort";
	public static final Logger LOGGER = LoggerFactory.getLogger("Simple Sort");

	public static final int SORT_RADIUS = 10; // Default radius for sorting, in blocks

	@SuppressWarnings("null")
	@Override
	public void onInitialize() {
		// Register the mod config
		AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);

		// Initializes blocks and creative tab
		ModBlocks.initialize();

		// Register the custom payload for sort trails
		PayloadTypeRegistry.clientboundPlay().register(SortTrailPayload.TYPE, SortTrailPayload.S_CODEC);

		// Server tick event for handling radius viewer particle effects
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) return; // every second
			SortingHubBlock.tickRadiusViewers(server);
		});
	}

} 
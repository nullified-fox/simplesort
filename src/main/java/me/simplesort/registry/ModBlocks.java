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

package me.simplesort.registry;

import java.util.Objects;
import java.util.function.Function;

import me.simplesort.Simplesort;
import me.simplesort.block.SortingHubBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    //region BLOCKS & ITEMS

    public static final Block sort_hub = register(
        "sorting_hub", SortingHubBlock::new, BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .destroyTime(1.0f)
            .explosionResistance(6.0f)
            .requiresCorrectToolForDrops()
            , true);   
    
    //endregion 


    //region CORE METHODS

    /**
     * Initializes the blocks
     */
    public static void initialize() {
        Simplesort.LOGGER.info("[Simple Sort] Successfully registered blocks");

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .register((creativeTab) -> creativeTab.accept(ModBlocks.sort_hub.asItem()));
    }

    /**
     * Registers a block and optionally its corresponding item
     * @param name
     * @param blockFactory
     * @param settings
     * @param shouldRegisterItem
     * @return
     */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties settings, boolean shouldRegisterItem) {
		ResourceKey<Block> blockKey = keyOfBlock(name);

		Block block = blockFactory.apply(settings);

		if (shouldRegisterItem) {
			ResourceKey<Item> itemKey = keyOfItem(name);

            Simplesort.LOGGER.info("[Simple Sort] Registered item: {}", itemKey.toString());

            // 
            
			BlockItem blockItem = new BlockItem(block, new Item.Properties());

            // If for some reason, I don't know how. This will throw an exception and crash the game during startup, which is better than silently failing.
			Objects.requireNonNull(Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem), "Failed to register item: " + name); 
		}

        Simplesort.LOGGER.info("[Simple Sort] Registered block: {}", name);

        // Same as above, if it do failith, then crashith with a clear error message.
		return Objects.requireNonNull(Registry.register(BuiltInRegistries.BLOCK, blockKey, block), "Failed to register block: " + name);
	}

    /**
     * Helper method to create a ResourceKey for a block based on the mod's namespace and the provided name
     * @param name
     * @return
     */
    private static ResourceKey<Block> keyOfBlock(String name) {
		return ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Simplesort.MOD_ID, name));
	}

    /**
     * Helper method to create a ResourceKey for an item based on the mod's namespace and the provided name
     * @param name
     * @return
     */
	private static ResourceKey<Item> keyOfItem(String name) {
		return ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Simplesort.MOD_ID, name));
	}

    //endregion 

}
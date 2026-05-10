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

/*

    Whole lotta complicated mess imo. I used open-source resources to figure out how to do a lot of this, alongside some general knowledge. If there is a better way, let me know and I'll be happy to optimize it.

*/

package me.simplesort.sorting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import me.shedaniel.autoconfig.AutoConfig;
import me.simplesort.Simplesort;
import me.simplesort.config.ModConfig;
import me.simplesort.util.ContainerUtils;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

public class SortUtils {

    private static final int PLAYER_INVENTORY_START = 9; // Starting index for the player's inventory (Excludes the hotbar)
    private static final int PLAYER_INVENTORY_END = 36; // Ending index for the player's inventory (Excludes the hotbar)
    
    /**
     * Sorts items from the player's inventory into nearby containers based on filters and container contents. THIS IS THE TRUE LOGIC
     * @param player - the player whose inventory will be sorted
     * @param centralPos - the central position to search around for nearby containers to sort into
     * @return
     */
    @SuppressWarnings("null")
    public static SortResult sortToNearbyContainers(Player player, BlockPos centralPos) {
        if (player.level().isClientSide())
            return new SortResult(SortStatus.NO_CONTAINERS, Set.of(), Set.of(), Set.of(), Map.of());

        ServerLevel level = (ServerLevel) player.level();
        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        Set<BlockPos> visited = new HashSet<>();

        Map<Item, List<Storage<ItemVariant>>> frameStorages = new HashMap<>(); // Stores containers with items in item frames on them, prioritized over regular containers
        Map<Item, List<Storage<ItemVariant>>> contentStorages = new HashMap<>(); // Stores containers with items in their inventory
        Map<Storage<ItemVariant>, BlockPos> storageToPos = new HashMap<>(); // Maps storages back to their positions for result reporting

        int radius = Simplesort.SORT_RADIUS; // Search radius in blocks for nearby containers

        int minChunkX = (centralPos.getX() - radius) >> 4;
        int maxChunkX = (centralPos.getX() + radius) >> 4;
        int minChunkZ = (centralPos.getZ() - radius) >> 4;
        int maxChunkZ = (centralPos.getZ() + radius) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);

                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.equals(centralPos))
                        continue;
                    if (Math.abs(pos.getX() - centralPos.getX()) > radius)
                        continue;
                    if (Math.abs(pos.getY() - centralPos.getY()) > radius)
                        continue;
                    if (Math.abs(pos.getZ() - centralPos.getZ()) > radius)
                        continue;
                    if (!visited.add(pos))
                        continue;

                    Storage<ItemVariant> storage = ContainerUtils.getStorage(level, pos);
                    if (storage == null)
                        continue;

                    // To respect container access restrictions (like locked chests), I added this here to check if the player can interact with the container at all before trying to move items. This will prevent players from transferring items into containers that they wouldn't normally be able to interact with, which could lead to items being "lost" in inaccessible containers. Luckily most external mods use this event to check for access permissions!
                    if (!ContainerUtils.canPlayerAccess(player, level, pos)) continue;

                    // Checking for double containers (like double chests) and adding both sides to the visited set to avoid processing them separately
                    // Ensures that items in both halves of a double chest are recognized and that the chest is only processed once
                    BlockState state = level.getBlockState(pos);
                    BlockPos connectedPos = null;
                    if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        connectedPos = pos.relative(ChestBlock.getConnectedDirection(state));
                        visited.add(connectedPos);
                    }

                    storageToPos.put(storage, pos);

                    Set<Item> frameItems = config.useItemFrameLabels ? ContainerUtils.getItemFrameItems(level, pos) : Set.of();
                    Set<Item> seenItems = new HashSet<>();

                    if (connectedPos != null && config.useItemFrameLabels) frameItems.addAll(ContainerUtils.getItemFrameItems(level, connectedPos));

                    // Keeps track of what items are displayed in item frames on the container, prioritized over regular container contents
                    // This is what allows the sorting to prioritize item frames as "labels" for what items should be sorted.
                    for (Item frameItem : frameItems) {
                        if (seenItems.add(frameItem)) {
                            frameStorages.computeIfAbsent(frameItem, k -> new ArrayList<>()).add(storage);
                        }
                    }

                    // If no item frames, fall back to regular container contents.
                    if (frameItems.isEmpty()) {
                        for (StorageView<ItemVariant> view : storage.nonEmptyViews()) {
                            Item item = view.getResource().getItem();
                            if (seenItems.add(item)) {
                                contentStorages.computeIfAbsent(item, k -> new ArrayList<>()).add(storage);
                            }
                        }
                    }
                }
            }
        }

        if (storageToPos.isEmpty())
            return new SortResult(SortStatus.NO_CONTAINERS, Set.of(), Set.of(), Set.of(), Map.of());

        PlayerInventoryStorage playerStorage = PlayerInventoryStorage.of(player);
        List<SingleSlotStorage<ItemVariant>> slots = playerStorage.getSlots();

        Set<BlockPos> transferredPositions = new HashSet<>();
        Set<BlockPos> failedPositions = new HashSet<>();
        Map<Item, Integer> unplacedItems = new HashMap<>();
        boolean anyMatch = false;
        boolean anyTransferred = false;

        // Hotbar = 0-8, main inventory = 9-35. Include hotbar only if configured.
        int slotStart = config.sortHotbar ? 0 : PLAYER_INVENTORY_START;
        for (int i = slotStart; i < PLAYER_INVENTORY_END; i++) {
            SingleSlotStorage<ItemVariant> slot = slots.get(i);
            if (slot.isResourceBlank())
                continue;

            Item item = slot.getResource().getItem();
            if (!frameStorages.containsKey(item) && !contentStorages.containsKey(item))
                continue;

            anyMatch = true;
            long amountBefore = slot.getAmount();
            if (tryMoveSlot(slot, frameStorages, contentStorages, storageToPos, transferredPositions)) {
                anyTransferred = true;
                if (!slot.isResourceBlank()) {
                    // Called when some but not all items from the stack were transferred, necessary for reporting back to the player how many items were transferred vs not transferred.
                    unplacedItems.merge(item, (int) slot.getAmount(), Integer::sum);
                    List<Storage<ItemVariant>> targets = new ArrayList<>();

                    // If the item was only partially transferred, we consider it a "failed" transfer for the purposes of result reporting, even though some items were transferred. This is because the presence of unplaced items indicates that the sorting was not fully successful for that item stack.
                    if (frameStorages.containsKey(item)) targets.addAll(frameStorages.get(item));
                    if (contentStorages.containsKey(item)) targets.addAll(contentStorages.get(item));
                    for (Storage<ItemVariant> target : targets) {
                        failedPositions.add(storageToPos.get(target));
                    }
                }
            } else {
                // Called when no items from the stack were transferred at all, necessary for reporting back to the player that the transfer failed and how many items were not transferred.
                unplacedItems.merge(item, (int) amountBefore, Integer::sum);
                List<Storage<ItemVariant>> targets = new ArrayList<>();

                // Same as above, if no items were transferred we consider it a "failed" transfer for result reporting, and add all relevant containers to the failed positions set.
                if (frameStorages.containsKey(item)) targets.addAll(frameStorages.get(item));
                if (contentStorages.containsKey(item)) targets.addAll(contentStorages.get(item));
                for (Storage<ItemVariant> target : targets) {
                    failedPositions.add(storageToPos.get(target));
                }
            }
                
        }

        // Configurable option to also sort offhand items, which are outside the main inventory and thus not sorted by default
        if (config.sortOffhand) {
            SingleSlotStorage<ItemVariant> offhand = slots.get(40);
            if (!offhand.isResourceBlank()) {
                Item item = offhand.getResource().getItem();
                if (frameStorages.containsKey(item) || contentStorages.containsKey(item)) {
                    anyMatch = true;
                    long offhandBefore = offhand.getAmount();
                    if (tryMoveSlot(offhand, frameStorages, contentStorages, storageToPos, transferredPositions)) {
                        anyTransferred = true;
                        if (!offhand.isResourceBlank())
                            unplacedItems.merge(item, (int) offhand.getAmount(), Integer::sum);
                    } else {
                        unplacedItems.merge(item, (int) offhandBefore, Integer::sum);
                    }
                }
            }
        }

        // Called when no items were transferred to any container, due to no matching containers
        if (!anyMatch)
            return new SortResult(SortStatus.NO_MATCHING_CONTAINER, Set.of(), Set.of(), Set.of(), Map.of());

        // Called when no items were transferred to any container, due to all matching containers being full or inaccessible. 
        if (!anyTransferred)
            return new SortResult(SortStatus.CONTAINERS_FULL, Set.of(), failedPositions, Set.of(), Map.of());

        // Called when at least some items were transferred, but failed transfers were also present.
        // Necessary for telling the player that the sorting was partially successful.
        if (!failedPositions.isEmpty()) {
            Set<BlockPos> truePartials = new HashSet<>(failedPositions);
            truePartials.retainAll(transferredPositions);
            Set<BlockPos> trueFailed = new HashSet<>(failedPositions);
            trueFailed.removeAll(transferredPositions);
            return new SortResult(SortStatus.PARTIAL_SUCCESS, transferredPositions, trueFailed, truePartials, unplacedItems);
        }

        return new SortResult(SortStatus.SUCCESS, transferredPositions, Set.of(), Set.of(), Map.of());
    }

    /**
     * Transfers items from the player's inventory to a target container at the specified position.
     * @param player - the player whose inventory will be sorted
     * @param pos - the position of the target container to sort into
     * @return
     */
    public static boolean transferItems(Player player, @NonNull BlockPos pos) {
        Storage<ItemVariant> targetStorage = ContainerUtils.getStorage(player.level(), pos);
        if (targetStorage == null)
            return false;

        // Retrieves the configuration instance
        // Necessary for checking the "sortHotbar" option
        // Necessary for checking the "useItemFrameLabels" option to determine what items to sort and where to sort them.
        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        PlayerInventoryStorage playerStorage = PlayerInventoryStorage.of(player);
        List<SingleSlotStorage<ItemVariant>> slots = playerStorage.getSlots();

        Set<Item> targetItems = new HashSet<>();
        for (StorageView<ItemVariant> view : targetStorage.nonEmptyViews()) {
            targetItems.add(view.getResource().getItem());
        }

        if (config.useItemFrameLabels)
            targetItems.addAll(ContainerUtils.getItemFrameItems(player.level(), pos));

        // Checks neighboring container aka double chest
        BlockState state = player.level().getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            targetItems.addAll(ContainerUtils.getItemFrameItems(player.level(), pos.relative(ChestBlock.getConnectedDirection(state))));
        }

        boolean transferred = false;
        int slotStart = config.sortHotbar ? 0 : PLAYER_INVENTORY_START;
        for (int i = slotStart; i < PLAYER_INVENTORY_END; i++) {
            SingleSlotStorage<ItemVariant> slot = slots.get(i);
            if (slot.isResourceBlank())
                continue;

            ItemVariant variant = slot.getResource();
            if (!targetItems.contains(variant.getItem()))
                continue;

            long amount = StorageUtil.move(slot, targetStorage, v -> v.equals(variant), slot.getAmount(), null);
            if (amount > 0)
                transferred = true;
        }

        return transferred;
    }    

    /**
     * Attempts to move items from a player's inventory slot to nearby containers that match the item type, and filters
     * @param slot - the inventory slot to transfer from
     * @param frameStorages - map of items to nearby storages that have those items in item frames on them, prioritized for sorting
     * @param contentStorages - map of items to nearby storages that have those items in their inventory, used as a fallback if no item frame labels are present
     * @param storageToPos - map of storages to their corresponding block positions, used for result reporting
     * @param transferred - set of block positions that have already had items transferred to them
     * @return
     */
    private static boolean tryMoveSlot(SingleSlotStorage<ItemVariant> slot,
            Map<Item, List<Storage<ItemVariant>>> frameStorages,
            Map<Item, List<Storage<ItemVariant>>> contentStorages,
            Map<Storage<ItemVariant>, BlockPos> storageToPos,
            Set<BlockPos> transferred) {
        ItemVariant variant = slot.getResource();
        List<Storage<ItemVariant>> frameTargets = frameStorages.get(variant.getItem());
        List<Storage<ItemVariant>> contentTargets = contentStorages.get(variant.getItem());

        List<Storage<ItemVariant>> targets = new ArrayList<>();
        if (frameTargets != null) targets.addAll(frameTargets);
        if (contentTargets != null) targets.addAll(contentTargets);

        boolean moved = false;
        for (Storage<ItemVariant> target : targets) {
            long amount = StorageUtil.move(slot, target, v -> v.equals(variant), slot.getAmount(), null);
            if (amount > 0) {
                moved = true;
                transferred.add(storageToPos.get(target));
            }
            if (slot.isResourceBlank()) break;
        }
        return moved;
    }

}

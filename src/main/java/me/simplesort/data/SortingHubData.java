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

package me.simplesort.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class SortingHubData extends SavedData {

    // region Serialization

    public static final SavedData.Factory<SortingHubData> FACTORY = new SavedData.Factory<>(
        SortingHubData::new, 
        SortingHubData::load, 
        null
    );

    private final Map<BlockPos, UUID> lockedBlocks = new HashMap<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, UUID> entry : lockedBlocks.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLongArray("pos", new long[]{entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ()});
            entryTag.putUUID("uuid", entry.getValue());
            list.add(entryTag);
        }
        tag.put("locked", list);
        return tag;
    }

    public static SortingHubData load(CompoundTag tag, HolderLookup.Provider registries) {
        SortingHubData data = new SortingHubData();
        ListTag list = tag.getList("locked", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            UUID uuid = entryTag.getUUID("uuid");
            data.lockedBlocks.put(pos, uuid);
        }
        return data;
    }

    // endregion

    // region Fields & Accessor


    public static SortingHubData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, "sorting_hub_data");
    }

    // endregion

    // region Mutators

    public boolean isLocked(BlockPos pos) {
        return lockedBlocks.containsKey(pos);
    }

    public boolean isLockedBy(BlockPos pos, UUID playerUuid) {
        return playerUuid.equals(lockedBlocks.get(pos));
    }

    public void lock(BlockPos pos, UUID playerUuid) {
        lockedBlocks.put(pos, playerUuid);
        setDirty();
    }

    public void unlock(BlockPos pos) {
        lockedBlocks.remove(pos);
        setDirty();
    }

    // endregion

}
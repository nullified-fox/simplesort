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

import org.jspecify.annotations.NonNull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.simplesort.Simplesort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SortingHubData extends SavedData {

    // region Serialization

    private record LockedEntry(BlockPos pos, UUID uuid) {
        static final Codec<LockedEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(LockedEntry::pos),
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(LockedEntry::uuid)
            ).apply(instance, LockedEntry::new));
    }

    public static final Codec<SortingHubData> CODEC = LockedEntry.CODEC.listOf().xmap(
        list -> {
            SortingHubData data = new SortingHubData();
            list.forEach(entry -> data.lockedBlocks.put(entry.pos(), entry.uuid()));
            return data;
        },
        data -> data.lockedBlocks.entrySet().stream()
            .map(e -> new LockedEntry(e.getKey(), e.getValue()))
            .toList());

    @SuppressWarnings("null")
    public static final @NonNull SavedDataType<SortingHubData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Simplesort.MOD_ID, "sorting_hub"),
        SortingHubData::new,
        CODEC,
        null);

    // endregion

    // region Fields & Accessor

    private final Map<BlockPos, UUID> lockedBlocks = new HashMap<>();

    public static SortingHubData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
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
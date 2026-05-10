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

package me.simplesort.network;

import java.util.List;

import me.simplesort.Simplesort;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SortTrailPayload(BlockPos center, List<BlockPos> successes, List<BlockPos> fails, List<BlockPos> partials) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SortTrailPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Simplesort.MOD_ID, "sort_trail"));

    public static final StreamCodec<FriendlyByteBuf, SortTrailPayload> S_CODEC = StreamCodec.of(SortTrailPayload::encode, SortTrailPayload::decode);

    @SuppressWarnings("null")
    public static void encode(FriendlyByteBuf buffer, SortTrailPayload payload) {
        buffer.writeBlockPos(payload.center());
        buffer.writeVarInt(payload.successes().size());
        for (BlockPos pos : payload.successes()) {
            buffer.writeBlockPos(pos);
        }
        buffer.writeVarInt(payload.fails().size());
        for (BlockPos pos : payload.fails()) {
            buffer.writeBlockPos(pos);
        }
        buffer.writeVarInt(payload.partials().size());
        for (BlockPos pos : payload.partials()) {
            buffer.writeBlockPos(pos);
        }
    }

    public static SortTrailPayload decode(FriendlyByteBuf buffer) {
        BlockPos center = buffer.readBlockPos();
        int successCount = buffer.readVarInt();
        List<BlockPos> successes = new java.util.ArrayList<>();
        for (int i = 0; i < successCount; i++) {
            successes.add(buffer.readBlockPos());
        }
        int failsCount = buffer.readVarInt();
        List<BlockPos> fails = new java.util.ArrayList<>();
        for (int i = 0; i < failsCount; i++) {
            fails.add(buffer.readBlockPos());
        }
        int partialsCount = buffer.readVarInt();
        List<BlockPos> partials = new java.util.ArrayList<>();
        for (int i = 0; i < partialsCount; i++) {
            partials.add(buffer.readBlockPos());
        }
        return new SortTrailPayload(center, successes, fails, partials);
    }

    @SuppressWarnings("null")
    public CustomPacketPayload.Type<SortTrailPayload> type() {
        return TYPE;
    }

}
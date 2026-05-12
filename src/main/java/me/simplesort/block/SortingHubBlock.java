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

package me.simplesort.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import me.simplesort.Simplesort;
import me.simplesort.data.SortingHubData;
import me.simplesort.network.SortTrailPayload;
import me.simplesort.sorting.SortResult;
import me.simplesort.sorting.SortStatus;
import me.simplesort.sorting.SortUtils;
import me.simplesort.util.ContainerUtils;
import me.simplesort.util.FeedbackUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SortingHubBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SortingHubBlock> CODEC = simpleCodec(SortingHubBlock::new);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    private static final Map<UUID, Long> lastSortTimes = new HashMap<>();

    private static final Map<UUID, BlockPos> radiusViewers = new HashMap<>();

    private static final int DEFAULT_COOLDOWN_MS = 3000;

    public SortingHubBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    // region Block Identity

    @Override
    public MapCodec<SortingHubBlock> codec() {
        return CODEC;
    }

    // endregion

    // region Block State

    /**
     * Sets the block's facing direction based on the player's orientation when placing the block.
     * @param context The context of the block placement, containing information about the player and the world.
     * @return The BlockState with the FACING property set to the opposite of the player's horizontal direction.
     */
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Animates the block by spawning particles around it.
     * @param state The current state of the block
     * @param level The level in which the block is located
     * @param pos The position of the block
     * @param random Random source
     */
    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
        final double x = pos.getX() + 0.2 + (random.nextDouble() * 0.6);
        final double y = pos.getY() + 0.25;
        final double z = pos.getZ() + 0.2 + (random.nextDouble() * 0.6);

        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.02, 0.0);
    }

    /**
     * Handles the logic when an entity steps on the block. If the entity is a player, it attempts to sort their inventory into nearby containers.
     * It also implements a cooldown mechanism to prevent spamming the sorting action.
     * @param level The level in which the block is located
     * @param pos The position of the block
     * @param state The current state of the block
     * @param entity The entity that stepped on the block
     */
    @Override
    public void stepOn(final Level level, final BlockPos pos, final BlockState state, final Entity entity) {
        if (level.isClientSide()) return;
        if (!(entity instanceof final ServerPlayer sp)) return;

        // Checks if the player can access the block; if not, sends a feedback message and returns early
        if (!ContainerUtils.canPlayerAccess(sp, level, pos)) {
            FeedbackUtils.onSortResult(sp, new SortResult(SortStatus.LACKING_PERMISSIONS, Set.of(), Set.of(), Set.of(), Map.of()));
            return;
        }

        // Checks if the hub is locked by another player; if so, sends a feedback message and returns early
        SortingHubData hubData = SortingHubData.get(level.getServer());
        if (hubData.isLocked(pos) && !hubData.isLockedBy(pos, sp.getUUID())) {
            FeedbackUtils.onSortResult(sp, new SortResult(SortStatus.HUB_LOCKED, Set.of(), Set.of(), Set.of(), Map.of()));
            return;
        }

        final long now = System.currentTimeMillis();
        final Long lastTime = lastSortTimes.get(sp.getUUID());

        if (lastTime != null && now - lastTime < DEFAULT_COOLDOWN_MS) return;

        try {
            final SortResult result = SortUtils.sortToNearbyContainers(sp, pos);
            lastSortTimes.put(sp.getUUID(), now);
            FeedbackUtils.onSortResult(sp, result);

            List<BlockPos> successes = new ArrayList<>(result.affectedContainers()); // Stores complete successes
            List<BlockPos> failures = new ArrayList<>(result.failedContainers()); // Stores complete failures
            List<BlockPos> partials = new ArrayList<>(result.partialContainers()); // Stores partial successes

            ServerPlayNetworking.send(sp, new SortTrailPayload(pos, successes, failures, partials));
        } catch (final Exception e) {
            Simplesort.LOGGER.error("Unexpected error whilst sorting for {}: {}", sp.getName().getString(), e.getMessage(), e);
            FeedbackUtils.onSortResult(sp, new SortResult(SortStatus.NO_CONTAINERS, Set.of(), Set.of(), Set.of(), Map.of()));
        }
    }

    /**
     * Handles interactions with the block
     * @param state The current state of the block
     * @param level The level in which the block is located
     * @param pos The position of the block
     * @param player The player interacting with the block
     * @param hit The result of the block hit
     * @return The result of the interaction, indicating whether it was successful or not
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        // Locks/Unlocks the hub
        if (player.isShiftKeyDown()) {
            SortingHubData hubData = SortingHubData.get(level.getServer());
            if (hubData.isLockedBy(pos, sp.getUUID())) {
                hubData.unlock(pos);
                FeedbackUtils.onLockToggle(sp, false);
            } else if (!hubData.isLocked(pos)) {
                hubData.lock(pos, sp.getUUID());
                FeedbackUtils.onLockToggle(sp, true);
            } else {
                FeedbackUtils.onSortResult(sp, new SortResult(SortStatus.HUB_LOCKED, Set.of(), Set.of(), Set.of(), Map.of()));
            }
            return InteractionResult.SUCCESS;
        }

        // Toggle radius viewer
        UUID playerId = sp.getUUID();
        if (radiusViewers.remove(playerId) != null) {
            FeedbackUtils.onRadiusToggle(sp, false);
            return InteractionResult.SUCCESS;
        } else {
            radiusViewers.put(playerId, pos);
            FeedbackUtils.onRadiusToggle(sp, true);
            showRadiusParticles((ServerLevel) level, pos, sp);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Called on block removal; cleans up any radius viewers that were viewing this block.
     */
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        radiusViewers.values().removeIf(storedPos -> storedPos.equals(pos));
        super.destroy(level, pos, state);
    }

    /**
     * Prevents the block from being placed on top of another Sorting Hub block, ensuring that hubs cannot be stacked vertically.
     * @param state The current state of the block
     * @param world The block reader for the world
     * @param pos The position where the block is being placed
     */
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return !(world.getBlockState(pos.below()).getBlock() instanceof SortingHubBlock);
    }

    // endregion

    // region Shape

    /**
     * Adds the FACING property to the block's state definition, allowing the block to have different states based on its orientation.
     * @param builder The StateDefinition.Builder used to define the block's state properties.
     */
    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // endregion

    // region Visuals
    
    /**
     * Sends particles to the player to visually indicate the sorting radius around the block.
     * @param level
     * @param pos
     * @param sp
     */
    private static void showRadiusParticles(ServerLevel level, BlockPos pos, ServerPlayer sp) {
        int radius = Simplesort.SORT_RADIUS;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double minY = cy - radius, maxY = cy + radius;
        int steps = 60;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double span = radius * 2;

            // Bottom face (4 edges at minY)
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius + t * span, minY, cz - radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius + t * span, minY, cz + radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius, minY, cz - radius + t * span, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx + radius, minY, cz - radius + t * span, 1, 0,0,0,0);

            // Top face (4 edges at maxY)
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius + t * span, maxY, cz - radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius + t * span, maxY, cz + radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius, maxY, cz - radius + t * span, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx + radius, maxY, cz - radius + t * span, 1, 0,0,0,0);

            // 4 vertical corner edges
            double vy = minY + t * span;
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius, vy, cz - radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx + radius, vy, cz - radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx - radius, vy, cz + radius, 1, 0,0,0,0);
            level.sendParticles(sp, ParticleTypes.END_ROD, true, cx + radius, vy, cz + radius, 1, 0,0,0,0);
        }
    }

    /**
     * Ticks the radius viewers, sending particle updates to any players currently viewing the sorting radius.
     * @param server
     */
    public static void tickRadiusViewers(MinecraftServer server) {
        radiusViewers.entrySet().removeIf(entry -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(entry.getKey());
            if (sp == null) return true; // player logged off, clean up
            showRadiusParticles((ServerLevel) sp.level(), entry.getValue(), sp);
            return false;
        });
    }

    // endregion

    // region Sorting

    /**
     * Retrieves the shape of the block
     * @param state The current state of the block
     * @param world The block getter for the world
     * @param pos The position of the block
     * @param context The collision context for the block
     * @return The VoxelShape representing the shape of the block
     */
    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter world, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }

    // endregion
}

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class SortTrailRenderer {

    private record PendingFlash(BlockPos pos, ParticleOptions particle, int ticks) {}

    private static final List<PendingFlash> activeFlashes = new ArrayList<>();

    private static final ParticleOptions PARTICLE_SUCCESS = new DustParticleOptions(0x00FF00, 1.0f); // Green color for successful sorts
    private static final ParticleOptions PARTICLE_FAILURE = new DustParticleOptions(0xFF3333, 1.0f); // Red color for failed sorts
    private static final ParticleOptions PARTICLE_PARTIAL = new DustParticleOptions(0xFF8700, 1.0f); // Orange color for partial sorts

    private static final int EDGE_STEPS = 5;       // Number of particles to spawn along each edge of the block
    private static final int FLASH_DURATION = 60;  // Total ticks to keep the outline visible (~3 seconds)
    private static final int SPAWN_INTERVAL = 10;  // Ticks between respawns, matching dust particle lifetime


    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;
            tick(client.level);
        });
    }

    public static void addTrails(List<BlockPos> successes, List<BlockPos> failures, List<BlockPos> partials) {
        Set<BlockPos> partialSet = new HashSet<>(partials);
        for (BlockPos pos : successes) {
            if (!partialSet.contains(pos)) activeFlashes.add(new PendingFlash(pos, PARTICLE_SUCCESS, FLASH_DURATION));
        }
        for (BlockPos pos : failures) activeFlashes.add(new PendingFlash(pos, PARTICLE_FAILURE, FLASH_DURATION));
        for (BlockPos pos : partials) activeFlashes.add(new PendingFlash(pos, PARTICLE_PARTIAL, FLASH_DURATION));
    }

    private static void tick(Level level) {
        activeFlashes.replaceAll(f -> new PendingFlash(f.pos(), f.particle(), f.ticks() - 1));
        activeFlashes.removeIf(f -> {
            if (f.ticks() % SPAWN_INTERVAL == 0) flash(level, f.pos(), f.particle());
            return f.ticks() <= 0;
        });
    }

    private static void flash(Level level, BlockPos pos, ParticleOptions particle) {

        double x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
        double x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
        
        // Checks for double chests
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction dir = ChestBlock.getConnectedDirection(state);
            x0 = Math.min(x0, x0 + dir.getStepX());
            y0 = Math.min(y0, y0 + dir.getStepY());
            z0 = Math.min(z0, z0 + dir.getStepZ());
            x1 = Math.max(x1, x1 + dir.getStepX());
            y1 = Math.max(y1, y1 + dir.getStepY());
            z1 = Math.max(z1, z1 + dir.getStepZ());
        }

        for (int i = 0; i <= EDGE_STEPS; i++) {
            double t = i / (double) EDGE_STEPS;
            double mx = Mth.lerp(t, x0, x1);
            double my = Mth.lerp(t, y0, y1);
            double mz = Mth.lerp(t, z0, z1);

            // 4 edges parallel to X
            level.addParticle(particle, mx, y0, z0, 0, 0, 0);
            level.addParticle(particle, mx, y1, z0, 0, 0, 0);
            level.addParticle(particle, mx, y0, z1, 0, 0, 0);
            level.addParticle(particle, mx, y1, z1, 0, 0, 0);

            // 4 edges parallel to Y
            level.addParticle(particle, x0, my, z0, 0, 0, 0);
            level.addParticle(particle, x1, my, z0, 0, 0, 0);
            level.addParticle(particle, x0, my, z1, 0, 0, 0);
            level.addParticle(particle, x1, my, z1, 0, 0, 0);

            // 4 edges parallel to Z
            level.addParticle(particle, x0, y0, mz, 0, 0, 0);
            level.addParticle(particle, x1, y0, mz, 0, 0, 0);
            level.addParticle(particle, x0, y1, mz, 0, 0, 0);
            level.addParticle(particle, x1, y1, mz, 0, 0, 0);
        }
    }

}


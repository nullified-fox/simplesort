package me.simplesort.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ContainerUtils {

    /**
     * Uses Fabric's Item Storage API to get the storage of a block at the given position.
     * @param level - The level to get the storage from.
     * @param pos - The position of the block to get the storage from.
     * @return
     */
    public static Storage<ItemVariant> getStorage(@NonNull Level level, @NonNull BlockPos pos) {
        return ItemStorage.SIDED.find(level, pos, null);
    }

    /**
     * Gets the items in item frames attached to the block at the given position.
     * @param level - The level to get the item frames from.
     * @param pos - The position of the block to get the item frames from.
     * @return
     */
    @SuppressWarnings("null")
    public static Set<Item> getItemFrameItems(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.5);
        List<GlowItemFrame> frames = level.getEntitiesOfClass(GlowItemFrame.class, box,
                f -> !f.getItem().isEmpty()
                        && f.blockPosition().relative(f.getDirection().getOpposite()).equals(pos));
        Set<Item> items = new HashSet<>();

        for (GlowItemFrame frame : frames) {
            items.add(frame.getItem().getItem());
        }

        return items;
    }

    /**
     * Checks if the player can access the block at the given position by simulating a right-click interaction with a fake BlockHitResult.
     * @param player - The player to check access for.
     * @param level - The level the block is in.
     * @param pos - The position of the block to check access for.
     * @return
     */
    public static boolean canPlayerAccess(@NonNull Player player, @NonNull Level level, @NonNull BlockPos pos) {
        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, fakeHit) != InteractionResult.FAIL;
    }

}

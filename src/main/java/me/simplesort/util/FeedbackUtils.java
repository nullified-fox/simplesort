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

package me.simplesort.util;

import java.util.Map;

import me.shedaniel.autoconfig.AutoConfig;
import me.simplesort.Simplesort;
import me.simplesort.config.ModConfig;
import me.simplesort.sorting.SortResult;
import me.simplesort.sorting.SortStatus;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class FeedbackUtils {

    private record Feedback(SoundEvent sound, float pitch, String translationKey, int color, String logTemplate) {}

    private static final Map<SortStatus, Feedback> FEEDBACK_MAP = Map.of(
        SortStatus.SUCCESS,               new Feedback(SoundEvents.BEACON_ACTIVATE,   1.4f, "simple-sort.text.transferred",     0x55FF55, "{} sorted items into {} containers"),
        SortStatus.PARTIAL_SUCCESS,       new Feedback(SoundEvents.BEACON_DEACTIVATE, 1.3f, "simple-sort.text.partial_transfer", 0xFFAA00, "{} sorted some items into {} containers, but not all could be transferred"),
        SortStatus.NO_MATCHING_CONTAINER, new Feedback(SoundEvents.BEACON_DEACTIVATE, 1.3f, "simple-sort.text.no_items",         0xFFAA00, "{} - No matching items to transfer"),
        SortStatus.CONTAINERS_FULL,       new Feedback(SoundEvents.BEACON_DEACTIVATE, 1.3f, "simple-sort.text.not_enough_space", 0xFF5555, "{} - All nearby containers are full"),
        SortStatus.NO_CONTAINERS,         new Feedback(SoundEvents.BEACON_DEACTIVATE, 1.3f, "simple-sort.text.no_containers",    0xFFAA00, "{} - No containers found nearby"),
        SortStatus.HUB_LOCKED,           new Feedback(SoundEvents.BEACON_DEACTIVATE, 0.8f, "simple-sort.text.hub_locked",       0xFF5555, "{} - This sorting hub is currently locked by another player"),
        SortStatus.LACKING_PERMISSIONS,   new Feedback(null, 0f, "simple-sort.text.no_permission",    0xFF5555, "{} - You don't have permission to use this sorting hub")
    );

    public static void onSortResult(ServerPlayer player, SortResult result) {
        Feedback feedback = FEEDBACK_MAP.get(result.status());
        if (feedback == null) throw new IllegalArgumentException("Unexpected SortStatus: " + result.status());

        if (result.status() == SortStatus.SUCCESS || result.status() == SortStatus.PARTIAL_SUCCESS) {
            Simplesort.LOGGER.debug(feedback.logTemplate(), player.getName().getString(), result.affectedContainers().size());
        } else {
            Simplesort.LOGGER.debug(feedback.logTemplate(), player.getName().getString());
        }

        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        if (config.playSounds && feedback.sound() != null) {
            try {
                player.connection.send(new ClientboundSoundPacket(
                        Holder.direct(feedback.sound()), SoundSource.BLOCKS,
                        player.getX(), player.getY(), player.getZ(),
                        0.3f, feedback.pitch(), player.level().getRandom().nextLong()));
            } catch (Exception e) {
                Simplesort.LOGGER.error("Failed to send sound packet to {}", player.getName().getString(), e);
            }
        }

        if (config.showOverlayMessages) {
            player.sendSystemMessage(Component.translatable(feedback.translationKey()).withColor(feedback.color()));
        }

        if (result.status() == SortStatus.PARTIAL_SUCCESS && !result.unplacedItems().isEmpty()) {
            MutableComponent hoverText = Component.empty();
            boolean first = true;
            for (Map.Entry<Item, Integer> entry : result.unplacedItems().entrySet()) {
                if (!first) hoverText.append(Component.literal("\n"));
                hoverText.append(Component.literal(entry.getValue() + "× ").append(Component.translatable(entry.getKey().getDescriptionId())));
                first = false;
            }
            player.sendSystemMessage(Component.translatable("simple-sort.text.could_not_transfer")
                    .withColor(0xFFAA00)
                    .withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText.withColor(0xFFAA00)))));
        }
    }

    public static void onLockToggle(ServerPlayer player, boolean locked) {
        if (locked) {
            player.sendSystemMessage(Component.translatable("simple-sort.text.hub_lock_enabled").withColor(0xFF5555));
        } else {
            player.sendSystemMessage(Component.translatable("simple-sort.text.hub_lock_disabled").withColor(0x55FF55));
        }

        player.connection.send(new ClientboundSoundPacket(
            SoundEvents.NOTE_BLOCK_PLING,
            SoundSource.BLOCKS,
            player.getX(), player.getY(), player.getZ(),
            0.4f,
            locked ? 1.4f : 0.8f,
            player.level().getRandom().nextLong()));
    }

    public static void onRadiusToggle(ServerPlayer player, boolean enabled) {
        if (enabled) {
            player.sendSystemMessage(Component.translatable("simple-sort.text.radius_enabled").withColor(0x55FF55));
        } else {
            player.sendSystemMessage(Component.translatable("simple-sort.text.radius_disabled").withColor(0xFF5555));
        }

        player.connection.send(new ClientboundSoundPacket(
            SoundEvents.NOTE_BLOCK_PLING,
            SoundSource.BLOCKS,
            player.getX(), player.getY(), player.getZ(),
            0.4f,
            enabled ? 1.4f : 0.8f,
            player.level().getRandom().nextLong()));
    }
}
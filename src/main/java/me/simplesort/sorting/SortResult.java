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

package me.simplesort.sorting;

import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public record SortResult(SortStatus status, Set<BlockPos> affectedContainers, Set<BlockPos> failedContainers, Set<BlockPos> partialContainers, Map<Item, Integer> unplacedItems) {}

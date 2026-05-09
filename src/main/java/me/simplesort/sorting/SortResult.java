package me.simplesort.sorting;

import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public record SortResult(SortStatus status, Set<BlockPos> affectedContainers, Set<BlockPos> failedContainers, Set<BlockPos> partialContainers, Map<Item, Integer> unplacedItems) {}

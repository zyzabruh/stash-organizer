package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

/** Storage blocks the organizer knows how to open and read. */
public final class ContainerTypes {
    private ContainerTypes() {
    }

    /** A vanilla storage block: every chest variant (including modded ones extending the base class), barrels and shulkers. */
    public static boolean isContainer(Block block) {
        return block instanceof AbstractChestBlock<?>
            || block instanceof BarrelBlock
            || block instanceof ShulkerBoxBlock;
    }

    /**
     * Same test, plus the block ids configured by the player. Modded storage that is not a chest
     * subclass can then be organised without a code change.
     */
    /**
     * A storage block the organizer may open. With {@code excludeEnderChest} the ender chest is left
     * alone: its inventory is per player and shared across dimensions, so organising it destroys
     * the player's own storage instead of the stash.
     */
    public static boolean isContainer(Block block, List<String> extraIds, boolean excludeEnderChest) {
        if (excludeEnderChest && isEnderChest(block)) return false;

        return isContainer(block, extraIds);
    }

    public static boolean isContainer(Block block, List<String> extraIds) {
        if (isContainer(block)) return true;
        if (block == null || extraIds == null || extraIds.isEmpty()) return false;

        Identifier id = Registries.BLOCK.getId(block);

        for (String entry : extraIds) {
            if (entry == null) continue;

            String normalized = entry.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) continue;
            if (id.toString().equals(normalized) || id.getPath().equals(normalized)) return true;
        }

        return false;
    }

    public static boolean isShulker(Block block) {
        return block instanceof ShulkerBoxBlock;
    }

    /**
     * The ender chest is an abstract chest block like the others, so it needs an explicit test.
     * The check is a class test, never a registry lookup: it must stay usable without a booted
     * game registry (a plain unit test) and without loading the block registry.
     */
    public static boolean isEnderChest(Block block) {
        return block instanceof EnderChestBlock;
    }
}

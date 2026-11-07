package com.example.addon.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Container screens: opening a storage block, reading its contents into the session cache,
 * closing it and clicking slots. The scripts only talk to this interface, so a test can drive
 * them without a client.
 */
public interface ContainerAccess {
    boolean isOpen();

    void close();

    /** Number of slots of the currently opened container (screen handler minus the player part). */
    int containerSlots();

    ItemStack liveStack(int slot);

    /** Live contents of the opened container, without touching the session cache. */
    List<ItemStack> liveContents();

    int liveSlotId(int slot);

    /**
     * Opens a container and waits for its screen.
     *
     * @return 1 when the screen is open, 0 while waiting, -1 on failure
     */
    int open(BlockPos pos);

    /** Resets the retry counters; call before starting to open a new container. */
    void resetOpen();

    /** Reads the opened container into the session cache. */
    void read(BlockPos pos, boolean recordShulkers);

    StashContainer at(BlockPos pos);

    void click(int slotId, int button, SlotActionType type);

    /** Screen handler id of a player inventory slot inside a container screen. */
    int playerSlotId(int inventoryIndex, int containerSlots);
}

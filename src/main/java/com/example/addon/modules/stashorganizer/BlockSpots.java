package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

/**
 * Rules shared by everything the organizer places: chests, staging chests, overflow shulker boxes
 * and the box a shulker is temporarily opened in. The scripts only talk to this interface, so a
 * test can answer without a world.
 */
public interface BlockSpots {
    /**
     * A block can be placed here: the cell and the cell above are free, the floor is solid and is
     * not a container (a block above a container blocks its lid), and the cell is not inside the
     * player.
     */
    boolean canPlaceAt(BlockPos pos);

    boolean hasFreeHorizontalFace(BlockPos pos);

    boolean touchesContainer(BlockPos pos);
}

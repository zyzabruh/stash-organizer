package com.example.addon.modules.stashorganizer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** The placement rules of {@link BlockSpots}, answered by the running client. */
public final class MinecraftBlockSpots implements BlockSpots {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final StashSession session;

    public MinecraftBlockSpots(StashSession session) {
        this.session = session;
    }

    @Override
    public boolean canPlaceAt(BlockPos pos) {
        MinecraftClient mc = MeteorClient.mc;

        if (pos.equals(mc.player.getBlockPos())) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;
        if (!mc.world.getBlockState(pos).isAir()) return false;
        // The lid of a chest or a shulker box needs the block above it to be free.
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

        BlockState floor = mc.world.getBlockState(pos.down());
        if (floor.getCollisionShape(mc.world, pos.down()).isEmpty()) return false;
        if (ContainerTypes.isContainer(floor.getBlock(), session.extraContainers())) return false;

        return BlockUtils.canPlace(pos);
    }

    @Override
    public boolean hasFreeHorizontalFace(BlockPos pos) {
        MinecraftClient mc = MeteorClient.mc;

        for (Direction direction : HORIZONTAL) {
            if (mc.world.getBlockState(pos.offset(direction)).isAir()) return true;
        }

        return false;
    }

    @Override
    public boolean touchesContainer(BlockPos pos) {
        MinecraftClient mc = MeteorClient.mc;

        for (Direction direction : HORIZONTAL) {
            if (ContainerTypes.isContainer(mc.world.getBlockState(pos.offset(direction)).getBlock(),
                session.extraContainers())) {
                return true;
            }
        }

        return false;
    }
}

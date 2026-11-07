package com.example.addon.modules.stashorganizer;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The client primitives the step scripts need. Everything a script reads from the world or does
 * to it goes through this interface, so a script can be run against a fake world outside the game.
 */
public interface Game {
    Block blockAt(BlockPos pos);

    /** True when the block is a chest, a barrel or a shulker box. */
    boolean isContainerAt(BlockPos pos);

    /** True when the block is a placed shulker box. */
    boolean isShulkerAt(BlockPos pos);

    boolean isAir(BlockPos pos);

    /** Every block position of the cube around a centre, in the vanilla iteration order. */
    Iterable<BlockPos> around(BlockPos center, int radius);

    boolean chunkLoaded(BlockPos pos);

    BlockPos playerFeet();

    Vec3d playerPos();

    /** True when the player's own bounding box intersects the block. */
    boolean playerIntersects(BlockPos pos);

    double squaredDistanceTo(BlockPos pos);

    List<ItemFrameEntity> itemFrames();

    /** Rotates towards a block centre; the rotation is applied over the next ticks. */
    void lookAt(BlockPos pos, int priority);

    /** Rotates towards a point; the rotation is applied over the next ticks. */
    void lookAt(Vec3d point, int priority);

    /** Keeps breaking the block across ticks, the way BlockUtils does. */
    void breakBlock(BlockPos pos);

    /** Puts the stack in the hotbar and places it, rotating towards the spot. */
    boolean place(BlockPos pos, ItemStack stack);

    void useBlock(BlockPos pos, Direction face, Vec3d hit);

    void useEntity(Entity entity);
}

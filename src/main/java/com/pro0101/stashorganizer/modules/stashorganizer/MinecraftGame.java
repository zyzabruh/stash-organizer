package com.pro0101.stashorganizer.modules.stashorganizer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** The real client behind {@link Game}. It holds every Minecraft call the scripts used to make. */
public final class MinecraftGame implements Game {
    /** Resolved on use: a test builds these services without loading the client. */
    private MinecraftClient mc() {
        return MeteorClient.mc;
    }

    @Override
    public Block blockAt(BlockPos pos) {
        return mc().world.getBlockState(pos).getBlock();
    }

    @Override
    public boolean isContainerAt(BlockPos pos) {
        return ContainerTypes.isContainer(blockAt(pos));
    }

    @Override
    public boolean isShulkerAt(BlockPos pos) {
        return ContainerTypes.isShulker(blockAt(pos));
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return mc().world.getBlockState(pos).isAir();
    }

    @Override
    public Iterable<BlockPos> around(BlockPos center, int radius) {
        return BlockPos.iterate(center.add(-radius, -radius, -radius), center.add(radius, radius, radius));
    }

    @Override
    public boolean chunkLoaded(BlockPos pos) {
        return mc().world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Override
    public BlockPos playerFeet() {
        return mc().player.getBlockPos();
    }

    @Override
    public Vec3d playerPos() {
        return mc().player.getEntityPos();
    }

    @Override
    public boolean playerIntersects(BlockPos pos) {
        return mc().player.getBoundingBox().intersects(new Box(pos));
    }

    @Override
    public double squaredDistanceTo(BlockPos pos) {
        return PlayerUtils.squaredDistanceTo(pos);
    }

    @Override
    public List<ItemFrameEntity> itemFrames() {
        List<ItemFrameEntity> frames = new ArrayList<>();

        for (Entity entity : mc().world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame) frames.add(frame);
        }

        return frames;
    }

    @Override
    public void lookAt(BlockPos pos, int priority) {
        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), priority);
    }

    @Override
    public void lookAt(Vec3d point, int priority) {
        Rotations.rotate(Rotations.getYaw(point), Rotations.getPitch(point), priority);
    }

    @Override
    public void breakBlock(BlockPos pos) {
        BlockUtils.breakBlock(pos, true);
    }

    @Override
    public boolean place(BlockPos pos, ItemStack stack) {
        boolean shulker = stack.isIn(ItemTags.SHULKER_BOXES);

        FindItemResult item = InvUtils.findInHotbar(candidate -> shulker
            ? ItemStack.areItemsAndComponentsEqual(candidate, stack)
            : candidate.getItem() == stack.getItem());

        return item.found() && BlockUtils.place(pos, item, true, 50);
    }

    @Override
    public void useBlock(BlockPos pos, Direction face, Vec3d hit) {
        mc().interactionManager.interactBlock(mc().player, Hand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    @Override
    public void useEntity(Entity entity) {
        mc().interactionManager.interactEntity(mc().player, entity, Hand.MAIN_HAND);
    }
}

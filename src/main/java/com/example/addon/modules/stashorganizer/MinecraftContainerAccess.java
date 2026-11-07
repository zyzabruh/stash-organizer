package com.example.addon.modules.stashorganizer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/** Low level access to the container screen, implemented on the client. */
public final class MinecraftContainerAccess implements ContainerAccess {
    private static final double REACH = 4.5;
    private static final double REACH_SQ = REACH * REACH;
    /** The ray may hit the near face while the centre is a little further away. */
    private static final double MAX_REACH = REACH + 1.0;
    private static final double MAX_REACH_SQ = MAX_REACH * MAX_REACH;

    private final StashSession session;
    /** Resolved on use: a test builds these services without loading the client. */
    private MinecraftClient mc() {
        return MeteorClient.mc;
    }

    private int openWait;
    private int openAttempts;
    private BlockPos openedPos;

    public MinecraftContainerAccess(StashSession session) {
        this.session = session;
    }

    @Override
    public boolean isOpen() {
        return mc().currentScreen instanceof HandledScreen<?> && mc().player.currentScreenHandler != mc().player.playerScreenHandler;
    }

    @Override
    public void close() {
        if (mc().currentScreen != null) mc().player.closeHandledScreen();
        openedPos = null;
    }

    @Override
    public int containerSlots() {
        return mc().player.currentScreenHandler.slots.size() - 36;
    }

    @Override
    public ItemStack liveStack(int slot) {
        if (slot < 0 || slot >= containerSlots()) return ItemStack.EMPTY;
        return mc().player.currentScreenHandler.slots.get(slot).getStack();
    }

    @Override
    public List<ItemStack> liveContents() {
        List<ItemStack> contents = new ArrayList<>();
        int size = containerSlots();

        for (int i = 0; i < size; i++) contents.add(liveStack(i));

        return contents;
    }

    @Override
    public int liveSlotId(int slot) {
        if (slot < 0 || slot >= containerSlots()) return -1;
        return mc().player.currentScreenHandler.slots.get(slot).id;
    }

    /**
     * Opens a container and waits for its screen.
     *
     * @return 1 when the screen is open, 0 while waiting, -1 on failure
     */
    @Override
    public int open(BlockPos pos) {
        if (isOpen()) {
            if (pos.equals(openedPos)) return 1;
            close();
            openWait = 2;
            return 0;
        }

        if (openWait > 0) {
            openWait--;
            return 0;
        }

        if (openAttempts >= 2) return -1;

        // Best effort only: a block in hand still opens a container, and a full inventory must be
        // dumpable. The real hit result below is what matters.
        session.inventory.ensureEmptyHand();

        BlockHitResult hit = findHitResult(pos);

        if (hit == null) {
            openAttempts++;
            openWait = 2;
            return 0;
        }

        Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 50);
        mc().interactionManager.interactBlock(mc().player, Hand.MAIN_HAND, hit);
        openedPos = pos;

        openWait = 4;
        openAttempts++;
        session.act();
        return 0;
    }

    /**
     * Finds a face of the container that the player can actually see. Chests standing above the
     * player's feet cannot be clicked on their top face, which is what the old code always used.
     */
    private BlockHitResult findHitResult(BlockPos pos) {
        Vec3d eye = mc().player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);

        if (eye.squaredDistanceTo(center) > MAX_REACH_SQ) return null;

        BlockHitResult direct = raycast(eye, center);
        if (direct != null && isTarget(direct.getBlockPos(), pos)
            && eye.squaredDistanceTo(direct.getPos()) <= REACH_SQ) {
            return direct;
        }

        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            Vec3d face = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            double distance = eye.squaredDistanceTo(face);
            if (distance > REACH_SQ || distance >= bestDistance) continue;

            BlockHitResult hit = raycast(eye, face);
            if (hit == null || !isTarget(hit.getBlockPos(), pos)) continue;

            best = hit;
            bestDistance = distance;
        }

        if (best != null) return best;

        // Last resort: the closest face, even if a transparent shape sits in the way.
        Direction side = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        Vec3d face = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        return eye.squaredDistanceTo(face) <= REACH_SQ ? new BlockHitResult(face, side, pos, false) : null;
    }

    private BlockHitResult raycast(Vec3d from, Vec3d to) {
        HitResult hit = mc().world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc().player));

        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) return blockHit;
        return null;
    }

    private boolean isTarget(BlockPos hit, BlockPos pos) {
        if (hit.equals(pos)) return true;

        StashContainer container = at(pos);
        return container != null && hit.equals(container.partner);
    }

    @Override
    public void resetOpen() {
        openWait = 0;
        openAttempts = 0;
    }

    @Override
    public void read(BlockPos pos, boolean recordShulkers) {
        if (!isOpen()) return;

        ScreenHandler handler = mc().player.currentScreenHandler;
        int size = handler.slots.size() - 36;
        if (size < 27 || size > 54) return;

        StashContainer container = at(pos);
        if (container == null) {
            container = new StashContainer(pos, mc().world.getBlockState(pos).getBlock());
            session.containers.add(container);
        }

        // Purge by container identity, not by position: the second half of a double chest is
        // read under its own position but belongs to the very same container.
        StashContainer target = container;
        if (recordShulkers) session.shulkers.removeIf(ref -> at(ref.pos) == target);

        container.size = size;
        container.contents.clear();

        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.slots.get(i).getStack().copy();
            container.contents.add(stack);

            if (recordShulkers && session.unpacksShulkers() && !InventoryTools.isEmptyShulker(stack)) {
                session.shulkers.add(new ShulkerRef(pos, i));
            }
        }

        container.ensureSize();
        container.scanned = true;

        if (size == 54 && container.partner == null) {
            container.partner = chestPartner(pos, container.block);
        }
    }

    /**
     * Finds the other half of a double chest from the block state. Using the chest type avoids
     * pairing a double chest with an unrelated same-block chest standing next to it.
     */
    private BlockPos chestPartner(BlockPos pos, Block block) {
        BlockState state = mc().world.getBlockState(pos);

        if (!(state.getBlock() instanceof ChestBlock) || !state.contains(ChestBlock.CHEST_TYPE)) return null;

        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) return null;

        Direction facing = state.get(ChestBlock.FACING);
        Direction side = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        BlockPos other = pos.offset(side);

        return mc().world.getBlockState(other).getBlock() == block ? other : null;
    }

    @Override
    public StashContainer at(BlockPos pos) {
        for (StashContainer container : session.containers) {
            if (container.pos.equals(pos)) return container;
            if (container.partner != null && container.partner.equals(pos)) return container;
        }
        return null;
    }

    @Override
    public void click(int slotId, int button, SlotActionType type) {
        ScreenHandler handler = mc().player.currentScreenHandler;
        if (handler == null || handler == mc().player.playerScreenHandler) return;
        mc().interactionManager.clickSlot(handler.syncId, slotId, button, type, mc().player);
    }

    @Override
    public int playerSlotId(int inventoryIndex, int containerSlots) {
        if (inventoryIndex >= 9 && inventoryIndex <= 35) return containerSlots + (inventoryIndex - 9);
        if (inventoryIndex >= 0 && inventoryIndex <= 8) return containerSlots + 27 + inventoryIndex;
        return -1;
    }

}

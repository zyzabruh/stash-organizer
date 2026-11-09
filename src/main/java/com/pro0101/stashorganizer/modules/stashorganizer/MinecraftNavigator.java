package com.pro0101.stashorganizer.modules.stashorganizer;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Walking implemented on the client.
 * <p>
 * Storage blocks are not used as pathing goals directly: a chest standing three blocks above the
 * floor cannot be reached by walking to the chest block, because Baritone would try to climb up to
 * its level. Instead the navigator looks for a spot where the player can actually stand and from
 * which the container is in line of sight, then paths to that spot.
 */
public final class MinecraftNavigator implements Navigator {
    private static final double SPOT_REACH = 4.5;
    private static final double INTERACT_REACH = 4.5;
    private static final double INTERACT_REACH_SQ = INTERACT_REACH * INTERACT_REACH;
    /**
     * The interaction check uses the point the ray hits on the block, which is closer than its
     * centre. The centre may therefore be a little further than the vanilla reach.
     */
    private static final double MAX_INTERACT_REACH = INTERACT_REACH + 1.0;
    private static final double MAX_INTERACT_REACH_SQ = MAX_INTERACT_REACH * MAX_INTERACT_REACH;
    private static final double EYE_HEIGHT = 1.62;

    private final StashSession session;
    /** Resolved on use: a test builds these services without loading the client. */
    private MinecraftClient mc() {
        return MeteorClient.mc;
    }

    private BlockPos target;
    private BlockPos standPos;
    private int ticks;

    public MinecraftNavigator(StashSession session) {
        this.session = session;
    }

    @Override
    public boolean walkTo(BlockPos pos) {
        return walkToDetailed(pos) == Result.ARRIVED;
    }

    @Override
    public Result walkToDetailed(BlockPos pos) {
        if (isContainer(pos)) return walkToContainer(pos);
        return walkToSpot(pos);
    }

    private Result walkToContainer(BlockPos pos) {
        // A shulker box only opens when the space above it is free. Trying from on top of the box
        // (or with a block above it) can never work, so skip it with a clear warning instead of
        // climbing onto it over and over.
        if (isShulker(pos) && !shulkerHasHeadroom(pos)) {
            stopMover();
            session.warning("Shulker at %s has no free space above it and cannot be opened.", pos.toShortString());
            return Result.FAILED;
        }

        if (canInteract(pos)) {
            stopMover();
            return Result.ARRIVED;
        }

        // The same position can first have been approached as an unknown block (for example while
        // its chunk was still loading). In that case the old stand spot may be above the shulker,
        // which Baritone cannot reach; recompute a side spot now that the block is known.
        boolean staleShulkerSpot = isShulker(pos)
            && (standPos == null || standPos.getY() > pos.getY() || standPos.equals(pos.up()));

        if (!pos.equals(target) || staleShulkerSpot) {
            target = pos;
            standPos = findStandPos(pos);
            ticks = 0;

            if (standPos == null) {
                target = null;
                session.warning("No reachable spot next to %s, skipping it.", pos.toShortString());
                return Result.FAILED;
            }

            session.mover.moveToStanding(standPos);
            session.act();
            return Result.MOVING;
        }

        if (++ticks > session.timeoutTicks()) {
            target = null;
            session.warning("Could not reach %s, skipping it.", pos.toShortString());
            return Result.FAILED;
        }

        if (ticks % 40 == 0) {
            if (standPos == null || !isStandable(standPos) || !canInteractFrom(standPos, pos)) {
                BlockPos better = findStandPos(pos);

                if (better == null) {
                    target = null;
                    session.warning("No reachable spot next to %s, skipping it.", pos.toShortString());
                    return Result.FAILED;
                }

                standPos = better;
            }

            if (!session.mover.isPathing()) session.mover.moveToStanding(standPos);
        }

        return Result.MOVING;
    }

    private Result walkToSpot(BlockPos pos) {
        // The spot must stay free for the new chest: standing inside it would make the placement fail.
        if (!mc().player.getBlockPos().equals(pos) && BlockUtils.canPlace(pos)
            && PlayerUtils.distanceTo(pos) <= SPOT_REACH) {
            stopMover();
            return Result.ARRIVED;
        }

        if (!pos.equals(target)) {
            target = pos;
            ticks = 0;

            // Prefer a floor spot next to the placement spot, never on top of it.
            standPos = findStandPos(pos, false);

            if (standPos != null) session.mover.moveToStanding(standPos);
            else session.mover.moveTo(pos);

            session.act();
            return Result.MOVING;
        }

        if (++ticks > session.timeoutTicks()) {
            target = null;
            session.warning("Could not reach %s, skipping it.", pos.toShortString());
            return Result.FAILED;
        }

        if (ticks % 40 == 0 && !session.mover.isPathing()) {
            if (standPos != null) session.mover.moveToStanding(standPos);
            else session.mover.moveTo(pos);
        }

        return Result.MOVING;
    }

    private void stopMover() {
        if (target != null) {
            session.mover.stop();
            target = null;
            standPos = null;
        }
    }

    private boolean isContainer(BlockPos pos) {
        return ContainerTypes.isContainer(mc().world.getBlockState(pos).getBlock(), session.extraContainers());
    }

    private boolean isShulker(BlockPos pos) {
        return ContainerTypes.isShulker(mc().world.getBlockState(pos).getBlock());
    }

    /** The lid needs the block above the box to be free, otherwise vanilla refuses to open it. */
    private boolean shulkerHasHeadroom(BlockPos pos) {
        BlockState above = mc().world.getBlockState(pos.up());
        return above.isAir() || above.getCollisionShape(mc().world, pos.up()).isEmpty();
    }

    /** True when the player's eyes are close enough and have a line of sight to the container. */
    private boolean canInteract(BlockPos pos) {
        // Standing on a shulker occupies the space its lid needs, so the screen would not open.
        if (isShulker(pos) && mc().player.getBlockPos().equals(pos.up())) return false;

        return canInteractFrom(mc().player.getEyePos(), pos);
    }

    private boolean canInteractFrom(BlockPos feet, BlockPos pos) {
        if (isShulker(pos) && feet.equals(pos.up())) return false;

        return canInteractFrom(eyePos(feet), pos);
    }

    /**
     * Vanilla measures the reach to the point the ray hits, not to the middle of the block. Using
     * the hit point avoids refusing a container when Baritone stopped a few tenths of a block
     * short of the spot's centre.
     */
    private boolean canInteractFrom(Vec3d eye, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);

        if (eye.squaredDistanceTo(center) > MAX_INTERACT_REACH_SQ) return false;

        if (visibleAtReach(eye, center, pos)) return true;

        // When the block in front of the centre is in the way, a visible face is enough: this is
        // also what ContainerAccess tries when it opens the screen.
        for (Direction side : Direction.values()) {
            Vec3d face = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(face) > INTERACT_REACH_SQ) continue;
            if (visibleAtReach(eye, face, pos)) return true;
        }

        return false;
    }

    private boolean visibleAtReach(Vec3d eye, Vec3d target, BlockPos pos) {
        BlockHitResult hit = raycast(eye, target);

        return hit != null && matchesContainer(hit.getBlockPos(), pos)
            && eye.squaredDistanceTo(hit.getPos()) <= INTERACT_REACH_SQ;
    }

    /**
     * Finds the closest reachable standing position around the container. The vertical search covers
     * chests up to six blocks above the floor and chests standing on the floor.
     */
    private BlockPos findStandPos(BlockPos container) {
        BlockPos best = findStandPos(container, true);
        return best != null ? best : findStandPos(container, false);
    }

    private BlockPos findStandPos(BlockPos container, boolean requireSight) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        boolean shulker = isShulker(container);
        double eyeX = mc().player.getX();
        double eyeY = mc().player.getY() + EYE_HEIGHT;
        double eyeZ = mc().player.getZ();

        // A shulker is opened from the side or from below, never from above: Baritone has no
        // reliable path onto a box and standing on one would block the lid anyway.
        for (int dy = shulker ? 0 : 2; dy >= -6; dy--) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    BlockPos feet = container.add(dx, dy, dz);
                    if (!isStandable(feet)) continue;

                    Vec3d eye = eyePos(feet);
                    double distance = eye.squaredDistanceTo(Vec3d.ofCenter(container));
                    if (distance > MAX_INTERACT_REACH_SQ) continue;

                    if (requireSight && !canInteractFrom(feet, container)) continue;

                    // Prefer a spot close to the container, then one close to the player and a
                    // small vertical detour. Keeping the spot close to the chest means a stop a
                    // few tenths short still leaves the container within interaction reach. A
                    // one-block gap is kept when possible so an item frame can fit on the face.
                    double horizontal = Math.sqrt((double) dx * dx + (double) dz * dz);
                    double playerDistance = Math.sqrt(eye.squaredDistanceTo(eyeX, eyeY, eyeZ));
                    double containerDistance = Math.sqrt(distance);
                    double score = playerDistance * 0.35 + containerDistance * 3.0
                        + Math.abs(dy) * 0.5 + (horizontal < 1.5 ? 2.0 : 0.0);

                    if (score < bestScore) {
                        bestScore = score;
                        best = feet;
                    }
                }
            }
        }

        return best;
    }

    /** A spot is standable when the player fits in it, it is not liquid and there is a floor. */
    private boolean isStandable(BlockPos feet) {
        BlockState floor = mc().world.getBlockState(feet.down());
        if (floor.getCollisionShape(mc().world, feet.down()).isEmpty()) return false;

        BlockState lower = mc().world.getBlockState(feet);
        if (!lower.getCollisionShape(mc().world, feet).isEmpty()) return false;
        if (!lower.getFluidState().isEmpty()) return false;

        BlockState upper = mc().world.getBlockState(feet.up());
        if (!upper.getCollisionShape(mc().world, feet.up()).isEmpty()) return false;
        if (!upper.getFluidState().isEmpty()) return false;

        return true;
    }

    private Vec3d eyePos(BlockPos feet) {
        return new Vec3d(feet.getX() + 0.5, feet.getY() + EYE_HEIGHT, feet.getZ() + 0.5);
    }

    private BlockHitResult raycast(Vec3d from, Vec3d to) {
        HitResult hit = mc().world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc().player));

        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) return blockHit;
        return null;
    }

    /** Accepts the other half of a double chest when the ray hits it first. */
    private boolean matchesContainer(BlockPos hit, BlockPos pos) {
        if (hit.equals(pos)) return true;

        StashContainer container = session.access.at(pos);
        return container != null && hit.equals(container.partner);
    }
}

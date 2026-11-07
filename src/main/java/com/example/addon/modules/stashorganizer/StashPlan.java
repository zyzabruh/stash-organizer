package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable form of a saved plan. It holds ids and positions only, never a game object, so the
 * format can be written by the client and read back (or tested) as plain JSON.
 */
public final class StashPlan {
    /** Format version: a plan written by another version is dropped instead of misread. */
    public static final int VERSION = 2;

    private StashPlan() {
    }

    public static Pos pos(BlockPos pos) {
        return pos == null ? null : new Pos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(Pos pos) {
        return pos == null ? null : new BlockPos(pos.x, pos.y, pos.z);
    }

    public static final class Data {
        public int version;
        public String world;
        public String mode;
        public String phase;
        public String resumeAfterCollect;
        public Pos dumpPos;
        public int totalMoves;
        public int placedChests;
        public int placedShulkers;
        public int packedShulkers;
        public List<ContainerData> containers = new ArrayList<>();
        public List<GroupData> groups = new ArrayList<>();
        public List<GroupData> unassigned = new ArrayList<>();
        public List<CarriedData> carried = new ArrayList<>();
        public List<ShulkerData> shulkers = new ArrayList<>();
    }

    public static final class ContainerData {
        public Pos pos;
        public String block;
        public int size;
        public int reserved;
        public boolean scanned;
        public boolean failed;
        public boolean packing;
        public Pos partner;
        public List<String> groups = new ArrayList<>();
    }

    public static final class GroupData {
        public String key;
        public int slots;
    }

    /** A stack taken from a container and waiting in the inventory to be delivered. */
    public static final class CarriedData {
        public int slot;
        public String item;
        public String group;
    }

    /** A shulker box of the plan, with the progress of the unpack phase. */
    public static final class ShulkerData {
        public Pos pos;
        public int slot;
        public boolean done;
    }

    public static final class Pos {
        public int x;
        public int y;
        public int z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Pos otherPos)) return false;
            return x == otherPos.x && y == otherPos.y && z == otherPos.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}

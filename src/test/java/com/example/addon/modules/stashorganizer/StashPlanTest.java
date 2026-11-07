package com.example.addon.modules.stashorganizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saved plan format, round-tripped as plain JSON: the in-flight state added for a resume (the
 * carried buffer, the shulker progress, the counters and the collect target) must survive.
 */
class StashPlanTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void roundTripsTheInflightState() {
        StashPlan.Data data = new StashPlan.Data();
        data.version = StashPlan.VERSION;
        data.world = "play.example.net|minecraft:overworld";
        data.mode = "Family";
        data.phase = "Unpack";
        data.resumeAfterCollect = "Pack";
        data.dumpPos = new StashPlan.Pos(1, 64, -2);
        data.totalMoves = 41;
        data.placedChests = 3;
        data.placedShulkers = 2;
        data.packedShulkers = 5;

        StashPlan.ContainerData container = new StashPlan.ContainerData();
        container.pos = new StashPlan.Pos(10, 70, 4);
        container.block = "minecraft:chest";
        container.size = 54;
        container.reserved = 6;
        container.scanned = true;
        container.packing = false;
        container.groups.add("item:stone");
        data.containers.add(container);

        StashPlan.GroupData group = new StashPlan.GroupData();
        group.key = "item:dirt";
        group.slots = 3;
        data.groups.add(group);
        data.unassigned.add(group);

        StashPlan.CarriedData carried = new StashPlan.CarriedData();
        carried.slot = 12;
        carried.item = "minecraft:stone";
        carried.group = "item:stone";
        data.carried.add(carried);

        StashPlan.ShulkerData shulker = new StashPlan.ShulkerData();
        shulker.pos = new StashPlan.Pos(11, 70, 4);
        shulker.slot = 3;
        shulker.done = true;
        data.shulkers.add(shulker);

        StashPlan.Data back = GSON.fromJson(GSON.toJson(data), StashPlan.Data.class);

        assertEquals(StashPlan.VERSION, back.version);
        assertEquals(data.world, back.world);
        assertEquals("Unpack", back.phase);
        assertEquals("Pack", back.resumeAfterCollect, "the collect target survives");
        assertEquals(data.dumpPos, back.dumpPos);
        assertEquals(41, back.totalMoves);
        assertEquals(5, back.packedShulkers);

        assertEquals(1, back.containers.size());
        assertEquals(new StashPlan.Pos(10, 70, 4), back.containers.get(0).pos);
        assertEquals(54, back.containers.get(0).size);
        assertEquals("minecraft:chest", back.containers.get(0).block);
        assertTrue(back.containers.get(0).scanned);
        assertEquals(1, back.containers.get(0).groups.size());

        assertEquals(1, back.carried.size(), "the carried stack survives");
        assertEquals(12, back.carried.get(0).slot);
        assertEquals("minecraft:stone", back.carried.get(0).item);
        assertEquals("item:stone", back.carried.get(0).group);

        assertEquals(1, back.shulkers.size(), "the shulker progress survives");
        assertTrue(back.shulkers.get(0).done, "an emptied box is not emptied twice");
        assertEquals(1, back.unassigned.size(), "the remaining groups survive");
        assertEquals(3, back.unassigned.get(0).slots);
    }

    @Test
    void anEmptyPlanHasNoPositions() {
        StashPlan.Data back = GSON.fromJson(GSON.toJson(new StashPlan.Data()), StashPlan.Data.class);

        assertFalse(back.containers == null, "the lists are never null");
        assertTrue(back.containers.isEmpty());
        assertTrue(back.shulkers.isEmpty());
        assertEquals(0, back.totalMoves);
    }
}

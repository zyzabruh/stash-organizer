package com.example.addon.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link ChestPlacer} outside the game: a chest is placed on the free spot of the
 * plan, registered as a container and given the group it was placed for.
 */
class ChestPlacerScriptTest {
    @BeforeAll
    static void boot() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");
    }

    @Test
    void placesAChestAndGivesItThePlannedGroup() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();
        FakeServices.Game game = new FakeServices.Game();
        FakeServices.Spots spots = new FakeServices.Spots();

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), game, access,
            new FakeServices.Walker(), inventory, spots, Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Place;

        StashGroup group = new StashGroup("item:stone");
        group.slots = 10;
        session.groups.put(group.key, group);
        session.unassigned.add(new UnassignedGroup(group, 10));

        inventory.slots[0] = new ItemStack(Items.CHEST, 4);

        BlockPos spot = new BlockPos(1, 64, 1);
        spots.allowed.add(spot);

        for (int tick = 0; tick < 80 && session.phase == StashPhase.Place; tick++) session.placer.tickPhase();

        assertEquals(StashPhase.Sort, session.phase, "the placement phase hands over to the sorting");
        assertEquals(1, session.placedChests, "one chest is placed");
        assertTrue(session.unassigned.isEmpty(), "the group found its chest");

        StashContainer placed = session.placer.lastPlaced();
        assertNotNull(placed, "the placed chest is known");
        assertEquals(spot, placed.pos);
        assertTrue(placed.groups.contains("item:stone"), "the new chest holds the group: " + placed.groups);
        assertEquals(10, placed.reserved, "its slots are reserved for the group");
        assertTrue(game.containers.contains(spot), "the block is in the world");
    }

    @Test
    void stopsThePlacementWhenNoChestIsAvailable() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), new FakeServices.Game(), access,
            new FakeServices.Walker(), new FakeServices.Inventory(), new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Place;

        StashGroup group = new StashGroup("item:stone");
        group.slots = 5;
        session.groups.put(group.key, group);
        session.unassigned.add(new UnassignedGroup(group, 5));

        for (int tick = 0; tick < 40 && session.phase == StashPhase.Place; tick++) session.placer.tickPhase();

        assertEquals(StashPhase.Sort, session.phase, "the run continues with the sorting");
        assertEquals(0, session.placedChests, "nothing could be placed");
        assertTrue(host.messages.stream().anyMatch(message ->
                message.startsWith("warning:") && message.contains("no container left")),
            "the missing chests are reported: " + host.messages);
    }
}

package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trip of the saved plan: the containers, the assignments, the remaining groups, the carried
 * stacks, the shulker progress and the counters must all come back from the file model.
 */
class StashSessionStoreTest {
    @BeforeAll
    static void boot() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");
    }

    @Test
    void roundTripsThePlanAndTheInflightState() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();

        StashSession session = session(host, access, inventory);
        BlockPos chestPos = new BlockPos(3, 70, -2);

        StashContainer container = new StashContainer(chestPos, Blocks.CHEST);
        container.scanned = true;
        container.reserved = 4;
        container.groups.add("item:stone");
        container.clearCache();
        session.containers.add(container);

        StashGroup dirt = new StashGroup("item:dirt");
        dirt.slots = 3;
        session.groups.put(dirt.key, dirt);
        session.unassigned.add(new UnassignedGroup(dirt, 2));

        inventory.slots[5] = new ItemStack(Items.STONE, 12);
        session.carried.put(5, new Carried(Items.STONE, "item:stone"));

        ShulkerRef done = new ShulkerRef(new BlockPos(6, 70, 0), 7);
        done.done = true;
        session.shulkers.add(done);

        session.totalMoves = 11;
        session.placedChests = 2;
        session.placedShulkers = 1;
        session.packedShulkers = 3;
        session.resumeAfterCollect = StashPhase.Pack;

        StashPlan.Data data = StashSessionStore.capture(session, "test|world", StashPhase.Sort);

        StashSession restored = session(host, access, inventory);
        StashSessionStore.restore(restored, data);

        assertEquals(1, restored.containers.size());
        assertEquals(chestPos, restored.containers.get(0).pos);
        assertTrue(restored.containers.get(0).groups.contains("item:stone"), "the assignment survives");
        assertEquals(4, restored.containers.get(0).reserved);
        assertTrue(restored.containers.get(0).scanned);

        assertEquals(StashPhase.Sort, restored.resumePhase, "the phase survives");
        assertEquals(StashPhase.Pack, restored.resumeAfterCollect, "the collect target survives");
        assertEquals(2, restored.unassigned.get(0).slots, "the remaining group survives");

        assertEquals(11, restored.totalMoves);
        assertEquals(2, restored.placedChests);
        assertEquals(1, restored.placedShulkers);
        assertEquals(3, restored.packedShulkers);

        assertEquals(1, restored.carried.size(), "the buffer survives");
        assertEquals("item:stone", restored.carried.get(5).group());
        assertTrue(restored.resuming, "the session knows it is a resume");

        assertTrue(restored.restoredShulkers.get(StashSession.refKey(done.pos, done.slot)),
            "an emptied box is not emptied twice");
    }

    @Test
    void dropsABufferEntryWhoseSlotChanged() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();

        StashSession session = session(host, access, inventory);
        StashContainer container = new StashContainer(new BlockPos(0, 64, 0), Blocks.CHEST);
        container.scanned = true;
        session.containers.add(container);

        inventory.slots[5] = new ItemStack(Items.STONE, 12);
        session.carried.put(5, new Carried(Items.STONE, "item:stone"));

        StashPlan.Data data = StashSessionStore.capture(session, "test|world", StashPhase.Sort);

        inventory.slots[5] = new ItemStack(Items.DIRT, 12);

        StashSession restored = session(host, access, inventory);
        StashSessionStore.restore(restored, data);

        assertTrue(restored.carried.isEmpty(), "a slot that now holds another item carries nothing");
    }

    private static StashSession session(FakeServices.Host host, FakeServices.Access access,
                                        FakeServices.Inventory inventory) {
        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), new FakeServices.Game(), access,
            new FakeServices.Walker(), inventory, new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        access.inventory = inventory;
        return session;
    }
}

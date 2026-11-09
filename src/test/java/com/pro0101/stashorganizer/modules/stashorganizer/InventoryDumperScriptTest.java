package com.pro0101.stashorganizer.modules.stashorganizer;

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
 * Drives the real {@link InventoryDumper} outside the game: the items of the inventory are moved
 * into a staging chest, the supplies of the organizer stay in the inventory, and the scan starts
 * with a clean buffer.
 */
class InventoryDumperScriptTest {
    @BeforeAll
    static void boot() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");
    }

    @Test
    void dumpsTheInventoryIntoAStagingChest() {
        FakeServices.Host host = new FakeServices.Host();
        host.dumpsInventory = true;

        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();
        FakeServices.Spots spots = new FakeServices.Spots();

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), new FakeServices.Game(), access,
            new FakeServices.Walker(), inventory, spots, Persistence.NONE);
        access.containers = session.containers;
        access.inventory = inventory;
        session.phase = StashPhase.Dump;

        inventory.slots[0] = new ItemStack(Items.CHEST, 2);
        inventory.slots[9] = new ItemStack(Items.STONE, 32);
        spots.allowed.add(new BlockPos(1, 64, 1));

        assertTrue(session.dumper.hasDumpableItems(), "the stone must be dumped");

        for (int tick = 0; tick < 160 && session.phase == StashPhase.Dump; tick++) session.dumper.tick();

        assertEquals(StashPhase.Scan, session.phase, "the dump hands over to the scan");
        assertNotNull(session.dumpPos, "the staging chest is remembered");
        assertEquals(32, ItemSorterScriptTest.screenCount(access.screens.get(session.dumpPos), Items.STONE),
            "the stone reached the staging chest");
        assertTrue(inventory.slots[9].isEmpty(), "the dumped slot is empty again");
        assertEquals(0, ItemSorterScriptTest.inventoryCount(inventory, Items.STONE), "no item stays behind");
        assertEquals(2, inventory.count(Items.CHEST), "the organizer keeps its own chests");
    }
}

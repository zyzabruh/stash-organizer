package com.example.addon.modules.stashorganizer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link ItemSorter} outside the game: a misplaced stack is taken from its chest,
 * carried in the inventory buffer and dropped in the chest of its group, then the phase hands over
 * to the collect pass.
 */
class ItemSorterScriptTest {
    @BeforeAll
    static void boot() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");
    }

    @Test
    void carriesAMisplacedStackToItsChest() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), new FakeServices.Game(), access,
            new FakeServices.Walker(), inventory, new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        access.inventory = inventory;
        session.phase = StashPhase.Sort;

        String group = ItemGrouping.key(new ItemStack(Items.STONE), ItemGrouping.Mode.Family);
        BlockPos source = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(2, 64, 0);

        StashContainer from = chest(access, source);
        from.contents.set(0, new ItemStack(Items.STONE, 64));
        access.screens.get(source).set(0, new ItemStack(Items.STONE, 64));

        StashContainer to = chest(access, destination);
        to.groups.add(group);

        session.containers.add(from);
        session.containers.add(to);

        for (int tick = 0; tick < 300 && session.phase == StashPhase.Sort; tick++) session.sorter.tick();

        assertEquals(StashPhase.Collect, session.phase, "the sort hands over to the collect pass");
        assertEquals(1, session.totalMoves, "one stack is moved");
        assertTrue(session.carried.isEmpty(), "the buffer was delivered");
        assertEquals(64, screenCount(access.screens.get(destination), Items.STONE), "the stack reached its chest");
        assertEquals(0, screenCount(access.screens.get(source), Items.STONE), "the source is empty");
        assertEquals(0, inventoryCount(inventory, Items.STONE), "nothing is left in the inventory");
        assertFalse(host.messages.stream().anyMatch(message -> message.startsWith("error:")),
            "the sort ends without an error: " + host.messages);
    }

    /** A scanned chest with an empty screen of its own. */
    static StashContainer chest(FakeServices.Access access, BlockPos pos) {
        access.screens.put(pos, FakeServices.Access.emptyScreen());

        StashContainer container = new StashContainer(pos, null);
        container.scanned = true;
        container.clearCache();
        return container;
    }

    static int screenCount(List<ItemStack> screen, Item item) {
        int count = 0;

        if (screen != null) {
            for (ItemStack stack : screen) {
                if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
            }
        }

        return count;
    }

    static int inventoryCount(FakeServices.Inventory inventory, Item item) {
        int count = 0;

        for (ItemStack stack : inventory.slots) {
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }

        return count;
    }
}

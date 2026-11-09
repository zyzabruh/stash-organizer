package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three assignment passes of the planner, driven on cached container contents. */
class StashPlannerTest {
    @BeforeAll
    static void boot() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");
    }

    @Test
    void givesEveryGroupTheChestThatAlreadyHoldsIt() {
        FakeServices.Host host = new FakeServices.Host();
        StashSession session = session(host);

        StashContainer stone = chest(new BlockPos(0, 64, 0));
        stone.contents.set(0, new ItemStack(Items.STONE, 64));

        StashContainer dirt = chest(new BlockPos(2, 64, 0));
        dirt.contents.set(0, new ItemStack(Items.DIRT, 32));

        session.containers.add(stone);
        session.containers.add(dirt);
        session.phase = StashPhase.Scan;

        session.planner.plan();

        assertEquals(2, session.groups.size(), "one group per item");
        assertTrue(stone.groups.contains("item:stone"), "the stone chest keeps the stone: " + stone.groups);
        assertTrue(dirt.groups.contains("item:dirt"), "the dirt chest keeps the dirt: " + dirt.groups);
        assertTrue(session.unassigned.isEmpty(), "both groups found a chest");
        assertEquals(StashPhase.Sort, session.phase, "no extra chest is needed");
    }

    @Test
    void prefersTheChestRememberedByTheGroupHistory() {
        FakeServices.Host host = new FakeServices.Host();
        StashSession session = session(host);
        session.history = new StashHistory() {
            @Override
            public boolean isHome(String group, BlockPos pos) {
                return "item:stone".equals(group) && pos.equals(new BlockPos(4, 64, 0));
            }

            @Override
            public void remember(String group, BlockPos pos) {
            }

            @Override
            public void save() {
            }
        };

        // The group comes from a full chest, so both candidates are free and only the history
        // can tell them apart.
        StashContainer full = chest(new BlockPos(-2, 64, 0));
        for (int slot = 0; slot < full.size; slot++) full.contents.set(slot, new ItemStack(Items.STONE, 64));

        StashContainer first = chest(new BlockPos(0, 64, 0));
        StashContainer remembered = chest(new BlockPos(4, 64, 0));

        session.containers.add(full);
        session.containers.add(first);
        session.containers.add(remembered);
        session.phase = StashPhase.Scan;

        session.planner.plan();

        assertTrue(remembered.groups.contains("item:stone"), "the remembered chest is reused: " + remembered.groups);
        assertTrue(first.groups.isEmpty(), "the other empty chest is left alone");
    }

    @Test
    void plansExtraChestsWhenEveryContainerIsFull() {
        FakeServices.Host host = new FakeServices.Host();
        StashSession session = session(host);

        StashContainer full = chest(new BlockPos(0, 64, 0));
        for (int slot = 0; slot < full.size; slot++) full.contents.set(slot, new ItemStack(Items.STONE, 64));

        session.containers.add(full);
        session.phase = StashPhase.Scan;

        session.planner.plan();

        assertEquals(1, session.unassigned.size(), "the group has no room left");
        assertEquals(27, session.unassigned.get(0).slots, "the whole group is planned for a new chest");
        assertEquals(StashPhase.Place, session.phase, "a chest is placed before sorting");
    }

    @Test
    void dryRunReportsThePlanWithoutMovingAnything() {
        FakeServices.Host host = new FakeServices.Host();
        host.dryRun = true;
        StashSession session = session(host);

        StashContainer full = chest(new BlockPos(0, 64, 0));
        for (int slot = 0; slot < full.size; slot++) full.contents.set(slot, new ItemStack(Items.STONE, 64));

        session.containers.add(full);
        session.phase = StashPhase.Scan;

        session.planner.plan();

        assertEquals(StashPhase.Done, session.phase, "the dry run ends the session");
        assertTrue(host.toggled, "the module is toggled off");
        assertTrue(host.messages.stream().anyMatch(message -> message.startsWith("info: Dry run")),
            "the plan is reported: " + host.messages);
    }

    private static StashSession session(FakeServices.Host host) {
        FakeServices.Access access = new FakeServices.Access();
        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), new FakeServices.Game(), access,
            new FakeServices.Walker(), new FakeServices.Inventory(), new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        return session;
    }

    private static StashContainer chest(BlockPos pos) {
        StashContainer container = new StashContainer(pos, null);
        container.scanned = true;
        container.clearCache();
        return container;
    }

}


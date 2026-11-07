package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link ChestLabeler} step script outside the game. Every chest of this stash is
 * either failed, unassigned or without a cached item, so the labeler must walk to none of them,
 * never open a screen, and end the run with its summary.
 */
class ChestLabelerScriptTest {
    @Test
    void skipsChestsThatCannotBeLabelledAndEndsTheRun() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Game game = new FakeServices.Game();
        FakeServices.Mover mover = new FakeServices.Mover();

        host.labelsChests = true;

        StashSession session = StashSession.forTest(host, mover, game, access, new FakeServices.Walker(),
            new FakeServices.Inventory(), new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Label;

        StashContainer failed = new StashContainer(new BlockPos(0, 64, 0), null);
        failed.failed = true;
        failed.groups.add("stone");
        session.containers.add(failed);

        StashContainer unassigned = new StashContainer(new BlockPos(2, 64, 0), null);
        unassigned.scanned = true;
        session.containers.add(unassigned);

        StashContainer withoutContents = new StashContainer(new BlockPos(4, 64, 0), null);
        withoutContents.scanned = true;
        withoutContents.groups.add("dirt");
        session.containers.add(withoutContents);

        for (int tick = 0; tick < 20 && session.phase == StashPhase.Label; tick++) {
            session.labeler.tick();
        }

        assertEquals(StashPhase.Done, session.phase, "the labelling ends the run");
        assertTrue(access.opened.isEmpty(), "no chest is opened when nothing can be labelled");
        assertEquals(0, access.reads, "no chest is read");
        assertEquals(0, access.resets, "no walk to a chest is prepared");
        assertTrue(access.closed, "the container screen is closed before finishing");
        assertTrue(host.messages.stream().anyMatch(message -> message.startsWith("info: Stash organised")),
            "the summary is reported: " + host.messages);
        assertTrue(host.messages.stream().noneMatch(message -> message.startsWith("error:")),
            "the run ends without an error: " + host.messages);
        assertTrue(mover.stopped, "movement is stopped");
        assertTrue(host.toggled, "the module is toggled off");
    }
}

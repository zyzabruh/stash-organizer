package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link StashScanner} step script outside the game. The session is built on the
 * fakes of {@link FakeServices}, so the two-step machine (reset the open counters, then open and
 * read) is exercised as plain Java.
 */
class StashScannerScriptTest {
    @Test
    void opensAndReadsTheQueuedContainerOnce() {
        FakeServices.Inventory inventory = new FakeServices.Inventory();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Game game = new FakeServices.Game();
        FakeServices.Host host = new FakeServices.Host();

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), game, access,
            new FakeServices.Walker(), inventory, new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Scan;

        BlockPos pos = new BlockPos(4, 64, -3);
        game.containers.add(pos);
        session.scanQueue.add(pos);

        for (int tick = 0; tick < 20 && session.phase == StashPhase.Scan; tick++) {
            session.scanner.tick();
        }

        assertTrue(session.scanQueue.isEmpty(), "the queued container is consumed");
        assertEquals(List.of(pos), access.opened, "the container was opened exactly once");
        assertEquals(1, access.reads, "its contents were read once");
        assertTrue(access.closed, "the screen was closed after reading");
        assertEquals(1, access.resets, "the open counters are reset once before the container is opened");
        assertEquals(StashPhase.Done, session.phase, "an empty stash hands over to the planner");
        assertTrue(host.messages.stream().noneMatch(message -> message.startsWith("error:")),
            "the scan ended without an error: " + host.messages);
    }
}

package com.example.addon.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link ShulkerUnpacker} step script outside the game. The script must drop every
 * reference the stash no longer backs with a box, without walking to or opening anything, and then
 * hand over to the packing phase.
 */
class ShulkerUnpackerScriptTest {
    @Test
    void dropsReferencesThatNoLongerPointAtABox() {
        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Game game = new FakeServices.Game();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();

        host.unpacksShulkers = true;

        StashSession session = StashSession.forTest(host, new FakeServices.Mover(), game, access,
            new FakeServices.Walker(), inventory, new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Unpack;

        BlockPos unknown = new BlockPos(0, 64, 0);
        BlockPos unreachable = new BlockPos(2, 64, 0);
        BlockPos emptySlot = new BlockPos(4, 64, 0);

        StashContainer failed = new StashContainer(unreachable, null);
        failed.failed = true;
        session.containers.add(failed);

        StashContainer empty = new StashContainer(emptySlot, null);
        empty.scanned = true;
        session.containers.add(empty);

        List<ShulkerRef> refs = List.of(
            new ShulkerRef(unknown, 0),
            new ShulkerRef(unreachable, 0),
            new ShulkerRef(emptySlot, 5));

        session.shulkers.addAll(refs);

        for (int tick = 0; tick < 40 && session.phase == StashPhase.Unpack; tick++) {
            session.unpacker.tick();
        }

        assertEquals(StashPhase.Pack, session.phase, "the unpack phase hands over to the packing");
        assertTrue(refs.stream().allMatch(ref -> ref.done), "every stale reference is dropped");
        assertTrue(access.opened.isEmpty(), "no container is opened for a stale reference");
        assertEquals(0, access.reads, "no container is read for a stale reference");
        assertTrue(host.messages.stream().noneMatch(message -> message.startsWith("error:")),
            "the phase ends without an error: " + host.messages);
    }
}

package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real {@link ShulkerPacker} step script outside the game: an overflow shulker is mined
 * with a pickaxe, its drop is searched for, and the lost pickup is reported before the phase hands
 * over to the labelling.
 */
class ShulkerPackerScriptTest {
    @Test
    void minesAnOverflowShulkerAndReportsTheLostPickup() {
        // The fake inventory builds empty item stacks, which need the vanilla registries to exist.
        // The bootstrap cannot complete on a plain test JVM (see TestWorld), but its attempt
        // leaves the registries creatable, so this test asks for it instead of depending on which
        // test class the runner happens to load first.
        TestWorld.bootstrap();

        FakeServices.Host host = new FakeServices.Host();
        FakeServices.Mover mover = new FakeServices.Mover();
        FakeServices.Game game = new FakeServices.Game();
        FakeServices.Access access = new FakeServices.Access();
        FakeServices.Inventory inventory = new FakeServices.Inventory();

        host.packsShulkers = true;
        inventory.pickaxeSlot = 0;

        StashSession session = StashSession.forTest(host, mover, game, access, new FakeServices.Walker(),
            inventory, new FakeServices.Spots(), Persistence.NONE);
        access.containers = session.containers;
        session.phase = StashPhase.Pack;

        BlockPos pos = new BlockPos(1, 70, 1);
        game.shulkers.add(pos);

        StashContainer target = new StashContainer(pos, null);
        target.scanned = true;
        target.packing = true;
        session.containers.add(target);

        for (int tick = 0; tick < 400 && session.phase == StashPhase.Pack; tick++) {
            session.packer.tick();
        }

        assertEquals(StashPhase.Arrange, session.phase, "the pack phase hands over to the tidying, then the labelling");
        assertEquals(List.of(pos), game.broken, "the placed shulker was mined exactly once");
        assertTrue(game.shulkers.isEmpty(), "the block is gone from the world");
        assertTrue(mover.moves.contains(pos), "the player walked onto the drop to collect it");
        assertFalse(target.packing, "the target is not packed again");
        assertTrue(target.failed, "the target is marked as failed");
        assertEquals(0, session.packedShulkers, "no shulker was stored");
        assertTrue(host.messages.stream().anyMatch(message ->
                message.startsWith("warning:") && message.contains("was not picked up")),
            "the lost pickup is reported: " + host.messages);
    }
}

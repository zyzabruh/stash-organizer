package com.pro0101.stashorganizer.modules.huntbuddy.sorter;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.player.PlayerEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterSafety {
    static final long PAUSE_LIMIT_NANOS = 120_000_000_000L;
    static final long RESUME_DELAY_NANOS = 5_000_000_000L;

    String waitingReason(boolean ignoreFriends) {
        String driver = driverReason();
        return driver != null ? driver : visiblePlayerReason(ignoreFriends);
    }

    /**
     * The imported sorter used to pause while two of its own addon's modules were running. Those modules are not part
     * of this addon, so the sorter no longer has a driver to wait for; the hook stays for the callers above.
     */
    String driverReason() {
        return null;
    }

    String visiblePlayerReason(boolean ignoreFriends) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.getUuid().equals(mc.player.getUuid())) {
                continue;
            }
            if (ignoreFriends && Friends.get().isFriend(player)) {
                continue;
            }
            return "Player visible: " + player.getName().getString();
        }

        return null;
    }
}

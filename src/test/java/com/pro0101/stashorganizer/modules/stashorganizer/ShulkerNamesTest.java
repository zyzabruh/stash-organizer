package com.pro0101.stashorganizer.modules.stashorganizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite for the shulker name filter: every case is a way a "kit" box could have slipped
 * through the protection. Pure string logic, so no game is needed.
 */
class ShulkerNamesTest {
    @Test
    void foldsPlainAndDecoratedNames() {
        assertEquals("kit", ShulkerNames.normalize("Kit"));
        assertEquals("tools", ShulkerNames.normalize("  TOOLS  "));
        assertEquals("kit", ShulkerNames.normalize("ｋｉｔ"), "fullwidth letters");
        assertEquals("kit", ShulkerNames.normalize("\uD835\uDCF4\uD835\uDCF2\uD835\uDCFD"), "mathematical letters");
        assertEquals("kit", ShulkerNames.normalize("k\u0301it"), "combining accent");
        assertEquals("kit", ShulkerNames.normalize("k\u200Bit"), "zero width space");
        assertEquals("kit", ShulkerNames.normalize("§cKit"), "formatting code");
        assertEquals("kit", ShulkerNames.normalize("ᴋɪᴛ"), "small caps");
        assertEquals("kit", ShulkerNames.normalize("КІТ"), "cyrillic lookalikes");
        assertEquals("kit", ShulkerNames.normalize("κιτ"), "greek lookalikes");
        assertEquals("", ShulkerNames.normalize("§k"));
    }

    @Test
    void matchesDecoratedKits() {
        assertTrue(ShulkerNames.matches(ShulkerNames.normalize("ᴋɪᴛ"), "kit"));
        assertTrue(ShulkerNames.matches(ShulkerNames.normalize("КІТ"), "kit"));
        assertTrue(ShulkerNames.matches(ShulkerNames.normalize("k1t"), "kit"), "leetspeak i");
        assertTrue(ShulkerNames.matches(ShulkerNames.normalize("t00ls"), "tools"), "leetspeak o");
        assertTrue(ShulkerNames.matches(ShulkerNames.normalize("Kit 2024"), "kit"), "kit in a longer name");
        assertTrue(ShulkerNames.matches("tools", "TOOLS"), "pattern is normalized too");
    }

    @Test
    void ignoresOtherBoxes() {
        assertFalse(ShulkerNames.matches(ShulkerNames.normalize("Dirt"), "kit"));
        assertFalse(ShulkerNames.matches(ShulkerNames.normalize("Netherite gear"), "kit"));
        assertFalse(ShulkerNames.matches("kit", ""));
        assertFalse(ShulkerNames.matches("", "kit"));
    }
}

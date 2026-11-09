package com.pro0101.stashorganizer.modules.bephax.util;

import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

/**
 * Replaces the Stardust message helper of the original addon: the module only needs the "[Module] message" line, so it
 * goes through Meteor's chat formatting instead of carrying the whole Stardust toolkit.
 */
public final class MsgUtil {
    private MsgUtil() {}

    public static void sendModuleMsg(String msg, String module) {
        ChatUtils.info("(highlight)%s(default) %s", Utils.nameToTitle(module), msg);
    }
}

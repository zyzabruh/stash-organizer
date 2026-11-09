package com.pro0101.stashorganizer.modules.huntbuddy.util;

import com.pro0101.stashorganizer.StashOrganizerAddon;

public class LogUtil {
   public static void info(String msg) {
      StashOrganizerAddon.LOG.info("{} {}", MsgUtil.getRawPrefix(), msg);
   }

   public static void info(String msg, String module) {
      StashOrganizerAddon.LOG.info("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
   }

   public static void warn(String msg) {
      StashOrganizerAddon.LOG.warn("{} {}", MsgUtil.getRawPrefix(), msg);
   }

   public static void warn(String msg, String module) {
      StashOrganizerAddon.LOG.warn("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
   }

   public static void error(String msg) {
      StashOrganizerAddon.LOG.error("{} {}", MsgUtil.getRawPrefix(), msg);
   }

   public static void error(String msg, String module) {
      StashOrganizerAddon.LOG.error("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
   }
}

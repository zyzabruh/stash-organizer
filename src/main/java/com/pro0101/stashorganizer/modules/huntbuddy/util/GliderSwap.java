package com.pro0101.stashorganizer.modules.huntbuddy.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class GliderSwap {
   private GliderSwap() {
   }

   public static void swapSilently(Hand hand) {
      net.minecraft.client.MinecraftClient mc = meteordevelopment.meteorclient.MeteorClient.mc;
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      mc.interactionManager.interactItem(mc.player, hand);
      mc.player.handSwinging = false;
      mc.player.handSwingTicks = 0;
      mc.player.handSwingProgress = 0.0F;
      mc.player.lastHandSwingProgress = 0.0F;
   }

   public static boolean isSwapSound(Identifier id) {
      if (id == null || !"minecraft".equals(id.getNamespace())) {
         return false;
      }

      String path = id.getPath();
      return path.startsWith("item.armor.equip_") || path.equals("item.elytra.flying");
   }

   public static boolean isGlider(ItemStack stack) {
      return !stack.isEmpty() && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
   }

   public static boolean isChestPiece(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isGlider(stack);
   }

   /**
    * Reports Curse of Binding, i.e. armour that the server refuses to let the
    * player take off. Handlers must never try to swap such a piece out of the
    * chest slot, and must never wear one on purpose.
    */
   public static boolean hasBindingCurse(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      return EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE);
   }

   /**
    * A chest piece that may be worn for a swap without trapping the player in
    * it afterwards.
    */
   public static boolean isSafeChestPiece(ItemStack stack) {
      return isChestPiece(stack) && !hasBindingCurse(stack);
   }

   public static Hand gliderHand(ClientPlayerEntity player) {
      if (isGlider(player.getStackInHand(Hand.MAIN_HAND))) {
         return Hand.MAIN_HAND;
      }

      return isGlider(player.getStackInHand(Hand.OFF_HAND)) ? Hand.OFF_HAND : null;
   }

   public static Hand chestPieceHand(ClientPlayerEntity player) {
      if (isChestPiece(player.getStackInHand(Hand.MAIN_HAND))) {
         return Hand.MAIN_HAND;
      }

      return isChestPiece(player.getStackInHand(Hand.OFF_HAND)) ? Hand.OFF_HAND : null;
   }

   public static int gliderSlot(ClientPlayerEntity player) {
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (isGlider(stack) && !stack.willBreakNextUse()) {
            return i;
         }
      }

      return -1;
   }

   public static boolean hasGlider(ClientPlayerEntity player) {
      return isGlider(player.getEquippedStack(EquipmentSlot.CHEST)) || gliderHand(player) != null || gliderSlot(player) != -1;
   }

   public static boolean wearGlider(ClientPlayerEntity player) {
      if (isGlider(player.getEquippedStack(EquipmentSlot.CHEST))) {
         return true;
      }

      Hand hand = gliderHand(player);
      if (hand != null && !player.getStackInHand(hand).willBreakNextUse()) {
         swapSilently(hand);
         return true;
      }

      int slot = gliderSlot(player);
      if (slot == -1) {
         return false;
      }

      if (slot < 9) {
         ((com.pro0101.stashorganizer.mixins.PlayerInventoryAccessor)player.getInventory()).setSelectedSlot(slot);
         swapSilently(Hand.MAIN_HAND);
         return true;
      }

      if (!QuietClicks.readyOrSettle()) {
         return false;
      }

      com.pro0101.stashorganizer.StashOrganizerAddon.LOG.info("[GliderSwap] inventory-wear slot=" + slot);
      meteordevelopment.meteorclient.utils.player.InvUtils.move().from(slot).toArmor(2);
      return true;
   }

   public static boolean rocketAttached(ClientPlayerEntity player) {
      net.minecraft.client.MinecraftClient mc = meteordevelopment.meteorclient.MeteorClient.mc;
      if (mc.world == null) {
         return false;
      }

      for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
         if (entity instanceof net.minecraft.entity.projectile.FireworkRocketEntity firework
            && firework.isAlive()
            && ((com.pro0101.stashorganizer.mixins.FireworkRocketEntityAccessor)firework).getShooter() == player) {
            return true;
         }
      }

      return false;
   }

   public static String pairProblem(ClientPlayerEntity player) {
      ItemStack worn = player.getEquippedStack(EquipmentSlot.CHEST);
      Hand hand = gliderHand(player);
      if (!isChestPiece(worn) || hand == null) {
         return "chest swap needs a glider and a chest piece, one worn and the other in a hand";
      }

      if (hasBindingCurse(worn) && !player.isCreative()) {
         return "chest piece has Curse of Binding";
      }

      return player.getStackInHand(hand).willBreakNextUse() ? "elytra is spent" : null;
   }
}

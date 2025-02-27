package com.maximde.fancyphysics.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ToolUtil {

    /**
     * Checks if the given tool is a valid axe.
     * This simple check assumes that axe material names end with "_AXE".
     *
     * @param tool the ItemStack to check.
     * @return true if the tool is an axe.
     */
    public static boolean isValidAxe(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        Material type = tool.getType();
        return type.toString().endsWith("_AXE");
    }

    /**
     * Applies the specified damage to the tool. If the resulting damage meets or exceeds the
     * tool’s maximum durability, the tool breaks.
     *
     * @param tool   the ItemStack representing the tool.
     * @param damage the amount of damage to apply.
     * @param player the player using the tool.
     */
    public static void damageTool(ItemStack tool, int damage, Player player) {
        short currentDamage = tool.getDurability();
        short maxDurability = tool.getType().getMaxDurability();
        short newDamage = (short) (currentDamage + damage);

        if (newDamage >= maxDurability) {
            // Remove the broken tool and notify the player.
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.sendMessage("Your axe broke while chopping the tree!");
        } else {
            tool.setDurability(newDamage);
        }
    }
}

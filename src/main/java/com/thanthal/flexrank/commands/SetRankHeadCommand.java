package com.thanthal.flexrank.commands;

import com.thanthal.flexrank.FlexRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SetRankHeadCommand implements CommandExecutor {

    private final FlexRank plugin;

    public SetRankHeadCommand(FlexRank plugin) {
        this.plugin = plugin;
        plugin.getCommand("setrankhead").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (!player.hasPermission("flexrank.admin")) {
            player.sendMessage("§cYou don’t have permission to do this!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§eUsage: /setrankhead <rankNumber>");
            return true;
        }

        int rankNumber;
        try {
            rankNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cRank number must be a valid number!");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§cYou must be holding an item!");
            return true;
        }

        // Save the item in config (as a serializable ItemStack)
        plugin.getConfig().set("heads." + rankNumber, item);
        plugin.saveConfig();
        player.sendMessage("§aSuccessfully set item for rank " + rankNumber + "!");

        return true;
    }
}

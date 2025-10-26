package com.thanthal.flexrank.commands;

import com.thanthal.flexrank.FlexRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public class RankCommand implements CommandExecutor {

    private final FlexRank plugin;

    public RankCommand(FlexRank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /rank reload");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            // Allow console or players with permission
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("flexrank.reload")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            // Perform reload on main thread (commands run on main thread)
            try {
                plugin.reloadAll();
                sender.sendMessage("§aFlexRank configuration reloaded.");
            } catch (Exception e) {
                plugin.getLogger().warning("Exception while reloading FlexRank configuration: " + e.getMessage());
                sender.sendMessage("§cAn error occurred while reloading. See console for details.");
            }

            return true;
        }

        sender.sendMessage("§eUnknown subcommand. Usage: /rank reload");
        return true;
    }
}

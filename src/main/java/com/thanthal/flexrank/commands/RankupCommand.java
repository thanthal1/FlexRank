package com.thanthal.flexrank.commands;

import com.thanthal.flexrank.FlexRank;
import com.thanthal.flexrank.data.RankManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankupCommand implements CommandExecutor {
    private final FlexRank plugin;
    private final RankManager rankManager;

    public RankupCommand(FlexRank plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can rank up.");
            return true;
        }

        String nextRank = rankManager.getNextRank(player);
        if (nextRank == null) {
            player.sendMessage(ChatColor.RED + "You’re already at the highest rank!");
            return true;
        }

        double cost = rankManager.getRankCost(nextRank);
        double progress = rankManager.getPlayerProgress(player, "%placeholder%");
        double requirement = 0; // placeholder, requirements are checked inside attemptRankup

        if (!rankManager.attemptRankup(player, nextRank)) return true;

        return true;
    }
}

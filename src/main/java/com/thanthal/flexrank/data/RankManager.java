package com.thanthal.flexrank.data;

import com.thanthal.flexrank.FlexRank;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RankManager {

    private final FlexRank plugin;
    private final Economy economy;
    private final Map<UUID, String> playerRanks = new HashMap<>();

    public RankManager(FlexRank plugin) {
        this.plugin = plugin;
        this.economy = plugin.getEconomy();

        if (plugin.getConfig().isConfigurationSection("players")) {
            for (String uuidStr : plugin.getConfig().getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String rank = plugin.getConfig().getString("players." + uuidStr);
                if (rank != null) playerRanks.put(uuid, rank);
            }
        }
    }

    public List<String> getRankList() {
        if (!plugin.getConfig().isConfigurationSection("ranks")) return new ArrayList<>();
        return new ArrayList<>(plugin.getConfig().getConfigurationSection("ranks").getKeys(false));
    }

    public String getRankDisplay(String rankKey) {
        return plugin.getConfig().getString("ranks." + rankKey + ".display", rankKey);
    }

    public String getRankDescription(String rankKey) {
        return plugin.getConfig().getString("ranks." + rankKey + ".description", "No description");
    }

    public double getRankCost(String rankKey) {
        return plugin.getConfig().getDouble("ranks." + rankKey + ".cost", 0);
    }

    public Map<String, Double> getRankRequirements(String rankKey) {
        Map<String, Double> reqs = new HashMap<>();
        if (plugin.getConfig().isConfigurationSection("ranks." + rankKey + ".requirements")) {
            for (String placeholder : plugin.getConfig().getConfigurationSection("ranks." + rankKey + ".requirements").getKeys(false)) {
                double value = plugin.getConfig().getDouble("ranks." + rankKey + ".requirements." + placeholder, 0);
                reqs.put(placeholder, value);
            }
        }
        return reqs;
    }

    public List<String> getRankCommands(String rankKey) {
        return plugin.getConfig().getStringList("ranks." + rankKey + ".commands");
    }

    public String getPlayerRank(Player player) {
        return playerRanks.getOrDefault(player.getUniqueId(), "None");
    }

    public double getPlayerProgress(Player player, String placeholder) {
        try {
            String raw = PlaceholderAPI.setPlaceholders(player, placeholder);
            return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean hasUnlocked(Player player, String rankKey) {
        List<String> ranks = getRankList();
        int idx = ranks.indexOf(rankKey);
        if (idx < 0) return false;
        if (idx == 0) return true;
        String previousRank = ranks.get(idx - 1);
        return getPlayerRank(player).equals(previousRank);
    }

    public boolean attemptRankup(Player player, String rankKey) {
        if (!hasUnlocked(player, rankKey)) {
            player.sendMessage("§cYou must complete the previous rank first!");
            return false;
        }

        double cost = getRankCost(rankKey);
        Map<String, Double> requirements = getRankRequirements(rankKey);

        for (var entry : requirements.entrySet()) {
            String placeholder = entry.getKey();
            double required = entry.getValue();
            double value = getPlayerProgress(player, placeholder);
            if (value < required) {
                player.sendMessage("§cRequirement not met: " + placeholder + " (" + (int) value + "/" + (int) required + ")");
                return false;
            }
        }

        if (cost > 0 && economy.getBalance(player) < cost) {
            player.sendMessage("§cYou do not have enough money! Cost: $" + cost);
            return false;
        }

        if (cost > 0) economy.withdrawPlayer(player, cost);

        // Save rank
        playerRanks.put(player.getUniqueId(), rankKey);
        plugin.getConfig().set("players." + player.getUniqueId(), rankKey);
        plugin.saveConfig();

        // Execute rank commands
        for (String cmd : getRankCommands(rankKey)) {
            String parsed = cmd.replace("%player_name%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        player.sendMessage("§aYou have ranked up to " + getRankDisplay(rankKey) + "!");
        return true;
    }

    public String getNextRank(Player player) {
        List<String> ranks = getRankList();
        String current = getPlayerRank(player);
        int idx = ranks.indexOf(current);
        if (idx == -1) return ranks.isEmpty() ? null : ranks.get(0);
        if (idx + 1 >= ranks.size()) return null;
        return ranks.get(idx + 1);
    }

    /**
     * Sets a player's rank directly (for admin use)
     */
    public void setPlayerRank(Player player, String rankKey) {
        playerRanks.put(player.getUniqueId(), rankKey);
        plugin.getConfig().set("players." + player.getUniqueId(), rankKey);
        plugin.saveConfig();

        // Execute rank commands if any
        for (String cmd : getRankCommands(rankKey)) {
            String parsed = cmd.replace("%player_name%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}

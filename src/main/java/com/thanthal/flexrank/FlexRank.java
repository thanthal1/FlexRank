package com.thanthal.flexrank;

import com.thanthal.flexrank.commands.RankupCommand;
import com.thanthal.flexrank.data.RankManager;
import com.thanthal.flexrank.gui.RankGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

public class FlexRank extends JavaPlugin {

    private static FlexRank instance;
    private RankManager rankManager;
    private RankGUI rankGUI;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("config.yml", false); // Ensure config exists
        reloadConfig(); // Load latest config

        if (!setupEconomy()) {
            getLogger().severe("Vault not found — disabling plugin!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.rankManager = new RankManager(this);
        this.rankGUI = new RankGUI(this);

        // Command to rank up
        getCommand("rankup").setExecutor(new RankupCommand(this));

        // Command to open GUI directly
        getCommand("rankgui").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the rank GUI!");
                return true;
            }
            rankGUI.openGUI(player);
            return true;
        });

        // Command to set any item for a rank
        getCommand("setrankitem").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
            if (!player.hasPermission("flexrank.setrankitem")) {
                player.sendMessage("§cYou don’t have permission to use this command!");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("§eUsage: /setrankitem <rankNumber>");
                return true;
            }

            String rankNumber = args[0];
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                player.sendMessage("§cYou must be holding an item!");
                return true;
            }

            // Save the full ItemStack so heads are treated like any other item
            getConfig().set("heads." + rankNumber, itemInHand);
            saveConfig();
            reloadConfig(); // Reload to ensure changes are picked up

            // Verify the save worked by reading as ItemStack
            ItemStack savedItem = getConfig().getItemStack("heads." + rankNumber);
            if (savedItem == null) {
                player.sendMessage("§cError: Failed to save item for this rank!");
                getLogger().warning(String.format("Head/item for rank %s failed to save!", rankNumber));
                return true;
            }

            player.sendMessage("§aItem for rank " + rankNumber + " has been saved!");
            player.sendMessage("§7Run /dumphead " + rankNumber + " to verify the data.");
            return true;
        });

        // Command to dump raw head config for debugging
        getCommand("dumphead").setExecutor((sender, command, label, args) -> {
            if (args.length != 1) {
                sender.sendMessage("§eUsage: /dumphead <rankNumber>");
                return true;
            }
            String rankNumber = args[0];
            Object raw = getConfig().get("heads." + rankNumber);
            if (raw == null) {
                sender.sendMessage("§cNo head configured for rank " + rankNumber + "");
                String key = String.format("heads.%s", rankNumber);
                getLogger().info(String.format("Dumphead: %s = null", key));
                return true;
            }
            String msg = "heads." + rankNumber + " -> type=" + raw.getClass().getName() + ", value=" + raw.toString();
            // Log full value to console (may be large)
            getLogger().info(msg);
            // Send a truncated version to the sender to avoid spamming chat
            String shortMsg = msg.length() > 240 ? msg.substring(0, 240) + "... (truncated)" : msg;
            sender.sendMessage("§6[FlexRank] " + shortMsg);
            return true;
        });

        // Command to forcibly set a player's rank (admin)
        getCommand("setrank").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("flexrank.setrank")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage("§eUsage: /setrank <player> <rank>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer " + args[0] + " is not online!");
                return true;
            }

            String rankName = args[1];
            // Validate rank exists
            if (!rankManager.getRankList().contains(rankName)) {
                sender.sendMessage("§cRank " + rankName + " doesn't exist! Available ranks: " + String.join(", ", rankManager.getRankList()));
                return true;
            }

            // Force set the rank
            rankManager.setPlayerRank(target, rankName);
            sender.sendMessage("§aSet " + target.getName() + "'s rank to " + rankName);
            target.sendMessage("§aYour rank was set to " + rankName + " by an administrator.");
            return true;
        });

        getLogger().info("FlexRank loaded successfully!");
    }

    public static FlexRank getInstance() {
        return instance;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public RankGUI getRankGUI() {
        return rankGUI;
    }

    public Economy getEconomy() {
        return economy;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}

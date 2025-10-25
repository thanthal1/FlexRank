package com.thanthal.flexrank;

import com.thanthal.flexrank.commands.RankupCommand;
import com.thanthal.flexrank.data.RankManager;
import com.thanthal.flexrank.gui.RankGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public class FlexRank extends JavaPlugin {
    private static FlexRank instance;
    private RankManager rankManager;
    private RankGUI rankGUI;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

package com.thanthal.flexrank.gui;

import com.thanthal.flexrank.FlexRank;
import com.thanthal.flexrank.data.RankManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RankGUI implements Listener {

    private final FlexRank plugin;
    private final RankManager rankManager;

    public RankGUI(FlexRank plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // Custom InventoryHolder to lock the inventory
    public static class RankInventoryHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openGUI(Player player) {
        List<String> ranks = rankManager.getRankList();
        int size = ((ranks.size() / 9) + 1) * 9;

        Inventory inv = Bukkit.createInventory(new RankInventoryHolder(), size, "§6Ranks");
        String currentRank = rankManager.getPlayerRank(player);

        for (int i = 0; i < ranks.size(); i++) {
            String rankKey = ranks.get(i);

            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer("headdb:" + (i + 1)));
            meta.setDisplayName(rankManager.getRankDisplay(rankKey));

            List<String> lore = new ArrayList<>();
            lore.add(rankManager.getRankDescription(rankKey));

            Map<String, Double> requirements = rankManager.getRankRequirements(rankKey);
            for (Map.Entry<String, Double> entry : requirements.entrySet()) {
                String placeholder = entry.getKey();
                double required = entry.getValue();
                double currentValue = rankManager.getPlayerProgress(player, placeholder);

                // Grab label from config
                String cleanKey = placeholder.replace("%", "").trim();
                String label = plugin.getConfig().getString("labels." + cleanKey, cleanKey);

                String color = currentValue >= required ? "§a" : "§c";
                lore.add(color + label + ": " + (int) currentValue + " / " + (int) required);
            }

            // Cost
            double cost = rankManager.getRankCost(rankKey);
            lore.add("§7Cost: §a$" + cost);

            // Rank status
            if (currentRank.equals(rankKey)) lore.add("§6Current Rank");
            else if (rankManager.hasUnlocked(player, rankKey)) lore.add("§aUnlocked");
            else lore.add("§cLocked");

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
        plugin.getLogger().info("Opened rank GUI for player: " + player.getName());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof RankInventoryHolder)) return;

        e.setCancelled(true); // lock inventory

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        String clickedRank = e.getCurrentItem().getItemMeta().getDisplayName();

        if (rankManager.hasUnlocked(player, clickedRank) &&
            !rankManager.getPlayerRank(player).equals(clickedRank)) {

            boolean success = rankManager.attemptRankup(player, clickedRank);
            if (success) player.sendMessage("§aSuccessfully ranked up to " + clickedRank + "!");
            else player.sendMessage("§cCould not rank up to " + clickedRank + ". Check requirements or balance.");

        } else if (rankManager.getPlayerRank(player).equals(clickedRank)) {
            player.sendMessage("§eYou are already at this rank!");
        } else {
            player.sendMessage("§cThis rank is locked! Complete previous ranks first.");
        }

        openGUI(player); // refresh GUI
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RankInventoryHolder) {
            e.setCancelled(true); // lock dragging
        }
    }
}

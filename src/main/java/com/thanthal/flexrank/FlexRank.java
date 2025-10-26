package com.thanthal.flexrank;

import com.thanthal.flexrank.commands.RankupCommand;
import com.thanthal.flexrank.commands.RankCommand;
import com.thanthal.flexrank.data.RankManager;
import com.thanthal.flexrank.gui.RankGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

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

        // Determine whether any rank uses currency (cost > 0). If so, Vault is required.
        boolean needsEconomy = false;
        if (getConfig().isConfigurationSection("ranks")) {
            for (String rk : getConfig().getConfigurationSection("ranks").getKeys(false)) {
                if (getConfig().getDouble("ranks." + rk + ".cost", 0) > 0) {
                    needsEconomy = true;
                    break;
                }
            }
        }

        if (needsEconomy) {
            if (!setupEconomy()) {
                getLogger().severe("Vault not found — disabling plugin because a rank requires currency!");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            // Economy not required by config; try to initialize Vault if present but don't fail if absent.
            setupEconomy(); // best-effort; economy remains null if not available
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

            // Bukkit's Inventory#getItemInMainHand returns a non-null ItemStack (AIR when empty),
            // so only check for AIR here.
            if (itemInHand.getType() == Material.AIR) {
                player.sendMessage("§cYou must be holding an item!");
                return true;
            }

            // Allow any item to be used for a rank. If it's a player head, attempt to
            // extract texture data; otherwise we'll simply save the item as-is.
            SkullMeta meta = null;
            String textureValue = null;
            if (itemInHand.getType() == Material.PLAYER_HEAD) {
                meta = (SkullMeta) itemInHand.getItemMeta();
                if (meta == null) {
                    player.sendMessage("§cInvalid skull item!");
                    return true;
                }

                // Try Paper's PlayerProfile API first (modern method)
                var playerProfile = meta.getPlayerProfile();
                if (playerProfile != null) {
                try {
                    var textures = playerProfile.getTextures();
                    if (textures != null) {
                        var skin = textures.getSkin();
                        if (skin != null) {
                            var url = skin.toString();
                            if (url.startsWith("http://textures.minecraft.net/texture/")) {
                                String hash = url.substring("http://textures.minecraft.net/texture/".length());
                                // Construct proper Minecraft texture JSON and Base64 encode it
                                String textureJson = String.format("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}", hash);
                                textureValue = java.util.Base64.getEncoder().encodeToString(textureJson.getBytes());
                            }
                        }
                    }
                } catch (IllegalStateException ignored) {
                    // Fall through to reflection method
                }
            }
                // Fallback to reflection if Paper API didn't work
                if (textureValue == null) {
                    try {
                        // Try to get GameProfile
                        Field profileField = null;
                        Class<?> clazz = meta.getClass();
                        while (clazz != null) {
                            try {
                                profileField = clazz.getDeclaredField("profile");
                                break;
                            } catch (NoSuchFieldException ignored) {
                                clazz = clazz.getSuperclass();
                            }
                        }

                        if (profileField != null) {
                            profileField.setAccessible(true);
                            Object profile = profileField.get(meta);
                            if (profile != null) {
                                try {
                                    // First try Paper-specific method
                                    Method getPropertiesMethod = profile.getClass().getMethod("getProperties");
                                    Object properties = getPropertiesMethod.invoke(profile);
                                    if (properties != null) {
                                        Method getMethod = properties.getClass().getMethod("get", String.class);
                                        Collection<?> textures = (Collection<?>) getMethod.invoke(properties, "textures");
                                        if (textures != null && !textures.isEmpty()) {
                                            Object property = textures.iterator().next();
                                            // The getValue() result should already be Base64 encoded
                                            textureValue = (String) property.getClass().getMethod("getValue").invoke(property);
                                        }
                                    }
                                } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                                    // If Paper method fails, try Bukkit/Spigot method
                                    try {
                                        Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                                        Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
                                        if (properties != null && propertyMapClass.isInstance(properties)) {
                                            Collection<?> textures = (Collection<?>) propertyMapClass.getMethod("get", Object.class).invoke(properties, "textures");
                                            if (textures != null && !textures.isEmpty()) {
                                                Object property = textures.iterator().next();
                                                textureValue = (String) property.getClass().getMethod("getValue").invoke(property);
                                            }
                                        }
                                    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
                                        // Both methods failed
                                    }
                                }
                            }
                        }
                    } catch (IllegalAccessException | SecurityException ex) {
                        getLogger().warning(String.format("Failed to extract texture from skull: %s", ex.getMessage()));
                        player.sendMessage("§cFailed to extract texture from skull. See console for details.");
                        return true;
                    }
                }
            }

            // Save the full ItemStack asynchronously so we don't block the server thread.
            final String path = "heads." + rankNumber;
            final Map<String, Object> serialized = itemInHand.serialize();
            // Add type hint so YamlConfiguration will deserialize it back into an ItemStack
            serialized.put("==", "org.bukkit.inventory.ItemStack");
            final Player senderPlayer = player;

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    File cfgFile = new File(getDataFolder(), "config.yml");
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cfgFile);

                    yaml.set(path, serialized);
                    yaml.save(cfgFile);

                    // Back on main thread: reload config so plugin sees the change and notify player
                    Bukkit.getScheduler().runTask(this, () -> {
                        reloadConfig();
                        ItemStack savedItem = getConfig().getItemStack(path);
                        if (savedItem == null) {
                            senderPlayer.sendMessage("§cError: Failed to save item for this rank!");
                            getLogger().warning(String.format("Head/item for rank %s failed to save!", rankNumber));
                        } else {
                            senderPlayer.sendMessage("§aItem for rank " + rankNumber + " has been saved!");
                            senderPlayer.sendMessage("§7Run /dumphead " + rankNumber + " to verify the data.");
                        }
                    });
                } catch (Exception ex) {
                    getLogger().log(java.util.logging.Level.WARNING, "Failed to save head config asynchronously", ex);
                    Bukkit.getScheduler().runTask(this, () -> senderPlayer.sendMessage("§cFailed to save item (IO error). See console."));
                }
            });

            return true;
        });

        // Command to dump raw head config for debugging
        getCommand("dumphead").setExecutor((sender, command, label, args) -> {
            if (args.length != 1) {
                sender.sendMessage("§eUsage: /dumphead <rankNumber>");
                return true;
            }
            String rankNumber = args[0];
            String path = "heads." + rankNumber;

            Object raw = getConfig().get(path);
            if (raw == null) {
                sender.sendMessage("§cNo head configured for rank " + rankNumber + "");
                // dumphead: no head configured for path
                return true;
            }

            // Log raw config node info
            // dumphead: raw config node present
            sender.sendMessage("§6[FlexRank] Raw type: §e" + raw.getClass().getName());

            // Try to get ItemStack via getItemStack
            ItemStack stack = getConfig().getItemStack(path);
            if (stack == null) {
                sender.sendMessage("§6[FlexRank] getItemStack returned: §cnull");
            } else {
                sender.sendMessage("§6[FlexRank] getItemStack -> type=" + stack.getType() + ", amount=" + stack.getAmount());
                // dumphead: getItemStack returned an ItemStack
                if (stack.getItemMeta() instanceof SkullMeta sm) {
                    sender.sendMessage("§6[FlexRank] Item has SkullMeta");

                    // Try Paper PlayerProfile
                    try {
                        var pp = sm.getPlayerProfile();
                        if (pp != null) {
                            sender.sendMessage("§6[FlexRank] PlayerProfile present: id=" + pp.getUniqueId());
                            try {
                                var textures = pp.getTextures();
                                if (textures != null && textures.getSkin() != null) {
                                    sender.sendMessage("§6[FlexRank] PlayerProfile skin URL: §e" + textures.getSkin().toString());
                                }
                            } catch (Exception t) {
                                // ignore
                            }
                        }
                    } catch (NoSuchMethodError ignored) {
                        // not available
                    } catch (Exception ignored) {
                        // not available
                    }

                    // Reflection fallback: look for profile field on SkullMeta implementation
                    try {
                        Field profileField = null;
                        Class<?> clazz = sm.getClass();
                        while (clazz != null) {
                            try {
                                profileField = clazz.getDeclaredField("profile");
                                break;
                            } catch (NoSuchFieldException ignored) {
                                clazz = clazz.getSuperclass();
                            }
                        }
                        if (profileField != null) {
                            profileField.setAccessible(true);
                            Object profile = profileField.get(sm);
                            if (profile != null) {
                                // dumphead: found GameProfile instance
                                try {
                                    Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
                                    if (properties != null) {
                                        try {
                                            Object textures = properties.getClass().getMethod("get", Object.class).invoke(properties, "textures");
                                            if (textures instanceof java.util.Collection<?> coll && !coll.isEmpty()) {
                                                Object prop = coll.iterator().next();
                                                String val = (String) prop.getClass().getMethod("getValue").invoke(prop);
                                                String sig = null;
                                                try {
                                                    sig = (String) prop.getClass().getMethod("getSignature").invoke(prop);
                                                } catch (Exception t) { /* ignore */ }
                                                sender.sendMessage("§6[FlexRank] GameProfile texture value length: " + (val == null ? 0 : val.length()));
                                                if (sig != null) sender.sendMessage("§6[FlexRank] GameProfile texture signature present");
                                            }
                                        } catch (NoSuchMethodException nsme) {
                                            // different property API
                                        }
                                    }
                                } catch (Exception t) {
                                    getLogger().warning(String.format("Failed to inspect GameProfile properties: %s", t.getMessage()));
                                }
                            } else {
                                sender.sendMessage("§6[FlexRank] profile field present but null");
                            }
                        } else {
                            sender.sendMessage("§6[FlexRank] No 'profile' field found on SkullMeta impl");
                        }
                    } catch (Exception t) {
                        getLogger().warning(String.format("Unexpected error while dumping skull meta: %s", t.getMessage()));
                    }
                } else {
                    sender.sendMessage("§6[FlexRank] Saved item has no SkullMeta");
                }
            }

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

    // Command group for administrative rank-related subcommands (e.g. /rank reload)
    getCommand("rank").setExecutor(new RankCommand(this));

    // FlexRank loaded (info logs suppressed)
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

    /**
     * Reload plugin configuration, reinitialize economy (best-effort) and rebuild
     * RankManager and RankGUI instances. This is intended to be called on the main thread.
     */
    public void reloadAll() {
        // Reload config file
        reloadConfig();

        // Re-evaluate economy need
        boolean needsEconomy = false;
        if (getConfig().isConfigurationSection("ranks")) {
            for (String rk : getConfig().getConfigurationSection("ranks").getKeys(false)) {
                if (getConfig().getDouble("ranks." + rk + ".cost", 0) > 0) {
                    needsEconomy = true;
                    break;
                }
            }
        }

        if (needsEconomy) {
            if (!setupEconomy()) {
                getLogger().warning("Vault not found — economy features will be unavailable until Vault is installed.");
            }
        } else {
            // Best-effort init if present
            setupEconomy();
        }

        // Recreate managers so runtime config changes take effect
        this.rankManager = new com.thanthal.flexrank.data.RankManager(this);
        this.rankGUI = new com.thanthal.flexrank.gui.RankGUI(this);
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

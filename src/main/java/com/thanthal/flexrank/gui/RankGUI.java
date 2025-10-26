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
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

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
    plugin.reloadConfig(); // Ensure we have latest config data
    List<String> ranks = rankManager.getRankList();
    int size = ((ranks.size() / 9) + 1) * 9;
    Inventory inv = Bukkit.createInventory(new RankInventoryHolder(), size, "§6Ranks");
    String currentRank = rankManager.getPlayerRank(player);

    for (int i = 0; i < ranks.size(); i++) {
        String rankKey = ranks.get(i);

        // Try to load a saved ItemStack first (treat heads like any other item)
        int rankNumber = i + 1;
        ItemStack storedItem = plugin.getConfig().getItemStack("heads." + rankNumber);
        ItemStack item;
        SkullMeta meta = null;
        TextureData textureData = null;
        if (storedItem != null) {
            item = storedItem.clone();
            if (item.getItemMeta() instanceof SkullMeta sm) meta = sm;
        } else {
            // Create default player head and try legacy/raw config formats
            item = new ItemStack(Material.PLAYER_HEAD);
            meta = (SkullMeta) item.getItemMeta();
            Object rawHead = plugin.getConfig().get("heads." + rankNumber);
            if (rawHead == null) {
                plugin.getLogger().info("No head configured for rank " + rankNumber + " (" + rankKey + ")");
            } else {
                if (rawHead instanceof String) {
                    textureData = new TextureData((String) rawHead, null);
                } else {
                    // Log the raw object so we can see what's stored in config
                    plugin.getLogger().info("Head config for rank " + rankNumber + " is type: " + rawHead.getClass().getName() + " -> " + rawHead.toString());
                    // Try to find a texture value and optional signature inside the structure
                    textureData = findTextureDataInObject(rawHead);
                }
            }
        }

    if (textureData != null && textureData.value != null && !textureData.value.isEmpty() && meta != null) {
            // Log some debug info so server console shows why skull injection may fail
            plugin.getLogger().info("Rank " + rankKey + " - SkullMeta implementation: " + meta.getClass().getName());
            try {
                // list declared fields for debugging
                Field[] declared = meta.getClass().getDeclaredFields();
                StringBuilder sb = new StringBuilder("Declared fields: ");
                for (Field f : declared) sb.append(f.getName()).append(',');
                plugin.getLogger().info(sb.toString());
                String originalValue = textureData.value;
                String signature = textureData.signature;
                plugin.getLogger().info("Base64 texture length: " + (originalValue == null ? 0 : originalValue.length()));

                // Create GameProfile with texture. Prefer preserving signature if present (signed Mojang textures).
                // GameProfile requires a non-null name in newer authlib versions. Use empty string.
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "");

                                String encodedTexture = null;
                                
                                // First check if the value is a texture hash
                                if (originalValue != null && originalValue.matches("^[0-9a-f]{64}$")) {
                                    plugin.getLogger().info("Converting texture hash to full texture data");
                                    String textureJson = String.format("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}", originalValue);
                                    encodedTexture = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
                                } else if (originalValue != null) {
                                    // Try to decode existing base64 and validate/fix if needed
                                    try {
                                        byte[] decoded = Base64.getDecoder().decode(originalValue);
                                        String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                                        final String debugStr = decodedStr.length() > 200 ? decodedStr.substring(0, 200) + "..." : decodedStr;
                                        plugin.getLogger().info(() -> String.format("Decoded texture payload: %s", debugStr));                        int urlIndex = decodedStr.indexOf("\"url\"");
                        String foundUrl = null;
                        if (urlIndex != -1) {
                            int colon = decodedStr.indexOf(':', urlIndex);
                            if (colon != -1) {
                                int firstQuote = decodedStr.indexOf('"', colon);
                                if (firstQuote != -1) {
                                    int secondQuote = decodedStr.indexOf('"', firstQuote + 1);
                                    if (secondQuote != -1) {
                                        foundUrl = decodedStr.substring(firstQuote + 1, secondQuote);
                                    }
                                }
                            }
                        } else {
                            int httpIndex = decodedStr.indexOf("http");
                            if (httpIndex != -1) {
                                int end = decodedStr.indexOf('"', httpIndex);
                                if (end == -1) end = decodedStr.indexOf('}', httpIndex);
                                if (end == -1) end = decodedStr.length();
                                foundUrl = decodedStr.substring(httpIndex, end).replaceAll("[\"}]", "");
                            }
                        }

                        if (foundUrl != null && !foundUrl.isEmpty()) {
                            plugin.getLogger().info("Extracted skin URL: " + foundUrl);
                            String textureJson = String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}", foundUrl);
                            encodedTexture = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
                            final String textureValue = encodedTexture;
                            plugin.getLogger().info(() -> String.format("Re-encoded texture JSON length: %d", textureValue.length()));
                        } else {
                            plugin.getLogger().info("No URL found inside decoded texture payload; using original base64");
                        }
                    } catch (IllegalArgumentException iae) {
                        plugin.getLogger().warning("Provided texture string was not valid Base64; using original value");
                    }
                } else {
                    plugin.getLogger().info("Found texture signature present, preserving signed texture value");
                }

                if (signature != null && !signature.isEmpty()) {
                    profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", originalValue, signature));
                } else {
                    profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", encodedTexture));
                }

                // Inject GameProfile into SkullMeta. The implementation class may keep the profile field
                // on a superclass, so walk the class hierarchy to find it.
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
                    profileField.set(meta, profile);
                    plugin.getLogger().info("Applied custom head for rank " + rankKey + " (in class " + profileField.getDeclaringClass().getName() + ")");
                } else {
                    // Try Paper's PlayerProfile API as a fallback (use reflection so plugin still runs on Spigot)
                    try {
                        Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                        Class<?> playerProfileClass = Class.forName("org.bukkit.profile.PlayerProfile");
                        Class<?> profilePropertyClass = Class.forName("org.bukkit.profile.ProfileProperty");

                        java.lang.reflect.Method createProfile = bukkitClass.getMethod("createProfile", java.util.UUID.class);
                        Object paperProfile = createProfile.invoke(null, java.util.UUID.randomUUID());

                        Object prop = null;
                        // Try to construct a ProfileProperty that preserves signatures when present (3-arg constructor)
                        try {
                            if (signature != null && !signature.isEmpty()) {
                                try {
                                    java.lang.reflect.Constructor<?> propCtor3 = profilePropertyClass.getConstructor(String.class, String.class, String.class);
                                    prop = propCtor3.newInstance("textures", originalValue, signature);
                                } catch (NoSuchMethodException ns) {
                                    // fallback to 2-arg if 3-arg not available
                                    java.lang.reflect.Constructor<?> propCtor2 = profilePropertyClass.getConstructor(String.class, String.class);
                                    prop = propCtor2.newInstance("textures", originalValue);
                                }
                            } else {
                                java.lang.reflect.Constructor<?> propCtor2 = profilePropertyClass.getConstructor(String.class, String.class);
                                prop = propCtor2.newInstance("textures", encodedTexture);
                            }
                        } catch (NoSuchMethodException ns2) {
                            // leave prop null and try other additions below
                        }

                        // Try addProperty or setProperty depending on API
                        try {
                            java.lang.reflect.Method addProp = playerProfileClass.getMethod("addProperty", profilePropertyClass);
                            if (prop != null) addProp.invoke(paperProfile, prop);
                        } catch (NoSuchMethodException ex) {
                            try {
                                java.lang.reflect.Method setProp = playerProfileClass.getMethod("setProperty", profilePropertyClass);
                                if (prop != null) setProp.invoke(paperProfile, prop);
                            } catch (NoSuchMethodException ex2) {
                                // try getProperties -> add
                                java.lang.reflect.Method getProps = playerProfileClass.getMethod("getProperties");
                                Object props = getProps.invoke(paperProfile);
                                if (props instanceof java.util.Collection && prop != null) {
                                    ((java.util.Collection) props).add(prop);
                                }
                            }
                        }

                        java.lang.reflect.Method setPlayerProfile = meta.getClass().getMethod("setPlayerProfile", playerProfileClass);
                        setPlayerProfile.invoke(meta, paperProfile);
                        plugin.getLogger().info("Applied custom head for rank " + rankKey + " via Paper PlayerProfile fallback");
                    } catch (ClassNotFoundException cnf) {
                        // Paper API not present; will fall through to warning below
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Paper PlayerProfile fallback failed for rank " + rankKey + ": " + ex.getMessage());
                    }

                    // If the field wasn't found, log a helpful warning so the server admin can see why
                    plugin.getLogger().warning("Could not find 'profile' field on SkullMeta implementation; head for rank " + rankKey + " will be default Steve.");
                }
            } catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
                final String error = e.getMessage();
                final String rank = rankKey;
                plugin.getLogger().warning(() -> String.format("Failed to set Base64 head for rank %s: %s", rank, error));
            }
        } else {
            if (meta == null) {
                final String rank = rankKey;
                plugin.getLogger().warning(() -> String.format("SkullMeta is null for rank item %s, skipping texture injection", rank));
            } else {
                final String rank = rankKey;
                final int number = rankNumber;
                plugin.getLogger().info(() -> String.format("No texture/base64 present for rank %s (rank #%d) - showing default head", rank, number));
            }
        }

        // Display name
        if (meta != null) meta.setDisplayName(rankManager.getRankDisplay(rankKey));

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(rankManager.getRankDescription(rankKey));

        Map<String, Double> requirements = rankManager.getRankRequirements(rankKey);
        for (Map.Entry<String, Double> entry : requirements.entrySet()) {
            String placeholder = entry.getKey();
            double required = entry.getValue();
            double currentValue = rankManager.getPlayerProgress(player, placeholder);

            String cleanKey = placeholder.replace("%", "").trim();
            String label = plugin.getConfig().getString("labels." + cleanKey, cleanKey);

            String color = currentValue >= required ? "§a" : "§c";
            lore.add(color + label + ": " + (int) currentValue + " / " + (int) required);
        }

        double cost = rankManager.getRankCost(rankKey);
        lore.add("§7Cost: §a$" + cost);

        // Rank status
        if (currentRank.equals(rankKey)) lore.add("§6Current Rank");
        else if (rankManager.hasUnlocked(player, rankKey)) lore.add("§aUnlocked");
        else lore.add("§cLocked");

        if (meta != null) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        inv.setItem(i, item);
    }

    player.openInventory(inv);
    plugin.getLogger().info(String.format("Opened rank GUI for player: %s", player.getName()));
}





    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof RankInventoryHolder)) return;

        e.setCancelled(true);

        ItemStack currentItem = e.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) return;
        
        var meta = currentItem.getItemMeta();
        if (meta == null) return;
        
        String clickedRank = meta.getDisplayName();

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

        openGUI(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RankInventoryHolder) {
            e.setCancelled(true);
        }
    }

    // Helper to hold texture value and optional signature when present in serialized data
    private static class TextureData {
        public final String value;
        public final String signature;
        public TextureData(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    // Recursively search a config object for texture value and optional signature
    private TextureData findTextureDataInObject(Object obj) {
        if (obj == null) return null;

        // Log the path we're searching to debug the extraction
        StringBuilder sb = new StringBuilder();
        sb.append("Searching in object type: ").append(obj.getClass().getName());
        plugin.getLogger().info(sb.toString());

        // Handle MemorySection (from YAML config)
        if (obj instanceof org.bukkit.configuration.ConfigurationSection section) {
            plugin.getLogger().info(() -> "Config keys at " + section.getCurrentPath() + ": " + section.getKeys(false));

            // First try components -> minecraft:profile -> properties path
            if (section.isConfigurationSection("components")) {
                ConfigurationSection components = section.getConfigurationSection("components");
                if (components != null && components.isConfigurationSection("minecraft:profile")) {
                    ConfigurationSection profile = components.getConfigurationSection("minecraft:profile");
                    if (profile != null && profile.isList("properties")) {
                        List<?> properties = profile.getList("properties");
                        if (properties != null) {
                            for (Object prop : properties) {
                                if (prop instanceof Map<?, ?> propMap) {
                                    Object name = propMap.get("name");
                                    if ("textures".equals(name)) {
                                        Object value = propMap.get("value");
                                        Object signature = propMap.get("signature");
                                        if (value instanceof String val && signature instanceof String sig) {
                                            plugin.getLogger().info("Found texture data in minecraft:profile.properties");
                                            return new TextureData(val, sig);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Check direct values in this section (in case it's serialized differently)
            if (section.isString("value")) {
                String val = section.getString("value");
                String sig = section.getString("signature");
                if (val != null && val.length() >= 50 && val.matches("^[A-Za-z0-9+/=]+$")) {
                    plugin.getLogger().info("Found texture data as direct config values");
                    return new TextureData(val, sig);
                }
            }

            // Recursively check all sub-sections
            for (String key : section.getKeys(false)) {
                if (section.isConfigurationSection(key)) {
                    TextureData td = findTextureDataInObject(section.getConfigurationSection(key));
                    if (td != null) return td;
                }
            }
            return null;
        }

        // Handle String (raw texture)
        if (obj instanceof String s) {
            if (s.length() >= 50 && s.matches("^[A-Za-z0-9+/=]+$")) return new TextureData(s, null);
            return null;
        }

        // Handle Map (serialized ItemStack or property map)
        if (obj instanceof Map<?, ?> map) {
            // Check for properties array
            if (map.get("properties") instanceof Iterable<?> properties) {
                for (Object prop : properties) {
                    if (prop instanceof Map<?, ?> propMap) {
                        Object name = propMap.get("name");
                        if ("textures".equals(name)) {
                            Object value = propMap.get("value");
                            Object signature = propMap.get("signature");
                            if (value instanceof String val && signature instanceof String sig) {
                                plugin.getLogger().info("Found texture data in properties array");
                                return new TextureData(val, sig);
                            }
                        }
                    }
                }
            }

            // Check direct name/value/signature
            if (map.get("name") instanceof String name && "textures".equalsIgnoreCase(name)) {
                Object val = map.get("value");
                Object sig = map.get("signature");
                if (val instanceof String value) {
                    plugin.getLogger().info("Found texture data in direct property");
                    return new TextureData(value, sig instanceof String ? (String) sig : null);
                }
            }

            // Recursively check all values (fallback)
            for (Map.Entry<?, ?> e : map.entrySet()) {
                TextureData td = findTextureDataInObject(e.getValue());
                if (td != null) return td;
            }
        }
        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                TextureData td = findTextureDataInObject(item);
                if (td != null) return td;
            }
        }
        return null;
    }
}


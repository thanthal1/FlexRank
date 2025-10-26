package com.thanthal.flexrank.gui;

import com.thanthal.flexrank.FlexRank;
import com.thanthal.flexrank.data.RankManager;

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

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


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
    // Determine GUI size: prefer explicit config value, otherwise compute smallest multiple
    // of 9 that fits all ranks.
    int configuredSize = plugin.getConfig().getInt("gui.size", -1);
    int size;
    if (configuredSize > 0) {
        // ensure size is a multiple of 9 and at least 9
        int mult = Math.max(1, configuredSize / 9);
        size = Math.max(9, mult * 9);
        if (configuredSize % 9 != 0) size += 9; // round up
    } else {
        size = ((ranks.size() / 9) + 1) * 9;
    }

    Inventory inv = Bukkit.createInventory(new RankInventoryHolder(), size, "§6Ranks");
    String currentRank = rankManager.getPlayerRank(player);

    // Read configured layout (rankKey -> slot index) if present
    java.util.Map<String, Integer> configuredSlots = new java.util.HashMap<>();
    if (plugin.getConfig().isConfigurationSection("gui.layout")) {
        ConfigurationSection layout = plugin.getConfig().getConfigurationSection("gui.layout");
        for (String key : layout.getKeys(false)) {
            try {
                int s = layout.getInt(key, -1);
                if (s >= 0 && s < size) configuredSlots.put(key, s);
            } catch (Exception ignored) {}
        }
    }

    // Track used slots
    boolean[] used = new boolean[size];

    // First place ranks that have explicit slots
    for (String rankKey : ranks) {
        Integer slot = configuredSlots.get(rankKey);
        if (slot != null) {
            used[slot] = true;
        }
    }

    // Now iterate ranks and place them in either configured slot or next available slot
    for (int i = 0; i < ranks.size(); i++) {
        String rankKey = ranks.get(i);

        // Prefer using a stored ItemStack from config if available (preserves skull-owner/profile data)
        int rankNumber = i + 1;
        ItemStack item = null;
        try {
            ItemStack stored = plugin.getConfig().getItemStack("heads." + rankNumber);
            if (stored != null) {
                item = stored.clone();
                // plugin.getLogger().info("Using stored ItemStack for rank " + rankNumber);
            }
    } catch (IllegalArgumentException | ClassCastException ignored) {}
        if (item == null) {
            // Create default player head
            item = new ItemStack(Material.PLAYER_HEAD);
        }
    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();        // Get texture data from config (supports plain string or serialized ItemStack/map with signature)
        Object rawHead = plugin.getConfig().get("heads." + rankNumber);
        TextureData textureData = null;
        if (rawHead == null) {
            // plugin.getLogger().info("No head configured for rank " + rankNumber + " (" + rankKey + ")");
        } else {
            if (rawHead instanceof String) {
                textureData = new TextureData((String) rawHead, null);
            } else {
                // Log the raw object so we can see what's stored in config
                // plugin.getLogger().info("Head config for rank " + rankNumber + " is type: " + rawHead.getClass().getName() + " -> " + rawHead.toString());
                // Try to find a texture value and optional signature inside the structure
                textureData = findTextureDataInObject(rawHead);
            }
        }

    if (textureData != null && textureData.value != null && !textureData.value.isEmpty() && meta instanceof SkullMeta skullMeta) {
            // Log some debug info so server console shows why skull injection may fail
            try {
                // list declared fields for debugging
                Field[] declared = skullMeta.getClass().getDeclaredFields();
                StringBuilder sb = new StringBuilder("Declared fields: ");
                for (Field f : declared) sb.append(f.getName()).append(',');
                // plugin.getLogger().info(sb.toString());
                String originalValue = textureData.value;
                String signature = textureData.signature;
                // plugin.getLogger().info("Base64 texture length: " + (originalValue == null ? 0 : originalValue.length()));

                // Create GameProfile with texture. Prefer preserving signature if present (signed Mojang textures).
                // GameProfile requires a non-null name in newer authlib versions. Use empty string.
                com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "");

                                String encodedTexture = null;
                                
                                // First check if the value is a texture hash
                                if (originalValue != null && originalValue.matches("^[0-9a-f]{64}$")) {
                                    // plugin.getLogger().info("Converting texture hash to full texture data");
                                    String textureJson = String.format("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}", originalValue);
                                    encodedTexture = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
                                } else if (originalValue != null) {
                                    // Try to decode existing base64 and validate/fix if needed
                                    try {
                                        byte[] decoded = Base64.getDecoder().decode(originalValue);
                                        String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                                        // decoded payload (shortened for readability) was previously logged for debugging
                        int urlIndex = decodedStr.indexOf("\"url\"");
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
                            // plugin.getLogger().info("Extracted skin URL: " + foundUrl);
                            String textureJson = String.format("{\"textures\":{\"SKIN\":{\"url\":\"%s\"}}}", foundUrl);
                            encodedTexture = Base64.getEncoder().encodeToString(textureJson.getBytes(StandardCharsets.UTF_8));
                            // Re-encoded texture JSON stored in `encodedTexture` for later injection
                        } else {
                            // plugin.getLogger().info("No URL found inside decoded texture payload; using original base64");
                        }
                    } catch (IllegalArgumentException iae) {
                        plugin.getLogger().warning("Provided texture string was not valid Base64; using original value");
                    }
                } else {
                    // plugin.getLogger().info("Found texture signature present, preserving signed texture value");
                }

                if (signature != null && !signature.isEmpty()) {
                    profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", originalValue, signature));
                } else {
                    profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", encodedTexture));
                }

                // Inject GameProfile into SkullMeta. The implementation class may keep the profile field
                // on a superclass, so walk the class hierarchy to find it.
                Field profileField = null;
                Class<?> clazz = skullMeta.getClass();
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
                    try {
                        profileField.set(skullMeta, profile);
                        // plugin.getLogger().info("Applied custom head for rank " + rankKey + " (in class " + profileField.getDeclaringClass().getName() + ")");
                    } catch (IllegalArgumentException | IllegalAccessException iae) {
                        // Could not set GameProfile into the implementation's profile field (different internal type).
                        // Try to wrap the GameProfile into the server-specific CraftPlayerProfile wrapper if available
                        try {
                            // Common CraftPlayerProfile class name used in dumps
                            String[] craftNames = new String[]{
                                "org.bukkit.craftbukkit.inventory.CraftPlayerProfile",
                                "org.bukkit.craftbukkit.profile.CraftPlayerProfile",
                                "org.bukkit.craftbukkit.profile.CraftPlayerprofile",
                                "org.bukkit.craftbukkit.v1_\u005F_1.CraftPlayerProfile"};
                            boolean wrapped = false;
                            for (String cname : craftNames) {
                                try {
                                    Class<?> craftProfileClass = Class.forName(cname);
                                    // Try constructor GameProfile
                                    try {
                                        java.lang.reflect.Constructor<?> ctor = craftProfileClass.getConstructor(com.mojang.authlib.GameProfile.class);
                                        Object craftProfile = ctor.newInstance(profile);
                                        profileField.set(skullMeta, craftProfile);
                                        // plugin.getLogger().info("Applied custom head for rank " + rankKey + " via CraftPlayerProfile wrapper");
                                        wrapped = true;
                                        break;
                                    } catch (NoSuchMethodException ns) {
                                        // Try static factory method that accepts GameProfile
                                        try {
                                            java.lang.reflect.Method m = craftProfileClass.getMethod("asCraftPlayerProfile", com.mojang.authlib.GameProfile.class);
                                            Object craftProfile = m.invoke(null, profile);
                                            profileField.set(skullMeta, craftProfile);
                                            // plugin.getLogger().info("Applied custom head for rank " + rankKey + " via CraftPlayerProfile.asCraftPlayerProfile");
                                            wrapped = true;
                                            break;
                                        } catch (NoSuchMethodException ns2) {
                                            // try alternate factory name
                                            try {
                                                java.lang.reflect.Method m2 = craftProfileClass.getMethod("asCraftProfile", com.mojang.authlib.GameProfile.class);
                                                Object craftProfile = m2.invoke(null, profile);
                                                profileField.set(skullMeta, craftProfile);
                                                // plugin.getLogger().info("Applied custom head for rank " + rankKey + " via CraftPlayerProfile.asCraftProfile");
                                                wrapped = true;
                                                break;
                                            } catch (NoSuchMethodException ns3) {
                                                // give up on this class
                                            }
                                        }
                                    }
                                } catch (ClassNotFoundException cnf) {
                                    // try next possible class name
                                }
                            }
                            if (wrapped) continue;
                        } catch (ReflectiveOperationException wrapEx) {
                            // ignore and fall back to Paper approach
                        }
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
                            setPlayerProfile.invoke(skullMeta, paperProfile);
                            // plugin.getLogger().info("Applied custom head for rank " + rankKey + " via Paper PlayerProfile fallback");
                        } catch (ClassNotFoundException cnf) {
                            // Paper API not present; will fall through to warning below
                        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                            // plugin.getLogger().warning("Paper PlayerProfile fallback failed for rank " + rankKey + ": " + ex.getMessage());
                        }

                        // Final fallback: create a new ItemStack (display-only) and set the PlayerProfile on its fresh SkullMeta.
                        try {
                            Class<?> bukkitClass2 = Class.forName("org.bukkit.Bukkit");
                            Class<?> playerProfileClass2 = Class.forName("org.bukkit.profile.PlayerProfile");
                            Class<?> profilePropertyClass2 = Class.forName("org.bukkit.profile.ProfileProperty");
                            java.lang.reflect.Method createProfile2 = bukkitClass2.getMethod("createProfile", java.util.UUID.class);
                            Object paperProfile2 = createProfile2.invoke(null, java.util.UUID.randomUUID());

                            Object prop2 = null;
                            try {
                                if (signature != null && !signature.isEmpty()) {
                                    try {
                                        java.lang.reflect.Constructor<?> propCtor3 = profilePropertyClass2.getConstructor(String.class, String.class, String.class);
                                        prop2 = propCtor3.newInstance("textures", originalValue, signature);
                                    } catch (NoSuchMethodException ns) {
                                        java.lang.reflect.Constructor<?> propCtor2 = profilePropertyClass2.getConstructor(String.class, String.class);
                                        prop2 = propCtor2.newInstance("textures", originalValue);
                                    }
                                } else {
                                    java.lang.reflect.Constructor<?> propCtor2 = profilePropertyClass2.getConstructor(String.class, String.class);
                                    prop2 = propCtor2.newInstance("textures", encodedTexture);
                                }
                            } catch (NoSuchMethodException ignored) {}

                            try {
                                java.lang.reflect.Method addProp2 = playerProfileClass2.getMethod("addProperty", profilePropertyClass2);
                                if (prop2 != null) addProp2.invoke(paperProfile2, prop2);
                            } catch (NoSuchMethodException ex) {
                                try {
                                    java.lang.reflect.Method setProp2 = playerProfileClass2.getMethod("setProperty", profilePropertyClass2);
                                    if (prop2 != null) setProp2.invoke(paperProfile2, prop2);
                                } catch (NoSuchMethodException ex2) {
                                    try {
                                        java.lang.reflect.Method getProps2 = playerProfileClass2.getMethod("getProperties");
                                        Object props = getProps2.invoke(paperProfile2);
                                        if (props instanceof java.util.Collection && prop2 != null) {
                                            ((java.util.Collection) props).add(prop2);
                                        }
                                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                                }
                            }

                            // Create a fresh head and set the PlayerProfile on its meta
                            ItemStack displayHead = new ItemStack(Material.PLAYER_HEAD);
                            SkullMeta newMeta = (SkullMeta) displayHead.getItemMeta();
                            try {
                                java.lang.reflect.Method setPlayerProfile2 = newMeta.getClass().getMethod("setPlayerProfile", playerProfileClass2);
                                setPlayerProfile2.invoke(newMeta, paperProfile2);
                                displayHead.setItemMeta(newMeta);
                                // Use this head for display instead of trying to mutate the original meta
                                item = displayHead;
                                meta = newMeta;
                                // plugin.getLogger().info("Applied custom head for rank " + rankKey + " by creating a display-only head");
                                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                                // ignore - will fall through to warning
                            }
                            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}

                        plugin.getLogger().warning("Could not set GameProfile into SkullMeta implementation; attempted Paper fallback.");
                    }
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
                        setPlayerProfile.invoke(skullMeta, paperProfile);
                        
                    } catch (ClassNotFoundException cnf) {
                        // Paper API not present; will fall through to warning below
                    } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                        // Use parameterized log to avoid string concatenation; log the throwable separately to include stacktrace
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Paper PlayerProfile fallback failed for rank {0}", new Object[]{rankKey});
                        plugin.getLogger().log(java.util.logging.Level.WARNING, ex.getMessage(), ex);
                    }

                    // If the field wasn't found, log a helpful warning so the server admin can see why
                    // Use lazy supplier to avoid string concatenation when warning is disabled
                    plugin.getLogger().log(java.util.logging.Level.WARNING, () -> "Could not find 'profile' field on SkullMeta implementation; head for rank " + rankKey + " will be default Steve.");
                }
            } catch (IllegalArgumentException | SecurityException e) {
                final String error = e.getMessage();
                final String rank = rankKey;
                plugin.getLogger().warning(() -> String.format("Failed to set Base64 head for rank %s: %s", rank, error));
            }
        } else {
            if (meta == null) {
                final String rank = rankKey;
                plugin.getLogger().warning(() -> String.format("SkullMeta is null for rank item %s, skipping texture injection", rank));
            } else {
                // No texture data to inject for this item; nothing to do here.
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

        // Determine target slot
        Integer cfgSlot = configuredSlots.get(rankKey);
        int targetSlot;
        if (cfgSlot != null && cfgSlot >= 0 && cfgSlot < size) {
            targetSlot = cfgSlot;
        } else {
            // find next free slot
            targetSlot = -1;
            for (int s = 0; s < size; s++) {
                if (!used[s]) { targetSlot = s; break; }
            }
            if (targetSlot == -1) targetSlot = Math.min(i, size - 1);
        }

        // mark used and place
        if (targetSlot >= 0 && targetSlot < size) used[targetSlot] = true;
        inv.setItem(targetSlot, item);
    }

    player.openInventory(inv);
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

    // Searching in object type: used for debugging
    StringBuilder sb = new StringBuilder();
    sb.append("Searching in object type: ").append(obj.getClass().getName());

        // Handle ItemStack (we may have stored the full ItemStack in config)
        if (obj instanceof ItemStack item) {
            try {
                var meta = item.getItemMeta();
                if (meta instanceof SkullMeta skullMeta) {
                    // Try Paper's PlayerProfile API first (reflection to stay compatible)
                    try {
                        java.lang.reflect.Method getPlayerProfile = skullMeta.getClass().getMethod("getPlayerProfile");
                        try {
                            Object paperProfile = getPlayerProfile.invoke(skullMeta);
                            if (paperProfile != null) {
                                try {
                                    java.lang.reflect.Method getProperties = paperProfile.getClass().getMethod("getProperties");
                                    try {
                                        Object props = getProperties.invoke(paperProfile);
                                        if (props instanceof Iterable<?> iterable) {
                                            for (Object prop : iterable) {
                                                try {
                                                    java.lang.reflect.Method getName = prop.getClass().getMethod("getName");
                                                    java.lang.reflect.Method getValue = prop.getClass().getMethod("getValue");
                                                    java.lang.reflect.Method getSignature = prop.getClass().getMethod("getSignature");
                                                    try {
                                                        Object name = getName.invoke(prop);
                                                        if ("textures".equals(name)) {
                                                            Object value = getValue.invoke(prop);
                                                            Object signature = getSignature.invoke(prop);
                                                            return new TextureData(value == null ? null : value.toString(), signature == null ? null : signature.toString());
                                                        }
                                                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                                                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                                                    // try other shapes below
                                                }
                                            }
                                        }
                                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                            }
                        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                    } catch (NoSuchMethodException ignored) {
                        // getPlayerProfile not available, fallthrough to reflection on underlying profile field
                    }

                    // Try to reflectively read a 'profile' field (GameProfile/CraftPlayerProfile)
                    Class<?> clazz = skullMeta.getClass();
                    while (clazz != null) {
                        try {
                            java.lang.reflect.Field pf = clazz.getDeclaredField("profile");
                            pf.setAccessible(true);
                            Object gp = pf.get(skullMeta);
                            if (gp != null) {
                                // If this is a com.mojang.authlib.GameProfile, inspect its properties map
                                try {
                                    java.lang.reflect.Method getProperties = gp.getClass().getMethod("getProperties");
                                    try {
                                        Object propMap = getProperties.invoke(gp);
                                        if (propMap != null) {
                                            // com.mojang.authlib.PropertyMap implements Iterable<Property>
                                            if (propMap instanceof java.util.Map<?, ?> map) {
                                                Object textures = map.get("textures");
                                                if (textures instanceof com.mojang.authlib.properties.Property prop) {
                                                    return new TextureData(prop.getValue(), prop.getSignature());
                                                }
                                            } else if (propMap instanceof Iterable<?> iterable) {
                                                for (Object p : iterable) {
                                                    try {
                                                        java.lang.reflect.Method getName = p.getClass().getMethod("getName");
                                                        java.lang.reflect.Method getValue = p.getClass().getMethod("getValue");
                                                        java.lang.reflect.Method getSignature = p.getClass().getMethod("getSignature");
                                                        try {
                                                            Object name = getName.invoke(p);
                                                            if ("textures".equals(name)) {
                                                                Object value = getValue.invoke(p);
                                                                Object signature = getSignature.invoke(p);
                                                                return new TextureData(value == null ? null : value.toString(), signature == null ? null : signature.toString());
                                                            }
                                                        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                                                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                } catch (NoSuchMethodException ignored) {
                                    // If gp isn't GameProfile-like, keep searching
                                }
                            }
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {
                            // continue up the class hierarchy
                        }
                        clazz = clazz.getSuperclass();
                    }

                    // If reflective introspection didn't yield a property map, try parsing the profile object's toString
                    // This helps with CraftPlayerProfile / net.minecraft wrappers that expose properties only in toString
                    try {
                        // search for any 'profile' field value in the class hierarchy again to get the last one
                        Class<?> c2 = skullMeta.getClass();
                        Object lastProfileObj = null;
                        while (c2 != null) {
                            try {
                                java.lang.reflect.Field pf2 = c2.getDeclaredField("profile");
                                pf2.setAccessible(true);
                                Object gp2 = pf2.get(skullMeta);
                                if (gp2 != null) lastProfileObj = gp2;
                            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
                            c2 = c2.getSuperclass();
                        }
                        if (lastProfileObj != null) {
                            String s = lastProfileObj.toString();
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile("value=([A-Za-z0-9+/=]+)");
                            java.util.regex.Matcher m = p.matcher(s);
                            String foundValue = null;
                            String foundSig = null;
                            if (m.find()) foundValue = m.group(1);
                            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("signature=([A-Za-z0-9+/=]+|null)");
                            java.util.regex.Matcher m2 = p2.matcher(s);
                            if (m2.find()) {
                                foundSig = m2.group(1);
                                if ("null".equals(foundSig)) foundSig = null;
                            }
                            if (foundValue != null) {
                                return new TextureData(foundValue, foundSig);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {}

                }
            } catch (SecurityException ignored) {
                // fall through to other handlers
            }
        }

        // Handle MemorySection (from YAML config)
        if (obj instanceof org.bukkit.configuration.ConfigurationSection section) {
            // debug: config keys at section (info logging suppressed)

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
                                            // found texture data in minecraft:profile.properties
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
                    // found texture data as direct config values
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
                                // found texture data in properties array
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
                    // found texture data in direct property
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


package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.managers.MessageManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for loading GUI configurations from YAML files
 */
public class GUIConfig {
    private final JavaPlugin plugin;
    private final String guiName;
    private FileConfiguration config;

    public GUIConfig(JavaPlugin plugin, String guiName) {
        this.plugin = plugin;
        this.guiName = guiName;
        loadConfig();
    }

    private void loadConfig() {
        File guiFile = new File(plugin.getDataFolder(), "guis/" + guiName + ".yml");
        if (!guiFile.exists()) {
            plugin.saveResource("guis/" + guiName + ".yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(guiFile);

        // JAR'daki varsayılan değerleri yükle - eksik key'ler için fallback
        InputStream defaultStream = plugin.getResource("guis/" + guiName + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            this.config.setDefaults(defaults);
        }
    }

    public String getTitle(String... replacements) {
        String title = config.getString("title", "&8GUI");
        title = resolveText(title);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                title = title.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    public int getSize() {
        return config.getInt("size", 54);
    }

    public boolean isBackgroundEnabled() {
        return config.getBoolean("background.enabled", true);
    }

    public ItemStack getBackgroundItem() {
        ConfigurationSection bgSection = config.getConfigurationSection("background");
        if (bgSection == null) {
            return createDefaultBackground();
        }

        String materialName = bgSection.getString("material", "BLACK_STAINED_GLASS_PANE");
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = bgSection.getString("name", " ");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack getItem(String path, String... replacements) {
        ConfigurationSection itemSection = config.getConfigurationSection("items." + path);
        if (itemSection == null) {
            return null;
        }

        return createItemFromSection(itemSection, replacements);
    }

    public int getItemSlot(String path) {
        // First try with "items." prefix
        int slot = config.getInt("items." + path + ".slot", -1);

        // If not found, try direct path (for configs without "items" section)
        if (slot == -1) {
            slot = config.getInt(path + ".slot", 0);
        }

        return slot;
    }

    private ItemStack createItemFromSection(ConfigurationSection section, String... replacements) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material: " + materialName + " - using STONE");
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Display name
        String name = section.getString("name", "");
        name = resolveText(name);
        name = applyReplacements(name, replacements);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        // Lore - supports msg: per line and msg-lore: for entire list
        if (section.contains("lore")) {
            List<String> lore;
            if (section.isString("lore")) {
                String loreValue = section.getString("lore");
                if (loreValue != null && loreValue.startsWith("msg-lore:")) {
                    lore = resolveTextList(loreValue.substring(9));
                } else {
                    lore = new ArrayList<>();
                    if (loreValue != null) {
                        lore.add(resolveText(loreValue));
                    }
                }
            } else {
                lore = section.getStringList("lore");
            }
            List<String> coloredLore = lore.stream()
                    .map(this::resolveText)
                    .map(line -> applyReplacements(line, replacements))
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList());
            meta.setLore(coloredLore);
        }

        // Enchantments
        if (section.getBoolean("enchanted", false) || section.getBoolean("glow", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }

        if (section.contains("enchantments")) {
            List<String> enchants = section.getStringList("enchantments");
            for (String enchantStr : enchants) {
                String[] parts = enchantStr.split(":");
                if (parts.length == 2) {
                    try {
                        Enchantment enchant = Enchantment.getByName(parts[0]);
                        int level = Integer.parseInt(parts[1]);
                        if (enchant != null) {
                            meta.addEnchant(enchant, level, true);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid enchantment: " + enchantStr);
                    }
                }
            }
        }

        // Item flags
        if (section.contains("flags")) {
            List<String> flags = section.getStringList("flags");
            for (String flagStr : flags) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagStr);
                    meta.addItemFlags(flag);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid item flag: " + flagStr);
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Resolves text that starts with "msg:" prefix through MessageManager.
     * Falls back to the raw text if MessageManager is not available.
     */
    private String resolveText(String text) {
        if (text != null && text.startsWith("msg:")) {
            if (plugin instanceof ReportSystemSpigot) {
                MessageManager mm = ((ReportSystemSpigot) plugin).getMessageManager();
                if (mm != null) {
                    return mm.getMessage(text.substring(4));
                }
            }
        }
        return text;
    }

    /**
     * Resolves a message key to a list of strings from MessageManager.
     */
    private List<String> resolveTextList(String key) {
        if (plugin instanceof ReportSystemSpigot) {
            MessageManager mm = ((ReportSystemSpigot) plugin).getMessageManager();
            if (mm != null) {
                List<String> list = mm.getMessageList(key);
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            }
        }
        return new ArrayList<>();
    }

    private String applyReplacements(String text, String... replacements) {
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                text = text.replace(replacements[i], replacements[i + 1]);
            }
        }
        return text;
    }

    private ItemStack createDefaultBackground() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Gets a config string value and resolves msg: prefix if present.
     */
    public String getConfigString(String path, String defaultValue) {
        String value = config.getString(path, defaultValue);
        return resolveText(value);
    }

    /**
     * Gets a config string list, resolving msg-lore: and msg: prefixes.
     * Supports both list format and single "msg-lore:key" string format.
     */
    public List<String> getConfigStringList(String path) {
        if (config.isString(path)) {
            String value = config.getString(path);
            if (value != null && value.startsWith("msg-lore:")) {
                return resolveTextList(value.substring(9));
            }
            List<String> single = new ArrayList<>();
            if (value != null) {
                single.add(resolveText(value));
            }
            return single;
        }
        return config.getStringList(path).stream()
                .map(this::resolveText)
                .collect(Collectors.toList());
    }

    public String getSound(String action) {
        return config.getString("sounds." + action, "UI_BUTTON_CLICK");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reload() {
        loadConfig();
    }
}

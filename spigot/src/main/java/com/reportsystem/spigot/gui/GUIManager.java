package com.reportsystem.spigot.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class GUIManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();

    public GUIManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadGUIConfigs();
    }

    /**
     * GUI config dosyalarını yükler
     */
    private void loadGUIConfigs() {
        File guiFolder = new File(plugin.getDataFolder(), "guis");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        // Kullanılan GUI dosyalarını yükle
        String[] guiFiles = {
                "report-create.yml",
                "report-list.yml",
                "punishment-selection.yml"
        };

        for (String fileName : guiFiles) {
            File file = new File(guiFolder, fileName);
            if (!file.exists()) {
                plugin.saveResource("guis/" + fileName, false);
            }

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            String key = fileName.replace(".yml", "");
            guiConfigs.put(key, config);
        }
    }

    /**
     * GUI config'ini döndürür
     */
    public FileConfiguration getGUIConfig(String guiName) {
        return guiConfigs.get(guiName);
    }

    /**
     * GUI config'lerini yeniden yükler
     */
    public void reloadConfigs() {
        guiConfigs.clear();
        loadGUIConfigs();
    }

    /**
     * ItemStack oluşturur
     */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (name != null) {
            meta.setDisplayName(name);
        }

        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * ItemStack oluşturur (enchanted)
     */
    public static ItemStack createItem(Material material, String name, List<String> lore, boolean enchanted) {
        ItemStack item = createItem(material, name, lore);

        if (enchanted) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Inventory oluşturur
     */
    public static Inventory createInventory(InventoryHolder holder, int size, String title) {
        // Size kontrolü
        if (size % 9 != 0 || size < 9 || size > 54) {
            size = 54; // Varsayılan
        }

        return Bukkit.createInventory(holder, size, title);
    }

    /**
     * Inventory'yi background item ile doldurur
     */
    public static void fillInventory(Inventory inventory, ItemStack backgroundItem) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, backgroundItem);
            }
        }
    }

    /**
     * Inventory'nin belirli slotlarını doldurur
     */
    public static void fillSlots(Inventory inventory, ItemStack item, int... slots) {
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    /**
     * Border oluşturur
     */
    public static void createBorder(Inventory inventory, ItemStack borderItem) {
        int size = inventory.getSize();

        // Üst satır
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem);
        }

        // Alt satır
        for (int i = size - 9; i < size; i++) {
            inventory.setItem(i, borderItem);
        }

        // Sol ve sağ kenarlar
        for (int i = 9; i < size - 9; i += 9) {
            inventory.setItem(i, borderItem); // Sol
            inventory.setItem(i + 8, borderItem); // Sağ
        }
    }

    /**
     * Sayfa numaralarını hesaplar
     */
    public static int calculateTotalPages(int totalItems, int itemsPerPage) {
        return Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
    }

    /**
     * Sayfa için item listesini döndürür
     */
    public static <T> List<T> getPageItems(List<T> allItems, int page, int itemsPerPage) {
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allItems.size());

        if (start >= allItems.size()) {
            return new ArrayList<>();
        }

        return allItems.subList(start, end);
    }

    /**
     * GUI click event listener
     */
    public static class GUIListener implements Listener {

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            // GUI holder kontrolü
            InventoryHolder holder = event.getInventory().getHolder();

            if (holder instanceof GUI) {
                event.setCancelled(true);

                if (event.getClickedInventory() == null ||
                        event.getClickedInventory() != event.getInventory()) {
                    return;
                }

                // Tıklanan item kontrolü
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                    return;
                }

                // GUI'ye tıklama olayını ilet
                ((GUI) holder).handleClick(event);
            }
            // PunishmentSelectionGUI check (doesn't extend GUI)
            else if (holder instanceof PunishmentSelectionGUI) {
                event.setCancelled(true);

                if (event.getClickedInventory() == null ||
                        event.getClickedInventory() != event.getInventory()) {
                    return;
                }

                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                    return;
                }

                ((PunishmentSelectionGUI) holder).handleClick(event);
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            InventoryHolder holder = event.getInventory().getHolder();

            if (holder instanceof GUI) {
                ((GUI) holder).handleClose(event);
            }
        }
    }

    /**
     * Placeholder'ları değiştirir
     */
    public static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null) {
            return text;
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }

        return text;
    }

    /**
     * Lore listesindeki placeholder'ları değiştirir
     */
    public static List<String> replacePlaceholders(List<String> lore, Map<String, String> placeholders) {
        if (lore == null || placeholders == null) {
            return lore;
        }

        List<String> newLore = new ArrayList<>();
        for (String line : lore) {
            newLore.add(replacePlaceholders(line, placeholders));
        }

        return newLore;
    }

    /**
     * Renk kodlarını çevirir
     */
    public static String colorize(String text) {
        if (text == null) return null;
        return text.replace('&', '§');
    }

    /**
     * Lore listesindeki renk kodlarını çevirir
     */
    public static List<String> colorize(List<String> lore) {
        if (lore == null) return null;

        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(colorize(line));
        }

        return coloredLore;
    }
}
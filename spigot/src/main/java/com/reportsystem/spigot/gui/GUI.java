package com.reportsystem.spigot.gui;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class GUI implements InventoryHolder {
    protected final ReportSystemSpigot plugin;
    protected final Player player;
    protected Inventory inventory;

    public GUI(ReportSystemSpigot plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public abstract void open();
    public abstract void build();

    public void handleClick(InventoryClickEvent event) {
        // Alt sınıflar bu metodu override edebilir
    }

    public void handleClose(InventoryCloseEvent event) {
        // Alt sınıflar bu metodu override edebilir
    }

    public void fillBorder(Material material) {
        if (inventory == null) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, item);
            inventory.setItem(inventory.getSize() - 9 + i, item);
        }
        for (int i = 9; i < inventory.getSize() - 9; i += 9) {
            inventory.setItem(i, item);
            inventory.setItem(i + 8, item);
        }
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
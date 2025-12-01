package com.reportsystem.spigot.utils;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * Toast bildirim sistemi (Advancement API kullanarak)
 */
public class AdvancementNotification {

    private final JavaPlugin plugin;

    public AdvancementNotification(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Frame türleri
     */
    public enum FrameType {
        TASK,
        GOAL,
        CHALLENGE
    }

    /**
     * Tek bir oyuncuya toast gönder
     */
    public void sendToast(Player player, Material icon, String title, String description, FrameType frameType) {
        try {
            String key = "toast_" + UUID.randomUUID().toString().replace("-", "");
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

            // Create temporary advancement
            createAdvancement(namespacedKey, icon, title, description, frameType);

            // Award and revoke advancement
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Advancement advancement = Bukkit.getAdvancement(namespacedKey);
                if (advancement != null && player.isOnline()) {
                    // Award
                    player.getAdvancementProgress(advancement).awardCriteria("impossible");

                    // Revoke after a delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.getAdvancementProgress(advancement).revokeCriteria("impossible");
                        }
                        removeAdvancement(namespacedKey);
                    }, 20L);
                }
            }, 2L);

        } catch (Exception e) {
            plugin.getLogger().warning("Toast bildirimi gönderilemedi: " + e.getMessage());
        }
    }

    /**
     * Yetkili oyunculara toast gönder
     */
    public void sendToStaff(Material icon, String title, String description, FrameType frameType, String permission) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            if (player.hasPermission(permission)) {
                sendToast(player, icon, title, description, frameType);
            }
        }
    }

    private void createAdvancement(NamespacedKey key, Material icon, String title, String description, FrameType frameType) {
        try {
            // Create advancement JSON
            JsonObject advancement = new JsonObject();

            // Display
            JsonObject display = new JsonObject();
            display.addProperty("title", title);
            display.addProperty("description", description);

            JsonObject iconObj = new JsonObject();
            iconObj.addProperty("item", "minecraft:" + icon.name().toLowerCase());
            display.add("icon", iconObj);

            display.addProperty("frame", frameType.name().toLowerCase());
            display.addProperty("show_toast", true);
            display.addProperty("announce_to_chat", false);
            display.addProperty("hidden", true);

            advancement.add("display", display);

            // Criteria (impossible to achieve naturally)
            JsonObject criteria = new JsonObject();
            JsonObject impossible = new JsonObject();
            impossible.addProperty("trigger", "minecraft:impossible");
            criteria.add("impossible", impossible);
            advancement.add("criteria", criteria);

            // Save to file
            File advancementFile = new File(plugin.getDataFolder(), "temp_advancements/" + key.getKey() + ".json");
            advancementFile.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(advancementFile)) {
                writer.write(advancement.toString());
            }

            // Load advancement
            Bukkit.getUnsafe().loadAdvancement(key, advancement.toString());

        } catch (Exception e) {
            plugin.getLogger().warning("Advancement oluşturulamadı: " + e.getMessage());
        }
    }

    private void removeAdvancement(NamespacedKey key) {
        try {
            Bukkit.getUnsafe().removeAdvancement(key);

            // Delete temp file
            File advancementFile = new File(plugin.getDataFolder(), "temp_advancements/" + key.getKey() + ".json");
            if (advancementFile.exists()) {
                advancementFile.delete();
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}

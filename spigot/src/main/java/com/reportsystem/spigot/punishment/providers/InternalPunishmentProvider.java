package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.punishment.PunishmentProvider;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InternalPunishmentProvider implements PunishmentProvider, Listener {

    private final JavaPlugin plugin;
    private final Map<String, Long> mutedPlayers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> playerWarnings = new ConcurrentHashMap<>();

    public InternalPunishmentProvider(JavaPlugin plugin) {
        this.plugin = plugin;

        // Chat listener'ı kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Mute listesini temizleme task'ı (her dakika)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredMutes, 0L, 1200L);
    }

    @Override
    public boolean ban(String playerName, String reason, String punisher, long durationMillis) {
        if (durationMillis > 0) {
            return tempBan(playerName, reason, punisher, durationMillis);
        } else {
            return permBan(playerName, reason, punisher);
        }
    }

    @Override
    public boolean tempBan(String playerName, String reason, String punisher, long durationMillis) {
        try {
            Date expires = new Date(System.currentTimeMillis() + durationMillis);

            // Ban mesajı
            String banMessage = ChatColor.RED + "Sunucudan yasaklandınız!\n\n" +
                    ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.GRAY + "Süre: " + ChatColor.WHITE + formatDuration(durationMillis) + "\n" +
                    ChatColor.GRAY + "Bitiş: " + ChatColor.WHITE + formatDate(expires) + "\n" +
                    ChatColor.GRAY + "Cezayı veren: " + ChatColor.WHITE + punisher;

            // Ban listesine ekle
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expires, punisher);

            // Oyuncu online ise at
            Player target = Bukkit.getPlayer(playerName);
            if (target != null && target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    target.kickPlayer(banMessage);
                });
            }

            // Log
            plugin.getLogger().info("[BAN] " + playerName + " banned by " + punisher +
                    " for " + formatDuration(durationMillis) + ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error banning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean permBan(String playerName, String reason, String punisher) {
        try {
            // Ban mesajı
            String banMessage = ChatColor.RED + "Sunucudan kalıcı olarak yasaklandınız!\n\n" +
                    ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + reason + "\n" +
                    ChatColor.GRAY + "Cezayı veren: " + ChatColor.WHITE + punisher + "\n\n" +
                    ChatColor.DARK_GRAY + "Ban kaldırma başvurusu için: discord.gg/sunucu";

            // Ban listesine ekle (null = kalıcı)
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, null, punisher);

            // Oyuncu online ise at
            Player target = Bukkit.getPlayer(playerName);
            if (target != null && target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    target.kickPlayer(banMessage);
                });
            }

            // Log
            plugin.getLogger().info("[PERMBAN] " + playerName + " permanently banned by " +
                    punisher + ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error permanently banning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean mute(String playerName, String reason, String punisher, long durationMillis) {
        try {
            String playerNameLower = playerName.toLowerCase();

            if (durationMillis > 0) {
                mutedPlayers.put(playerNameLower, System.currentTimeMillis() + durationMillis);
            } else {
                mutedPlayers.put(playerNameLower, Long.MAX_VALUE); // Kalıcı mute
            }

            Player target = Bukkit.getPlayer(playerName);
            if (target != null && target.isOnline()) {
                target.sendMessage("");
                target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage(ChatColor.RED + "          SUSTURULDUNUZ!");
                target.sendMessage("");
                target.sendMessage(ChatColor.GRAY + "  Sebep: " + ChatColor.WHITE + reason);
                target.sendMessage(ChatColor.GRAY + "  Süre: " + ChatColor.WHITE +
                        (durationMillis > 0 ? formatDuration(durationMillis) : "Kalıcı"));
                target.sendMessage(ChatColor.GRAY + "  Yetkili: " + ChatColor.WHITE + punisher);
                target.sendMessage(ChatColor.RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage("");

                // Ses efekti
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
            }

            // Log
            plugin.getLogger().info("[MUTE] " + playerName + " muted by " + punisher +
                    " for " + (durationMillis > 0 ? formatDuration(durationMillis) : "permanent") +
                    ". Reason: " + reason);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error muting player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean kick(String playerName, String reason, String punisher) {
        try {
            Player target = Bukkit.getPlayer(playerName);
            if (target != null && target.isOnline()) {
                String kickMessage = ChatColor.RED + "Sunucudan atıldınız!\n\n" +
                        ChatColor.GRAY + "Sebep: " + ChatColor.WHITE + reason + "\n" +
                        ChatColor.GRAY + "Atan yetkili: " + ChatColor.WHITE + punisher + "\n\n" +
                        ChatColor.GREEN + "Tekrar girebilirsiniz.";

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    target.kickPlayer(kickMessage);
                });

                // Log
                plugin.getLogger().info("[KICK] " + playerName + " kicked by " + punisher +
                        ". Reason: " + reason);

                return true;
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Error kicking player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean warn(String playerName, String reason, String punisher) {
        try {
            // Uyarıyı kaydet
            String playerNameLower = playerName.toLowerCase();
            List<String> warnings = playerWarnings.computeIfAbsent(playerNameLower, k -> new ArrayList<>());
            warnings.add(reason + " - " + punisher + " (" + new Date() + ")");

            Player target = Bukkit.getPlayer(playerName);
            if (target != null && target.isOnline()) {
                target.sendMessage("");
                target.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage(ChatColor.GOLD + "          ⚠ UYARI ⚠");
                target.sendMessage("");
                target.sendMessage(ChatColor.RED + "  Bir yetkili tarafından uyarıldınız!");
                target.sendMessage("");
                target.sendMessage(ChatColor.GRAY + "  Sebep: " + ChatColor.WHITE + reason);
                target.sendMessage(ChatColor.GRAY + "  Yetkili: " + ChatColor.WHITE + punisher);
                target.sendMessage(ChatColor.GRAY + "  Uyarı sayınız: " + ChatColor.YELLOW + warnings.size());
                target.sendMessage("");
                target.sendMessage(ChatColor.RED + "  Kuralları ihlal etmeye devam ederseniz");
                target.sendMessage(ChatColor.RED + "  daha ağır cezalar alabilirsiniz!");
                target.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                target.sendMessage("");

                // Ses efekti ve title
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                target.sendTitle(ChatColor.GOLD + "⚠ UYARI ⚠",
                        ChatColor.RED + "Kuralları ihlal ettiniz!", 10, 60, 20);
            }

            // Log
            plugin.getLogger().info("[WARN] " + playerName + " warned by " + punisher +
                    ". Reason: " + reason + " (Total warnings: " + warnings.size() + ")");

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error warning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unban(String playerName) {
        try {
            // Bukkit.getBanList().pardon() void döndürüyor, bu yüzden önce kontrol edelim
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) {
                Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
                plugin.getLogger().info("[UNBAN] " + playerName + " has been unbanned.");
                return true;
            } else {
                return false; // Zaten banlı değil
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error unbanning player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unmute(String playerName) {
        try {
            boolean result = mutedPlayers.remove(playerName.toLowerCase()) != null;
            if (result) {
                plugin.getLogger().info("[UNMUTE] " + playerName + " has been unmuted.");

                Player target = Bukkit.getPlayer(playerName);
                if (target != null && target.isOnline()) {
                    target.sendMessage(ChatColor.GREEN + "✓ Artık konuşabilirsiniz!");
                }
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error unmuting player: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isBanned(String playerName) {
        return Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName);
    }

    @Override
    public boolean isMuted(String playerName) {
        Long muteEnd = mutedPlayers.get(playerName.toLowerCase());
        if (muteEnd == null) return false;

        if (muteEnd == Long.MAX_VALUE) return true; // Kalıcı mute

        if (System.currentTimeMillis() < muteEnd) {
            return true;
        } else {
            mutedPlayers.remove(playerName.toLowerCase());
            return false;
        }
    }

    /**
     * Chat event handler - mute kontrolü için
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (isMuted(player.getName())) {
            event.setCancelled(true);

            Long muteEnd = mutedPlayers.get(player.getName().toLowerCase());
            String timeLeft = "";

            if (muteEnd != null && muteEnd != Long.MAX_VALUE) {
                long remaining = muteEnd - System.currentTimeMillis();
                if (remaining > 0) {
                    timeLeft = " (" + formatDuration(remaining) + " kaldı)";
                }
            } else if (muteEnd == Long.MAX_VALUE) {
                timeLeft = " (Kalıcı)";
            }

            player.sendMessage(ChatColor.RED + "✗ Susturulmuşsunuz!" + timeLeft);
            player.sendMessage(ChatColor.GRAY + "Sohbete yazamazsınız.");

            // Ses efekti
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        }
    }

    /**
     * Süresi dolmuş mute'ları temizler
     */
    private void cleanupExpiredMutes() {
        long now = System.currentTimeMillis();
        mutedPlayers.entrySet().removeIf(entry -> {
            Long muteEnd = entry.getValue();
            return muteEnd != Long.MAX_VALUE && muteEnd < now;
        });
    }

    /**
     * Süreyi formatlar
     */
    private String formatDuration(long millis) {
        if (millis <= 0) return "0 saniye";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " gün" + (hours % 24 > 0 ? " " + (hours % 24) + " saat" : "");
        }
        if (hours > 0) {
            return hours + " saat" + (minutes % 60 > 0 ? " " + (minutes % 60) + " dakika" : "");
        }
        if (minutes > 0) {
            return minutes + " dakika" + (seconds % 60 > 0 ? " " + (seconds % 60) + " saniye" : "");
        }
        return seconds + " saniye";
    }

    /**
     * Tarihi formatlar
     */
    private String formatDate(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(date);
    }

    /**
     * Oyuncunun uyarı sayısını döndürür
     */
    public int getWarningCount(String playerName) {
        List<String> warnings = playerWarnings.get(playerName.toLowerCase());
        return warnings != null ? warnings.size() : 0;
    }

    /**
     * Oyuncunun uyarılarını döndürür
     */
    public List<String> getWarnings(String playerName) {
        return playerWarnings.getOrDefault(playerName.toLowerCase(), new ArrayList<>());
    }
}
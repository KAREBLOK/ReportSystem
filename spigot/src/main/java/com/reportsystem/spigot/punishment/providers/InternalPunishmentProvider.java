package com.reportsystem.spigot.punishment.providers;

import com.reportsystem.common.punishment.PunishmentProvider;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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

    private com.reportsystem.spigot.managers.MessageManager getMessageManager() {
        return ((ReportSystemSpigot) plugin).getMessageManager();
    }

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
            String banMessage = getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.ban.screen-temp")
                            .replace("%reason%", reason)
                            .replace("%duration%", formatDuration(durationMillis))
                            .replace("%expires%", formatDate(expires))
                            .replace("%staff%", punisher));

            // Ban listesine ekle (NAME)
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, expires, punisher);

            // Profile ban listesine de ekle (Paper 1.21+ uyumluluğu)
            addProfileBan(playerName, reason, expires, punisher);

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
            String banMessage = getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.ban.screen-perm")
                            .replace("%reason%", reason)
                            .replace("%staff%", punisher)
                            .replace("%discord_url%", getMessageManager().getMessage("punishments.discord-url")));

            // Ban listesine ekle (null = kalıcı)
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, null, punisher);

            // Profile ban listesine de ekle (Paper 1.21+ uyumluluğu)
            addProfileBan(playerName, reason, null, punisher);

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
                String muteMsg = getMessageManager().getMessage("punishments.mute.player-message")
                        .replace("%reason%", reason)
                        .replace("%duration%", durationMillis > 0 ? formatDuration(durationMillis) : getMessageManager().getMessage("misc.duration.permanent"))
                        .replace("%staff%", punisher);
                for (String line : muteMsg.split("\n")) {
                    target.sendMessage(getMessageManager().colorize(line));
                }

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
                String kickMessage = getMessageManager().colorize(
                        getMessageManager().getMessage("punishments.kick.screen")
                                .replace("%reason%", reason)
                                .replace("%staff%", punisher));

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
                String warnMsg = getMessageManager().getMessage("punishments.warn.player-message")
                        .replace("%reason%", reason)
                        .replace("%staff%", punisher)
                        .replace("%count%", String.valueOf(warnings.size()));
                for (String line : warnMsg.split("\n")) {
                    target.sendMessage(getMessageManager().colorize(line));
                }

                // Ses efekti ve title
                target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                target.sendTitle(
                        getMessageManager().colorize(getMessageManager().getMessage("punishments.warn.title")),
                        getMessageManager().colorize(getMessageManager().getMessage("punishments.warn.subtitle")),
                        10, 60, 20);
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
            boolean unbanned = false;

            // NAME ban listesinden kaldır
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) {
                Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
                unbanned = true;
            }

            // PROFILE ban listesinden de kaldır (Paper 1.21+ uyumluluğu)
            if (removeProfileBan(playerName)) {
                unbanned = true;
            }

            if (unbanned) {
                plugin.getLogger().info("[UNBAN] " + playerName + " has been unbanned.");
            }
            return unbanned;
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
                    target.sendMessage(getMessageManager().colorize(getMessageManager().getMessage("punishments.mute.unmuted-player")));
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
        // Her iki ban listesini de kontrol et
        if (Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) {
            return true;
        }
        // Profile ban kontrolü
        return isProfileBanned(playerName);
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
                    timeLeft = getMessageManager().getMessage("punishments.mute.time-remaining").replace("%time%", formatDuration(remaining));
                }
            } else if (muteEnd == Long.MAX_VALUE) {
                timeLeft = getMessageManager().getMessage("punishments.mute.time-permanent");
            }

            player.sendMessage(getMessageManager().colorize(
                    getMessageManager().getMessage("punishments.mute.chat-blocked")
                            .replace("%time_left%", timeLeft)));
            player.sendMessage(getMessageManager().colorize(getMessageManager().getMessage("punishments.mute.chat-blocked-detail")));

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
        if (millis <= 0) return "0 " + getMessageManager().getMessage("misc.time.seconds");

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        String daysWord = getMessageManager().getMessage("misc.time.days");
        String hoursWord = getMessageManager().getMessage("misc.time.hours");
        String minutesWord = getMessageManager().getMessage("misc.time.minutes");
        String secondsWord = getMessageManager().getMessage("misc.time.seconds");

        if (days > 0) {
            return days + " " + daysWord + (hours % 24 > 0 ? " " + (hours % 24) + " " + hoursWord : "");
        }
        if (hours > 0) {
            return hours + " " + hoursWord + (minutes % 60 > 0 ? " " + (minutes % 60) + " " + minutesWord : "");
        }
        if (minutes > 0) {
            return minutes + " " + minutesWord + (seconds % 60 > 0 ? " " + (seconds % 60) + " " + secondsWord : "");
        }
        return seconds + " " + secondsWord;
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

    /**
     * Profile ban listesine ekler (Paper 1.21+ uyumluluğu)
     * BanList.Type.PROFILE kullanarak UUID bazlı ban ekler
     */
    private void addProfileBan(String playerName, String reason, Date expires, String punisher) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                Bukkit.getBanList(BanList.Type.PROFILE).addBan(
                        offlinePlayer.getName(), reason, expires, punisher);
            }
        } catch (Exception e) {
            // Profile ban eklenemezse sessizce devam et (NAME ban zaten eklendi)
            plugin.getLogger().fine("[BAN] Profile ban could not be added: " + e.getMessage());
        }
    }

    /**
     * Profile ban listesinden kaldırır
     */
    private boolean removeProfileBan(String playerName) {
        try {
            if (Bukkit.getBanList(BanList.Type.PROFILE).isBanned(playerName)) {
                Bukkit.getBanList(BanList.Type.PROFILE).pardon(playerName);
                return true;
            }

            // İsim ile bulamazsa OfflinePlayer üzerinden dene
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.isBanned()) {
                Bukkit.getBanList(BanList.Type.PROFILE).pardon(
                        offlinePlayer.getName() != null ? offlinePlayer.getName() : playerName);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[UNBAN] Profile unban error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Profile ban listesinde banlı mı kontrol eder
     */
    private boolean isProfileBanned(String playerName) {
        try {
            if (Bukkit.getBanList(BanList.Type.PROFILE).isBanned(playerName)) {
                return true;
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            return offlinePlayer.isBanned();
        } catch (Exception e) {
            return false;
        }
    }
}
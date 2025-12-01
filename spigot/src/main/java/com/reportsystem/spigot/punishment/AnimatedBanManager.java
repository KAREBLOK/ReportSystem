package com.reportsystem.spigot.punishment;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Animasyonlu ban yöneticisi
 * Havadan örs düşürme, yıldırım çakma ve dramatik ölüm animasyonu
 */
public class AnimatedBanManager implements Listener {

    private final JavaPlugin plugin;
    private final PunishmentManager punishmentManager;

    // Ölecek oyuncuların envanterlerini sakla (geçici)
    private final Map<Player, SavedInventory> savedInventories = new HashMap<>();

    // Freeze edilen oyuncular ve lokasyonları
    private final Map<Player, FreezeData> frozenPlayers = new HashMap<>();

    // Animasyonlu ban ile öldürülen oyuncular (drop'u engellemek için)
    private final Set<UUID> dyingPlayers = new HashSet<>();

    public AnimatedBanManager(JavaPlugin plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;

        // Event listener kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Freeze task başlat
        startFreezeTask();
    }

    /**
     * Oyuncu ölümünde drop'u engelle (animated ban için)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (dyingPlayers.contains(player.getUniqueId())) {
            // Tüm drop'ları engelle
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);

            // Death message'i de özelleştir
            event.setDeathMessage(null);

            plugin.getLogger().info("[AnimatedBan] Death drops prevented for " + player.getName());
        }
    }

    /**
     * Animasyonlu ban başlat
     * @param target Hedef oyuncu
     * @param reason Ban sebebi
     * @param executor Ban atan yetkili
     * @param durationMillis Ban süresi (millisaniye), -1 ise kalıcı
     */
    public void executeAnimatedBan(Player target, String reason, Player executor, long durationMillis) {
        if (target == null || !target.isOnline()) {
            plugin.getLogger().warning("Cannot execute animated ban: target is null or offline");
            return;
        }

        // Envanteri kaydet
        saveInventory(target);

        // Oyuncuyu freeze et
        Location targetLoc = target.getLocation();
        freezePlayer(target, targetLoc);
        World world = targetLoc.getWorld();

        // 1. Hedef etrafında particle efekti (kırmızı spiral)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40 || !target.isOnline()) { // 2 saniye
                    cancel();
                    return;
                }

                // Spiral particle
                double angle = ticks * 0.5;
                double radius = 1.5;
                double x = targetLoc.getX() + radius * Math.cos(angle);
                double z = targetLoc.getZ() + radius * Math.sin(angle);
                double y = targetLoc.getY() + (ticks * 0.1);

                world.spawnParticle(Particle.DUST, x, y, z, 1,
                    new Particle.DustOptions(Color.RED, 1.5f));
                world.spawnParticle(Particle.SMOKE, targetLoc, 3, 0.5, 0.5, 0.5, 0.02);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // 2. Yıldırım sesleri ve efektleri
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 3 || !target.isOnline()) {
                    cancel();
                    return;
                }

                // Yıldırım efekti (hasar vermeyen)
                world.strikeLightningEffect(targetLoc.clone().add(
                    (Math.random() - 0.5) * 3,
                    0,
                    (Math.random() - 0.5) * 3
                ));

                // Yıldırım sesi
                world.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

                // Kırmızı parıltı
                world.spawnParticle(Particle.FLASH, targetLoc, 1);

                count++;
            }
        }.runTaskTimer(plugin, 20L, 15L); // 1 saniye sonra başla, her 0.75 saniyede bir

        // 3. Havadan örs düşür (3 saniye sonra)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline()) return;

            Location anvilSpawnLoc = targetLoc.clone().add(0, 20, 0);

            // Örs düşür
            FallingBlock fallingAnvil = world.spawnFallingBlock(
                anvilSpawnLoc,
                Material.ANVIL.createBlockData()
            );
            fallingAnvil.setDropItem(false);
            fallingAnvil.setHurtEntities(false); // Vanilla hasar verme

            // Örs düşerken ses ve efekt
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fallingAnvil.isValid() || fallingAnvil.isOnGround()) {
                        cancel();

                        // Örs yere düştüğünde
                        if (target.isOnline()) {
                            onAnvilLanded(target, reason, executor, durationMillis);
                        }

                        // Örsü sil
                        fallingAnvil.remove();
                        return;
                    }

                    // Düşerken ses ve particle
                    Location anvilLoc = fallingAnvil.getLocation();
                    world.playSound(anvilLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
                    world.spawnParticle(Particle.CLOUD, anvilLoc, 5, 0.2, 0.2, 0.2, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 5L);

        }, 60L); // 3 saniye sonra
    }

    /**
     * Örs yere düştüğünde çalışır
     */
    private void onAnvilLanded(Player target, String reason, Player executor, long durationMillis) {
        if (!target.isOnline()) return;

        Location loc = target.getLocation();
        World world = loc.getWorld();

        // Büyük patlama efekti
        world.spawnParticle(Particle.EXPLOSION, loc, 10, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.LAVA, loc, 50, 1, 1, 1, 0.1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_ANVIL_FALL, 2.0f, 1.0f);

        // Son yıldırım
        world.strikeLightningEffect(loc);

        // Oyuncuyu dying list'e ekle (drop'u engellemek için)
        dyingPlayers.add(target.getUniqueId());

        // Oyuncuyu öldür (PlayerDeathEvent drop'u engelleyecek)
        target.setHealth(0.0);

        // Ölüm mesajı
        String deathMessage = ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ " +
            ChatColor.RED + target.getName() +
            ChatColor.GRAY + " tanrıların gazabına uğradı!";

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(deathMessage);
        }

        // Envanter geri yükleme ve ban (1 saniye sonra)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Envanteri geri yükle
            restoreInventory(target);

            // Freeze'i kaldır (kick olmadan önce)
            unfreezePlayer(target);

            // Ban mesajı oluştur
            String banMessage = ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚡ BAN ⚡\n" +
                ChatColor.RED + "Sebep: " + ChatColor.WHITE + reason + "\n" +
                ChatColor.GRAY + "Yetkili: " + ChatColor.WHITE + executor.getName();

            // Ban at
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Süreye göre kalıcı veya geçici ban uygula
                boolean success;
                if (durationMillis == -1) {
                    // Kalıcı ban
                    success = punishmentManager.getProvider().permBan(
                        target.getName(),
                        reason,
                        executor.getName()
                    );
                    plugin.getLogger().info("[AnimatedBan] Permanent ban applied to " + target.getName());
                } else {
                    // Geçici ban
                    success = punishmentManager.getProvider().tempBan(
                        target.getName(),
                        reason,
                        executor.getName(),
                        durationMillis
                    );
                    plugin.getLogger().info("[AnimatedBan] Temporary ban applied to " + target.getName() +
                        " for " + durationMillis + "ms");
                }

                if (success) {
                    plugin.getLogger().info("[AnimatedBan] " + target.getName() + " banned by " +
                        executor.getName() + " - Reason: " + reason);
                } else {
                    plugin.getLogger().warning("[AnimatedBan] Failed to ban " + target.getName());
                }

                // Kick player
                target.kickPlayer(banMessage);

                // Dying list'ten temizle
                dyingPlayers.remove(target.getUniqueId());
            });

        }, 20L); // 1 saniye sonra
    }

    /**
     * Oyuncunun envanterini kaydet
     */
    private void saveInventory(Player player) {
        SavedInventory saved = new SavedInventory();

        // Tüm envanteri kopyala
        saved.contents = player.getInventory().getContents().clone();
        saved.armorContents = player.getInventory().getArmorContents().clone();

        savedInventories.put(player, saved);

        plugin.getLogger().info("[AnimatedBan] Inventory saved for " + player.getName());
    }

    /**
     * Oyuncunun envanterini geri yükle
     */
    private void restoreInventory(Player player) {
        SavedInventory saved = savedInventories.remove(player);

        if (saved != null && player.isOnline()) {
            player.getInventory().setContents(saved.contents);
            player.getInventory().setArmorContents(saved.armorContents);

            plugin.getLogger().info("[AnimatedBan] Inventory restored for " + player.getName());
        }
    }

    /**
     * Oyuncuyu freeze et
     */
    private void freezePlayer(Player player, Location freezeLoc) {
        FreezeData data = new FreezeData();
        data.location = freezeLoc.clone();
        data.originalWalkSpeed = player.getWalkSpeed();
        data.originalFlySpeed = player.getFlySpeed();
        data.wasFlying = player.isFlying();
        data.allowFlight = player.getAllowFlight();

        // Speed'leri 0 yap
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
        if (player.isFlying()) {
            player.setFlying(false);
        }

        frozenPlayers.put(player, data);

        plugin.getLogger().info("[AnimatedBan] Frozen player: " + player.getName());
    }

    /**
     * Oyuncunun freeze'ini kaldır
     */
    private void unfreezePlayer(Player player) {
        FreezeData data = frozenPlayers.remove(player);

        if (data != null && player.isOnline()) {
            player.setWalkSpeed(data.originalWalkSpeed);
            player.setFlySpeed(data.originalFlySpeed);
            player.setAllowFlight(data.allowFlight);
            if (data.wasFlying && data.allowFlight) {
                player.setFlying(true);
            }

            plugin.getLogger().info("[AnimatedBan] Unfrozen player: " + player.getName());
        }
    }

    /**
     * Freeze task - Her tick freeze edilen oyuncuları aynı yere teleport et
     */
    private void startFreezeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Player, FreezeData> entry : frozenPlayers.entrySet()) {
                    Player player = entry.getKey();
                    FreezeData data = entry.getValue();

                    if (player.isOnline()) {
                        // Oyuncu hareket ettiyse geri teleport et
                        Location current = player.getLocation();
                        if (current.distanceSquared(data.location) > 0.01) {
                            player.teleport(data.location);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Her tick
    }

    /**
     * Kaydedilmiş envanter
     */
    private static class SavedInventory {
        ItemStack[] contents;
        ItemStack[] armorContents;
    }

    /**
     * Freeze data
     */
    private static class FreezeData {
        Location location;
        float originalWalkSpeed;
        float originalFlySpeed;
        boolean wasFlying;
        boolean allowFlight;
    }
}

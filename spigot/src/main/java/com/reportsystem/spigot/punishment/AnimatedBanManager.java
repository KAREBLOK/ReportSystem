package com.reportsystem.spigot.punishment;

import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Animasyonlu ban yöneticisi
 * Havadan örs düşürme, yıldırım çakma ve dramatik ölüm animasyonu
 *
 * Ban, animasyon bitince uygulanır; ancak hedef animasyon sırasında çıkarsa veya eklenti
 * kapanırsa ban yine uygulanır. Tüm yollar tek idempotent noktadan geçer: {@link #applyPendingBan}.
 * Tüm alanlara yalnız ana thread'den erişilir.
 */
public class AnimatedBanManager implements Listener {

    private final JavaPlugin plugin;
    private final PunishmentManager punishmentManager;

    // Ölecek oyuncuların envanterlerini sakla (geçici) — UUID anahtarlı (Player nesnesi sızmasın)
    private final Map<UUID, SavedInventory> savedInventories = new HashMap<>();

    // Freeze edilen oyuncular ve lokasyonları
    private final Map<UUID, FreezeData> frozenPlayers = new HashMap<>();

    // Animasyonlu ban ile öldürülen oyuncular (drop'u engellemek için)
    private final Set<UUID> dyingPlayers = new HashSet<>();

    // Animasyonu süren, henüz uygulanmamış banlar
    private final Map<UUID, PendingBan> pendingBans = new HashMap<>();

    // Animasyon örsleri (gerçek FallingBlock) — yere değince blok yerleştirmeleri engellenir
    private final Set<UUID> animationAnvils = new HashSet<>();

    // Freeze task referansı (cancel için)
    private BukkitTask freezeTask;

    public AnimatedBanManager(JavaPlugin plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;

        // Event listener kaydet
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Freeze task başlat
        startFreezeTask();
    }

    private void debug(String message) {
        if (plugin instanceof com.reportsystem.spigot.ReportSystemSpigot rs) {
            rs.debug(message);
        }
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

            debug("[AnimatedBan] Death drops prevented for " + player.getName());
        }
    }

    /**
     * Animasyon örsü yere değdiğinde vanilla gerçek ÖRS bloğu yerleştirir → yalnız bizim örslerimiz
     * için iptal edilir ve entity kaldırılır (dünyada kazılabilir örs kalmasın).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) return;
        if (!animationAnvils.remove(event.getEntity().getUniqueId())) return;
        event.setCancelled(true);
        event.getEntity().remove();
    }

    /**
     * Animasyon sırasında çıkış: ban kaçırılmasın, hız/envanter playerdata'ya bozuk yazılmasın.
     * Freeze/envanter SENKRON geri alınır (playerdata çıkışta bu olaydan sonra kaydedilir),
     * ban ise 1 tick sonra (oyuncu artık çevrimdışı → sağlayıcı onu atmaya çalışmaz) uygulanır.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean pending = pendingBans.containsKey(uuid);
        if (!pending && !frozenPlayers.containsKey(uuid) && !savedInventories.containsKey(uuid)) {
            return;
        }

        unfreezePlayer(player, true);
        restoreInventory(player, true);
        dyingPlayers.remove(uuid);

        if (pending) {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> applyPendingBan(uuid, false));
            } else {
                applyPendingBan(uuid, false);
            }
        }
    }

    /**
     * Animasyonlu ban başlat
     * @param target Hedef oyuncu
     * @param reason Ban sebebi
     * @param executorName Ban atan yetkilinin adı
     * @param durationMillis Ban süresi (millisaniye), -1 ise kalıcı
     * @param onResult Ban gerçekten uygulandığında (veya uygulanamadığında) ana thread'de çağrılır; null olabilir
     */
    public void executeAnimatedBan(Player target, String reason, String executorName, long durationMillis,
                                   Consumer<Boolean> onResult) {
        if (target == null) {
            plugin.getLogger().warning("Cannot execute animated ban: target is null");
            notifyResult(onResult, false);
            return;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName();

        // Hedef çevrimdışıysa veya zaten bir animasyonlu ban bekliyorsa (aynı oyuncuya ikinci rapor):
        // animasyon başlatma, doğrudan sağlayıcı ile banla.
        if (!target.isOnline() || pendingBans.containsKey(uuid)) {
            boolean success = applyBan(targetName, reason, executorName, durationMillis);
            notifyResult(onResult, success);
            return;
        }

        PendingBan pb = new PendingBan();
        pb.uuid = uuid;
        pb.name = targetName;
        pb.reason = reason;
        pb.executorName = executorName;
        pb.durationMillis = durationMillis;
        pb.onResult = onResult;
        pendingBans.put(uuid, pb);

        // Güvenlik ağı: animasyon zinciri istisnayla koparsa ban 15 sn sonra yine uygulanır.
        // applyPendingBan idempotent → normal akış (~5 sn) bittiyse no-op; görev saklanmaz/iptal
        // edilmez (kapanışta Bukkit eklentinin görevlerini zaten iptal eder, shutdown() uygular).
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyPendingBan(uuid, true), 300L);
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
            // Çıkış/kapanış yolu banı zaten uyguladıysa animasyona gerek yok (yalnız görsel iptal)
            if (!target.isOnline() || !pendingBans.containsKey(uuid)) return;

            Location anvilSpawnLoc = targetLoc.clone().add(0, 20, 0);

            // Örs düşür
            FallingBlock fallingAnvil = world.spawnFallingBlock(
                anvilSpawnLoc,
                Material.ANVIL.createBlockData()
            );
            fallingAnvil.setDropItem(false);
            fallingAnvil.setHurtEntities(false); // Vanilla hasar verme
            fallingAnvil.setPersistent(false);   // chunk ile diske yazılmasın
            animationAnvils.add(fallingAnvil.getUniqueId()); // yere değince gerçek blok bırakmasın

            // Örs düşerken ses ve efekt (200 tick = 10 saniye timeout)
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    ticks += 5; // Her 5 tick'te bir çalışıyor

                    if (!fallingAnvil.isValid() || fallingAnvil.isOnGround() || ticks >= 200) {
                        cancel();

                        // Örs yere düştüğünde (veya timeout)
                        if (target.isOnline()) {
                            onAnvilLanded(target);
                        }

                        // Örsü sil
                        fallingAnvil.remove();
                        animationAnvils.remove(fallingAnvil.getUniqueId());
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
    private void onAnvilLanded(Player target) {
        if (!target.isOnline()) return;

        UUID uuid = target.getUniqueId();
        // Ban başka bir yoldan (çıkış/kapanış) zaten uygulandıysa ölüm animasyonu yapma
        if (!pendingBans.containsKey(uuid)) return;

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
        dyingPlayers.add(uuid);

        // Oyuncuyu öldür (PlayerDeathEvent drop'u engelleyecek)
        target.setHealth(0.0);

        // Ölüm mesajı
        com.reportsystem.spigot.managers.MessageManager mm = ((com.reportsystem.spigot.ReportSystemSpigot) plugin).getMessageManager();
        String deathMessage = mm.colorize(mm.getMessage("punishments.ban.death-message").replace("%player%", target.getName()));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(deathMessage);
        }

        // Envanter geri yükleme + ban + kick (1 saniye sonra) — tek uygulama noktası
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyPendingBan(uuid, true), 20L);
    }

    /**
     * TEK ban uygulama noktası (idempotent): animasyon sonu, çıkış ve kapanış bunu çağırır;
     * ilk çağıran uygular, sonrakiler no-op. Yalnız ana thread.
     * @param kick true ise ve oyuncu çevrimiçiyse ban ekranıyla atılır
     */
    private void applyPendingBan(UUID uuid, boolean kick) {
        PendingBan pb = pendingBans.remove(uuid);
        if (pb == null) return;

        Player online = Bukkit.getPlayer(uuid);
        boolean isOnline = online != null && online.isOnline();
        if (isOnline) {
            // Envanteri geri yükle ve freeze'i kaldır (kick olmadan önce)
            restoreInventory(online, false);
            unfreezePlayer(online, false);
        } else {
            // Çıkışta zaten senkron geri alındı; kalan kayıt varsa sızmasın
            savedInventories.remove(uuid);
            frozenPlayers.remove(uuid);
        }

        // Süreye göre kalıcı veya geçici ban uygula
        boolean success = applyBan(pb.name, pb.reason, pb.executorName, pb.durationMillis);

        if (success) {
            plugin.getLogger().info("[AnimatedBan] " + pb.name + " banned by " + pb.executorName
                    + " - Reason: " + pb.reason);
            // Ban ekranıyla at (yalnız ban gerçekten uygulandıysa; başarısızsa rapor PENDING'e döner)
            // (sağlayıcı kapanışta oyuncuyu kendisi atmış olabilir → yeniden kontrol)
            if (kick && isOnline && online.isOnline()) {
                com.reportsystem.spigot.managers.MessageManager mm = ((com.reportsystem.spigot.ReportSystemSpigot) plugin).getMessageManager();
                String banMessage = mm.colorize(mm.getMessage("punishments.ban.animated-screen")
                        .replace("%reason%", pb.reason)
                        .replace("%staff%", pb.executorName));
                online.kickPlayer(banMessage);
            }
        } else {
            plugin.getLogger().warning("[AnimatedBan] Failed to ban " + pb.name);
        }

        // Dying list'ten temizle
        dyingPlayers.remove(uuid);

        notifyResult(pb.onResult, success);
    }

    /**
     * Sağlayıcı ile ban uygular (-1 = kalıcı). İstisna başarısızlık sayılır.
     */
    private boolean applyBan(String name, String reason, String executorName, long durationMillis) {
        try {
            if (durationMillis == -1) {
                debug("[AnimatedBan] Permanent ban applied to " + name);
                return punishmentManager.getProvider().permBan(name, reason, executorName);
            }
            debug("[AnimatedBan] Temporary ban applied to " + name + " for " + durationMillis + "ms");
            return punishmentManager.getProvider().tempBan(name, reason, executorName, durationMillis);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[AnimatedBan] Ban provider error for " + name, e);
            return false;
        }
    }

    private void notifyResult(Consumer<Boolean> onResult, boolean success) {
        if (onResult == null) return;
        try {
            onResult.accept(success);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[AnimatedBan] Result callback error", t);
        }
    }

    /**
     * Oyuncunun envanterini kaydet
     */
    private void saveInventory(Player player) {
        SavedInventory saved = new SavedInventory();

        // Tüm envanteri kopyala
        saved.contents = player.getInventory().getContents().clone();
        saved.armorContents = player.getInventory().getArmorContents().clone();

        savedInventories.put(player.getUniqueId(), saved);

        debug("[AnimatedBan] Inventory saved for " + player.getName());
    }

    /**
     * Oyuncunun envanterini geri yükle
     * @param force true ise çevrimiçi kontrolü atlanır (PlayerQuitEvent içinde; playerdata doğru envanterle kaydedilsin)
     */
    private void restoreInventory(Player player, boolean force) {
        SavedInventory saved = savedInventories.remove(player.getUniqueId());

        if (saved != null && (force || player.isOnline())) {
            player.getInventory().setContents(saved.contents);
            player.getInventory().setArmorContents(saved.armorContents);

            debug("[AnimatedBan] Inventory restored for " + player.getName());
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

        frozenPlayers.put(player.getUniqueId(), data);

        debug("[AnimatedBan] Frozen player: " + player.getName());
    }

    /**
     * Oyuncunun freeze'ini kaldır
     * @param force true ise çevrimiçi kontrolü atlanır (PlayerQuitEvent içinde; hız 0 playerdata'ya yazılmasın)
     */
    private void unfreezePlayer(Player player, boolean force) {
        FreezeData data = frozenPlayers.remove(player.getUniqueId());

        if (data != null && (force || player.isOnline())) {
            player.setWalkSpeed(data.originalWalkSpeed);
            player.setFlySpeed(data.originalFlySpeed);
            player.setAllowFlight(data.allowFlight);
            if (data.wasFlying && data.allowFlight) {
                player.setFlying(true);
            }

            debug("[AnimatedBan] Unfrozen player: " + player.getName());
        }
    }

    /**
     * Freeze task - Her tick freeze edilen oyuncuları aynı yere teleport et
     */
    private void startFreezeTask() {
        this.freezeTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Kopya üzerinde dön: teleport → başka eklenti atarsa onQuit haritayı değiştirir (CME)
                for (Map.Entry<UUID, FreezeData> entry : new ArrayList<>(frozenPlayers.entrySet())) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    FreezeData data = entry.getValue();

                    if (player != null && player.isOnline()) {
                        // T-3: dondurma konumunun dünyası boşaltıldıysa getWorld() "World unloaded" atar ve
                        // her tick log basar → teleport deneme, bu oyuncuyu atla
                        if (data.location == null || !data.location.isWorldLoaded()) continue;
                        // Oyuncu hareket ettiyse geri teleport et
                        Location current = player.getLocation();
                        if (!java.util.Objects.equals(current.getWorld(), data.location.getWorld())
                                || current.distanceSquared(data.location) > 0.01) {
                            player.teleport(data.location);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Her tick
    }

    /**
     * Plugin kapanırken: bekleyen banları uygula (kaçış olmasın), freeze task'ı iptal et ve
     * tüm frozen oyuncuları serbest bırak.
     */
    public void shutdown() {
        if (freezeTask != null) {
            freezeTask.cancel();
            freezeTask = null;
        }

        // Bekleyen banları uygula (senkron; kopya üzerinde dön — applyPendingBan haritadan siler)
        for (UUID uuid : new ArrayList<>(pendingBans.keySet())) {
            try {
                applyPendingBan(uuid, true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[AnimatedBan] Failed to apply pending ban on shutdown", e);
            }
        }

        // Tüm frozen oyuncuları serbest bırak (kopya ile iterate et, ConcurrentModificationException önle)
        for (UUID uuid : new ArrayList<>(frozenPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                unfreezePlayer(player, false);
            }
        }
        for (UUID uuid : new ArrayList<>(savedInventories.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restoreInventory(player, false);
            }
        }
        frozenPlayers.clear();
        savedInventories.clear();
        dyingPlayers.clear();
    }

    /**
     * Animasyonu süren, henüz uygulanmamış ban
     */
    private static class PendingBan {
        UUID uuid;
        String name;
        String reason;
        String executorName;
        long durationMillis;
        Consumer<Boolean> onResult;
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

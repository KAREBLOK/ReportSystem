package com.reportsystem.spigot.replay;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Prevents replay viewers from interacting with the world
 * and protects them from all damage sources
 */
public class ReplayProtectionListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReplayProtectionListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Replay izliyor veya Overwatch post-replay'de (koruma gerekli)
     */
    private boolean needsProtection(Player player) {
        if (plugin.getReplayManager().isWatchingReplay(player)) return true;
        if (plugin.getOverwatchReplayListener() != null
                && plugin.getOverwatchReplayListener().needsProtection(player.getUniqueId())) return true;
        return false;
    }

    // ==================== KOMUT FILTRESI (H-47) ====================

    /**
     * Replay izleyen veya Overwatch post-replay'deki oyuncu yalniz
     * {@code replay.viewer-allowed-commands} listesindeki komutlari kullanabilir (yetkililer dahil).
     * Kara liste yerine izin listesi: ad alanli ({@code /essentials:home}) ve listede olmayan
     * komutlar da engellenir. Liste her olayda okunur → {@code /rs reload} sonrasi aninda gecerli.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!needsProtection(player)) return;

        String message = event.getMessage();
        String body = message.startsWith("/") ? message.substring(1) : message;
        String label = body.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        // M-2: ad alanı yalnız bizim (reportsystem / plugin.yml adından gelen reportsystem-spigot) veya
        // vanilla (minecraft) ise soyulur; başka eklentinin ad alanlı komutu (/baskaeklenti:msg) izin
        // listesindeki "msg" ile eşleşmez → engellenir.
        // K-c: tam etiket (ör. essentials:msg) izin listesinde açıkça varsa ad alanı soyulmadan izin ver.
        for (String cmd : plugin.getConfig().getStringList("replay.viewer-allowed-commands")) {
            if (cmd == null) continue;
            String c = cmd.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) c = c.substring(1);
            if (label.indexOf(':') >= 0 && c.equals(label)) return;
        }
        int colon = label.indexOf(':');
        if (colon >= 0) {
            String namespace = label.substring(0, colon);
            if (!namespace.equals("reportsystem") && !namespace.equals("reportsystem-spigot")
                    && !namespace.equals("minecraft")) {
                label = "";
            } else {
                label = label.substring(colon + 1);
            }
        }

        Set<String> allowed = new HashSet<>();
        for (String cmd : plugin.getConfig().getStringList("replay.viewer-allowed-commands")) {
            if (cmd == null) continue;
            String c = cmd.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) c = c.substring(1);
            if (!c.isEmpty()) allowed.add(c);
        }

        if (!label.isEmpty() && allowed.contains(label)) return;

        event.setCancelled(true);
        plugin.getMessageManager().sendMessage(player, "replay.command-blocked");
        plugin.getMessageManager().sendMessage(player, "replay.command-blocked-tip");
    }

    // ==================== MESAFE SINIRI (H-47) ====================

    /**
     * Replay izleyicisi NPC'nin (kaydedilen oyuncunun son konumu) {@code replay.viewer-max-distance}
     * blok otesine gidemez; {@code reportsystem.view.all} sahipleri muaf, 0 = kapali.
     * Sinir disindayken (or. isinlanma sonrasi) NPC'ye YAKLASAN hareket serbesttir → izleyici kilitlenmez.
     * Farkli dunya → dokunulmaz. Isinlanmalar (PlayerTeleportEvent) bu olaya dusmez.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Yalniz bakis acisi degistiyse hesaplama yapma
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();
        if (!plugin.getReplayManager().isWatchingReplay(player)) return;
        if (player.hasPermission("reportsystem.view.all")) return;

        double max = plugin.getConfig().getDouble("replay.viewer-max-distance", 48);
        if (max <= 0) return;

        ReplayPlayer rp = plugin.getReplayManager().getViewerReplay(player);
        if (rp == null) return;
        Location last = rp.getLastLocation();
        if (last == null || last.getWorld() == null || !Objects.equals(last.getWorld(), to.getWorld())) return;

        double maxSq = max * max;
        double toSq = to.distanceSquared(last);
        if (toSq <= maxSq) return;
        // Zaten sinir disindaysa NPC'ye yaklasmaya izin ver (uzaklasmayi engelle)
        if (Objects.equals(from.getWorld(), last.getWorld()) && toSq <= from.distanceSquared(last)) return;

        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
    }

    // ==================== HASAR KORUMASI ====================

    /**
     * Tum hasar kaynaklarini engelle (mob, dusme, ates, bogulma, void, vs.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Replay viewer'in baska entity'lere hasar vermesini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Replay projectile hasarini engelle
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
                event.setCancelled(true);
                event.setDamage(0);
                return;
            }
        }

        // Viewer'in baskasina hasar vermesini engelle
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (needsProtection(damager)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Moblarin replay viewer'i hedef almasini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player && needsProtection((Player) target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Moblarin replay viewer'i hedef almasini engelle (living entity)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLiving(EntityTargetLivingEntityEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player && needsProtection((Player) target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Aclik barinin degismesini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== ETKILESIM KORUMASI ====================

    /**
     * Item toplama engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Item dusurme engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blok kirma engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Blok koyma engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (needsProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Envanter tiklama engelle (GUI'ler haric)
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (needsProtection(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Dunya etkilesimlerini engelle (kontrol itemleri haric)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (needsProtection(event.getPlayer())) {
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Replay projectile hit efektlerini engelle
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.hasMetadata("REPLAY_PROJECTILE")) {
            event.setCancelled(true);
            projectile.remove();
        }
    }
}

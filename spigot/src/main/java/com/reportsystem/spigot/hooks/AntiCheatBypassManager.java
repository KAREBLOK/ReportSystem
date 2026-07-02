package com.reportsystem.spigot.hooks;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replay izlerken viewer'in anti-cheat tarafindan "hile" olarak isaretlenmesini onler.
 *
 * Iki katmanli koruma:
 * 1) PermissionAttachment — viewer'a anti-cheat'lerin standart bypass perm'lerini
 *    gecici olarak verir (vulcan.bypass, grim.bypass, polar.bypass, themis.bypass vb.).
 *    En evrensel yontem; ozel API call'una gerek yok.
 * 2) Event cancellation — VulcanFlagEvent / GrimAC / Polar detection listener'larimiz
 *    isExempt() ile kontrol edip flag'i bastirir.
 *
 * Replay bittiginde attachment otomatik kaldirilir.
 */
public class AntiCheatBypassManager {

    private final ReportSystemSpigot plugin;

    // Aktif viewer'larin UUID'leri — detection listener'lar buradan kontrol eder
    private final Set<UUID> exemptPlayers = ConcurrentHashMap.newKeySet();

    // Viewer basina kayitli attachment — unexempt'te kaldirmak icin
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    // Varsayilan bypass perm listesi (config ile genisletilebilir)
    private static final List<String> DEFAULT_BYPASS_PERMS = Collections.unmodifiableList(java.util.Arrays.asList(
            // Vulcan
            "vulcan.bypass",
            // GrimAC
            "grim.bypass",
            // Polar
            "polar.bypass",
            // Themis
            "themis.bypass",
            // AAC (Advanced Anti-Cheat)
            "aac.bypass",
            // Matrix
            "matrix.bypass",
            // NoCheatPlus
            "ncp.bypass",
            // Spartan
            "spartan.bypass",
            // Karhu
            "karhu.bypass",
            // Intave
            "intave.bypass",
            // Verus
            "verus.bypass",
            // Witherac / NoksAntiCheat ve ozel cozumler bunu kontrol edebilir
            "reportsystem.bypass.anticheat"
    ));

    public AntiCheatBypassManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    /**
     * Viewer replay izlemeye basladiginda cagrilir.
     * Anti-cheat bypass perm'lerini verir ve exempt set'ine ekler.
     */
    public void exempt(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        UUID uuid = viewer.getUniqueId();

        exemptPlayers.add(uuid);

        // Eski attachment varsa once temizle (cift cagri korumasi)
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) {
            try { viewer.removeAttachment(old); } catch (Exception ignored) {}
        }

        try {
            PermissionAttachment attachment = viewer.addAttachment(plugin);
            for (String perm : getBypassPerms()) {
                attachment.setPermission(perm, true);
            }
            attachments.put(uuid, attachment);

            plugin.debug("[AC-BYPASS] Exempt: " + viewer.getName() +
                    " (perms: " + getBypassPerms().size() + ")");
        } catch (Exception e) {
            plugin.getLogger().warning("[AC-BYPASS] Failed to grant bypass perms for " +
                    viewer.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Replay bittiginde / viewer cikinca cagrilir.
     */
    public void unexempt(Player viewer) {
        if (viewer == null) return;
        unexempt(viewer.getUniqueId());
    }

    public void unexempt(UUID uuid) {
        if (uuid == null) return;

        exemptPlayers.remove(uuid);

        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null && viewer.isOnline()) {
                try {
                    viewer.removeAttachment(attachment);
                    plugin.debug("[AC-BYPASS] Unexempt: " + viewer.getName());
                } catch (IllegalArgumentException ignored) {
                    // Attachment zaten kaldirilmis olabilir
                }
            }
        }
    }

    /**
     * Detection listener'lar tarafindan kullanilir.
     * Player exempt ise event.setCancelled(true) ile flag bastirilmali.
     */
    public boolean isExempt(UUID uuid) {
        return uuid != null && exemptPlayers.contains(uuid);
    }

    public boolean isExempt(Player player) {
        return player != null && isExempt(player.getUniqueId());
    }

    /**
     * Plugin disable olurken tum exempt'leri temizle.
     */
    public void shutdown() {
        for (Map.Entry<UUID, PermissionAttachment> entry : new HashSet<>(attachments.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) {
                try { viewer.removeAttachment(entry.getValue()); } catch (Exception ignored) {}
            }
        }
        attachments.clear();
        exemptPlayers.clear();
    }

    /**
     * Config'den ek bypass perm'leri okur (ornegin ozel anti-cheat'ler icin).
     * config.yml: anticheat-bypass.extra-permissions: ["myac.bypass", ...]
     */
    private List<String> getBypassPerms() {
        List<String> extra = plugin.getConfig().getStringList("anticheat-bypass.extra-permissions");
        if (extra == null || extra.isEmpty()) {
            return DEFAULT_BYPASS_PERMS;
        }
        List<String> combined = new java.util.ArrayList<>(DEFAULT_BYPASS_PERMS);
        for (String p : extra) {
            if (p != null && !p.isEmpty() && !combined.contains(p)) {
                combined.add(p);
            }
        }
        return combined;
    }
}

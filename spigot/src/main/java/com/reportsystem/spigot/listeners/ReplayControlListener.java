package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.replay.ReplayPlayer;
import com.reportsystem.spigot.gui.ReplayInfoGUI;
import com.reportsystem.spigot.gui.ReplayTeleportGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ReplayControlListener implements Listener {

    private final ReportSystemSpigot plugin;

    public ReplayControlListener(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ReplayPlayer replayPlayer = plugin.getReplayManager().getViewerReplay(player);

        if (replayPlayer == null) return;

        event.setCancelled(true);

        Action action = event.getAction();
        boolean isLeftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean isRightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        if (!isLeftClick && !isRightClick) return;

        // 1. sahis modunda tiklama devre disi - cikis sadece Q (drop) ile
        if (replayPlayer.getNpcManager().isFirstPersonEnabled()) {
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);

        if (item == null || item.getType() == Material.AIR) return;

        switch (slot) {
            case 0: // Play/Pause
                if (replayPlayer.isPaused()) {
                    replayPlayer.resume();
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
                    showTitle(player, ChatColor.GREEN + "▶", "");
                } else {
                    replayPlayer.pause();
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
                    showTitle(player, ChatColor.GRAY + "⏸", "");
                }
                break;

            case 1: // Replay Ayarları GUI
                if (item.getType() == Material.COMPARATOR) {
                    new ReplayInfoGUI(plugin, player, replayPlayer).open();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                }
                break;

            case 3: // 5 saniye geri
                if (item.getType() == Material.PLAYER_HEAD) {
                    replayPlayer.rewind(5);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    showTitle(player, ChatColor.RED + "◀◀", "");
                }
                break;

            case 4: // Işınlanma GUI
                if (item.getType() == Material.ENDER_PEARL) {
                    new ReplayTeleportGUI(plugin, player, replayPlayer).open();
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
                }
                break;

            case 5: // 5 saniye ileri
                if (item.getType() == Material.PLAYER_HEAD) {
                    replayPlayer.forward(5);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                    showTitle(player, ChatColor.GREEN + "▶▶", "");
                }
                break;

            case 7: // Hız kontrolü
                double currentSpeed = replayPlayer.getPlaybackSpeed();
                double newSpeed;

                if (isLeftClick) {
                    if (currentSpeed == 0.25) newSpeed = 0.5;
                    else if (currentSpeed == 0.5) newSpeed = 1.0;
                    else if (currentSpeed == 1.0) newSpeed = 2.0;
                    else if (currentSpeed == 2.0) newSpeed = 4.0;
                    else newSpeed = 4.0;
                } else {
                    if (currentSpeed == 4.0) newSpeed = 2.0;
                    else if (currentSpeed == 2.0) newSpeed = 1.0;
                    else if (currentSpeed == 1.0) newSpeed = 0.5;
                    else if (currentSpeed == 0.5) newSpeed = 0.25;
                    else newSpeed = 0.25;
                }

                replayPlayer.setPlaybackSpeed(newSpeed);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                showTitle(player, ChatColor.AQUA + "" + newSpeed + "×", "");
                break;

            case 8: // Durdur
                plugin.debug("[REPLAY-CONTROL] Stop button clicked by " + player.getName());
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.5f);
                showTitle(player, ChatColor.RED + "■", "Durduruluyor...");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        // Overwatch review'daysa finish() cagir (post-replay itemleri gosterir)
                        if (plugin.getOverwatchReplayListener() != null
                                && plugin.getOverwatchReplayListener().isReviewing(player.getUniqueId())) {
                            replayPlayer.finish();
                        } else {
                            plugin.getReplayManager().stopReplay(player);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[REPLAY-CONTROL] Error stopping replay for " + player.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        plugin.getReplayManager().removeActiveReplay(player.getUniqueId());
                        plugin.getReplayManager().getControlManager().removeControlItems(player);
                        player.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("replay.stopped-error")));
                    }
                });
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        ReplayPlayer replayPlayer = plugin.getReplayManager().getViewerReplay(player);
        if (replayPlayer != null && replayPlayer.getNpcManager().isFirstPersonEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (plugin.getReplayManager().isWatchingReplay(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        ReplayPlayer replayPlayer = plugin.getReplayManager().getViewerReplay(player);
        if (replayPlayer != null && replayPlayer.getNpcManager().isFirstPersonEnabled()) {
            replayPlayer.getNpcManager().setFirstPerson(false);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.5f);
            plugin.getReplayManager().getControlManager().exitFirstPersonMode(player, replayPlayer);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && plugin.getReplayManager().isWatchingReplay((Player) event.getWhoClicked())) {
            if (event.getClickedInventory() != null && event.getClickedInventory() == event.getWhoClicked().getInventory()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (plugin.getReplayManager().isWatchingReplay(event.getPlayer())) {
            // bilgi gosterme kaldirildi
        }
    }

    private void showTitle(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, 5, 15, 5);
    }
}

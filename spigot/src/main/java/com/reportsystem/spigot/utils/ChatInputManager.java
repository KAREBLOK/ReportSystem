package com.reportsystem.spigot.utils;

import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager implements Listener {
    private final ReportSystemSpigot plugin;
    private final Map<UUID, ChatInputSession> activeSessions = new ConcurrentHashMap<>();

    public ChatInputManager(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void requestInput(Player player, String prompt, Consumer<String> callback) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing session
        if (activeSessions.containsKey(uuid)) {
            activeSessions.get(uuid).cancel();
        }

        // Create new session
        String cancelMsg = plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("reports.chat.input-cancelled"));
        ChatInputSession session = new ChatInputSession(player, prompt, callback, cancelMsg);
        activeSessions.put(uuid, session);

        // Send prompt
        player.sendMessage("");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("§6" + prompt);
        plugin.getMessageManager().sendMessage(player, "reports.chat.cancel-instruction");
        player.sendMessage("§8§m                                                     ");
        player.sendMessage("");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSession session = activeSessions.get(uuid);

        if (session != null) {
            event.setCancelled(true);

            String message = event.getMessage();

            if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
                activeSessions.remove(uuid);
                session.cancel();
            } else {
                activeSessions.remove(uuid);

                // Run callback on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    session.complete(message);
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSession session = activeSessions.remove(uuid);
        if (session != null) {
            session.cancel();
        }
    }

    private static class ChatInputSession {
        private final Player player;
        private final String prompt;
        private final Consumer<String> callback;
        private final String cancelMessage;
        private boolean completed = false;

        public ChatInputSession(Player player, String prompt, Consumer<String> callback, String cancelMessage) {
            this.player = player;
            this.prompt = prompt;
            this.callback = callback;
            this.cancelMessage = cancelMessage;
        }

        public void complete(String input) {
            if (!completed) {
                completed = true;
                callback.accept(input);
            }
        }

        public void cancel() {
            if (!completed) {
                completed = true;
                player.sendMessage(cancelMessage);
            }
        }
    }
}
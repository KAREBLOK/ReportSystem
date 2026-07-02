package com.reportsystem.spigot.listeners;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.common.replay.actions.BookEditAction;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.recording.RecordingSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class BookEditListener implements Listener {

    private final RecordingManager recordingManager;
    private final JavaPlugin plugin;

    public BookEditListener(RecordingManager recordingManager, JavaPlugin plugin) {
        this.recordingManager = recordingManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        RecordingSession session = recordingManager.getSession(player.getUniqueId());

        if (session != null) {
            BookMeta newMeta = event.getNewBookMeta();
            boolean signed = event.isSigning();

            String title = signed && newMeta.hasTitle() ? newMeta.getTitle() : null;
            String author = signed && newMeta.hasAuthor() ? newMeta.getAuthor() : player.getName();
            List<String> pages = newMeta.hasPages() ? newMeta.getPages() : new ArrayList<>();

            BookEditAction action = new BookEditAction(title, author, pages, signed);
            session.addAction(action);

            ReportSystemSpigot.getInstance().debug("[RECORDING-DEBUG] Book edited: " +
                    (signed ? "Signed" : "Draft") + ", " + pages.size() + " pages");
        }
    }
}

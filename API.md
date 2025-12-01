# ReportSystem API Documentation

## Table of Contents
1. [Getting Started](#getting-started)
2. [Maven/Gradle Setup](#mavengradle-setup)
3. [API Usage](#api-usage)
4. [Events](#events)
5. [Examples](#examples)

---

## Getting Started

ReportSystem provides a comprehensive API for developers to integrate with the plugin. You can create reports programmatically, listen to events, and interact with the replay system.

### Requirements
- Java 17 or higher
- Spigot/Paper 1.19+
- ReportSystem plugin installed on your server

---

## Maven/Gradle Setup

### Maven
```xml
<repositories>
    <repository>
        <id>your-repo</id>
        <url>https://repo.example.com/releases</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.reportsystem</groupId>
        <artifactId>reportsystem-common</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle
```gradle
repositories {
    maven { url 'https://repo.example.com/releases' }
}

dependencies {
    compileOnly 'com.reportsystem:reportsystem-common:1.0.0'
}
```

### plugin.yml
```yaml
depend: [ReportSystem]
```

---

## API Usage

### Getting the API Instance

```java
import com.reportsystem.common.ReportSystemAPI;

public class YourPlugin extends JavaPlugin {

    private ReportSystemAPI reportAPI;

    @Override
    public void onEnable() {
        // Get API instance
        reportAPI = ReportSystemAPI.getInstance();

        if (reportAPI == null) {
            getLogger().severe("ReportSystem not found!");
            return;
        }
    }
}
```

### Creating Reports Programmatically

```java
import com.reportsystem.common.ReportSystemAPI;
import org.bukkit.entity.Player;

public void createReport(Player reporter, Player target, String reason) {
    ReportSystemAPI api = ReportSystemAPI.getInstance();

    // Create a report
    api.createReport(
        reporter.getName(),
        reporter.getUniqueId(),
        target.getName(),
        target.getUniqueId(),
        reason,
        "YourServerName"
    );
}
```

### Retrieving Reports

```java
import com.reportsystem.common.models.Report;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public void getReports() {
    ReportSystemAPI api = ReportSystemAPI.getInstance();

    // Get all pending reports
    CompletableFuture<List<Report>> future = api.getReportsByStatus("PENDING");

    future.thenAccept(reports -> {
        for (Report report : reports) {
            System.out.println("Report #" + report.getId());
            System.out.println("Target: " + report.getTarget());
            System.out.println("Reason: " + report.getReason());
        }
    });
}
```

### Managing Report Status

```java
import com.reportsystem.common.models.Report;

public void updateReportStatus(int reportId, String newStatus) {
    ReportSystemAPI api = ReportSystemAPI.getInstance();

    // Update report status
    api.updateReportStatus(reportId, newStatus);
}
```

### Working with Replays

```java
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.replay.ReplayManager;
import org.bukkit.entity.Player;

public void startReplay(Player viewer, int reportId) {
    ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
    ReplayManager replayManager = plugin.getReplayManager();

    // Start replay for a report
    replayManager.startReplay(viewer, reportId);
}

public void stopReplay(Player viewer) {
    ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
    ReplayManager replayManager = plugin.getReplayManager();

    // Stop active replay
    replayManager.stopReplay(viewer);
}
```

---

## Events

ReportSystem provides several events you can listen to:

### ReportCreateEvent

Fired when a new report is created.

```java
import com.reportsystem.spigot.events.ReportCreateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onReportCreate(ReportCreateEvent event) {
        Report report = event.getReport();
        Player reporter = event.getReporter();

        // Do something with the report
        System.out.println(reporter.getName() + " created a report!");

        // Cancel the report creation
        // event.setCancelled(true);
    }
}
```

### ReportCloseEvent

Fired when a report is closed.

```java
import com.reportsystem.spigot.events.ReportCloseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onReportClose(ReportCloseEvent event) {
        Report report = event.getReport();
        Player closer = event.getCloser();

        System.out.println("Report #" + report.getId() + " was closed by " + closer.getName());
    }
}
```

### ReplayStartEvent

Fired when a replay is started.

```java
import com.reportsystem.spigot.events.ReplayStartEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onReplayStart(ReplayStartEvent event) {
        Player viewer = event.getViewer();
        int reportId = event.getReportId();

        System.out.println(viewer.getName() + " started viewing replay #" + reportId);

        // Cancel replay start
        // event.setCancelled(true);
    }
}
```

### ReplayStopEvent

Fired when a replay is stopped.

```java
import com.reportsystem.spigot.events.ReplayStopEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onReplayStop(ReplayStopEvent event) {
        Player viewer = event.getViewer();

        System.out.println(viewer.getName() + " stopped viewing a replay");
    }
}
```

---

## Examples

### Example 1: Auto-Report on Anti-Cheat Detection

```java
import com.reportsystem.common.ReportSystemAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiCheatIntegration extends JavaPlugin {

    @Override
    public void onEnable() {
        // Your anti-cheat detection logic
    }

    public void onCheatDetected(Player cheater, String cheatType) {
        ReportSystemAPI api = ReportSystemAPI.getInstance();

        if (api != null) {
            api.createReport(
                "AntiCheat",
                null, // No UUID for system reporter
                cheater.getName(),
                cheater.getUniqueId(),
                "Auto-reported for " + cheatType,
                "Lobby-1"
            );

            getLogger().info("Auto-reported " + cheater.getName() + " for " + cheatType);
        }
    }
}
```

### Example 2: Custom Report GUI

```java
import com.reportsystem.common.ReportSystemAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomReportGUI {

    public void openReportGUI(Player player, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, "Report " + target.getName());

        // Add report reasons
        addReportOption(gui, 10, Material.IRON_SWORD, "Cheating", target);
        addReportOption(gui, 12, Material.PAPER, "Chat Abuse", target);
        addReportOption(gui, 14, Material.TNT, "Griefing", target);
        addReportOption(gui, 16, Material.BARRIER, "Other", target);

        player.openInventory(gui);
    }

    private void addReportOption(Inventory gui, int slot, Material material,
                                 String reason, Player target) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + reason);
        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    public void handleReportClick(Player reporter, Player target, String reason) {
        ReportSystemAPI api = ReportSystemAPI.getInstance();

        api.createReport(
            reporter.getName(),
            reporter.getUniqueId(),
            target.getName(),
            target.getUniqueId(),
            reason,
            "YourServer"
        );

        reporter.sendMessage("§aReport sent successfully!");
        reporter.closeInventory();
    }
}
```

### Example 3: Report Statistics Dashboard

```java
import com.reportsystem.common.ReportSystemAPI;
import com.reportsystem.common.models.Report;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReportStatsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;
        ReportSystemAPI api = ReportSystemAPI.getInstance();

        if (args.length == 0) {
            showGlobalStats(player, api);
        } else {
            showPlayerStats(player, args[0], api);
        }

        return true;
    }

    private void showGlobalStats(Player player, ReportSystemAPI api) {
        CompletableFuture<List<Report>> pendingFuture = api.getReportsByStatus("PENDING");
        CompletableFuture<List<Report>> reviewingFuture = api.getReportsByStatus("REVIEWING");
        CompletableFuture<List<Report>> closedFuture = api.getReportsByStatus("CLOSED");

        CompletableFuture.allOf(pendingFuture, reviewingFuture, closedFuture)
            .thenRun(() -> {
                player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("§e§lReport Statistics");
                player.sendMessage("");
                player.sendMessage("§7Pending: §e" + pendingFuture.join().size());
                player.sendMessage("§7Reviewing: §6" + reviewingFuture.join().size());
                player.sendMessage("§7Closed: §a" + closedFuture.join().size());
                player.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            });
    }

    private void showPlayerStats(Player player, String targetName, ReportSystemAPI api) {
        // Implementation for showing individual player report history
        player.sendMessage("§eShowing stats for: §f" + targetName);
    }
}
```

---

## Advanced Features

### Working with Recording Manager

```java
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.recording.RecordingManager;
import org.bukkit.entity.Player;

public class RecordingExample {

    public void startRecording(Player player) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
        RecordingManager manager = plugin.getRecordingManager();

        // Start recording a player
        manager.startRecording(player, -1); // -1 = no associated report yet
    }

    public void stopRecording(Player player) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
        RecordingManager manager = plugin.getRecordingManager();

        // Stop recording
        manager.stopRecording(player.getUniqueId());
    }

    public boolean isRecording(Player player) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
        RecordingManager manager = plugin.getRecordingManager();

        return manager.isRecording(player.getUniqueId());
    }
}
```

### Using Punishment System

```java
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.punishment.PunishmentManager;
import org.bukkit.entity.Player;

public class PunishmentExample {

    public void punishPlayer(Player staff, String targetName, String type, String reason) {
        ReportSystemSpigot plugin = ReportSystemSpigot.getInstance();
        PunishmentManager manager = plugin.getPunishmentManager();

        // Execute punishment
        manager.executePunishment(staff, targetName, type, reason, -1);
    }
}
```

---

## Support

For questions and support:
- Discord: https://discord.gg/yourserver
- Website: https://kareblok.tc
- GitHub Issues: https://github.com/yourusername/reportsystem/issues

## License

This API is provided as part of ReportSystem. See LICENSE file for details.

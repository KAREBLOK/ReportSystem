package com.reportsystem.spigot.commands;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.utils.AdvancementNotification;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReportSystemCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;

    public ReportSystemCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reportsystem.admin")) {
            plugin.getMessageManager().sendNoPermission(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reload();
                plugin.getMessageManager().reload();
                String reloadMsg = plugin.getMessageManager().getMessage("commands.reportsystem.reloaded");
                sender.sendMessage(plugin.getMessageManager().colorize(reloadMsg));
                break;

            case "stats":
                sendStats(sender);
                break;

            case "info":
                sendInfo(sender);
                break;

            case "lang":
                sender.sendMessage("§6Current language: §f" + plugin.getConfigManager().getLanguage());
                sender.sendMessage("§6Loaded messages file: §fmessages_" + plugin.getConfigManager().getLanguage() + ".yml");
                sender.sendMessage("§6Test message: §f" + plugin.getMessageManager().getMessage("commands.reportsystem.help.header"));
                break;

            case "debug":
                if (args.length > 1) {
                    boolean debug = Boolean.parseBoolean(args[1]);
                    plugin.getConfig().set("general.debug", debug);
                    plugin.saveConfig();
                    String debugMsg = plugin.getMessageManager().getMessage(debug ? "commands.reportsystem.debug.enabled" : "commands.reportsystem.debug.disabled");
                    sender.sendMessage(plugin.getMessageManager().colorize(debugMsg));
                } else {
                    String usageMsg = plugin.getMessageManager().getMessage("commands.reportsystem.debug.usage");
                    sender.sendMessage(plugin.getMessageManager().colorize(usageMsg));
                }
                break;

            case "testnotif":
                if (!(sender instanceof Player)) {
                    plugin.getMessageManager().sendMessage(sender, "general.only-players");
                    return true;
                }
                testNotification((Player) sender);
                break;

            case "checkadv":
                checkAdvancement(sender);
                break;

            default:
                sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.header")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.reload")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.stats")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.info")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.debug")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.testnotif")));
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.help.checkadv")));
    }

    private void sendStats(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.stats.header")));

        String recordingsMsg = plugin.getMessageManager().getMessage("commands.reportsystem.stats.active-recordings")
                .replace("%count%", String.valueOf(plugin.getRecordingManager().getActiveRecordingCount()));
        sender.sendMessage(plugin.getMessageManager().colorize(recordingsMsg));

        String replaysMsg = plugin.getMessageManager().getMessage("commands.reportsystem.stats.active-replays")
                .replace("%count%", String.valueOf(plugin.getReplayManager().getActiveReplays().size()));
        sender.sendMessage(plugin.getMessageManager().colorize(replaysMsg));

        // Database istatistikleri
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int totalReports = plugin.getReportService().getTotalReports();
                int pendingReports = plugin.getReportService().getPendingReportsCount();

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String totalMsg = plugin.getMessageManager().getMessage("commands.reportsystem.stats.total-reports")
                            .replace("%count%", String.valueOf(totalReports));
                    sender.sendMessage(plugin.getMessageManager().colorize(totalMsg));

                    String pendingMsg = plugin.getMessageManager().getMessage("commands.reportsystem.stats.pending-reports")
                            .replace("%count%", String.valueOf(pendingReports));
                    sender.sendMessage(plugin.getMessageManager().colorize(pendingMsg));
                });
            } catch (Exception e) {
                String errorMsg = plugin.getMessageManager().getMessage("commands.reportsystem.stats.database-error");
                sender.sendMessage(plugin.getMessageManager().colorize(errorMsg));
            }
        });
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().colorize(plugin.getMessageManager().getMessage("commands.reportsystem.info.header")));

        String versionMsg = plugin.getMessageManager().getMessage("commands.reportsystem.info.version")
                .replace("%version%", plugin.getDescription().getVersion());
        sender.sendMessage(plugin.getMessageManager().colorize(versionMsg));

        String developerMsg = plugin.getMessageManager().getMessage("commands.reportsystem.info.developer")
                .replace("%developer%", plugin.getDescription().getAuthors().get(0));
        sender.sendMessage(plugin.getMessageManager().colorize(developerMsg));

        String modeKey = plugin.getConfigManager().isBungeeCordEnabled() ? "commands.reportsystem.info.bungeecord" : "commands.reportsystem.info.standalone";
        String modeMsg = plugin.getMessageManager().getMessage("commands.reportsystem.info.mode")
                .replace("%mode%", plugin.getMessageManager().getMessage(modeKey));
        sender.sendMessage(plugin.getMessageManager().colorize(modeMsg));

        String databaseMsg = plugin.getMessageManager().getMessage("commands.reportsystem.info.database")
                .replace("%database%", plugin.getConfigManager().getDatabaseType());
        sender.sendMessage(plugin.getMessageManager().colorize(databaseMsg));

        String replayStatusKey = plugin.getConfigManager().isReplayEnabled() ? "commands.reportsystem.info.active" : "commands.reportsystem.info.inactive";
        String replayMsg = plugin.getMessageManager().getMessage("commands.reportsystem.info.replay")
                .replace("%replay%", plugin.getMessageManager().getMessage(replayStatusKey));
        sender.sendMessage(plugin.getMessageManager().colorize(replayMsg));
    }

    private void testNotification(Player player) {
        String sendingMsg = plugin.getMessageManager().getMessage("commands.reportsystem.testnotif.sending");
        player.sendMessage(plugin.getMessageManager().colorize(sendingMsg));

        AdvancementNotification notif = plugin.getAdvancementNotification();
        if (notif != null) {
            String title = plugin.getMessageManager().getMessage("commands.reportsystem.testnotif.title");
            String message = plugin.getMessageManager().getMessage("commands.reportsystem.testnotif.message");

            notif.sendToast(
                player,
                Material.WRITABLE_BOOK,
                title,
                message,
                AdvancementNotification.FrameType.TASK
            );

            String sentMsg = plugin.getMessageManager().getMessage("commands.reportsystem.testnotif.sent");
            player.sendMessage(plugin.getMessageManager().colorize(sentMsg));
        } else {
            String failedMsg = plugin.getMessageManager().getMessage("commands.reportsystem.testnotif.failed");
            player.sendMessage(plugin.getMessageManager().colorize(failedMsg));
        }
    }

    private void checkAdvancement(CommandSender sender) {
        String headerMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.header");
        sender.sendMessage(plugin.getMessageManager().colorize(headerMsg));

        // Datapack kontrolü
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.advancement.Advancement advancement = Bukkit.getAdvancement(
                new org.bukkit.NamespacedKey("reportsystem", "notification")
            );

            if (advancement != null) {
                String loadedMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.loaded");
                sender.sendMessage(plugin.getMessageManager().colorize(loadedMsg));

                String workingMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.working");
                sender.sendMessage(plugin.getMessageManager().colorize(workingMsg));
            } else {
                String notFoundMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.not-found");
                sender.sendMessage(plugin.getMessageManager().colorize(notFoundMsg));

                String datapackLoadedMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.datapack-loaded");
                sender.sendMessage(plugin.getMessageManager().colorize(datapackLoadedMsg));

                String needRestartMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.need-restart");
                sender.sendMessage(plugin.getMessageManager().colorize(needRestartMsg));

                String fallbackMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.fallback");
                sender.sendMessage(plugin.getMessageManager().colorize(fallbackMsg));
            }

            // Datapack klasörünü kontrol et
            java.io.File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
            java.io.File datapackDir = new java.io.File(worldFolder, "datapacks/reportsystem_notifications");

            if (datapackDir.exists()) {
                String existsMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.datapack-exists");
                sender.sendMessage(plugin.getMessageManager().colorize(existsMsg));

                java.io.File notifJson = new java.io.File(datapackDir, "data/reportsystem/advancements/notification.json");
                if (notifJson.exists()) {
                    String jsonExistsMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.json-exists");
                    sender.sendMessage(plugin.getMessageManager().colorize(jsonExistsMsg));
                } else {
                    String jsonMissingMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.json-missing");
                    sender.sendMessage(plugin.getMessageManager().colorize(jsonMissingMsg));
                }
            } else {
                String missingMsg = plugin.getMessageManager().getMessage("commands.reportsystem.checkadv.datapack-missing");
                sender.sendMessage(plugin.getMessageManager().colorize(missingMsg));
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "stats", "info", "lang", "debug", "testnotif", "checkadv");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Arrays.asList("true", "false");
        }

        return new ArrayList<>();
    }
}
package com.reportsystem.spigot.overwatch.commands;

import com.reportsystem.common.models.overwatch.OverwatchStats;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.gui.OverwatchLeaderboardGUI;
import com.reportsystem.spigot.overwatch.gui.OverwatchMenuGUI;
import com.reportsystem.spigot.overwatch.gui.OverwatchStatsGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Overwatch Command Handler
 * /overwatch - Open main menu
 * /overwatch stats [player] - View statistics
 * /overwatch leaderboard - View leaderboard
 * /overwatch npc create - Create NPC (admin)
 * /overwatch npc delete [id] - Delete NPC (admin)
 * /overwatch npc list - List NPCs (admin)
 * /overwatch addqueue <reportId> [priority] - Add report to queue (admin)
 */
public class OverwatchCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;

    public OverwatchCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.player-only");
            sender.sendMessage(plugin.getMessageManager().colorize(msg));
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("reportsystem.overwatch")) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.no-permission");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return true;
        }

        // No args - Open main menu
        if (args.length == 0) {
            new OverwatchMenuGUI(plugin, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stats":
                handleStats(player, args);
                break;

            case "leaderboard":
            case "lb":
            case "top":
                new OverwatchLeaderboardGUI(plugin, player).open();
                break;

            case "npc":
                handleNPC(player, args);
                break;

            case "addqueue":
                handleAddQueue(player, args);
                break;

            case "help":
                showHelp(player);
                break;

            default:
                String msg = plugin.getMessageManager().getMessage("overwatch.commands.unknown-command");
                player.sendMessage(plugin.getMessageManager().colorize(msg));
                break;
        }

        return true;
    }

    private void handleStats(Player player, String[] args) {
        if (args.length == 1) {
            // Show own stats
            new OverwatchStatsGUI(plugin, player, player.getUniqueId()).open();
        } else {
            // Show other player's stats
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);

            if (target != null) {
                new OverwatchStatsGUI(plugin, player, target.getUniqueId()).open();
            } else {
                // Try to find UUID from database
                String msg = plugin.getMessageManager().getMessage("overwatch.commands.player-not-found")
                        .replace("%player%", targetName);
                player.sendMessage(plugin.getMessageManager().colorize(msg));
            }
        }
    }

    private void handleNPC(Player player, String[] args) {
        if (!player.hasPermission("reportsystem.overwatch.admin")) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.no-permission");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return;
        }

        if (args.length < 2) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.npc.usage");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return;
        }

        String npcAction = args[1].toLowerCase();

        switch (npcAction) {
            case "create":
                handleNPCCreate(player, args);
                break;

            case "delete":
                handleNPCDelete(player, args);
                break;

            case "list":
                handleNPCList(player);
                break;

            default:
                String msg = plugin.getMessageManager().getMessage("overwatch.commands.npc.unknown");
                player.sendMessage(plugin.getMessageManager().colorize(msg));
                break;
        }
    }

    private void handleNPCCreate(Player player, String[] args) {
        Location loc = player.getLocation();

        // Get custom name if provided (args: /overwatch npc create [customName])
        String customName = null;
        if (args.length >= 3) {
            // Join all remaining args as the custom name (supports spaces)
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) nameBuilder.append(" ");
                nameBuilder.append(args[i]);
            }
            customName = nameBuilder.toString();
        }

        String npcId = plugin.getNPCManager().createNPC(player, loc, customName);

        if (npcId != null) {
            String successMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.created");
            player.sendMessage(plugin.getMessageManager().colorize(successMsg));

            String idMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.created-id")
                    .replace("%id%", npcId);
            player.sendMessage(plugin.getMessageManager().colorize(idMsg));

            String locMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.created-location")
                    .replace("%world%", loc.getWorld().getName())
                    .replace("%x%", String.format("%.1f", loc.getX()))
                    .replace("%y%", String.format("%.1f", loc.getY()))
                    .replace("%z%", String.format("%.1f", loc.getZ()));
            player.sendMessage(plugin.getMessageManager().colorize(locMsg));
        } else {
            String failMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.create-failed");
            player.sendMessage(plugin.getMessageManager().colorize(failMsg));
        }
    }

    private void handleNPCDelete(Player player, String[] args) {
        if (args.length < 3) {
            String usageMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.delete-usage");
            player.sendMessage(plugin.getMessageManager().colorize(usageMsg));

            String helpMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.delete-help");
            player.sendMessage(plugin.getMessageManager().colorize(helpMsg));
            return;
        }

        String nameOrId = args[2];

        // Find NPC by display name or ID
        String npcId = plugin.getNPCManager().findNPCId(nameOrId);

        if (npcId == null) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.npc.not-found")
                    .replace("%id%", nameOrId);
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return;
        }

        boolean success = plugin.getNPCManager().deleteNPC(npcId);

        if (success) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.npc.deleted")
                    .replace("%id%", nameOrId);
            player.sendMessage(plugin.getMessageManager().colorize(msg));
        } else {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.npc.delete-failed");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
        }
    }

    private void handleNPCList(Player player) {
        var npcs = plugin.getNPCManager().getActiveNPCs();

        if (npcs.isEmpty()) {
            String emptyMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-empty");
            player.sendMessage(plugin.getMessageManager().colorize(emptyMsg));

            String helpMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-help");
            player.sendMessage(plugin.getMessageManager().colorize(helpMsg));
            return;
        }

        String header = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-header");
        player.sendMessage(plugin.getMessageManager().colorize(header));

        String title = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-title");
        player.sendMessage(plugin.getMessageManager().colorize(title));

        player.sendMessage(plugin.getMessageManager().colorize(header));

        int count = 1;
        for (var entry : npcs.entrySet()) {
            String npcId = entry.getKey();
            var npcData = entry.getValue();
            Location loc = npcData.getLocation();

            player.sendMessage("");

            // Display name varsa göster, yoksa ID'nin ilk 8 karakteri
            String displayText = npcData.getDisplayName() != null && !npcData.getDisplayName().isEmpty()
                    ? npcData.getDisplayName()
                    : npcId.substring(0, 8) + "...";

            String numberMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-number")
                    .replace("%num%", String.valueOf(count))
                    .replace("%id%", displayText);
            player.sendMessage(plugin.getMessageManager().colorize(numberMsg));

            String worldMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-world")
                    .replace("%world%", loc.getWorld().getName());
            player.sendMessage(plugin.getMessageManager().colorize(worldMsg));

            String locMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-location")
                    .replace("%x%", String.format("%.1f", loc.getX()))
                    .replace("%y%", String.format("%.1f", loc.getY()))
                    .replace("%z%", String.format("%.1f", loc.getZ()));
            player.sendMessage(plugin.getMessageManager().colorize(locMsg));

            String deleteMsg = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-delete")
                    .replace("%id%", npcId);
            player.sendMessage(plugin.getMessageManager().colorize(deleteMsg));

            count++;
        }

        String footer = plugin.getMessageManager().getMessage("overwatch.commands.npc.list-footer");
        player.sendMessage(plugin.getMessageManager().colorize(footer));
    }

    private void handleAddQueue(Player player, String[] args) {
        if (!player.hasPermission("reportsystem.overwatch.admin")) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.no-permission");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return;
        }

        if (args.length < 2) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.addqueue-usage");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
            return;
        }

        try {
            int reportId = Integer.parseInt(args[1]);
            int priority = args.length >= 3 ? Integer.parseInt(args[2]) : 0;

            plugin.getOverwatchManager().addReportToQueue(reportId, priority);

            String successMsg = plugin.getMessageManager().getMessage("overwatch.commands.addqueue-success")
                    .replace("%id%", String.valueOf(reportId));
            player.sendMessage(plugin.getMessageManager().colorize(successMsg));

            String priorityMsg = plugin.getMessageManager().getMessage("overwatch.commands.addqueue-priority")
                    .replace("%priority%", String.valueOf(priority));
            player.sendMessage(plugin.getMessageManager().colorize(priorityMsg));

        } catch (NumberFormatException e) {
            String msg = plugin.getMessageManager().getMessage("overwatch.commands.invalid-number");
            player.sendMessage(plugin.getMessageManager().colorize(msg));
        }
    }

    private void showHelp(Player player) {
        String header = plugin.getMessageManager().getMessage("overwatch.commands.help.header");
        player.sendMessage(plugin.getMessageManager().colorize(header));

        String title = plugin.getMessageManager().getMessage("overwatch.commands.help.title");
        player.sendMessage(plugin.getMessageManager().colorize(title));

        player.sendMessage(plugin.getMessageManager().colorize(header));
        player.sendMessage("");

        String cmd1 = plugin.getMessageManager().getMessage("overwatch.commands.help.cmd-menu");
        player.sendMessage(plugin.getMessageManager().colorize(cmd1));

        String cmd2 = plugin.getMessageManager().getMessage("overwatch.commands.help.cmd-stats");
        player.sendMessage(plugin.getMessageManager().colorize(cmd2));

        String cmd3 = plugin.getMessageManager().getMessage("overwatch.commands.help.cmd-leaderboard");
        player.sendMessage(plugin.getMessageManager().colorize(cmd3));
        player.sendMessage("");

        if (player.hasPermission("reportsystem.overwatch.admin")) {
            String adminTitle = plugin.getMessageManager().getMessage("overwatch.commands.help.admin-title");
            player.sendMessage(plugin.getMessageManager().colorize(adminTitle));

            String adminCmd1 = plugin.getMessageManager().getMessage("overwatch.commands.help.admin-npc-create");
            player.sendMessage(plugin.getMessageManager().colorize(adminCmd1));

            String adminCmd2 = plugin.getMessageManager().getMessage("overwatch.commands.help.admin-npc-delete");
            player.sendMessage(plugin.getMessageManager().colorize(adminCmd2));

            String adminCmd3 = plugin.getMessageManager().getMessage("overwatch.commands.help.admin-npc-list");
            player.sendMessage(plugin.getMessageManager().colorize(adminCmd3));

            String adminCmd4 = plugin.getMessageManager().getMessage("overwatch.commands.help.admin-addqueue");
            player.sendMessage(plugin.getMessageManager().colorize(adminCmd4));
            player.sendMessage("");
        }

        String footer = plugin.getMessageManager().getMessage("overwatch.commands.help.footer");
        player.sendMessage(plugin.getMessageManager().colorize(footer));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("stats", "leaderboard", "help"));

            if (sender.hasPermission("reportsystem.overwatch.admin")) {
                completions.addAll(Arrays.asList("npc", "addqueue"));
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("npc")) {
                completions.addAll(Arrays.asList("create", "delete", "list"));
            } else if (args[0].equalsIgnoreCase("stats")) {
                // Add online player names
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("delete")) {
                // Add NPC IDs
                for (String npcId : plugin.getNPCManager().getActiveNPCs().keySet()) {
                    completions.add(npcId);
                }
            }
        }

        // Filter based on what user has typed
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));

        return completions;
    }
}

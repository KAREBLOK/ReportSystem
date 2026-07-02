package com.reportsystem.spigot.overwatch.commands;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.overwatch.NPCManager;
import com.reportsystem.spigot.overwatch.gui.OverwatchLeaderboardGUI;
import com.reportsystem.spigot.overwatch.gui.OverwatchMenuGUI;
import com.reportsystem.spigot.overwatch.gui.OverwatchStatsGUI;
import com.reportsystem.spigot.utils.SkinUtils;
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

public class OverwatchCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;

    public OverwatchCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(msg("overwatch.commands.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("reportsystem.overwatch")) {
            player.sendMessage(msg("overwatch.commands.no-permission"));
            return true;
        }

        if (args.length == 0) {
            new OverwatchMenuGUI(plugin, player).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stats":
                handleStats(player, args);
                break;
            case "leaderboard": case "lb": case "top":
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
                player.sendMessage(msg("overwatch.commands.unknown-command"));
                break;
        }

        return true;
    }

    private void handleStats(Player player, String[] args) {
        if (args.length == 1) {
            new OverwatchStatsGUI(plugin, player, player.getUniqueId()).open();
        } else {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                new OverwatchStatsGUI(plugin, player, target.getUniqueId()).open();
            } else {
                player.sendMessage(msg("overwatch.commands.player-not-found", "%player%", args[1]));
            }
        }
    }

    private void handleNPC(Player player, String[] args) {
        if (!player.hasPermission("reportsystem.overwatch.admin")) {
            player.sendMessage(msg("overwatch.commands.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(msg("overwatch.commands.npc.usage"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create":
                handleNPCCreate(player, args);
                break;
            case "delete":
                handleNPCDelete(player, args);
                break;
            case "list":
                handleNPCList(player);
                break;
            case "skin":
                handleNPCSkin(player, args);
                break;
            case "look":
                handleNPCLook(player, args);
                break;
            case "move":
                handleNPCMove(player, args);
                break;
            case "name":
                handleNPCName(player, args);
                break;
            case "select":
                handleNPCSelect(player, args);
                break;
            default:
                player.sendMessage(msg("overwatch.commands.npc.unknown"));
                break;
        }
    }

    // ============= NPC Subcommands =============

    private void handleNPCCreate(Player player, String[] args) {
        Location loc = player.getLocation();
        String customName = args.length >= 3 ? joinArgs(args, 2) : null;

        String npcId = plugin.getNPCManager().createNPC(player, loc, customName);

        if (npcId != null) {
            player.sendMessage(msg("overwatch.commands.npc.created"));
            player.sendMessage(msg("overwatch.commands.npc.created-id", "%id%", npcId));
            player.sendMessage(msg("overwatch.commands.npc.created-location",
                    "%world%", loc.getWorld().getName(),
                    "%x%", String.format("%.1f", loc.getX()),
                    "%y%", String.format("%.1f", loc.getY()),
                    "%z%", String.format("%.1f", loc.getZ())));
        } else {
            player.sendMessage(msg("overwatch.commands.npc.create-failed"));
        }
    }

    private void handleNPCDelete(Player player, String[] args) {
        String npcId = resolveNPC(player, args, 2);
        if (npcId == null) return;

        if (plugin.getNPCManager().deleteNPC(npcId)) {
            player.sendMessage(msg("overwatch.commands.npc.deleted", "%id%", args.length >= 3 ? args[2] : npcId));
        } else {
            player.sendMessage(msg("overwatch.commands.npc.delete-failed"));
        }
    }

    private void handleNPCSkin(Player player, String[] args) {
        // /overwatch npc skin <npc> <playerName>
        String npcId = resolveNPC(player, args, 2);
        if (npcId == null) return;

        // Determine skin player name argument position
        int skinArgIndex = args.length >= 4 ? 3 : -1;
        if (skinArgIndex == -1) {
            // Maybe selected NPC, so skin name is at index 2
            if (plugin.getNPCManager().getSelectedNPC(player.getUniqueId()) != null && args.length >= 3) {
                skinArgIndex = 2;
            } else {
                player.sendMessage(msg("overwatch.commands.npc.skin-usage"));
                return;
            }
        }

        String skinPlayerName = args[skinArgIndex];
        player.sendMessage(msg("overwatch.commands.npc.skin-fetching", "%player%", skinPlayerName));

        final String finalNpcId = npcId;
        SkinUtils.fetchSkin(skinPlayerName, plugin, (texture, signature) -> {
            if (texture == null) {
                player.sendMessage(msg("overwatch.commands.npc.skin-failed", "%player%", skinPlayerName));
                return;
            }
            plugin.getNPCManager().setSkin(finalNpcId, texture, signature);
            player.sendMessage(msg("overwatch.commands.npc.skin-success", "%player%", skinPlayerName));
        });
    }

    private void handleNPCLook(Player player, String[] args) {
        String npcId = resolveNPC(player, args, 2);
        if (npcId == null) return;

        plugin.getNPCManager().lookNPC(npcId, player.getEyeLocation());
        player.sendMessage(msg("overwatch.commands.npc.look-success"));
    }

    private void handleNPCMove(Player player, String[] args) {
        String npcId = resolveNPC(player, args, 2);
        if (npcId == null) return;

        plugin.getNPCManager().moveNPC(npcId, player.getLocation());
        player.sendMessage(msg("overwatch.commands.npc.move-success"));
    }

    private void handleNPCName(Player player, String[] args) {
        // /overwatch npc name <npc> <newName...> OR /overwatch npc name <newName...> (with selection)
        String npcId = resolveNPC(player, args, 2);
        if (npcId == null) return;

        int nameStartIndex;
        if (args.length >= 4 && plugin.getNPCManager().findNPCId(args[2]) != null) {
            nameStartIndex = 3;
        } else if (plugin.getNPCManager().getSelectedNPC(player.getUniqueId()) != null) {
            nameStartIndex = 2;
        } else {
            player.sendMessage(msg("overwatch.commands.npc.name-usage"));
            return;
        }

        if (nameStartIndex >= args.length) {
            player.sendMessage(msg("overwatch.commands.npc.name-usage"));
            return;
        }

        String newName = joinArgs(args, nameStartIndex);
        plugin.getNPCManager().renameNPC(npcId, newName);
        player.sendMessage(msg("overwatch.commands.npc.name-success", "%name%", newName));
    }

    private void handleNPCSelect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(msg("overwatch.commands.npc.select-usage"));
            return;
        }

        String npcId = plugin.getNPCManager().findNPCId(args[2]);
        if (npcId == null) {
            player.sendMessage(msg("overwatch.commands.npc.not-found", "%id%", args[2]));
            return;
        }

        plugin.getNPCManager().selectNPC(player.getUniqueId(), npcId);
        NPCManager.NPCData data = plugin.getNPCManager().getActiveNPCs().get(npcId);
        String name = data != null && data.getDisplayName() != null ? data.getDisplayName() : npcId.substring(0, 8);
        player.sendMessage(msg("overwatch.commands.npc.select-success", "%name%", name));
    }

    private void handleNPCList(Player player) {
        var npcs = plugin.getNPCManager().getActiveNPCs();

        if (npcs.isEmpty()) {
            player.sendMessage(msg("overwatch.commands.npc.list-empty"));
            player.sendMessage(msg("overwatch.commands.npc.list-help"));
            return;
        }

        player.sendMessage(msg("overwatch.commands.npc.list-header"));
        player.sendMessage(msg("overwatch.commands.npc.list-title"));
        player.sendMessage(msg("overwatch.commands.npc.list-header"));

        int count = 1;
        for (var entry : npcs.entrySet()) {
            String npcId = entry.getKey();
            var npcData = entry.getValue();
            Location loc = npcData.getLocation();

            player.sendMessage("");

            String displayText = npcData.getDisplayName() != null && !npcData.getDisplayName().isEmpty()
                    ? npcData.getDisplayName() : npcId.substring(0, 8) + "...";

            player.sendMessage(msg("overwatch.commands.npc.list-number", "%num%", String.valueOf(count), "%id%", displayText));
            player.sendMessage(msg("overwatch.commands.npc.list-world", "%world%", loc.getWorld().getName()));
            player.sendMessage(msg("overwatch.commands.npc.list-location",
                    "%x%", String.format("%.1f", loc.getX()),
                    "%y%", String.format("%.1f", loc.getY()),
                    "%z%", String.format("%.1f", loc.getZ())));

            String deleteName = npcData.getDisplayName() != null && !npcData.getDisplayName().isEmpty()
                    ? npcData.getDisplayName() : npcId;
            player.sendMessage(msg("overwatch.commands.npc.list-delete", "%id%", deleteName));

            count++;
        }

        player.sendMessage(msg("overwatch.commands.npc.list-footer"));
    }

    private void handleAddQueue(Player player, String[] args) {
        if (!player.hasPermission("reportsystem.overwatch.admin")) {
            player.sendMessage(msg("overwatch.commands.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(msg("overwatch.commands.addqueue-usage"));
            return;
        }

        try {
            int reportId = Integer.parseInt(args[1]);
            int priority = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
            plugin.getOverwatchManager().addReportToQueue(reportId, priority);
            player.sendMessage(msg("overwatch.commands.addqueue-success", "%id%", String.valueOf(reportId)));
            player.sendMessage(msg("overwatch.commands.addqueue-priority", "%priority%", String.valueOf(priority)));
        } catch (NumberFormatException e) {
            player.sendMessage(msg("overwatch.commands.invalid-number"));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(msg("overwatch.commands.help.header"));
        player.sendMessage(msg("overwatch.commands.help.title"));
        player.sendMessage(msg("overwatch.commands.help.header"));
        player.sendMessage("");
        player.sendMessage(msg("overwatch.commands.help.cmd-menu"));
        player.sendMessage(msg("overwatch.commands.help.cmd-stats"));
        player.sendMessage(msg("overwatch.commands.help.cmd-leaderboard"));
        player.sendMessage("");

        if (player.hasPermission("reportsystem.overwatch.admin")) {
            player.sendMessage(msg("overwatch.commands.help.admin-title"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-create"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-delete"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-list"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-skin"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-look"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-move"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-name"));
            player.sendMessage(msg("overwatch.commands.help.admin-npc-select"));
            player.sendMessage(msg("overwatch.commands.help.admin-addqueue"));
            player.sendMessage("");
        }

        player.sendMessage(msg("overwatch.commands.help.footer"));
    }

    // ============= Helpers =============

    /**
     * Resolve NPC ID from args or selected NPC. Sends error message if not found.
     */
    private String resolveNPC(Player player, String[] args, int argIndex) {
        String npcId = null;

        // Try from argument
        if (args.length > argIndex) {
            npcId = plugin.getNPCManager().findNPCId(args[argIndex]);
        }

        // Try from selection
        if (npcId == null) {
            npcId = plugin.getNPCManager().getSelectedNPC(player.getUniqueId());
        }

        if (npcId == null) {
            if (args.length > argIndex) {
                player.sendMessage(msg("overwatch.commands.npc.not-found", "%id%", args[argIndex]));
            } else {
                player.sendMessage(msg("overwatch.commands.npc.select-none"));
            }
        }

        return npcId;
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private String msg(String key, String... replacements) {
        String message = plugin.getMessageManager().getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return plugin.getMessageManager().colorize(message);
    }

    // ============= Tab Complete =============

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
                completions.addAll(Arrays.asList("create", "delete", "list", "skin", "look", "move", "name", "select"));
            } else if (args[0].equalsIgnoreCase("stats")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("npc")) {
                String sub = args[1].toLowerCase();
                if (Arrays.asList("delete", "skin", "look", "move", "name", "select").contains(sub)) {
                    // Suggest NPC display names
                    for (var entry : plugin.getNPCManager().getActiveNPCs().values()) {
                        String name = entry.getDisplayName();
                        if (name != null && !name.isEmpty()) {
                            completions.add(name);
                        }
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("skin")) {
                // Suggest online player names for skin
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        return completions;
    }
}

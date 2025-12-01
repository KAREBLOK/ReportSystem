package com.reportsystem.spigot.commands;

import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.ReportListGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ReportsCommand implements CommandExecutor, TabCompleter {

    private final ReportSystemSpigot plugin;

    public ReportsCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "general.only-players");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("reportsystem.view")) {
            plugin.getMessageManager().sendNoPermission(player);
            return true;
        }

        // Rapor listesi GUI'sini aç
        new ReportListGUI(plugin, player, 1).open();

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return new ArrayList<>();
    }
}
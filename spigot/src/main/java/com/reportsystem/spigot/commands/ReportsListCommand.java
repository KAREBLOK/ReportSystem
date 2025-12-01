package com.reportsystem.spigot.commands;

import com.reportsystem.common.service.ReportService;
import com.reportsystem.spigot.ReportSystemSpigot;
import com.reportsystem.spigot.gui.ReportListGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReportsListCommand implements CommandExecutor {

    private final ReportSystemSpigot plugin;
    private final ReportService reportService;

    public ReportsListCommand(ReportSystemSpigot plugin) {
        this.plugin = plugin;
        this.reportService = plugin.getReportService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reportsystem.view")) {
            plugin.getMessageManager().sendNoPermission(sender);
            return true;
        }

        // If player, open GUI
        if (sender instanceof Player) {
            Player player = (Player) sender;

            int page = 1;
            if (args.length > 0) {
                try {
                    page = Integer.parseInt(args[0]);
                    if (page < 1) page = 1;
                } catch (NumberFormatException ignored) {
                    // Invalid page number, use 1
                }
            }

            // Open config-based GUI
            new ReportListGUI((ReportSystemSpigot) plugin, player, page).open();
            return true;
        }

        // For console
        plugin.getMessageManager().sendMessage(sender, "general.only-players");
        return true;
    }
}
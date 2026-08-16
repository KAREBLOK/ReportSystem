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

        // GÜVENLİK: Bu GUI TÜM raporları gösterir ve ceza akışına açılır → yetkili yüzeyi.
        // 'reportsystem.view' (varsayılan true, "kendi raporlarını gör") YETERSİZDİ; herkes
        // listeye girip ceza verebiliyordu. Artık 'view.all' (varsayılan op) gerekiyor.
        if (!player.hasPermission("reportsystem.view.all")) {
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
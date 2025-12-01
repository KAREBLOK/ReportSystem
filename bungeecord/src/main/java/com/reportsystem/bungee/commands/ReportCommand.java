package com.reportsystem.bungee.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.bungee.ReportSystemBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.Collections;
import java.util.stream.Collectors;

public class ReportCommand extends Command implements TabExecutor {

    private final ReportSystemBungee plugin;

    public ReportCommand(ReportSystemBungee plugin) {
        super("report", "reportsystem.report", "raporla");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent("Bu komutu sadece oyuncular kullanabilir."));
            return;
        }

        ProxiedPlayer reporter = (ProxiedPlayer) sender;

        if (args.length < 1) {
            reporter.sendMessage(new TextComponent(ChatColor.RED + "Kullanım: /report <oyuncu>"));
            return;
        }

        String targetName = args[0];
        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);

        // Tam isim eşleşmesi kontrolü (partial matching engelleme)
        if (target == null || !target.getName().equalsIgnoreCase(targetName)) {
            reporter.sendMessage(new TextComponent(ChatColor.RED + "Oyuncu '" + targetName + "' bulunamadı!"));
            return;
        }

        if (reporter.equals(target)) {
            reporter.sendMessage(new TextComponent(ChatColor.RED + "Kendinizi rapor edemezsiniz!"));
            return;
        }

        // Spigot sunucusuna mesaj gönder
        sendReportGUIRequest(reporter, target.getName());
    }

    private void sendReportGUIRequest(ProxiedPlayer reporter, String targetName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("OpenReportGUI");
        out.writeUTF(reporter.getName());
        out.writeUTF(targetName);

        // Oyuncunun bulunduğu sunucuya gönder
        reporter.getServer().sendData("reportsystem:channel", out.toByteArray());

        // Debug mesajı
        reporter.sendMessage(new TextComponent(ChatColor.GREEN + "Rapor menüsü açılıyor..."));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return ProxyServer.getInstance().getPlayers().stream()
                    .map(ProxiedPlayer::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .filter(name -> !name.equals(sender.getName()))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
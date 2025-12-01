package com.reportsystem.spigot.gui;

import com.reportsystem.common.models.Report;
import com.reportsystem.spigot.ReportSystemSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ReportListGUI extends GUI {
    private int currentPage;
    private int maxPage = 1;
    private List<Report> reports;
    private final int itemsPerPage = 28;
    private final GUIConfig guiConfig;

    private SortType currentSort = SortType.NEWEST_FIRST;

    public enum SortType { NEWEST_FIRST, OLDEST_FIRST, PRIORITY, PLAYER_NAME }

    public ReportListGUI(ReportSystemSpigot plugin, Player player, int page) {
        super(plugin, player);
        this.currentPage = page;
        this.guiConfig = new GUIConfig(plugin, "report-list");
    }

    @Override
    public void open() {
        loadAndBuild();
    }

    private void loadAndBuild() {
        CompletableFuture.supplyAsync(() -> {
            try {
                List<Report> allReports = plugin.getReportService().getAllReports();
                return processReports(allReports);
            } catch (Exception e) {
                plugin.getLogger().severe("Raporlar yüklenirken hata: " + e.getMessage());
                return null;
            }
        }).thenAccept(processedReports -> {
            if (processedReports != null) {
                this.reports = processedReports;
                this.maxPage = (int) Math.ceil((double) this.reports.size() / itemsPerPage);
                if (this.maxPage == 0) this.maxPage = 1;

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    build();
                    player.openInventory(inventory);
                });
            } else {
                String errorMsg = plugin.getMessageManager().getMessage("gui.report-list.loading-error");
                player.sendMessage(plugin.getMessageManager().colorize(errorMsg));
            }
        });
    }

    @Override
    public void build() {
        // Get title from messages
        String title = plugin.getMessageManager().getMessage("gui.report-list.title")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPage));
        title = plugin.getMessageManager().colorize(title);

        int size = guiConfig.getSize();
        inventory = Bukkit.createInventory(this, size, title);

        // Background/Border
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // Report items
        if (reports != null && !reports.isEmpty()) {
            int startIndex = (currentPage - 1) * itemsPerPage;
            for (int i = 0; i < itemsPerPage; i++) {
                int reportIndex = startIndex + i;
                if (reportIndex < reports.size()) {
                    int slot = getReportSlot(i);
                    inventory.setItem(slot, createReportItem(reports.get(reportIndex)));
                }
            }
        } else {
            // Empty list - create from messages
            inventory.setItem(guiConfig.getItemSlot("empty-list"), createEmptyListItem());
        }

        // Navigation - create from messages
        if (currentPage > 1) {
            inventory.setItem(guiConfig.getItemSlot("navigation.previous"), createNavigationItem("previous", currentPage - 1));
        }

        inventory.setItem(guiConfig.getItemSlot("navigation.page-info"), createPageInfoItem());

        if (currentPage < maxPage) {
            inventory.setItem(guiConfig.getItemSlot("navigation.next"), createNavigationItem("next", currentPage + 1));
        }

        // Actions - create from messages (refresh only for now)
        inventory.setItem(guiConfig.getItemSlot("actions.refresh"), createRefreshItem());
    }

    private ItemStack createReportItem(Report report) {
        String status = report.getStatus();
        String materialName = guiConfig.getConfig().getString("report-item.materials." + status, "PAPER");
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Name from messages
        String name = plugin.getMessageManager().getMessage("gui.report-list.report-item.name")
                .replace("%id%", String.valueOf(report.getId()))
                .replace("%reported%", report.getReportedPlayerName());
        meta.setDisplayName(plugin.getMessageManager().colorize(name));

        // Get total reports
        int totalReports = plugin.getReportService().getReportCount(report.getReportedPlayerName());

        // Build lore from message labels
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String statusColor = guiConfig.getConfig().getString("report-item.status-colors." + status, "&e");
        String statusName = guiConfig.getConfig().getString("report-item.status-names." + status, status);

        String labelReporter = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.reporter");
        String labelReason = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.reason");
        String labelServer = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.server");
        String labelDate = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.date");
        String labelStatus = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.status");
        String labelTotalReports = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.total-reports");
        String labelClick = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.click");

        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("&8▪ &7" + labelReporter + " &e" + report.getReporterName());
        lore.add("&8▪ &7" + labelReason + " &f" + report.getReason());
        lore.add("&8▪ &7" + labelServer + " &f" + (report.getServerName() != null ? report.getServerName() : "N/A"));
        lore.add("&8▪ &7" + labelDate + " &f" + sdf.format(new Date(report.getTimestamp())));
        lore.add("&8▪ &7" + labelStatus + " " + statusColor + statusName);
        lore.add("&8▪ &7" + labelTotalReports + " &c" + totalReports);

        // Add Overwatch voting statistics if available
        String votingStats = plugin.getOverwatchManager().getVotingStats(report.getId());
        if (votingStats != null) {
            String labelOverwatch = plugin.getMessageManager().getMessage("gui.report-list.report-item.labels.overwatch");
            lore.add("&8▪ &7" + labelOverwatch + " &e" + votingStats);
        }

        lore.add("");
        lore.add("&8" + labelClick);

        List<String> coloredLore = lore.stream()
                .map(line -> plugin.getMessageManager().colorize(line))
                .collect(Collectors.toList());

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyListItem() {
        Material material = Material.getMaterial(guiConfig.getConfig().getString("empty-list.material", "BARRIER"));
        if (material == null) material = Material.BARRIER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getMessageManager().getMessage("gui.report-list.empty-list.name");
        meta.setDisplayName(plugin.getMessageManager().colorize(name));

        List<String> lore = plugin.getMessageManager().getMessageList("gui.report-list.empty-list.lore");
        List<String> coloredLore = lore.stream()
                .map(line -> plugin.getMessageManager().colorize(line))
                .collect(Collectors.toList());

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationItem(String type, int pageNum) {
        String path = "navigation." + type;
        Material material = Material.getMaterial(guiConfig.getConfig().getString(path + ".material", "ARROW"));
        if (material == null) material = Material.ARROW;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getMessageManager().getMessage("gui.report-list." + path + ".name");
        meta.setDisplayName(plugin.getMessageManager().colorize(name));

        String loreKey = "gui.report-list." + path + ".lore";
        List<String> lore = plugin.getMessageManager().getMessageList(loreKey);
        if (lore.isEmpty()) {
            String singleLore = plugin.getMessageManager().getMessage(loreKey);
            if (!singleLore.equals(loreKey)) {
                lore = java.util.Arrays.asList(singleLore);
            }
        }

        List<String> coloredLore = lore.stream()
                .map(line -> line.replace("%prev_page%", String.valueOf(pageNum))
                        .replace("%next_page%", String.valueOf(pageNum)))
                .map(line -> plugin.getMessageManager().colorize(line))
                .collect(Collectors.toList());

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageInfoItem() {
        Material material = Material.getMaterial(guiConfig.getConfig().getString("navigation.page-info.material", "BOOK"));
        if (material == null) material = Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getMessageManager().getMessage("gui.report-list.navigation.page-info.name")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPage));
        meta.setDisplayName(plugin.getMessageManager().colorize(name));

        int total = reports != null ? reports.size() : 0;
        int showing = Math.min(itemsPerPage, total - ((currentPage - 1) * itemsPerPage));

        List<String> lore = plugin.getMessageManager().getMessageList("gui.report-list.navigation.page-info.lore");
        List<String> coloredLore = lore.stream()
                .map(line -> line.replace("%total%", String.valueOf(total))
                        .replace("%showing%", String.valueOf(showing)))
                .map(line -> plugin.getMessageManager().colorize(line))
                .collect(Collectors.toList());

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshItem() {
        Material material = Material.getMaterial(guiConfig.getConfig().getString("actions.refresh.material", "EMERALD"));
        if (material == null) material = Material.EMERALD;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = plugin.getMessageManager().getMessage("gui.report-list.actions.refresh.name");
        meta.setDisplayName(plugin.getMessageManager().colorize(name));

        List<String> lore = plugin.getMessageManager().getMessageList("gui.report-list.actions.refresh.lore");
        List<String> coloredLore = lore.stream()
                .map(line -> plugin.getMessageManager().colorize(line))
                .collect(Collectors.toList());

        meta.setLore(coloredLore);
        item.setItemMeta(meta);
        return item;
    }

    private int getReportSlot(int index) {
        // 4 rows x 7 columns layout
        int row = index / 7;
        int col = index % 7;
        return 10 + col + (row * 9);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        // Navigation
        if (slot == guiConfig.getItemSlot("navigation.previous") && currentPage > 1) {
            currentPage--;
            refresh();
        } else if (slot == guiConfig.getItemSlot("navigation.next") && currentPage < maxPage) {
            currentPage++;
            refresh();
        } else if (slot == guiConfig.getItemSlot("actions.refresh")) {
            refresh();
        }

        // Report item click
        else {
            Report report = getReportAtSlot(slot);
            if (report != null) {
                player.closeInventory();
                new ReportDetailGUI(player, report, ((ReportSystemSpigot) plugin).getReplayDAO()).open();
            }
        }
    }

    private List<Report> processReports(List<Report> initialList) {
        // Sadece sıralama yap (filtre kaldırıldı)
        initialList.sort((r1, r2) -> (currentSort == SortType.OLDEST_FIRST)
                ? Long.compare(r1.getTimestamp(), r2.getTimestamp())
                : Long.compare(r2.getTimestamp(), r1.getTimestamp()));

        return initialList;
    }

    public Report getReportAtSlot(int slot) {
        if (reports == null) return null;
        int index = -1;
        if (slot >= 10 && slot <= 16) index = slot - 10;
        else if (slot >= 19 && slot <= 25) index = slot - 12;
        else if (slot >= 28 && slot <= 34) index = slot - 14;
        else if (slot >= 37 && slot <= 43) index = slot - 16;

        int actualIndex = ((currentPage - 1) * itemsPerPage) + index;
        if (index != -1 && actualIndex < reports.size()) {
            return reports.get(actualIndex);
        }
        return null;
    }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int page) { this.currentPage = page; }
    public int getMaxPage() { return maxPage; }
    public void refresh() { loadAndBuild(); }
    public SortType getSortType() { return currentSort; }
    public void setSortType(SortType sort) { this.currentSort = sort; }
}

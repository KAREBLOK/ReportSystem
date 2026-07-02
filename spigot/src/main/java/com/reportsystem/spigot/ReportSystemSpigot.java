package com.reportsystem.spigot;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.reportsystem.common.database.Database;
import com.reportsystem.common.database.DatabaseConfig;
import com.reportsystem.common.database.DatabaseFactory;
import com.reportsystem.common.database.MySQLDatabase;
import com.reportsystem.common.database.SQLiteDatabase;
import com.reportsystem.common.database.ReplayDAO;
import com.reportsystem.common.service.ReportService;
import com.reportsystem.spigot.commands.*;
import com.reportsystem.spigot.config.ConfigManager;
import com.reportsystem.spigot.listeners.GUIListener; // GUIListener import'u GUIManager yerine
import com.reportsystem.spigot.listeners.*;
import com.reportsystem.spigot.managers.MessageManager;
import com.reportsystem.spigot.punishment.PunishmentManager;
import com.reportsystem.spigot.recording.RecordingManager;
import com.reportsystem.spigot.replay.ReplayManager;
import com.reportsystem.spigot.utils.ChatInputManager;
import com.reportsystem.spigot.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ReportSystemSpigot extends JavaPlugin {

    private static ReportSystemSpigot instance;

    // Managers
    private ConfigManager configManager;
    private Database database;
    private ReportService reportService;
    private ReplayManager replayManager;
    private RecordingManager recordingManager;
    private com.reportsystem.spigot.recording.NearbyPlayerMoveListener nearbyPlayerMoveListener;
    private PunishmentManager punishmentManager;
    private MessageManager messageManager;
    private ChatInputManager chatInputManager;
    private ReplayDAO replayDAO; // EKLENDİ: getReplayDAO için alan
    private com.reportsystem.spigot.punishment.AnimatedBanManager animatedBanManager;
    private com.reportsystem.spigot.utils.AdvancementNotification advancementNotification;
    private com.reportsystem.spigot.webhook.WebhookManager webhookManager;

    // Overwatch System
    private com.reportsystem.spigot.overwatch.OverwatchManager overwatchManager;
    private com.reportsystem.spigot.overwatch.NPCManager npcManager;
    private com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener overwatchReplayListener;

    // Trust Level
    private com.reportsystem.spigot.trust.TrustLevelManager trustLevelManager;

    // Anti-Cheat Hooks
    private com.reportsystem.spigot.hooks.polar.PolarHook polarHook;
    private com.reportsystem.spigot.hooks.vulcan.VulcanHook vulcanHook;
    private com.reportsystem.spigot.hooks.grim.GrimHook grimHook;
    private com.reportsystem.spigot.hooks.AntiCheatBypassManager antiCheatBypassManager;

    // Commands
    private ReportCommand reportCommand;

    // Plugin messaging
    private final String CHANNEL = "reportsystem:channel";

    // Pending replays (for cross-server teleportation)
    private final Map<UUID, Integer> pendingReplays = new ConcurrentHashMap<>();

    // Tasks
    private BukkitTask autoSaveTask;
    private BukkitTask cleanupTask;
    private BukkitTask licenseCheckTask; // KAREBLOK.TC - Runtime license verification

    // License Manager
    private com.reportsystem.spigot.license.LicenseManager licenseManager; // KAREBLOK.TC

    // Telemetry Manager
    private com.reportsystem.spigot.telemetry.TelemetryManager telemetryManager; // KAREBLOK.TC

    // Cache
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    // Pending reports
    private final Map<UUID, PendingReport> pendingReports = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingCrossServerTargets = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        // Create directories
        createDirectories();

        // Save default configs
        saveDefaultConfigs();

        // =====================================
        // KAREBLOK.TC - LICENSE VERIFICATION
        // =====================================
        String licenseKey = getConfig().getString("license.license-key", "RS-XXXX-XXXX-XXXX");
        String apiUrl = getConfig().getString("license.api-url", "https://kareblok.tc");

        // Lisans Manager'ı oluştur ve field'a ata
        this.licenseManager = new com.reportsystem.spigot.license.LicenseManager(this, licenseKey, apiUrl);

        try {
            // İlk lisans doğrulamasını bekle (max 15 saniye, main thread'i bloklamayı sınırla)
            boolean isValid = licenseManager.verifyLicense().get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (!isValid) {
                getLogger().severe("");
                getLogger().severe("╔════════════════════════════════════════════════════════╗");
                getLogger().severe("║                                                        ║");
                getLogger().severe("║  KAREBLOK.TC - LISANS DOĞRULAMA BAŞARISIZ!           ║");
                getLogger().severe("║                                                        ║");
                getLogger().severe("║  Eklenti geçersiz lisans nedeniyle devre dışı!       ║");
                getLogger().severe("║                                                        ║");
                getLogger().severe("║  Hata: " + String.format("%-45s",
                        licenseManager.getErrorMessage() != null ?
                        licenseManager.getErrorMessage() : "Bilinmeyen hata") + " ║");
                getLogger().severe("║                                                        ║");
                getLogger().severe("║  Lisans satın almak için:                            ║");
                getLogger().severe("║  https://kareblok.tc                                  ║");
                getLogger().severe("║                                                        ║");
                getLogger().severe("╚════════════════════════════════════════════════════════╝");
                getLogger().severe("");

                // Eklentiyi devre dışı bırak
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            // RUNTIME VERIFICATION - Her 1 saatte bir lisansi kontrol et
            licenseCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                licenseManager.verifyLicenseRuntime().thenAccept(valid -> {
                    if (!valid) {
                        getLogger().severe("");
                        getLogger().severe("╔════════════════════════════════════════════════════════╗");
                        getLogger().severe("║  RUNTIME LISANS DOGRULAMA BASARISIZ!                 ║");
                        getLogger().severe("║  Eklenti kapatiliyor...                               ║");
                        getLogger().severe("╚════════════════════════════════════════════════════════╝");
                        getLogger().severe("");
                        Bukkit.getScheduler().runTask(this, () -> {
                            getServer().getPluginManager().disablePlugin(this);
                        });
                    } else {
                        getLogger().info("[KAREBLOK.TC] Runtime license check: ✓ Valid");
                    }
                });
            }, 20L * 60L * 60L, 20L * 60L * 60L); // Her 1 saatte

        } catch (java.util.concurrent.TimeoutException e) {
            // Timeout = API ulasilamiyor = plugin ACILMAZ
            getLogger().severe("===========================================");
            getLogger().severe("Lisans dogrulama zaman asimina ugradi (15s)!");
            getLogger().severe("API'ye ulasilamiyor. Eklenti acilamaz.");
            getLogger().severe("API URL: " + getConfig().getString("license.api-url", "https://kareblok.tc"));
            getLogger().severe("===========================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            getLogger().severe("Lisans dogrulama sirasinda kritik hata!");
            getLogger().log(Level.SEVERE, "Exception:", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // =====================================

        // Initialize managers
        if (!initializeManagers()) {
            getLogger().severe("Failed to initialize managers! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Register channels
        registerChannels();

        // Start tasks
        startTasks();

        // Check for updates
        if (configManager.isUpdateCheckEnabled()) {
            checkForUpdates();
        }

        // bStats Metrics
        new org.bstats.bukkit.Metrics(this, 25469);

        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.reportsystem.spigot.hooks.ReportSystemPlaceholders(this).register();
            getLogger().info("PlaceholderAPI support enabled!");
        }

        // Polar Anti-Cheat (softdepend — yoksa atlanir)
        if (getServer().getPluginManager().getPlugin("PolarLoader") != null
                && getConfig().getBoolean("polar.enabled", false)) {
            try {
                // ClassLoader kontrolu — Polar API sinifi yuklu mu?
                Class.forName("top.polar.api.loader.LoaderApi");
                this.polarHook = new com.reportsystem.spigot.hooks.polar.PolarHook(this);
                getLogger().info("[POLAR] Polar callback kaydedildi, Polar yuklenince aktif olacak.");
            } catch (ClassNotFoundException e) {
                getLogger().warning("[POLAR] Polar API sinifi bulunamadi. PolarLoader yuklu mu?");
                polarHook = null;
            } catch (Exception e) {
                getLogger().warning("[POLAR] Polar hook hatasi: " + e.getMessage());
                polarHook = null;
            }
        }

        // Vulcan Anti-Cheat (softdepend — yoksa atlanir)
        if (getServer().getPluginManager().getPlugin("Vulcan") != null
                && getConfig().getBoolean("vulcan.enabled", false)) {
            try {
                this.vulcanHook = new com.reportsystem.spigot.hooks.vulcan.VulcanHook(this);
                if (!vulcanHook.connect()) {
                    vulcanHook = null;
                }
            } catch (Exception e) {
                getLogger().warning("[VULCAN] Vulcan hook hatasi: " + e.getMessage());
                vulcanHook = null;
            }
        }

        // GrimAC (softdepend — yoksa atlanir, ucretsiz + acik kaynak)
        if (getServer().getPluginManager().getPlugin("GrimAC") != null
                && getConfig().getBoolean("grim.enabled", false)) {
            try {
                Class.forName("ac.grim.grimac.api.GrimAbstractAPI");
                this.grimHook = new com.reportsystem.spigot.hooks.grim.GrimHook(this);
            } catch (ClassNotFoundException e) {
                getLogger().warning("[GRIM] GrimAC API sinifi bulunamadi.");
                grimHook = null;
            } catch (Exception e) {
                getLogger().warning("[GRIM] GrimAC hook hatasi: " + e.getMessage());
                grimHook = null;
            }
        }

        // Anti-Cheat Bypass Manager — replay viewer'lari AC flag'lerinden korur
        // Hooks kuruldu mu kurulmadi mi farketmez, evrensel calisir (PermissionAttachment tabanli)
        this.antiCheatBypassManager = new com.reportsystem.spigot.hooks.AntiCheatBypassManager(this);
        getLogger().info("[AC-BYPASS] Anti-cheat bypass manager aktif.");

        // =====================================
        // KAREBLOK.TC - TELEMETRY SYSTEM
        // =====================================
        telemetryManager = new com.reportsystem.spigot.telemetry.TelemetryManager(this);
        telemetryManager.start();

        // Startup Banner
        printBanner();
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        if (licenseCheckTask != null) licenseCheckTask.cancel(); // KAREBLOK.TC

        // Save all active recordings
        if (recordingManager != null) {
            recordingManager.stopAllRecordings();
        }

        // Stop all replays
        if (replayManager != null) {
            replayManager.stopAllReplays();
        }

        // Cleanup Anti-Cheat hooks
        if (polarHook != null) polarHook.disconnect();
        if (vulcanHook != null) vulcanHook.disconnect();
        if (grimHook != null) grimHook.disconnect();
        if (antiCheatBypassManager != null) antiCheatBypassManager.shutdown();

        // Cleanup Overwatch NPCs
        if (npcManager != null) {
            npcManager.shutdown();
        }

        // Cleanup AnimatedBanManager (freeze task iptal, frozen oyuncuları serbest bırak)
        if (animatedBanManager != null) {
            animatedBanManager.shutdown();
        }

        // Close database
        if (database != null) {
            try {
                database.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error closing database", e);
            }
        }

        // Unregister channels
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);

        getLogger().info("ReportSystem has been disabled!");
    }

    /**
     * Create necessary directories
     */
    private void createDirectories() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // GUI folder
        File guiFolder = new File(getDataFolder(), "guis");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }
    }

    /**
     * Save default config files
     */
    private void saveDefaultConfigs() {
        // Main config - Auto update with new keys
        updateConfig();

        // Language files - Auto update
        updateYamlFile("messages_en.yml");
        updateYamlFile("messages_tr.yml");

        // GUI configs - Auto update
        updateYamlFile("guis/report-create.yml");
        updateYamlFile("guis/report-list.yml");
        updateYamlFile("guis/report-detail.yml");
        updateYamlFile("guis/replay-control.yml");
        updateYamlFile("guis/replay-info.yml");
        updateYamlFile("guis/replay-teleport.yml");
        updateYamlFile("guis/punishment.yml");
        updateYamlFile("guis/punishment-selection.yml");
        updateYamlFile("guis/animated-ban.yml");
        updateYamlFile("guis/replay-settings.yml");
        updateYamlFile("guis/replay-hotbar.yml");
        updateYamlFile("guis/overwatch-menu.yml");
        updateYamlFile("guis/overwatch-verdict.yml");
        updateYamlFile("guis/overwatch-stats.yml");
        updateYamlFile("guis/overwatch-leaderboard.yml");
    }

    /**
     * Update config.yml with new keys while preserving user settings
     */
    private void updateConfig() {
        File configFile = new File(getDataFolder(), "config.yml");

        // First installation - just save default
        if (!configFile.exists()) {
            saveDefaultConfig();
            return;
        }

        try {
            // Load current config
            FileConfiguration currentConfig = getConfig();

            // Load default config from jar
            try (InputStream defaultStream = getResource("config.yml")) {
                if (defaultStream == null) {
                    getLogger().warning("Could not load default config from jar!");
                    return;
                }

                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );

                // Add missing keys (deep comparison)
                boolean updated = false;
                for (String key : defaultConfig.getKeys(true)) {
                    // Skip parent nodes (only check leaf nodes)
                    if (defaultConfig.isConfigurationSection(key)) {
                        continue;
                    }

                    if (!currentConfig.contains(key)) {
                        Object value = defaultConfig.get(key);
                        currentConfig.set(key, value);
                        updated = true;
                        getLogger().info("§e[Config] Added missing key: §f" + key);
                    }
                }

                // Save if updated
                if (updated) {
                    currentConfig.save(configFile);
                    getLogger().info("§a[Config] Yeni ayarlar eklendi! Kullanıcı ayarlarınız korundu.");
                } else {
                    getLogger().info("§7[Config] config.yml zaten güncel.");
                }
            }

        } catch (Exception e) {
            getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update any YAML file with new keys while preserving user settings
     */
    private void updateYamlFile(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);

        // First installation - just save default
        if (!file.exists()) {
            // Create parent directories if needed
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            saveResource(resourcePath, false);
            return;
        }

        try {
            // Load current file
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);

            // Load default config from jar
            try (InputStream defaultStream = getResource(resourcePath)) {
                if (defaultStream == null) {
                    getLogger().warning("Could not load default " + resourcePath + " from jar!");
                    return;
                }

                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );

                // Add missing keys (deep comparison)
                boolean updated = false;
                for (String key : defaultConfig.getKeys(true)) {
                    // Skip parent nodes (only check leaf nodes)
                    if (defaultConfig.isConfigurationSection(key)) {
                        continue;
                    }

                    if (!currentConfig.contains(key)) {
                        Object value = defaultConfig.get(key);
                        currentConfig.set(key, value);
                        updated = true;
                        getLogger().info("§e[Config] Added missing key: §f" + key + " §7to " + resourcePath);
                    }
                }

                // Save if updated
                if (updated) {
                    currentConfig.save(file);
                    getLogger().info("§a[Config] " + resourcePath + " güncellendi! Yeni ayarlar eklendi.");
                } else {
                    getLogger().info("§7[Config] " + resourcePath + " zaten güncel.");
                }
            }

        } catch (Exception e) {
            getLogger().severe("Failed to update " + resourcePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save resource only if it doesn't exist (prevents warnings on reload)
     * @deprecated Use updateYamlFile() instead for auto-update functionality
     */
    @Deprecated
    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    /**
     * Initialize all managers
     */
    private boolean initializeManagers() {
        try {
            // Config Manager
            this.configManager = new ConfigManager(this);

            // Database
            if (configManager.getDatabaseType().equalsIgnoreCase("mysql")) {
                DatabaseConfig dbConfig = new DatabaseConfig(
                        configManager.getMySQLHost(),
                        configManager.getMySQLPort(),
                        configManager.getMySQLDatabase(),
                        configManager.getMySQLUsername(),
                        configManager.getMySQLPassword()
                );
                this.database = new MySQLDatabase(dbConfig); // DatabaseFactory kullanımı yerine doğrudan
            } else {
                DatabaseConfig dbConfig = new DatabaseConfig(getDataFolder());
                this.database = new SQLiteDatabase(getDataFolder()); // DatabaseFactory kullanımı yerine doğrudan
            }
            database.connect();
            database.createTables();

            // Report Service
            if (database instanceof MySQLDatabase) {
                MySQLDatabase mysqlDb = (MySQLDatabase) database;
                this.reportService = new ReportService(mysqlDb.getReportDAO());
                this.replayDAO = mysqlDb.getReplayDAO(); // EKLENDİ: replayDAO alanını doldur
            } else if (database instanceof SQLiteDatabase) {
                SQLiteDatabase sqliteDb = (SQLiteDatabase) database;
                this.reportService = new ReportService(sqliteDb.getReportDAO());
                this.replayDAO = sqliteDb.getReplayDAO(); // EKLENDİ: replayDAO alanını doldur
            }

            // Message Manager
            this.messageManager = new MessageManager(this);

            // ChatInput Manager
            this.chatInputManager = new ChatInputManager(this);

            // Recording Manager
            this.recordingManager = new RecordingManager(this, replayDAO);

            // Nearby Player Move Listener (opsiyonel - config'den açılabilir)
            this.nearbyPlayerMoveListener = new com.reportsystem.spigot.recording.NearbyPlayerMoveListener(this, recordingManager);

            // Replay Manager
            this.replayManager = new ReplayManager(this, replayDAO, recordingManager);

            // Punishment Manager
            this.punishmentManager = new PunishmentManager(this);

            // Animated Ban Manager
            this.animatedBanManager = new com.reportsystem.spigot.punishment.AnimatedBanManager(this, punishmentManager);

            // Advancement Notification
            this.advancementNotification = new com.reportsystem.spigot.utils.AdvancementNotification(this);

            // Webhook Manager
            this.webhookManager = new com.reportsystem.spigot.webhook.WebhookManager(this);
            if (webhookManager.isEnabled()) {
                getLogger().info("Discord webhook system initialized");
            }

            // Trust Level Manager
            if (getConfig().getBoolean("trust-level.enabled", true)) {
                this.trustLevelManager = new com.reportsystem.spigot.trust.TrustLevelManager(this);
                getLogger().info("Trust Level system initialized");
            }

            // Overwatch System
            if (getConfig().getBoolean("overwatch.enabled", true)) {
                this.overwatchManager = new com.reportsystem.spigot.overwatch.OverwatchManager(this);
                this.npcManager = new com.reportsystem.spigot.overwatch.NPCManager(this);
                this.overwatchReplayListener = new com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener(this);

                // Create Overwatch tables
                try {
                    overwatchManager.getDAO().createTables();
                    getLogger().info("[OVERWATCH] Database tables created successfully");
                } catch (Exception e) {
                    getLogger().severe("[OVERWATCH] Failed to create database tables: " + e.getMessage());
                    e.printStackTrace();
                }

                // Run migrations for existing tables
                try {
                    overwatchManager.getDAO().migrateTables();
                    getLogger().info("[OVERWATCH] Database migrations completed successfully");
                } catch (Exception e) {
                    getLogger().severe("[OVERWATCH] Failed to run database migrations: " + e.getMessage());
                    e.printStackTrace();
                }

                getLogger().info("Overwatch system initialized");
            }

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing managers", e);
            return false;
        }
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        // Report command
        this.reportCommand = new ReportCommand(this);
        getCommand("report").setExecutor(reportCommand);
        getCommand("report").setTabCompleter(reportCommand);

        // Reports command
        ReportsCommand reportsCommand = new ReportsCommand(this);
        getCommand("reports").setExecutor(reportsCommand);
        getCommand("reports").setTabCompleter(reportsCommand);

        // ReportSystem admin command
        ReportSystemCommand rsCommand = new ReportSystemCommand(this);
        getCommand("reportsystem").setExecutor(rsCommand);
        getCommand("reportsystem").setTabCompleter(rsCommand);

        // Overwatch command
        if (overwatchManager != null) {
            com.reportsystem.spigot.overwatch.commands.OverwatchCommand overwatchCommand =
                    new com.reportsystem.spigot.overwatch.commands.OverwatchCommand(this);
            getCommand("overwatch").setExecutor(overwatchCommand);
            getCommand("overwatch").setTabCompleter(overwatchCommand);
        }
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        // GUI listener
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // Chat listener
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Player listener
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Punishment listener
        getServer().getPluginManager().registerEvents(new PunishmentListener(this), this);

        // Recording listeners
        if (configManager.isReplayEnabled()) {
            getServer().getPluginManager().registerEvents(new RecordingListener(this), this);
            getServer().getPluginManager().registerEvents(new EntityInteractListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.ItemListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.ExplosionListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.UseItemListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.FishingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.DamageListener(recordingManager), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.EquipmentListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.VehicleListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BedListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BlockListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BlockPhysicsListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.TeleportListener(recordingManager, this), this);

            // Replay interaction listener (PacketEvents - for right-clicking fake NPCs to view equipment)
            PacketEvents.getAPI().getEventManager().registerListener(new com.reportsystem.spigot.replay.ReplayInteractionListener(this));
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.HealthListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.PotionListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.GameModeListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.WeatherListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.SoundListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.EntitySpawnListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.LeashListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.ChatRecordingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.ReplayControlListener(this), this);
            // Replay protection listener (prevent viewers from picking up items, breaking blocks, etc.)
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.replay.ReplayProtectionListener(this), this);
            // Nearby player tracker - records actions of players near the recorded player
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.NearbyPlayerTrackerListener(this), this);
            // Nearby Player Move Listener - instant move tracking (opsiyonel)
            if (nearbyPlayerMoveListener.isEnabled()) {
                getServer().getPluginManager().registerEvents(nearbyPlayerMoveListener, this);
                getLogger().info("NearbyPlayerMoveListener registered (enhanced tracking enabled)");
            }
            // New comprehensive listeners (entity properties, vehicle placement, etc.)
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.EntityDyeListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.HangingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.FallingBlockListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BreedListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BucketListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.NoteBlockListener(recordingManager, this), this);
            // Deep scan comprehensive listeners (signs, anvils, brewing, crafting, enchanting)
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.SignListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.AnvilListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BrewingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.CraftingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.EnchantingListener(recordingManager, this), this);
            // Second deep scan - comprehensive coverage (utility, farming, portals, decorations, redstone, etc.)
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.UtilityBlockListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BookEditListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.PortalListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.FarmingListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.PlayerStateListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.DecorationListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.RedstoneListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.EntityCommandListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.ContainerListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.BlockIgniteListener(recordingManager, this), this);
            getServer().getPluginManager().registerEvents(new com.reportsystem.spigot.listeners.FarmlandTrampleListener(recordingManager, this), this);
            getLogger().info("All recording listeners registered (45 listeners - including replay protection and nearby player tracking)");
        }

        // Overwatch listeners
        if (overwatchManager != null) {
            com.reportsystem.spigot.overwatch.listeners.NPCClickListener npcClickListener =
                    new com.reportsystem.spigot.overwatch.listeners.NPCClickListener(this);
            // Register as both PacketEvents listener (for NPC click) and Bukkit listener (for join + hologram protection)
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(npcClickListener);
            getServer().getPluginManager().registerEvents(npcClickListener, this);
            getServer().getPluginManager().registerEvents(overwatchReplayListener, this);
            getServer().getPluginManager().registerEvents(
                    new com.reportsystem.spigot.overwatch.listeners.OverwatchGUIListener(this), this);
            getLogger().info("Overwatch listeners registered");
        }
    }

    /**
     * Register plugin messaging channels
     */
    private void registerChannels() {
        if (configManager.isBungeeCordEnabled()) {
            // BungeeCord channel
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

            // Custom channel
            getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL,
                    new ReportPluginMessageListener(this));

            // Notification channel
            getServer().getMessenger().registerOutgoingPluginChannel(this, "reportsystem:notification");
            getServer().getMessenger().registerIncomingPluginChannel(this, "reportsystem:notification",
                    new com.reportsystem.spigot.listeners.ReportNotificationPluginMessageListener(this));

            getLogger().info("Registered plugin messaging channels (including notifications)");
        }
    }

    /**
     * Start scheduled tasks
     */
    private void startTasks() {
        // Load Overwatch NPCs
        if (npcManager != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                npcManager.loadNPCs();
                npcManager.startHologramUpdateTask();
            }, 60L); // Load after 3 seconds (wait for worlds and chunks to fully load)
        }

        // Auto-save task (every 5 minutes)
        autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            debug("[DEBUG] Running auto-save task...");

            // Save active recordings
            recordingManager.saveAllRecordings();

            // Clear old cache entries
            cleanupCache();

        }, 20L * 60 * 5, 20L * 60 * 5); // 5 minutes

        // Cleanup task (every hour)
        cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            debug("[DEBUG] Running cleanup task...");

            // Auto-close old reports
            int autoCloseDays = configManager.getAutoCloseDays();
            if (autoCloseDays > 0) {
                reportService.autoCloseOldReports(autoCloseDays);
            }

            // Delete old replays
            int autoDeleteDays = configManager.getReplayAutoDeleteDays();
            if (autoDeleteDays > 0) {
                replayManager.deleteOldReplays(autoDeleteDays);
            }

        }, 20L * 60 * 60, 20L * 60 * 60); // 1 hour
    }

    /**
     * Check for plugin updates
     */
    private void checkForUpdates() {
        // TODO: Spigot resource ID'yi gerçek değerle değiştir veya kareblok.tc API'sinden güncelleme kontrolü yap
        getLogger().info("[UpdateChecker] Guncelleme kontrolu henuz yapilandirilmamis. Spigot resource ID gerekli.");
    }

    /**
     * Clean up old cache entries
     */
    private void cleanupCache() {
        long now = System.currentTimeMillis();
        int cacheExpiry = configManager.getCacheExpiry() * 60 * 1000; // Convert to millis

        cooldowns.entrySet().removeIf(entry ->
                now - entry.getValue() > cacheExpiry
        );
    }

    /**
     * Create cross-server report
     */
    public void createCrossServerReport(Player reporter, String targetName, String reason) {
        if (!configManager.isBungeeCordEnabled()) {
            getLogger().warning("Tried to create cross-server report but BungeeCord is disabled!");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CreateReport");
        out.writeUTF(reporter.getName());
        out.writeUTF(reporter.getUniqueId().toString());
        out.writeUTF(targetName);
        out.writeUTF(reason);

        reporter.sendPluginMessage(this, CHANNEL, out.toByteArray());

        debug("[DEBUG] Sent cross-server report: " + reporter.getName() + " -> " + targetName);
    }

    /**
     * Store pending replay for cross-server teleportation
     */
    public void storePendingReplay(UUID playerUUID, int reportId) {
        pendingReplays.put(playerUUID, reportId);

        // Remove after 30 seconds
        getServer().getScheduler().runTaskLater(this, () -> {
            pendingReplays.remove(playerUUID);
        }, 20L * 30);
    }

    /**
     * Get pending replay ID
     */
    public Integer getPendingReplay(UUID playerUUID) {
        return pendingReplays.remove(playerUUID);
    }

    /**
     * Update recording with real report ID
     */
    public void updateRecordingReportId(UUID playerUUID, int reportId) {
        recordingManager.updateReportId(playerUUID, reportId);
    }

    /**
     * Get server name
     */
    public String getServerName() {
        if (configManager.isBungeeCordEnabled()) {
            String displayName = configManager.getServerDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }
        return getServer().getName();
    }

    /**
     * Check cooldown
     */
    public boolean checkCooldown(String key, int seconds) {
        long now = System.currentTimeMillis();
        Long lastTime = cooldowns.get(key);

        if (lastTime != null && now - lastTime < seconds * 1000L) {
            return false;
        }

        cooldowns.put(key, now);
        return true;
    }

    /**
     * Get remaining cooldown
     */
    public long getRemainingCooldown(String key, int seconds) {
        long now = System.currentTimeMillis();
        Long lastTime = cooldowns.get(key);

        if (lastTime == null) return 0;

        long diff = now - lastTime;
        long cooldownMillis = seconds * 1000L;

        if (diff >= cooldownMillis) return 0;

        return (cooldownMillis - diff) / 1000;
    }

    /**
     * Pending report management
     */
    public void addPendingReport(UUID reporter, String targetName) {
        pendingReports.put(reporter, new PendingReport(targetName, false));
    }

    public PendingReport getPendingReport(UUID reporter) {
        return pendingReports.get(reporter);
    }

    public void removePendingReport(UUID reporter) {
        pendingReports.remove(reporter);
    }

    public void addPendingCrossServerTarget(UUID reporter, String targetName) {
        pendingCrossServerTargets.put(reporter, targetName);
    }

    public String getPendingCrossServerTarget(UUID reporter) {
        return pendingCrossServerTargets.get(reporter);
    }

    public void removePendingCrossServerTarget(UUID reporter) {
        pendingCrossServerTargets.remove(reporter);
    }

    /**
     * Debug logging
     */
    public boolean isDebugEnabled() {
        return configManager.isDebugEnabled();
    }

    public void debug(String message) {
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().info(message);
        }
    }

    private void printBanner() {
        String version = getDescription().getVersion();
        String dbType = configManager.getDatabaseType().toUpperCase();
        String lang = configManager.getLanguage().toUpperCase();
        String mode = configManager.isBungeeCordEnabled() ? "Network" : "Standalone";
        boolean papi = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        getLogger().info("");
        getLogger().info("  ██████╗ ███████╗██████╗  ██████╗ ██████╗ ████████╗");
        getLogger().info("  ██╔══██╗██╔════╝██╔══██╗██╔═══██╗██╔══██╗╚══██╔══╝");
        getLogger().info("  ██████╔╝█████╗  ██████╔╝██║   ██║██████╔╝   ██║   ");
        getLogger().info("  ██╔══██╗██╔══╝  ██╔═══╝ ██║   ██║██╔══██╗   ██║   ");
        getLogger().info("  ██║  ██║███████╗██║     ╚██████╔╝██║  ██║   ██║   ");
        getLogger().info("  ╚═╝  ╚═╝╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ");
        getLogger().info("  ╔══════════════════════════════════════════════════╗");
        getLogger().info("  ║  SYSTEM  v" + version + " | " + mode + " | " + dbType + " | " + lang + "  ║");
        getLogger().info("  ╚══════════════════════════════════════════════════╝");
        boolean polar = polarHook != null && polarHook.isConnected();
        boolean vulcan = vulcanHook != null && vulcanHook.isConnected();
        boolean grim = grimHook != null && grimHook.isConnected();
        getLogger().info("  Hooks: PacketEvents" + (papi ? " | PlaceholderAPI" : "") + (polar ? " | Polar AC" : "") + (vulcan ? " | Vulcan AC" : "") + (grim ? " | GrimAC" : "") + " | bStats");
        getLogger().info("  Author: KAREBLOK.TC");
        getLogger().info("");
    }

    // Getters
    public static ReportSystemSpigot getInstance() {
        return instance;
    }


    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Database getDatabase() {
        return database;
    }

    public ReportService getReportService() {
        if (reportService == null) {
            getLogger().severe("ReportService is null! Plugin may not be fully initialized.");
        }
        return reportService;
    }

    public ReplayManager getReplayManager() {
        return replayManager;
    }

    public RecordingManager getRecordingManager() {
        return recordingManager;
    }

    public com.reportsystem.spigot.hooks.AntiCheatBypassManager getAntiCheatBypassManager() {
        return antiCheatBypassManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public MessageManager getMessageManager() {
        if (messageManager == null) {
            getLogger().severe("MessageManager is null! Plugin may not be fully initialized.");
        }
        return messageManager;
    }

    public ReportCommand getReportCommand() {
        return reportCommand;
    }

    public String getChannelName() {
        return CHANNEL;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    // EKLENDİ: GUIListener'ın ihtiyaç duyduğu metot
    public ReplayDAO getReplayDAO() {
        if (replayDAO == null) {
            getLogger().severe("ReplayDAO is null! Database may not be initialized.");
        }
        return replayDAO;
    }

    public com.reportsystem.spigot.punishment.AnimatedBanManager getAnimatedBanManager() {
        return animatedBanManager;
    }

    public com.reportsystem.spigot.utils.AdvancementNotification getAdvancementNotification() {
        return advancementNotification;
    }

    public com.reportsystem.spigot.overwatch.OverwatchManager getOverwatchManager() {
        return overwatchManager;
    }

    public com.reportsystem.spigot.trust.TrustLevelManager getTrustLevelManager() {
        return trustLevelManager;
    }

    public com.reportsystem.spigot.overwatch.NPCManager getNPCManager() {
        return npcManager;
    }

    public com.reportsystem.spigot.overwatch.listeners.OverwatchReplayListener getOverwatchReplayListener() {
        return overwatchReplayListener;
    }

    public Database getDatabaseManager() {
        return database;
    }

    public com.reportsystem.spigot.webhook.WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public com.reportsystem.spigot.telemetry.TelemetryManager getTelemetryManager() {
        return telemetryManager;
    }

    public com.reportsystem.spigot.license.LicenseManager getLicenseManager() {
        return licenseManager;
    }

    /**
     * Inner class for pending reports
     */
    public static class PendingReport {
        private final String target;
        private final boolean isOnline;

        public PendingReport(String target, boolean isOnline) {
            this.target = target;
            this.isOnline = isOnline;
        }

        public String getTarget() {
            return target;
        }

        public boolean isOnline() {
            return isOnline;
        }
    }
}
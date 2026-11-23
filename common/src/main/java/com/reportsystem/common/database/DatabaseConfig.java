package com.reportsystem.common.database;

import java.io.File;
import java.util.Locale;
import java.util.Set;

public class DatabaseConfig {
    /** Connector/J 8 {@code sslMode} değerleri. Bunların dışı DISABLED'a düşer. */
    private static final Set<String> SSL_MODES =
            Set.of("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY");

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final File dataFolder;

    // Havuz ayarları (HikariCP) — varsayılanlar config okunamazsa geçerlidir.
    private int maxPoolSize = 10;
    private int minIdle = 2;
    private long connectionTimeoutMs = 5000L;
    private long idleTimeoutMs = 600000L;
    private long maxLifetimeMs = 1800000L;
    // DISABLED = eski davranış (SSL kapalı)
    private String sslMode = "DISABLED";

    // MySQL constructor
    public DatabaseConfig(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.dataFolder = null;
    }

    // SQLite constructor
    public DatabaseConfig(File dataFolder) {
        this.host = null;
        this.port = 0;
        this.database = null;
        this.username = null;
        this.password = null;
        this.dataFolder = dataFolder;
    }

    /**
     * Havuz ayarlarını uygular (sınırlara kırpılır).
     * pool 1-50, minIdle 0..pool, timeout >= 1000, idle >= 10000, lifetime >= 30_000 ms.
     */
    public DatabaseConfig withPool(int maxPoolSize, int minIdle, long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs) {
        this.maxPoolSize = Math.max(1, Math.min(50, maxPoolSize));
        this.minIdle = Math.max(0, Math.min(this.maxPoolSize, minIdle));
        this.connectionTimeoutMs = Math.max(1000L, connectionTimeoutMs);
        this.idleTimeoutMs = Math.max(10000L, idleTimeoutMs);
        this.maxLifetimeMs = Math.max(30_000L, maxLifetimeMs);
        return this;
    }

    /** Connector/J sslMode; tanınmayan / boş değer DISABLED olur. */
    public DatabaseConfig withSslMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        this.sslMode = SSL_MODES.contains(normalized) ? normalized : "DISABLED";
        return this;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    public String getSslMode() {
        return sslMode;
    }

    public String getJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?sslMode=" + sslMode + "&autoReconnect=true";
    }
}

package com.reportsystem.velocity.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class VelocityConfig {

    private final Path dataDirectory;
    private final Logger logger;
    private Map<String, Object> data = new LinkedHashMap<>();

    public VelocityConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.yml");

            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }

            try (InputStream in = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(in);
                if (loaded instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) loaded;
                    this.data = map;
                }
            }
        } catch (IOException e) {
            logger.error("Config dosyasi yuklenemedi!", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = data;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }

        return current.get(parts[parts.length - 1]);
    }

    public String getString(String path, String def) {
        Object value = getNestedValue(path);
        return value != null ? value.toString() : def;
    }

    public int getInt(String path, int def) {
        Object value = getNestedValue(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = getNestedValue(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return def;
    }

    /**
     * Config'den mesaj alir ve placeholder'lari degistirir.
     * Eger config'de yoksa defaultValue kullanilir.
     */
    public String getMessage(String path, String defaultValue, String... placeholders) {
        String msg = getString("messages." + path, defaultValue);
        // Placeholder'lari ciftler halinde uygula: key, value, key, value...
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        return msg;
    }
}

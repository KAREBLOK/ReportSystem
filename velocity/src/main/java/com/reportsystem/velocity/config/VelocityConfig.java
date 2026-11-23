package com.reportsystem.velocity.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    /**
     * Ust duzey "message-secret" degerini yazar (yorumlari bozmadan metin olarak):
     * satir varsa degistirir, yoksa dosyanin basina ekler. Bellekteki veriyi de gunceller.
     */
    public void saveMessageSecret(String secret) {
        Path configFile = dataDirectory.resolve("config.yml");
        try {
            String text = Files.exists(configFile)
                    ? new String(Files.readAllBytes(configFile), java.nio.charset.StandardCharsets.UTF_8)
                    : "";
            String line = "message-secret: \"" + secret + "\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^message-secret:.*$");
            if (p.matcher(text).find()) {
                text = p.matcher(text).replaceFirst(java.util.regex.Matcher.quoteReplacement(line));
            } else {
                text = line + System.lineSeparator() + text;
            }
            // Yarim yazilmis config kalmasin: once gecici dosya, sonra atomik tasima
            Path tmp = configFile.resolveSibling("config.yml.tmp");
            Files.write(tmp, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
                Files.move(tmp, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            data.put("message-secret", secret);
        } catch (IOException e) {
            logger.error("message-secret config'e kaydedilemedi!", e);
        }
    }

    /**
     * Ust duzey "message-secret" degerini okur. YAML tirnaksiz sayisal/bool degeri cevirir
     * (ornek 1e5 -> 100000.0); bu durumda uyari loglanir ve Spigot/Bungee ile ayni deger uretilsin diye
     * String.valueOf (toString) kullanilir. Yoksa bos string doner.
     */
    public String getMessageSecret() {
        Object value = data.get("message-secret");
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        logger.warn("[GUVENLIK] message-secret tirnak icinde yazilmali (ornek: message-secret: \"...\"); "
                + "tirnaksiz deger YAML tarafindan {} olarak cevrildi ve \"{}\" olarak kullaniliyor.",
                value.getClass().getSimpleName(), String.valueOf(value));
        return String.valueOf(value);
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

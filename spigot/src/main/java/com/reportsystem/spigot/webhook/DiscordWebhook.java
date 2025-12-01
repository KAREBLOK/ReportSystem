package com.reportsystem.spigot.webhook;

import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discord Webhook utility for sending rich embed messages
 * Supports buttons, embeds, colors, fields, and more
 */
public class DiscordWebhook {

    private final JavaPlugin plugin;
    private final String url;
    private String content;
    private String username;
    private String avatarUrl;
    private boolean tts;
    private List<EmbedObject> embeds = new ArrayList<>();
    private List<ActionRow> components = new ArrayList<>();

    public DiscordWebhook(JavaPlugin plugin, String url) {
        this.plugin = plugin;
        this.url = url;
    }

    public DiscordWebhook setContent(String content) {
        this.content = content;
        return this;
    }

    public DiscordWebhook setUsername(String username) {
        this.username = username;
        return this;
    }

    public DiscordWebhook setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public DiscordWebhook setTts(boolean tts) {
        this.tts = tts;
        return this;
    }

    public DiscordWebhook addEmbed(EmbedObject embed) {
        this.embeds.add(embed);
        return this;
    }

    public DiscordWebhook addActionRow(ActionRow row) {
        this.components.add(row);
        return this;
    }

    /**
     * Execute the webhook (send message to Discord)
     */
    public void execute() {
        if (this.url == null || this.url.isEmpty()) {
            plugin.getLogger().warning("[Webhook] Webhook URL is empty!");
            return;
        }

        // Run asynchronously to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String json = buildJSON();
                sendJSON(json);
            } catch (Exception e) {
                plugin.getLogger().severe("[Webhook] Failed to send webhook: " + e.getMessage());
                if (plugin.getConfig().getBoolean("discord-webhook.debug", false)) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Build JSON payload
     */
    private String buildJSON() {
        StringBuilder json = new StringBuilder("{");

        // Content
        if (content != null && !content.isEmpty()) {
            json.append("\"content\":").append(quote(content)).append(",");
        }

        // Username
        if (username != null && !username.isEmpty()) {
            json.append("\"username\":").append(quote(username)).append(",");
        }

        // Avatar URL
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.append("\"avatar_url\":").append(quote(avatarUrl)).append(",");
        }

        // TTS
        json.append("\"tts\":").append(tts).append(",");

        // Embeds
        if (!embeds.isEmpty()) {
            json.append("\"embeds\":[");
            for (int i = 0; i < embeds.size(); i++) {
                json.append(embeds.get(i).toJSON());
                if (i < embeds.size() - 1) json.append(",");
            }
            json.append("],");
        }

        // Components (buttons)
        if (!components.isEmpty()) {
            json.append("\"components\":[");
            for (int i = 0; i < components.size(); i++) {
                json.append(components.get(i).toJSON());
                if (i < components.size() - 1) json.append(",");
            }
            json.append("],");
        }

        // Remove trailing comma
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Send JSON to Discord
     */
    private void sendJSON(String json) throws IOException {
        URL url = new URL(this.url);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "ReportSystem-Webhook");
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        // Set timeouts to prevent blocking
        connection.setConnectTimeout(5000); // 5 seconds
        connection.setReadTimeout(5000);    // 5 seconds

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.connect();

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(bytes);
        }

        int responseCode = connection.getResponseCode();

        if (responseCode == 429) { // Rate limited
            plugin.getLogger().warning("[Webhook] Rate limited by Discord! Please reduce webhook frequency.");
        } else if (responseCode >= 400) {
            plugin.getLogger().warning("[Webhook] Discord returned error code: " + responseCode);

            // Log error response body for debugging
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                plugin.getLogger().warning("[Webhook] Error response: " + errorResponse.toString());
            } catch (Exception e) {
                // Ignore error stream reading errors
            }

            // Log the JSON that was sent
            if (plugin.getConfig().getBoolean("discord-webhook.debug", false)) {
                plugin.getLogger().warning("[Webhook] Sent JSON: " + json);
            }
        } else if (plugin.getConfig().getBoolean("discord-webhook.debug", false)) {
            plugin.getLogger().info("[Webhook] Message sent successfully (code: " + responseCode + ")");
        }

        connection.disconnect();
    }

    /**
     * Quote and escape string for JSON
     */
    private String quote(String str) {
        if (str == null) return "null";
        return "\"" + str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /**
     * Embed Object for rich messages
     */
    public static class EmbedObject {
        private String title;
        private String description;
        private String url;
        private Integer color;
        private Footer footer;
        private Thumbnail thumbnail;
        private Image image;
        private Author author;
        private List<Field> fields = new ArrayList<>();
        private String timestamp;

        public EmbedObject setTitle(String title) {
            this.title = title;
            return this;
        }

        public EmbedObject setDescription(String description) {
            this.description = description;
            return this;
        }

        public EmbedObject setUrl(String url) {
            this.url = url;
            return this;
        }

        public EmbedObject setColor(Color color) {
            this.color = color.getRGB() & 0xFFFFFF; // Remove alpha
            return this;
        }

        public EmbedObject setColor(int color) {
            this.color = color;
            return this;
        }

        public EmbedObject setFooter(String text, String icon) {
            this.footer = new Footer(text, icon);
            return this;
        }

        public EmbedObject setThumbnail(String url) {
            this.thumbnail = new Thumbnail(url);
            return this;
        }

        public EmbedObject setImage(String url) {
            this.image = new Image(url);
            return this;
        }

        public EmbedObject setAuthor(String name, String url, String icon) {
            this.author = new Author(name, url, icon);
            return this;
        }

        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

        public EmbedObject setTimestamp(Instant timestamp) {
            this.timestamp = timestamp.toString();
            return this;
        }

        public EmbedObject setTimestamp() {
            this.timestamp = Instant.now().toString();
            return this;
        }

        private String toJSON() {
            StringBuilder json = new StringBuilder("{");

            if (title != null) json.append("\"title\":").append(quote(title)).append(",");
            if (description != null) json.append("\"description\":").append(quote(description)).append(",");
            if (url != null) json.append("\"url\":").append(quote(url)).append(",");
            if (color != null) json.append("\"color\":").append(color).append(",");
            if (footer != null) json.append("\"footer\":").append(footer.toJSON()).append(",");
            if (thumbnail != null) json.append("\"thumbnail\":").append(thumbnail.toJSON()).append(",");
            if (image != null) json.append("\"image\":").append(image.toJSON()).append(",");
            if (author != null) json.append("\"author\":").append(author.toJSON()).append(",");
            if (timestamp != null) json.append("\"timestamp\":").append(quote(timestamp)).append(",");

            if (!fields.isEmpty()) {
                json.append("\"fields\":[");
                for (int i = 0; i < fields.size(); i++) {
                    json.append(fields.get(i).toJSON());
                    if (i < fields.size() - 1) json.append(",");
                }
                json.append("],");
            }

            if (json.charAt(json.length() - 1) == ',') {
                json.setLength(json.length() - 1);
            }

            json.append("}");
            return json.toString();
        }

        private static String quote(String str) {
            if (str == null) return "null";
            return "\"" + str
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }

        private static class Footer {
            private final String text;
            private final String iconUrl;

            Footer(String text, String iconUrl) {
                this.text = text;
                this.iconUrl = iconUrl;
            }

            String toJSON() {
                StringBuilder json = new StringBuilder("{");
                json.append("\"text\":").append(quote(text));
                if (iconUrl != null) json.append(",\"icon_url\":").append(quote(iconUrl));
                json.append("}");
                return json.toString();
            }
        }

        private static class Thumbnail {
            private final String url;

            Thumbnail(String url) {
                this.url = url;
            }

            String toJSON() {
                return "{\"url\":" + quote(url) + "}";
            }
        }

        private static class Image {
            private final String url;

            Image(String url) {
                this.url = url;
            }

            String toJSON() {
                return "{\"url\":" + quote(url) + "}";
            }
        }

        private static class Author {
            private final String name;
            private final String url;
            private final String iconUrl;

            Author(String name, String url, String iconUrl) {
                this.name = name;
                this.url = url;
                this.iconUrl = iconUrl;
            }

            String toJSON() {
                StringBuilder json = new StringBuilder("{");
                json.append("\"name\":").append(quote(name));
                if (url != null) json.append(",\"url\":").append(quote(url));
                if (iconUrl != null) json.append(",\"icon_url\":").append(quote(iconUrl));
                json.append("}");
                return json.toString();
            }
        }

        private static class Field {
            private final String name;
            private final String value;
            private final boolean inline;

            Field(String name, String value, boolean inline) {
                this.name = name;
                this.value = value;
                this.inline = inline;
            }

            String toJSON() {
                return "{\"name\":" + quote(name) +
                       ",\"value\":" + quote(value) +
                       ",\"inline\":" + inline + "}";
            }
        }
    }

    /**
     * Action Row for buttons
     */
    public static class ActionRow {
        private final List<Button> buttons = new ArrayList<>();

        public ActionRow addButton(Button button) {
            if (buttons.size() >= 5) {
                throw new IllegalStateException("Action row can only have 5 buttons");
            }
            this.buttons.add(button);
            return this;
        }

        String toJSON() {
            StringBuilder json = new StringBuilder("{\"type\":1,\"components\":[");
            for (int i = 0; i < buttons.size(); i++) {
                json.append(buttons.get(i).toJSON());
                if (i < buttons.size() - 1) json.append(",");
            }
            json.append("]}");
            return json.toString();
        }
    }

    /**
     * Button component
     */
    public static class Button {
        private final ButtonStyle style;
        private final String label;
        private final String url;
        private final String emoji;

        public Button(ButtonStyle style, String label, String url) {
            this(style, label, url, null);
        }

        public Button(ButtonStyle style, String label, String url, String emoji) {
            this.style = style;
            this.label = label;
            this.url = url;
            this.emoji = emoji;
        }

        String toJSON() {
            StringBuilder json = new StringBuilder("{");
            json.append("\"type\":2,");
            json.append("\"style\":").append(style.getValue()).append(",");
            json.append("\"label\":").append(quote(label));
            if (url != null) json.append(",\"url\":").append(quote(url));
            if (emoji != null) json.append(",\"emoji\":{\"name\":").append(quote(emoji)).append("}");
            json.append("}");
            return json.toString();
        }

        private static String quote(String str) {
            if (str == null) return "null";
            return "\"" + str
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
    }

    /**
     * Button styles
     */
    public enum ButtonStyle {
        PRIMARY(1),    // Blue
        SECONDARY(2),  // Grey
        SUCCESS(3),    // Green
        DANGER(4),     // Red
        LINK(5);       // Grey with link

        private final int value;

        ButtonStyle(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}

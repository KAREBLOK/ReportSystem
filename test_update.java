import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Test script to verify updateYamlFile logic
 */
public class TestUpdate {

    public static void main(String[] args) {
        // Simulate current messages_tr.yml (without new key)
        String currentYaml = "reports:\n" +
                             "  success: \"Success message\"\n" +
                             "  cannot-report-self: \"Cannot report self\"\n";

        // Simulate new messages_tr.yml from jar (with new key)
        String defaultYaml = "reports:\n" +
                             "  success: \"Success message\"\n" +
                             "  cannot-report-self: \"Cannot report self\"\n" +
                             "  already-recording: \"Already recording message\"\n";

        try {
            // Load current config
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(
                new StringReader(currentYaml)
            );

            // Load default config
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new StringReader(defaultYaml)
            );

            System.out.println("=== BEFORE UPDATE ===");
            System.out.println("Current config keys: " + currentConfig.getKeys(true));
            System.out.println("Contains 'reports.already-recording': " + currentConfig.contains("reports.already-recording"));

            // Add missing keys (simulate updateYamlFile logic)
            boolean updated = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!currentConfig.contains(key)) {
                    System.out.println("Adding missing key: " + key);
                    currentConfig.set(key, defaultConfig.get(key));
                    updated = true;
                }
            }

            System.out.println("\n=== AFTER UPDATE ===");
            System.out.println("Updated: " + updated);
            System.out.println("Current config keys: " + currentConfig.getKeys(true));
            System.out.println("Contains 'reports.already-recording': " + currentConfig.contains("reports.already-recording"));
            System.out.println("Value: " + currentConfig.getString("reports.already-recording"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

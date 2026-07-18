package tblack.stackwise.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path directory;

    @Test
    void firstRunCreatesVersionTwoFixedConfigurationWithoutInternalLimits() throws IOException {
        ConfigManager manager = new ConfigManager(directory);

        ConfigOperationResult result = manager.loadInitial();
        String json = Files.readString(directory.resolve("config.json"));

        assertTrue(result.success());
        assertTrue(json.contains("\"configVersion\": 2"));
        assertTrue(json.contains("\"enabled\": true"));
        assertTrue(json.contains("\"globalStackMode\": \"FIXED\""));
        assertTrue(json.contains("\"globalStackMultiplier\": 2.0"));
        assertTrue(json.contains("\"globalStackCap\": 999"));
        assertFalse(json.contains("minimumStack"));
        assertFalse(json.contains("maximumStack"));
    }

    @Test
    void versionOneConfigurationMigratesToFixedModeWithAnExactBackup() throws IOException {
        String versionOne = """
                {
                  "configVersion": 1,
                  "enabled": true,
                  "globalLimitEnabled": true,
                  "globalStackLimit": 1000,
                  "safeMode": true,
                  "allowDecreases": false,
                  "allowRuntimeDecreases": false,
                  "restoreUnmatchedItems": true,
                  "respectExternalChanges": true,
                  "commands": {
                    "primary": "stackwise",
                    "aliases": ["sw"],
                    "adminPermission": "stackwise.admin"
                  },
                  "rules": [
                    {
                      "id": "arrows",
                      "enabled": true,
                      "action": "SET",
                      "matchType": "PREFIX",
                      "value": "Weapon_Arrow",
                      "maxStack": 1000,
                      "priority": 600,
                      "allowUnsafe": false
                    }
                  ]
                }
                """;
        Files.writeString(directory.resolve("config.json"), versionOne);
        ConfigManager manager = new ConfigManager(directory);

        ConfigOperationResult result = manager.loadInitial();

        assertTrue(result.success());
        StackWiseConfig migrated = manager.get();
        assertEquals(2, migrated.configVersion);
        assertEquals(GlobalStackMode.FIXED, migrated.globalStackMode);
        assertEquals(1000, migrated.globalStackLimit);
        assertEquals(2.0D, migrated.globalStackMultiplier);
        assertEquals(999, migrated.globalStackCap);
        assertEquals("arrows", migrated.rules.getFirst().id);
        assertEquals(1000, migrated.rules.getFirst().maxStack);
        try (var files = Files.list(directory)) {
            Path backup = files
                    .filter(path -> path.getFileName().toString().startsWith("config.before-v2."))
                    .findFirst()
                    .orElseThrow();
            assertEquals(versionOne, Files.readString(backup));
        }
        String rewritten = Files.readString(directory.resolve("config.json"));
        assertTrue(rewritten.contains("\"configVersion\": 2"));
        assertTrue(rewritten.contains("\"globalStackMode\": \"FIXED\""));
    }

    @Test
    void optionalIconIdsAreNormalizedBeforeSaving() throws IOException {
        ConfigManager manager = new ConfigManager(directory);
        StackWiseConfig config = manager.loadInitial().config();
        config.rules.getFirst().iconItemId = "  Weapon_Arrow_Crude\0  ";

        ConfigOperationResult result = manager.save(config);

        assertTrue(result.success());
        assertEquals("Weapon_Arrow_Crude", manager.get().rules.getFirst().iconItemId);
    }

    @Test
    void unsupportedFutureVersionDoesNotReplaceTheActiveConfiguration() throws IOException {
        ConfigManager manager = new ConfigManager(directory);
        manager.loadInitial();
        Files.writeString(directory.resolve("config.json"), """
                {
                  "configVersion": 3,
                  "enabled": false,
                  "globalLimitEnabled": true,
                  "globalStackMode": "FIXED",
                  "globalStackLimit": 1000,
                  "globalStackMultiplier": 2.0,
                  "globalStackCap": 999,
                  "safeMode": true,
                  "allowDecreases": false,
                  "allowRuntimeDecreases": false,
                  "restoreUnmatchedItems": true,
                  "respectExternalChanges": true,
                  "commands": {
                    "primary": "stackwise",
                    "aliases": ["sw"],
                    "adminPermission": "stackwise.admin"
                  },
                  "rules": []
                }
                """);

        ConfigOperationResult result = manager.reload();

        assertFalse(result.success());
        assertTrue(manager.get().enabled);
        assertTrue(result.validation().errors().stream().anyMatch(issue -> issue.path().equals("configVersion")));
    }

    @Test
    void unknownGlobalModeDoesNotReplaceTheActiveConfiguration() throws IOException {
        ConfigManager manager = new ConfigManager(directory);
        manager.loadInitial();
        Files.writeString(directory.resolve("config.json"), """
                {
                  "configVersion": 2,
                  "enabled": false,
                  "globalLimitEnabled": true,
                  "globalStackMode": "UNKNOWN",
                  "globalStackLimit": 1000,
                  "globalStackMultiplier": 2.0,
                  "globalStackCap": 999,
                  "safeMode": true,
                  "allowDecreases": false,
                  "allowRuntimeDecreases": false,
                  "restoreUnmatchedItems": true,
                  "respectExternalChanges": true,
                  "commands": {
                    "primary": "stackwise",
                    "aliases": ["sw"],
                    "adminPermission": "stackwise.admin"
                  },
                  "rules": []
                }
                """);

        ConfigOperationResult result = manager.reload();

        assertFalse(result.success());
        assertTrue(manager.get().enabled);
        assertEquals(GlobalStackMode.FIXED, manager.get().globalStackMode);
    }

    @Test
    void importSaveCreatesTimestampedBackup() throws IOException {
        ConfigManager manager = new ConfigManager(directory);
        manager.loadInitial();
        StackWiseConfig candidate = manager.get();
        candidate.globalLimitEnabled = false;

        ConfigOperationResult result = manager.saveWithBackup(candidate, "config.before-import-overstacked");

        assertTrue(result.success());
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("config.before-import-overstacked.")));
        }
    }
}

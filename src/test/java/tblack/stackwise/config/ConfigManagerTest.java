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
    void firstRunCreatesVersionOneEnabledConfigurationWithoutInternalLimits() throws IOException {
        ConfigManager manager = new ConfigManager(directory);

        ConfigOperationResult result = manager.loadInitial();
        String json = Files.readString(directory.resolve("config.json"));

        assertTrue(result.success());
        assertTrue(json.contains("\"configVersion\": 1"));
        assertTrue(json.contains("\"enabled\": true"));
        assertFalse(json.contains("minimumStack"));
        assertFalse(json.contains("maximumStack"));
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
                  "configVersion": 2,
                  "enabled": false,
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
                  "rules": []
                }
                """);

        ConfigOperationResult result = manager.reload();

        assertFalse(result.success());
        assertTrue(manager.get().enabled);
        assertTrue(result.validation().errors().stream().anyMatch(issue -> issue.path().equals("configVersion")));
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

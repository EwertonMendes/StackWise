package tblack.stackwise.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class ConfigManager {
    private final Path modDirectory;
    private final Path configFile;
    private final Path catalogFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ConfigValidator validator = new ConfigValidator();
    private StackWiseConfig current;

    public ConfigManager(Path dataDirectory) {
        modDirectory = dataDirectory.toAbsolutePath().normalize();
        configFile = modDirectory.resolve("config.json");
        catalogFile = modDirectory.resolve("item-catalog.json");
    }

    public synchronized ConfigOperationResult loadInitial() {
        try {
            Files.createDirectories(modDirectory);
            if (!Files.exists(configFile)) {
                StackWiseConfig created = normalize(new StackWiseConfig());
                ValidationResult validation = validator.validate(created);
                write(created);
                current = created;
                return ConfigOperationResult.success(copy(created), validation, "Configuration created");
            }
            return readAndAccept();
        } catch (Exception exception) {
            StackWiseConfig fallback = normalize(new StackWiseConfig());
            current = fallback;
            return ConfigOperationResult.failure(copy(fallback), validator.validate(fallback), message(exception));
        }
    }

    public synchronized ConfigOperationResult reload() {
        if (!Files.exists(configFile)) return loadInitial();
        return readAndAccept();
    }

    public synchronized ConfigOperationResult save(StackWiseConfig candidate) {
        StackWiseConfig normalized = normalize(copy(candidate));
        ValidationResult validation = validator.validate(normalized);
        if (!validation.isValid()) {
            return ConfigOperationResult.failure(copy(current), validation, validation.firstError());
        }
        try {
            write(normalized);
            current = normalized;
            return ConfigOperationResult.success(copy(normalized), validation, "Configuration saved");
        } catch (IOException exception) {
            return ConfigOperationResult.failure(copy(current), validation, message(exception));
        }
    }

    public synchronized StackWiseConfig get() {
        if (current == null) loadInitial();
        return copy(current);
    }

    public synchronized ValidationResult validateCurrent() {
        return validator.validate(get());
    }

    public Path directory() {
        return modDirectory;
    }

    public Path configFile() {
        return configFile;
    }

    public Path catalogFile() {
        return catalogFile;
    }

    public Gson gson() {
        return gson;
    }

    private ConfigOperationResult readAndAccept() {
        StackWiseConfig previous = current == null ? normalize(new StackWiseConfig()) : current;
        String json = null;
        try {
            json = Files.readString(configFile, StandardCharsets.UTF_8);
            StackWiseConfig parsed = gson.fromJson(json, StackWiseConfig.class);
            if (parsed == null) throw new JsonParseException("Configuration is empty");
            StackWiseConfig normalized = normalize(parsed);
            ValidationResult validation = validator.validate(normalized);
            if (!validation.isValid()) {
                backupInvalid(json);
                current = previous;
                return ConfigOperationResult.failure(copy(previous), validation, validation.firstError());
            }
            write(normalized);
            current = normalized;
            return ConfigOperationResult.success(copy(normalized), validation, "Configuration loaded");
        } catch (Exception exception) {
            if (json != null) backupInvalid(json);
            current = previous;
            return ConfigOperationResult.failure(copy(previous), validator.validate(previous), message(exception));
        }
    }

    private StackWiseConfig normalize(StackWiseConfig config) {
        int sourceVersion = config.configVersion;
        if (sourceVersion < 2) {
            config.globalLimitEnabled = true;
            int configuredMaximum = config.maximumStack > 0 ? config.maximumStack : 9999;
            config.globalStackLimit = Math.min(1000, configuredMaximum);
        }
        config.configVersion = 2;
        if (config.globalStackLimit < 1) config.globalStackLimit = Math.min(1000, Math.max(1, config.maximumStack));
        if (config.commands == null) config.commands = new StackWiseConfig.Commands();
        if (config.commands.primary == null || config.commands.primary.isBlank()) config.commands.primary = "stackwise";
        config.commands.primary = config.commands.primary.trim().toLowerCase(Locale.ROOT);
        if (config.commands.aliases == null) config.commands.aliases = new ArrayList<>();
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add("sw");
        for (String alias : config.commands.aliases) {
            if (alias == null || alias.isBlank()) continue;
            String normalized = alias.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(config.commands.primary)) aliases.add(normalized);
        }
        config.commands.aliases = new ArrayList<>(aliases);
        if (config.commands.adminPermission == null || config.commands.adminPermission.isBlank()) {
            config.commands.adminPermission = "stackwise.admin";
        }
        if (config.rules == null) config.rules = new ArrayList<>();
        for (int index = 0; index < config.rules.size(); index++) {
            StackRule rule = config.rules.get(index);
            if (rule == null) continue;
            if (rule.id == null || rule.id.isBlank()) rule.id = "rule-" + (index + 1);
            rule.id = rule.id.trim().toLowerCase(Locale.ROOT);
            if (rule.action == null) rule.action = RuleAction.SET;
            if (rule.matchType == null) rule.matchType = MatchType.EXACT;
            if (rule.value == null) rule.value = "";
            rule.value = rule.value.trim();
        }
        return config;
    }

    private StackWiseConfig copy(StackWiseConfig source) {
        if (source == null) return normalize(new StackWiseConfig());
        return gson.fromJson(gson.toJson(source), StackWiseConfig.class);
    }

    private void write(StackWiseConfig config) throws IOException {
        Files.createDirectories(modDirectory);
        Path temporary = configFile.resolveSibling("config.json.tmp");
        Files.writeString(temporary, gson.toJson(config), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backupInvalid(String content) {
        try {
            Files.createDirectories(modDirectory);
            String timestamp = Long.toString(Instant.now().toEpochMilli());
            Files.writeString(modDirectory.resolve("config.invalid." + timestamp + ".json"), content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private String message(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }
}

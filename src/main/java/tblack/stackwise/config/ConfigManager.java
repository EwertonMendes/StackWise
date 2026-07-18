package tblack.stackwise.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ConfigValidator validator = new ConfigValidator();
    private StackWiseConfig current;

    public ConfigManager(Path dataDirectory) {
        modDirectory = dataDirectory.toAbsolutePath().normalize();
        configFile = modDirectory.resolve("config.json");
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
        return save(candidate, null);
    }

    public synchronized ConfigOperationResult saveWithBackup(StackWiseConfig candidate, String backupName) {
        if (backupName == null || backupName.isBlank()) return save(candidate);
        return save(candidate, backupName.trim());
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

    private ConfigOperationResult save(StackWiseConfig candidate, String backupName) {
        StackWiseConfig normalized = normalize(copy(candidate));
        ValidationResult validation = validator.validate(normalized);
        if (!validation.isValid()) {
            return ConfigOperationResult.failure(copy(current), validation, validation.firstError());
        }
        try {
            if (backupName != null) backupCurrent(backupName);
            write(normalized);
            current = normalized;
            return ConfigOperationResult.success(copy(normalized), validation, "Configuration saved");
        } catch (IOException exception) {
            return ConfigOperationResult.failure(copy(current), validation, message(exception));
        }
    }

    private ConfigOperationResult readAndAccept() {
        StackWiseConfig previous = current == null ? normalize(new StackWiseConfig()) : current;
        String json = null;
        try {
            json = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) throw new JsonParseException("Configuration root must be an object");
            JsonObject object = root.getAsJsonObject();
            int sourceVersion = sourceVersion(object);
            requireGlobalStackMode(object, sourceVersion);
            StackWiseConfig parsed = gson.fromJson(object, StackWiseConfig.class);
            if (parsed == null) throw new JsonParseException("Configuration is empty");
            boolean upgraded = upgradeSchema(parsed, sourceVersion);
            StackWiseConfig normalized = normalize(parsed);
            ValidationResult validation = validator.validate(normalized);
            if (!validation.isValid()) {
                backupInvalid(json);
                current = previous;
                return ConfigOperationResult.failure(copy(previous), validation, validation.firstError());
            }
            if (upgraded) backupOriginal(json, "config.before-v2");
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
        if (config.configVersion <= 0) config.configVersion = StackWiseConfig.CURRENT_CONFIG_VERSION;
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
            rule.iconItemId = normalizeOptionalItemId(rule.iconItemId);
        }
        return config;
    }

    private int sourceVersion(JsonObject object) {
        JsonElement value = object.get("configVersion");
        if (value == null || value.isJsonNull()) return 1;
        try {
            int version = value.getAsInt();
            return version <= 0 ? 1 : version;
        } catch (RuntimeException exception) {
            throw new JsonParseException("configVersion must be an integer", exception);
        }
    }

    private boolean upgradeSchema(StackWiseConfig config, int sourceVersion) {
        if (sourceVersion != 1) return false;
        config.configVersion = StackWiseConfig.CURRENT_CONFIG_VERSION;
        config.globalStackMode = GlobalStackMode.FIXED;
        config.globalStackMultiplier = 2.0D;
        config.globalStackCap = 999;
        return true;
    }

    private void requireGlobalStackMode(JsonObject object, int sourceVersion) {
        if (sourceVersion < StackWiseConfig.CURRENT_CONFIG_VERSION) return;
        JsonElement value = object.get("globalStackMode");
        if (value == null || value.isJsonNull()) {
            throw new JsonParseException("globalStackMode is required");
        }
        try {
            GlobalStackMode.valueOf(value.getAsString());
        } catch (RuntimeException exception) {
            throw new JsonParseException("Unsupported globalStackMode: " + value, exception);
        }
    }

    private String normalizeOptionalItemId(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        return normalized.isEmpty() ? null : normalized;
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

    private void backupCurrent(String backupName) throws IOException {
        if (!Files.isRegularFile(configFile)) return;
        Files.createDirectories(modDirectory);
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        Path backup = modDirectory.resolve(backupName + "." + timestamp + ".json");
        Files.copy(configFile, backup);
    }

    private void backupOriginal(String content, String backupName) throws IOException {
        Files.createDirectories(modDirectory);
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        Files.writeString(
                modDirectory.resolve(backupName + "." + timestamp + ".json"),
                content,
                StandardCharsets.UTF_8
        );
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

package tblack.stackwise.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class OverstackedRuleMigration implements RuleMigrationSource {
    private static final String SOURCE_ID = "overstacked";
    private static final String FILE_NAME = "MaxStackSizes.json";
    private static final int EXACT_PRIORITY = 700;
    private static final int REGEX_PRIORITY = 600;
    private static final int PREFIX_PRIORITY = 500;

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public RuleMigrationResult migrate(Path stackWiseDirectory, StackWiseConfig currentConfig) {
        Path sourceFile = sourceFile(stackWiseDirectory);
        if (!Files.isRegularFile(sourceFile)) {
            return RuleMigrationResult.failure("messages.import_file_not_found", null, FILE_NAME);
        }
        try {
            ParsedConfig parsed = read(sourceFile);
            List<StackRule> rules = createRules(parsed);
            StackWiseConfig candidate = copy(currentConfig);
            candidate.rules = rules;
            candidate.globalLimitEnabled = false;
            int prefixCount = count(rules, MatchType.PREFIX);
            int exactCount = count(rules, MatchType.EXACT);
            int regexCount = count(rules, MatchType.REGEX);
            return RuleMigrationResult.success(
                    candidate,
                    "messages.import_success",
                    parsed.itemIds.size() + parsed.patterns.size(),
                    prefixCount,
                    exactCount,
                    regexCount
            );
        } catch (MigrationException exception) {
            return RuleMigrationResult.failure(exception.messageKey, exception, exception.messageArgs);
        } catch (MigrationRuntimeException exception) {
            return RuleMigrationResult.failure(exception.messageKey, exception, exception.messageArgs);
        } catch (IOException | RuntimeException exception) {
            return RuleMigrationResult.failure("messages.import_read_failed", exception, FILE_NAME);
        }
    }

    private Path sourceFile(Path stackWiseDirectory) {
        Path parent = stackWiseDirectory.getParent();
        Path modsDirectory = parent == null ? stackWiseDirectory : parent;
        return modsDirectory.resolve("Darkhax_Overstacked").resolve(FILE_NAME).normalize();
    }

    private ParsedConfig read(Path sourceFile) throws IOException, MigrationException {
        JsonElement root;
        try (Reader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        }
        if (!root.isJsonObject()) throw new MigrationException("messages.import_invalid_root");
        JsonObject object = root.getAsJsonObject();
        Map<String, Integer> itemIds = readSection(object, "ItemIds", false);
        Map<String, Integer> patterns = readSection(object, "Patterns", true);
        if (itemIds.isEmpty() && patterns.isEmpty()) throw new MigrationException("messages.import_empty_file", FILE_NAME);
        return new ParsedConfig(itemIds, patterns);
    }

    private Map<String, Integer> readSection(JsonObject root, String sectionName, boolean regex) throws MigrationException {
        JsonElement section = root.get(sectionName);
        if (section == null || section.isJsonNull()) return Map.of();
        if (!section.isJsonObject()) throw new MigrationException("messages.import_invalid_section", sectionName);
        Map<String, Integer> values = new LinkedHashMap<>();
        section.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.put(entry.getKey(), parseEntry(sectionName, entry.getKey(), entry.getValue(), regex)));
        return values;
    }

    private int parseEntry(String sectionName, String key, JsonElement value, boolean regex) {
        if (key == null || key.isBlank()) throw new MigrationRuntimeException("messages.import_invalid_entry", sectionName);
        if (regex) validateRegex(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new MigrationRuntimeException("messages.import_invalid_value", key, StackWiseConfig.MIN_STACK_LIMIT, StackWiseConfig.MAX_STACK_LIMIT);
        }
        try {
            BigDecimal number = value.getAsBigDecimal().stripTrailingZeros();
            if (number.scale() > 0) {
                throw new MigrationRuntimeException("messages.import_invalid_value", key, StackWiseConfig.MIN_STACK_LIMIT, StackWiseConfig.MAX_STACK_LIMIT);
            }
            int parsed = number.intValueExact();
            if (parsed < StackWiseConfig.MIN_STACK_LIMIT || parsed > StackWiseConfig.MAX_STACK_LIMIT) {
                throw new MigrationRuntimeException("messages.import_invalid_value", key, StackWiseConfig.MIN_STACK_LIMIT, StackWiseConfig.MAX_STACK_LIMIT);
            }
            return parsed;
        } catch (ArithmeticException exception) {
            throw new MigrationRuntimeException("messages.import_invalid_value", key, StackWiseConfig.MIN_STACK_LIMIT, StackWiseConfig.MAX_STACK_LIMIT);
        }
    }

    private void validateRegex(String value) {
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw new MigrationRuntimeException("messages.import_invalid_regex", value);
        }
    }

    private List<StackRule> createRules(ParsedConfig parsed) throws MigrationException {
        try {
            List<StackRule> rules = new ArrayList<>();
            Set<String> ruleIds = new HashSet<>();
            Map<String, List<ItemEntry>> families = groupFamilies(parsed.itemIds);
            Set<String> groupedItems = new HashSet<>();

            for (Map.Entry<String, List<ItemEntry>> family : families.entrySet()) {
                List<ItemEntry> entries = family.getValue();
                if (!canUsePrefix(entries)) continue;
                int stackLimit = entries.getFirst().stackLimit;
                rules.add(rule(ruleIds, "prefix", family.getKey(), MatchType.PREFIX, family.getKey(), stackLimit, PREFIX_PRIORITY));
                entries.forEach(entry -> groupedItems.add(entry.itemId));
            }

            parsed.itemIds.entrySet().stream()
                    .filter(entry -> !groupedItems.contains(entry.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> rules.add(rule(
                            ruleIds,
                            "exact",
                            entry.getKey(),
                            MatchType.EXACT,
                            entry.getKey(),
                            entry.getValue(),
                            EXACT_PRIORITY
                    )));

            parsed.patterns.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> rules.add(rule(
                            ruleIds,
                            "regex",
                            entry.getKey(),
                            MatchType.REGEX,
                            entry.getKey(),
                            entry.getValue(),
                            REGEX_PRIORITY
                    )));

            rules.sort(Comparator
                    .comparingInt((StackRule rule) -> rule.priority).reversed()
                    .thenComparing(rule -> rule.matchType.name())
                    .thenComparing(rule -> rule.value));
            return rules;
        } catch (MigrationRuntimeException exception) {
            throw new MigrationException(exception.messageKey, exception.messageArgs);
        }
    }

    private Map<String, List<ItemEntry>> groupFamilies(Map<String, Integer> itemIds) {
        Map<String, List<ItemEntry>> families = new LinkedHashMap<>();
        itemIds.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String prefix = familyPrefix(entry.getKey());
            if (prefix == null) return;
            families.computeIfAbsent(prefix, ignored -> new ArrayList<>())
                    .add(new ItemEntry(entry.getKey(), entry.getValue()));
        });
        return families;
    }

    private String familyPrefix(String itemId) {
        String[] segments = itemId.split("_", -1);
        if (segments.length < 3 || segments[0].isBlank() || segments[1].isBlank()) return null;
        return segments[0] + "_" + segments[1] + "_";
    }

    private boolean canUsePrefix(List<ItemEntry> entries) {
        if (entries.size() < 2) return false;
        int expected = entries.getFirst().stackLimit;
        return entries.stream().allMatch(entry -> entry.stackLimit == expected);
    }

    private StackRule rule(
            Set<String> existingIds,
            String kind,
            String idSource,
            MatchType matchType,
            String value,
            int maxStack,
            int priority
    ) {
        StackRule rule = new StackRule();
        rule.id = uniqueRuleId(existingIds, kind, idSource);
        rule.enabled = true;
        rule.action = RuleAction.SET;
        rule.matchType = matchType;
        rule.value = value;
        rule.maxStack = maxStack;
        rule.priority = priority;
        rule.allowUnsafe = false;
        return rule;
    }

    private String uniqueRuleId(Set<String> existingIds, String kind, String source) {
        String slug = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = Integer.toUnsignedString(source.hashCode(), 36);
        String prefix = "import-overstacked-" + kind + "-";
        int available = 64 - prefix.length();
        if (slug.length() > available) {
            String hash = Integer.toUnsignedString(source.hashCode(), 36);
            int textLength = Math.max(1, available - hash.length() - 1);
            slug = slug.substring(0, Math.min(textLength, slug.length())) + "-" + hash;
        }
        String base = prefix + slug;
        String candidate = base;
        int suffix = 2;
        while (!existingIds.add(candidate)) {
            String addition = "-" + suffix++;
            int maximumBase = 64 - addition.length();
            candidate = base.substring(0, Math.min(base.length(), maximumBase)) + addition;
        }
        return candidate;
    }

    private int count(List<StackRule> rules, MatchType type) {
        return (int) rules.stream().filter(rule -> rule.matchType == type).count();
    }

    private StackWiseConfig copy(StackWiseConfig config) {
        StackWiseConfig source = config == null ? new StackWiseConfig() : config;
        return gson.fromJson(gson.toJson(source), StackWiseConfig.class);
    }

    private record ParsedConfig(Map<String, Integer> itemIds, Map<String, Integer> patterns) {
    }

    private record ItemEntry(String itemId, int stackLimit) {
    }

    private static final class MigrationException extends Exception {
        private final String messageKey;
        private final Object[] messageArgs;

        private MigrationException(String messageKey, Object... messageArgs) {
            super(messageKey);
            this.messageKey = messageKey;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        }
    }

    private static final class MigrationRuntimeException extends RuntimeException {
        private final String messageKey;
        private final Object[] messageArgs;

        private MigrationRuntimeException(String messageKey, Object... messageArgs) {
            super(messageKey);
            this.messageKey = messageKey;
            this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        }
    }
}

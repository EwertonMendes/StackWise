package tblack.stackwise.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tblack.stackwise.config.StackWiseConfig;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.StackRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverstackedRuleMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileReturnsAPlayerFacingFailure() {
        RuleMigrationResult result = migrate(null);

        assertFalse(result.success());
        assertEquals("messages.import_file_not_found", result.messageKey());
        assertEquals("MaxStackSizes.json", result.messageArgs()[0]);
    }

    @Test
    void sameFamilyAndValueBecomeOnePrefixRule() throws IOException {
        write("""
                {
                  "ItemIds": {
                    "Ingredient_Crystal_Blue": 1000,
                    "Ingredient_Crystal_Green": 1000,
                    "Ore_Onyxium": 1000,
                    "Ore_Onyxium_Basalt": 1000,
                    "Ore_Onyxium_Stone": 1000
                  },
                  "Patterns": {}
                }
                """);

        RuleMigrationResult result = migrate(new StackWiseConfig());

        assertTrue(result.success());
        assertFalse(result.config().globalLimitEnabled);
        assertRule(result.config().rules, MatchType.PREFIX, "Ingredient_Crystal_", 1000);
        assertRule(result.config().rules, MatchType.PREFIX, "Ore_Onyxium_", 1000);
        assertRule(result.config().rules, MatchType.EXACT, "Ore_Onyxium", 1000);
        assertEquals(3, result.config().rules.size());
    }

    @Test
    void mixedValuesKeepTheWholeFamilyExact() throws IOException {
        write("""
                {
                  "ItemIds": {
                    "Ingredient_Crystal_Blue": 1000,
                    "Ingredient_Crystal_Green": 1000,
                    "Ingredient_Crystal_Red": 500
                  },
                  "Patterns": {}
                }
                """);

        RuleMigrationResult result = migrate(new StackWiseConfig());

        assertTrue(result.success());
        assertEquals(3, result.config().rules.size());
        assertTrue(result.config().rules.stream().allMatch(rule -> rule.matchType == MatchType.EXACT));
    }

    @Test
    void patternsRemainRegularExpressions() throws IOException {
        write("""
                {
                  "ItemIds": {},
                  "Patterns": {
                    "^Plant_Seeds_.*$": 250
                  }
                }
                """);

        RuleMigrationResult result = migrate(new StackWiseConfig());

        assertTrue(result.success());
        assertRule(result.config().rules, MatchType.REGEX, "^Plant_Seeds_.*$", 250);
    }

    @Test
    void invalidValuesAndRegexAreRejected() throws IOException {
        write("""
                {
                  "ItemIds": {
                    "Ingredient_Stick": 1000000
                  },
                  "Patterns": {}
                }
                """);
        RuleMigrationResult invalidValue = migrate(new StackWiseConfig());
        assertFalse(invalidValue.success());
        assertEquals("messages.import_invalid_value", invalidValue.messageKey());

        write("""
                {
                  "ItemIds": {},
                  "Patterns": {
                    "[": 100
                  }
                }
                """);
        RuleMigrationResult invalidRegex = migrate(new StackWiseConfig());
        assertFalse(invalidRegex.success());
        assertEquals("messages.import_invalid_regex", invalidRegex.messageKey());
    }

    @Test
    void generatedRuleIdsAreDeterministic() throws IOException {
        write("""
                {
                  "ItemIds": {
                    "Plant_Seeds_Azure": 1000,
                    "Plant_Seeds_Beech": 1000
                  },
                  "Patterns": {}
                }
                """);

        List<String> first = migrate(new StackWiseConfig()).config().rules.stream().map(rule -> rule.id).toList();
        List<String> second = migrate(new StackWiseConfig()).config().rules.stream().map(rule -> rule.id).toList();

        assertEquals(first, second);
    }

    private RuleMigrationResult migrate(StackWiseConfig config) {
        Path stackWiseDirectory = temporaryDirectory.resolve("Tblack_StackWise");
        return new OverstackedRuleMigration().migrate(stackWiseDirectory, config);
    }

    private void write(String content) throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("Darkhax_Overstacked");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("MaxStackSizes.json"), content);
    }

    private void assertRule(List<StackRule> rules, MatchType type, String value, int maxStack) {
        assertTrue(rules.stream().anyMatch(rule ->
                rule.matchType == type && rule.value.equals(value) && rule.maxStack == maxStack
        ));
    }
}

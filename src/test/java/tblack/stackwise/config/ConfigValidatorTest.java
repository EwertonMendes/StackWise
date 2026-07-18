package tblack.stackwise.config;

import org.junit.jupiter.api.Test;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void defaultConfigurationIsEnabledAndValid() {
        StackWiseConfig config = new StackWiseConfig();

        assertEquals(2, config.configVersion);
        assertTrue(config.enabled);
        assertTrue(config.globalLimitEnabled);
        assertEquals(GlobalStackMode.FIXED, config.globalStackMode);
        assertEquals(1000, config.globalStackLimit);
        assertEquals(2.0D, config.globalStackMultiplier);
        assertEquals(999, config.globalStackCap);
        assertEquals(999999, StackWiseConfig.MAX_STACK_LIMIT);
        assertTrue(validator.validate(config).isValid());
    }

    @Test
    void defaultConfigurationDemonstratesEveryActionAndMatchType() {
        StackWiseConfig config = new StackWiseConfig();

        for (MatchType type : MatchType.values()) {
            assertTrue(config.rules.stream().anyMatch(rule -> rule.matchType == type), type.name());
        }
        for (RuleAction action : RuleAction.values()) {
            assertTrue(config.rules.stream().anyMatch(rule -> rule.action == action), action.name());
        }
    }

    @Test
    void maximumSupportedLimitIsAccepted() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackLimit = StackWiseConfig.MAX_STACK_LIMIT;
        config.rules.getFirst().maxStack = StackWiseConfig.MAX_STACK_LIMIT;

        assertTrue(validator.validate(config).isValid());
    }

    @Test
    void valuesAboveHardLimitAreRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackLimit = StackWiseConfig.MAX_STACK_LIMIT + 1;
        config.rules.getFirst().maxStack = StackWiseConfig.MAX_STACK_LIMIT + 1;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackLimit")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().endsWith(".maxStack")));
    }

    @Test
    void valuesBelowMinimumAreRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackLimit = 0;
        config.globalStackMultiplier = 0.99D;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackMultiplier")));
    }

    @Test
    void invalidMultiplierAndCapAreRejectedEvenInFixedMode() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMultiplier = Double.NaN;
        config.globalStackCap = 0;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackMultiplier")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackCap")));
    }

    @Test
    void missingModeAndOutOfRangeMultiplierAreRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackMode = null;
        config.globalStackMultiplier = StackWiseConfig.MAX_STACK_MULTIPLIER + 1.0D;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackMode")));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackMultiplier")));
    }

    @Test
    void duplicateRuleIdsAreRejectedIgnoringCase() {
        StackWiseConfig config = new StackWiseConfig();
        config.rules = new ArrayList<>();
        config.rules.add(rule("ores", MatchType.PREFIX, "Ore_"));
        config.rules.add(rule("ORES", MatchType.EXACT, "Ore_Iron"));

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.message().contains("unique")));
    }

    @Test
    void invalidRegexIsRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.rules = new ArrayList<>();
        config.rules.add(rule("broken-regex", MatchType.REGEX, "["));

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.message().contains("regular expression")));
    }

    @Test
    void excessivelyLongOptionalIconIdIsRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.rules.getFirst().iconItemId = "x".repeat(257);

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().endsWith(".iconItemId")));
    }

    @Test
    void invalidRuleIdIsRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.rules = new ArrayList<>();
        config.rules.add(rule("Invalid Rule", MatchType.EXACT, "Ingredient_Stick"));

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().endsWith(".id")));
    }

    private StackRule rule(String id, MatchType type, String value) {
        StackRule rule = new StackRule();
        rule.id = id;
        rule.matchType = type;
        rule.value = value;
        rule.maxStack = 100;
        return rule;
    }
}

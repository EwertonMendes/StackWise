package tblack.stackwise.config;

import org.junit.jupiter.api.Test;
import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    void defaultConfigurationIsValid() {
        StackWiseConfig config = new StackWiseConfig();

        assertTrue(config.globalLimitEnabled);
        assertTrue(config.globalStackLimit == 1000);
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
        assertTrue(config.rules.stream().anyMatch(rule ->
                rule.action == RuleAction.SET
                        && rule.matchType == MatchType.PREFIX
                        && rule.value.equals("Weapon_Arrow")
                        && rule.maxStack == 1000
        ));
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
    void valuesAboveHardLimitAreRejected() {
        StackWiseConfig config = new StackWiseConfig();
        config.maximumStack = 10000;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
    }


    @Test
    void globalLimitMustStayWithinTheConfiguredRange() {
        StackWiseConfig config = new StackWiseConfig();
        config.globalStackLimit = 10000;

        ValidationResult result = validator.validate(config);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(issue -> issue.path().equals("globalStackLimit")));
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

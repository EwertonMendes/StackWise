package tblack.stackwise.rule;

import org.junit.jupiter.api.Test;
import tblack.stackwise.config.StackWiseConfig;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCompilerTest {
    private final RuleCompiler compiler = new RuleCompiler();

    @Test
    void exactRuleWinsWhenPriorityIsEqual() {
        StackWiseConfig config = config(
                rule("prefix", MatchType.PREFIX, "Rock_", 200, 100, RuleAction.SET),
                rule("exact", MatchType.EXACT, "Rock_Stone", 300, 100, RuleAction.SET)
        );

        RuleResolution resolution = compiler.compile(config).resolve("Rock_Stone");

        assertTrue(resolution.matched());
        assertEquals("exact", resolution.rule().id);
        assertEquals(300, resolution.rule().maxStack);
    }

    @Test
    void higherPriorityWinsBeforeSpecificity() {
        StackWiseConfig config = config(
                rule("prefix", MatchType.PREFIX, "Rock_", 200, 200, RuleAction.SET),
                rule("exact", MatchType.EXACT, "Rock_Stone", 300, 100, RuleAction.SET)
        );

        RuleResolution resolution = compiler.compile(config).resolve("Rock_Stone");

        assertEquals("prefix", resolution.rule().id);
    }

    @Test
    void exclusionStopsLowerPrioritySetRule() {
        StackWiseConfig config = config(
                rule("exclude", MatchType.EXACT, "Rock_Bedrock", 1, 1000, RuleAction.EXCLUDE),
                rule("rocks", MatchType.PREFIX, "Rock_", 250, 100, RuleAction.SET)
        );

        RuleResolution resolution = compiler.compile(config).resolve("Rock_Bedrock");

        assertTrue(resolution.matched());
        assertTrue(resolution.excluded());
        assertEquals("exclude", resolution.rule().id);
    }

    @Test
    void globEscapesRegexCharacters() {
        StackWiseConfig config = config(rule("glob", MatchType.GLOB, "Item.[A]?*", 50, 0, RuleAction.SET));
        CompiledRuleSet rules = compiler.compile(config);

        assertTrue(rules.resolve("Item.[A]Value").matched());
        assertFalse(rules.resolve("ItemXAValue").matched());
    }

    @Test
    void disabledRulesAreNotCompiled() {
        StackRule rule = rule("disabled", MatchType.PREFIX, "Rock_", 250, 0, RuleAction.SET);
        rule.enabled = false;
        StackWiseConfig config = config(rule);

        assertEquals(0, compiler.compile(config).size());
    }

    private StackWiseConfig config(StackRule... rules) {
        StackWiseConfig config = new StackWiseConfig();
        config.rules = new ArrayList<>();
        config.rules.addAll(java.util.List.of(rules));
        return config;
    }

    private StackRule rule(String id, MatchType type, String value, int maxStack, int priority, RuleAction action) {
        StackRule rule = new StackRule();
        rule.id = id;
        rule.matchType = type;
        rule.value = value;
        rule.maxStack = maxStack;
        rule.priority = priority;
        rule.action = action;
        return rule;
    }
}

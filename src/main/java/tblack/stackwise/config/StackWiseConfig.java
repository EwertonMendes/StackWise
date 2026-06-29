package tblack.stackwise.config;

import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.ArrayList;
import java.util.List;

public class StackWiseConfig {
    public int configVersion = 2;
    public boolean enabled = true;
    public boolean globalLimitEnabled = true;
    public int globalStackLimit = 1000;
    public boolean safeMode = true;
    public boolean allowDecreases = false;
    public boolean allowRuntimeDecreases = false;
    public boolean restoreUnmatchedItems = true;
    public boolean respectExternalChanges = true;
    public int minimumStack = 1;
    public int maximumStack = 9999;
    public Commands commands = new Commands();
    public List<StackRule> rules = defaultRules();

    public static class Commands {
        public String primary = "stackwise";
        public List<String> aliases = new ArrayList<>(List.of("sw"));
        public String adminPermission = "stackwise.admin";
    }

    public static List<StackRule> defaultRules() {
        List<StackRule> defaults = new ArrayList<>();
        defaults.add(setRule("arrows", MatchType.PREFIX, "Weapon_Arrow", 1000, 600));
        defaults.add(setRule("sticks", MatchType.EXACT, "Ingredient_Stick", 250, 500));
        defaults.add(setRule("seeds", MatchType.GLOB, "Plant_Seeds_*", 250, 400));
        defaults.add(setRule("crystal-colors", MatchType.REGEX, "^Ingredient_Crystal_(Cyan|Green|Purple|Red|White)$", 150, 350));
        defaults.add(setRule("white-items", MatchType.SUFFIX, "_White", 150, 300));
        defaults.add(excludeRule("exclude-swords", MatchType.PREFIX, "Weapon_Sword_", 1000));
        return defaults;
    }

    private static StackRule setRule(String id, MatchType type, String value, int maxStack, int priority) {
        StackRule rule = baseRule(id, type, value, priority);
        rule.action = RuleAction.SET;
        rule.maxStack = maxStack;
        return rule;
    }

    private static StackRule excludeRule(String id, MatchType type, String value, int priority) {
        StackRule rule = baseRule(id, type, value, priority);
        rule.action = RuleAction.EXCLUDE;
        rule.maxStack = 1;
        return rule;
    }

    private static StackRule baseRule(String id, MatchType type, String value, int priority) {
        StackRule rule = new StackRule();
        rule.id = id;
        rule.enabled = true;
        rule.matchType = type;
        rule.value = value;
        rule.priority = priority;
        rule.allowUnsafe = false;
        return rule;
    }
}

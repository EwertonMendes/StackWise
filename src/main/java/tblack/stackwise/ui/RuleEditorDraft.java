package tblack.stackwise.ui;

import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.Locale;

public record RuleEditorDraft(
        String ruleId,
        boolean enabled,
        RuleAction action,
        MatchType matchType,
        String matchValue,
        int maxStack,
        int priority,
        boolean allowUnsafe,
        String iconItemId
) {
    public RuleEditorDraft {
        ruleId = ruleId == null ? "" : ruleId;
        action = action == null ? RuleAction.SET : action;
        matchType = matchType == null ? MatchType.EXACT : matchType;
        matchValue = matchValue == null ? "" : matchValue;
        iconItemId = normalizeIconItemId(iconItemId);
    }

    public static RuleEditorDraft fromRule(StackRule rule) {
        return new RuleEditorDraft(
                rule.id,
                rule.enabled,
                rule.action,
                rule.matchType,
                rule.value,
                rule.maxStack,
                rule.priority,
                rule.allowUnsafe,
                rule.iconItemId
        );
    }

    public StackRule toRule() {
        StackRule rule = new StackRule();
        rule.id = normalizeRuleId(ruleId);
        rule.enabled = enabled;
        rule.action = action;
        rule.matchType = matchType;
        rule.value = matchValue.trim();
        rule.maxStack = maxStack;
        rule.priority = priority;
        rule.allowUnsafe = allowUnsafe;
        rule.iconItemId = iconItemId;
        return rule;
    }

    public RuleEditorDraft withIconItemId(String value) {
        return new RuleEditorDraft(
                ruleId,
                enabled,
                action,
                matchType,
                matchValue,
                maxStack,
                priority,
                allowUnsafe,
                value
        );
    }

    private static String normalizeRuleId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static String normalizeIconItemId(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

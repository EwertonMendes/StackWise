package tblack.stackwise.config;

import tblack.stackwise.rule.MatchType;
import tblack.stackwise.rule.RuleAction;
import tblack.stackwise.rule.StackRule;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConfigValidator {
    private static final int MAX_RULES = 5000;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 256;

    public ValidationResult validate(StackWiseConfig config) {
        ValidationResult result = new ValidationResult();
        if (config == null) {
            result.addError("config", "validation.configuration_missing");
            return result;
        }
        if (config.minimumStack < 1) result.addError("minimumStack", "validation.minimum_at_least", 1);
        if (config.maximumStack < config.minimumStack) result.addError("maximumStack", "validation.maximum_not_below_minimum");
        if (config.maximumStack > 9_999) result.addError("maximumStack", "validation.maximum_not_above", 9999);
        if (config.globalStackLimit < config.minimumStack) result.addError("globalStackLimit", "validation.minimum_at_least", config.minimumStack);
        if (config.globalStackLimit > config.maximumStack) result.addError("globalStackLimit", "validation.maximum_not_above", config.maximumStack);
        if (config.commands == null) result.addError("commands", "validation.section_missing");
        if (config.commands != null) {
            if (blank(config.commands.primary)) result.addError("commands.primary", "validation.command_required");
            if (blank(config.commands.adminPermission)) result.addError("commands.adminPermission", "validation.permission_required");
        }
        if (config.rules == null) {
            result.addError("rules", "validation.rules_missing");
            return result;
        }
        if (config.rules.size() > MAX_RULES) result.addError("rules", "validation.too_many_rules", MAX_RULES);
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < config.rules.size(); index++) {
            validateRule(config, config.rules.get(index), index, ids, result);
        }
        return result;
    }

    private void validateRule(StackWiseConfig config, StackRule rule, int index, Set<String> ids, ValidationResult result) {
        String path = "rules[" + index + "]";
        if (rule == null) {
            result.addError(path, "validation.rule_missing");
            return;
        }
        if (blank(rule.id)) {
            result.addError(path + ".id", "validation.rule_id_required");
        } else {
            String normalized = rule.id.trim().toLowerCase(Locale.ROOT);
            if (rule.id.length() > MAX_ID_LENGTH) result.addError(path + ".id", "validation.max_characters", MAX_ID_LENGTH);
            if (!normalized.matches("[a-z0-9][a-z0-9._-]*")) result.addError(path + ".id", "validation.rule_id_characters");
            if (!ids.add(normalized)) result.addError(path + ".id", "validation.rule_id_unique");
        }
        if (rule.action == null) result.addError(path + ".action", "validation.action_required");
        if (rule.matchType == null) result.addError(path + ".matchType", "validation.match_type_required");
        if (blank(rule.value)) {
            result.addError(path + ".value", "validation.match_value_required");
        } else {
            if (rule.value.length() > MAX_VALUE_LENGTH) result.addError(path + ".value", "validation.max_characters", MAX_VALUE_LENGTH);
            if (rule.matchType == MatchType.REGEX) validateRegex(path + ".value", rule.value, result);
        }
        if (rule.action == RuleAction.SET) {
            if (rule.maxStack < config.minimumStack) result.addError(path + ".maxStack", "validation.minimum_at_least", config.minimumStack);
            if (rule.maxStack > config.maximumStack) result.addError(path + ".maxStack", "validation.maximum_not_above", config.maximumStack);
        }
        if (rule.priority < -10000 || rule.priority > 10000) result.addError(path + ".priority", "validation.priority_range");
    }

    private void validateRegex(String path, String value, ValidationResult result) {
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            result.addError(path, "validation.invalid_regex", exception.getDescription());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

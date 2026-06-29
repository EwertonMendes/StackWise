package tblack.stackwise.rule;

import tblack.stackwise.config.StackWiseConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class RuleCompiler {
    public CompiledRuleSet compile(StackWiseConfig config) {
        List<CompiledRule> compiled = new ArrayList<>();
        for (int index = 0; index < config.rules.size(); index++) {
            StackRule rule = config.rules.get(index);
            if (rule == null || !rule.enabled) continue;
            Pattern pattern = switch (rule.matchType) {
                case GLOB -> Pattern.compile(globToRegex(rule.value));
                case REGEX -> Pattern.compile(rule.value);
                default -> null;
            };
            compiled.add(new CompiledRule(rule.copy(), pattern, index));
        }
        compiled.sort(Comparator
                .comparingInt((CompiledRule rule) -> rule.source().priority).reversed()
                .thenComparing(Comparator.comparingInt(CompiledRule::specificity).reversed())
                .thenComparingInt(CompiledRule::order));
        return new CompiledRuleSet(compiled);
    }

    static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> regex.append('\\').append(character);
                default -> regex.append(character);
            }
        }
        return regex.append('$').toString();
    }
}

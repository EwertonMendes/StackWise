package tblack.stackwise.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompiledRuleSet {
    private final List<CompiledRule> rules;
    private final Map<String, Integer> matchCounts = new LinkedHashMap<>();

    CompiledRuleSet(List<CompiledRule> rules) {
        this.rules = List.copyOf(rules);
        for (CompiledRule rule : rules) {
            matchCounts.put(rule.source().id, 0);
        }
    }

    public RuleResolution resolve(String itemId) {
        for (CompiledRule compiled : rules) {
            if (!compiled.matches(itemId)) continue;
            matchCounts.computeIfPresent(compiled.source().id, (key, count) -> count + 1);
            return new RuleResolution(compiled.source(), compiled.source().action == RuleAction.EXCLUDE);
        }
        return RuleResolution.none();
    }

    public Map<String, Integer> matchCounts() {
        return Collections.unmodifiableMap(matchCounts);
    }

    public int size() {
        return rules.size();
    }
}

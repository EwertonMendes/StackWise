package tblack.stackwise.rule;

public class StackRule {
    public String id = "new-rule";
    public boolean enabled = true;
    public RuleAction action = RuleAction.SET;
    public MatchType matchType = MatchType.EXACT;
    public String value = "";
    public int maxStack = 100;
    public int priority = 0;
    public boolean allowUnsafe = false;

    public StackRule copy() {
        StackRule copy = new StackRule();
        copy.id = id;
        copy.enabled = enabled;
        copy.action = action;
        copy.matchType = matchType;
        copy.value = value;
        copy.maxStack = maxStack;
        copy.priority = priority;
        copy.allowUnsafe = allowUnsafe;
        return copy;
    }
}

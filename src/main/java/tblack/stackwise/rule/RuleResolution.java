package tblack.stackwise.rule;

public record RuleResolution(StackRule rule, boolean excluded) {
    public static RuleResolution none() {
        return new RuleResolution(null, false);
    }

    public boolean matched() {
        return rule != null;
    }
}

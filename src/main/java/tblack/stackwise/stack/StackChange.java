package tblack.stackwise.stack;

public record StackChange(
        String itemId,
        int original,
        int previous,
        int requested,
        int applied,
        String ruleId,
        String outcome,
        String detail
) {
    public StackChange(
            String itemId,
            int original,
            int previous,
            int requested,
            int applied,
            String ruleId,
            String outcome
    ) {
        this(itemId, original, previous, requested, applied, ruleId, outcome, "");
    }
}

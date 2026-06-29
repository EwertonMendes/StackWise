package tblack.stackwise.stack;

public record ItemCatalogEntry(
        String itemId,
        int originalStack,
        int currentStack,
        Integer targetStack,
        String ruleId,
        String action,
        String matchType,
        String matchValue,
        String status,
        String unsafeReason
) {
}

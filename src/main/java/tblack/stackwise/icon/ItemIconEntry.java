package tblack.stackwise.icon;

import java.util.Locale;

public record ItemIconEntry(
        String itemId,
        String translationKey,
        String displayName,
        String searchText
) {
    public ItemIconEntry {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId cannot be blank");
        }
        itemId = itemId.trim();
        translationKey = normalizeNullable(translationKey);
        displayName = normalizeNullable(displayName);
        searchText = normalizeSearchText(searchText);
    }

    public boolean hasDisplayName() {
        return displayName != null;
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeSearchText(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

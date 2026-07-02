package tblack.stackwise.icon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemIconEntryTest {
    @Test
    void normalizesOptionalDisplayValuesAndSearchText() {
        ItemIconEntry entry = new ItemIconEntry(
                " Ingredient_Stick ",
                " %items.stick.name ",
                " Wooden Stick ",
                "  ingredient_stick   wooden stick  "
        );

        assertEquals("Ingredient_Stick", entry.itemId());
        assertEquals("%items.stick.name", entry.translationKey());
        assertEquals("Wooden Stick", entry.displayName());
        assertEquals("ingredient_stick wooden stick", entry.searchText());
        assertTrue(entry.hasDisplayName());
    }

    @Test
    void supportsItemsWithoutTranslatedNames() {
        ItemIconEntry entry = new ItemIconEntry("Unknown_Item", null, " ", "unknown_item");

        assertFalse(entry.hasDisplayName());
        assertEquals("unknown_item", entry.searchText());
    }

    @Test
    void rejectsBlankItemIds() {
        assertThrows(IllegalArgumentException.class, () -> new ItemIconEntry(" ", null, null, ""));
    }
}

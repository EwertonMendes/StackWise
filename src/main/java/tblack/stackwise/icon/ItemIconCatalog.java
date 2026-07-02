package tblack.stackwise.icon;

import java.util.List;

public interface ItemIconCatalog {
    boolean isValidItemId(String itemId);

    List<ItemIconEntry> search(String query, String locale);

    ItemIconEntry describe(String itemId, String locale);

    void invalidate();
}

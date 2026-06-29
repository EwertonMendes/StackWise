package tblack.stackwise.stack;

import java.util.List;

public record ItemCatalogExport(int schemaVersion, int itemCount, List<ItemCatalogEntry> items) {
}

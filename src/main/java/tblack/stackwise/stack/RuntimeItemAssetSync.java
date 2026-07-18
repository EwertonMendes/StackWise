package tblack.stackwise.stack;

import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Publishes runtime Item mutations through the same asset-store update path used
 * by Hytale asset reloads. This refreshes connected clients and invalidates the
 * store-level initialization packet cache for players who connect later.
 */
public final class RuntimeItemAssetSync {
    private final Method updateMethod;
    private final String description;

    public RuntimeItemAssetSync() {
        Method resolved = null;
        String resolvedDescription = "Unavailable";
        try {
            resolved = HytaleAssetStore.class.getDeclaredMethod(
                    "handleRemoveOrUpdate",
                    Set.class,
                    Map.class,
                    AssetUpdateQuery.class
            );
            if (!resolved.trySetAccessible()) {
                resolved = null;
            } else {
                resolvedDescription = "HytaleAssetStore.handleRemoveOrUpdate";
            }
        } catch (NoSuchMethodException | RuntimeException ignored) {
        }
        updateMethod = resolved;
        description = resolvedDescription;
    }

    public int synchronize(Set<String> changedItemIds) throws ReflectiveOperationException {
        if (changedItemIds == null || changedItemIds.isEmpty()) return 0;
        if (updateMethod == null) {
            throw new NoSuchMethodException("Hytale runtime item asset update method is unavailable");
        }

        Map<String, Item> updates = new LinkedHashMap<>();
        for (String itemId : changedItemIds) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item != null) updates.put(itemId, item);
        }
        if (updates.isEmpty()) return 0;

        Object store = Item.getAssetStore();
        if (!HytaleAssetStore.class.isInstance(store)) {
            throw new IllegalStateException("Item asset store does not support runtime client updates");
        }
        try {
            updateMethod.invoke(store, Set.of(), updates, AssetUpdateQuery.DEFAULT_NO_REBUILD);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ReflectiveOperationException("Hytale runtime item asset update failed", cause);
        }
        return updates.size();
    }

    public boolean isAvailable() {
        return updateMethod != null;
    }

    public String description() {
        return description;
    }
}

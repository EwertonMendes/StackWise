package tblack.stackwise.permissions;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionService {
    private static final long CACHE_TTL_MILLIS = 5000L;

    private final Map<CacheKey, CachedPermission> cache = new ConcurrentHashMap<>();
    private boolean luckPermsChecked;
    private Object luckPermsApi;

    public void register(String permission) {
        if (permission == null || permission.isBlank()) return;
        try {
            PermissionsModule.get().registerPermission(permission);
        } catch (Throwable ignored) {
        }
    }

    public boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isBlank()) return false;
        CacheKey key = new CacheKey(uuid, permission);
        CachedPermission cached = cache.get(key);
        if (cached != null && cached.isValid()) return cached.allowed;

        boolean allowed = hasNativePermission(uuid, permission)
                || hasNativePermission(uuid, "stackwise.admin")
                || hasNativePermission(uuid, "*")
                || hasLuckPermsPermission(uuid, permission)
                || hasLuckPermsPermission(uuid, "stackwise.admin")
                || hasLuckPermsPermission(uuid, "*");
        cache.put(key, new CachedPermission(allowed, System.currentTimeMillis()));
        return allowed;
    }

    public void clearCache() {
        cache.clear();
        luckPermsChecked = false;
        luckPermsApi = null;
    }

    private boolean hasNativePermission(UUID uuid, String permission) {
        try {
            return PermissionsModule.get().hasPermission(uuid, permission);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasLuckPermsPermission(UUID uuid, String permission) {
        try {
            Object user = loadLuckPermsUser(uuid);
            if (user == null) return false;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
            Object bool = result.getClass().getMethod("asBoolean").invoke(result);
            return Boolean.TRUE.equals(bool);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object loadLuckPermsUser(UUID uuid) throws Exception {
        Object api = getLuckPermsApi();
        if (api == null) return null;
        Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
        return userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
    }

    private Object getLuckPermsApi() {
        if (luckPermsChecked) return luckPermsApi;
        luckPermsChecked = true;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPermsApi = provider.getMethod("get").invoke(null);
        } catch (Throwable ignored) {
            luckPermsApi = null;
        }
        return luckPermsApi;
    }

    private record CacheKey(UUID uuid, String permission) {
    }

    private record CachedPermission(boolean allowed, long createdAt) {
        private boolean isValid() {
            return System.currentTimeMillis() - createdAt <= CACHE_TTL_MILLIS;
        }
    }
}

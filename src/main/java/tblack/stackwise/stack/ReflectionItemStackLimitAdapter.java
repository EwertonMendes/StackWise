package tblack.stackwise.stack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class ReflectionItemStackLimitAdapter implements ItemStackLimitAdapter {
    private static final List<String> GETTER_NAMES = List.of("getMaxStack", "getMaxStackSize", "maxStack");
    private static final List<String> SETTER_NAMES = List.of("setMaxStack", "setMaxStackSize");
    private static final List<String> FIELD_NAMES = List.of("maxStack", "maxStackSize", "stackSize");
    private static final List<String> CACHE_METHOD_NAMES = List.of("invalidatePacketCache", "clearPacketCache");
    private static final List<String> CACHE_FIELD_NAMES = List.of("cachedPacket", "cachedItemBase", "packetCache");

    private final Class<?> expectedType;
    private Method getter;
    private Method setter;
    private Field field;
    private Method cacheMethod;
    private Field cacheField;
    private String description = "Unavailable";
    private Class<?> resolvedType;

    public ReflectionItemStackLimitAdapter(Class<?> expectedType) {
        this.expectedType = expectedType;
    }

    public ReflectionItemStackLimitAdapter() {
        this(null);
    }

    @Override
    public synchronized int read(Object item) throws ReflectiveOperationException {
        requireItem(item);
        resolve(resolveType(item));
        if (getter != null) return ((Number) getter.invoke(item)).intValue();
        if (field != null) return ((Number) field.get(item)).intValue();
        throw new NoSuchFieldException("No supported max stack accessor was found");
    }

    @Override
    public synchronized void write(Object item, int value) throws ReflectiveOperationException {
        requireItem(item);
        resolve(resolveType(item));
        if (setter != null) setter.invoke(item, value);
        else if (field != null) field.set(item, value);
        else throw new NoSuchFieldException("No supported max stack mutator was found");
        invalidatePacketCache(item);
    }

    @Override
    public synchronized boolean isAvailable() {
        if (resolvedType == null && expectedType != null) resolve(expectedType);
        return setter != null || field != null;
    }

    @Override
    public synchronized String description() {
        if (resolvedType == null && expectedType != null) resolve(expectedType);
        return description;
    }

    private void requireItem(Object item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");
        if (expectedType != null && !expectedType.isInstance(item)) {
            throw new IllegalArgumentException("Unsupported item type: " + item.getClass().getName());
        }
    }

    private Class<?> resolveType(Object item) {
        return expectedType == null ? item.getClass() : expectedType;
    }

    private void resolve(Class<?> type) {
        if (resolvedType != null) return;
        resolvedType = type;
        getter = findGetter(type);
        setter = findSetter(type);
        field = findField(type, FIELD_NAMES, true);
        cacheMethod = findNoArgumentMethod(type, CACHE_METHOD_NAMES);
        cacheField = findField(type, CACHE_FIELD_NAMES, false);
        if (setter != null) description = "Method " + setter.getName() + "(int)";
        else if (field != null) description = "Field " + field.getName();
        if (!"Unavailable".equals(description) && cacheMethod != null) description += " with " + cacheMethod.getName() + "()";
        else if (!"Unavailable".equals(description) && cacheField != null) description += " with " + cacheField.getName() + " invalidation";
    }

    private Method findGetter(Class<?> type) {
        for (String name : GETTER_NAMES) {
            Method method = findMethod(type, name);
            if (method == null) continue;
            if (!Number.class.isAssignableFrom(box(method.getReturnType()))) continue;
            return method;
        }
        return null;
    }

    private Method findSetter(Class<?> type) {
        for (String name : SETTER_NAMES) {
            Method method = findMethod(type, name, int.class);
            if (method != null) return method;
        }
        return null;
    }

    private Method findNoArgumentMethod(Class<?> type, List<String> names) {
        for (String name : names) {
            Method method = findMethod(type, name);
            if (method != null) return method;
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                if (Modifier.isStatic(method.getModifiers())) return null;
                if (!method.trySetAccessible()) return null;
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Field findField(Class<?> type, List<String> names, boolean numeric) {
        for (String name : names) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Field candidate = current.getDeclaredField(name);
                    if (Modifier.isStatic(candidate.getModifiers())) break;
                    if (numeric && !Number.class.isAssignableFrom(box(candidate.getType()))) break;
                    if (!candidate.trySetAccessible()) break;
                    return candidate;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        }
        return null;
    }

    private void invalidatePacketCache(Object item) throws ReflectiveOperationException {
        if (cacheMethod != null) {
            cacheMethod.invoke(item);
            return;
        }
        if (cacheField != null) cacheField.set(item, null);
    }

    private Class<?> box(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        return type;
    }
}

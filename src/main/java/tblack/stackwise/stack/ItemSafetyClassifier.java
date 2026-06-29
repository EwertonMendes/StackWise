package tblack.stackwise.stack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ItemSafetyClassifier {
    private static final List<String> STATE_METHOD_NAMES = List.of(
            "getTool",
            "getWeapon",
            "getArmor",
            "getGlider",
            "getPortalKey",
            "getBlockSelectorToolData",
            "getBuilderTool",
            "getBuilderToolData",
            "getPullbackConfig"
    );

    private final List<Method> stateMethods = new ArrayList<>();
    private Method durabilityMethod;
    private Class<?> resolvedType;

    public synchronized String unsafeReason(Object item, int originalLimit) {
        if (item == null) return "missing-item";
        if (originalLimit > 1) return null;
        resolve(item.getClass());
        String durabilityReason = durabilityReason(item);
        if (durabilityReason != null) return durabilityReason;
        for (Method method : stateMethods) {
            try {
                if (method.invoke(item) != null) return methodReason(method.getName());
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "originally-non-stackable";
    }

    private void resolve(Class<?> type) {
        if (resolvedType == type) return;
        resolvedType = type;
        durabilityMethod = findMethod(type, "getMaxDurability");
        stateMethods.clear();
        for (String name : STATE_METHOD_NAMES) {
            Method method = findMethod(type, name);
            if (method != null) stateMethods.add(method);
        }
    }

    private Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (Modifier.isStatic(method.getModifiers())) return null;
                if (!method.trySetAccessible()) return null;
                return method;
            } catch (NoSuchMethodException exception) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String durabilityReason(Object item) {
        if (durabilityMethod == null) return null;
        try {
            Object value = durabilityMethod.invoke(item);
            if (value instanceof Number number && number.doubleValue() > 0) return "durability";
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private String methodReason(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String value = methodName.substring(3);
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
        return methodName;
    }
}

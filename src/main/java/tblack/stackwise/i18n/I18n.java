package tblack.stackwise.i18n;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class I18n {
    private static final String DEFAULT_LOCALE = "en-US";
    private static final String SERVER_TRANSLATION_PREFIX = "server.stackwise.";
    private static final String LANGUAGE_KEY_PREFIX = "stackwise.";
    private static final String LANGUAGE_PATH_PREFIX = "Server/Languages/";
    private static final Map<String, Properties> CACHE = new ConcurrentHashMap<>();

    private static final String[] LOCALE_METHODS = {
            "getLanguage",
            "language",
            "getLocale",
            "locale",
            "getLanguageCode",
            "getLanguageTag",
            "getClientLocale",
            "getClientLanguage"
    };

    private static final String[] LOCALE_CONTAINER_METHODS = {
            "getPlayerRef",
            "getClientInfo",
            "getClientInformation",
            "getClientSettings",
            "getSettings",
            "getPreferences",
            "getConnection",
            "getSession",
            "getProfile"
    };

    private I18n() {
    }

    public static String commandKey(String relativeKey) {
        return SERVER_TRANSLATION_PREFIX + stripKnownPrefix(relativeKey);
    }

    public static String translate(CommandContext context, String relativeKey, Object... args) {
        return translate(localeFromContext(context), relativeKey, args);
    }

    public static String locale(CommandContext context) {
        return localeFromContext(context);
    }

    public static String localeFromPlayer(Player player) {
        if (player == null) return DEFAULT_LOCALE;
        String locale = findLocale(player, 0, new HashSet<>());
        return locale == null ? DEFAULT_LOCALE : normalizeLocale(locale);
    }

    public static String localeFromPlayerRef(PlayerRef playerRef) {
        if (playerRef == null) return DEFAULT_LOCALE;
        try {
            String language = playerRef.getLanguage();
            if (language != null && !language.isBlank()) return normalizeLocale(language);
        } catch (Throwable ignored) {
        }
        String locale = findLocale(playerRef, 0, new HashSet<>());
        return locale == null ? DEFAULT_LOCALE : normalizeLocale(locale);
    }

    public static String translate(String locale, String relativeKey, Object... args) {
        String normalizedLocale = normalizeLocale(locale);
        String languageKey = languageKey(relativeKey);
        String translated = value(normalizedLocale, languageKey);
        if (translated == null && !Objects.equals(normalizedLocale, DEFAULT_LOCALE)) {
            translated = value(DEFAULT_LOCALE, languageKey);
        }
        if (translated == null) translated = languageKey;
        return format(translated, args);
    }

    private static String value(String locale, String key) {
        return properties(locale).getProperty(key);
    }

    private static Properties properties(String locale) {
        return CACHE.computeIfAbsent(locale, I18n::loadProperties);
    }

    private static Properties loadProperties(String locale) {
        Properties properties = new Properties();
        loadPropertiesFile(properties, LANGUAGE_PATH_PREFIX + locale + "/server.lang");
        return properties;
    }

    private static void loadPropertiesFile(Properties properties, String path) {
        try (InputStream input = openResource(path)) {
            if (input == null) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))) {
                properties.load(reader);
            }
        } catch (IOException exception) {
            System.err.println("[StackWise] Failed to load language file " + path + ": " + exception.getMessage());
        }
    }

    private static InputStream openResource(String path) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream input = contextClassLoader.getResourceAsStream(path);
            if (input != null) return input;
        }
        ClassLoader ownClassLoader = I18n.class.getClassLoader();
        return ownClassLoader == null ? null : ownClassLoader.getResourceAsStream(path);
    }

    private static String format(String value, Object... args) {
        if (args == null || args.length == 0) return value;
        try {
            return MessageFormat.format(value.replace("'", "''"), args);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static String languageKey(String relativeKey) {
        return LANGUAGE_KEY_PREFIX + stripKnownPrefix(relativeKey);
    }

    private static String stripKnownPrefix(String key) {
        if (key == null || key.isBlank()) return "missing";
        String trimmed = key.trim();
        if (trimmed.startsWith(SERVER_TRANSLATION_PREFIX)) {
            return trimmed.substring(SERVER_TRANSLATION_PREFIX.length());
        }
        if (trimmed.startsWith(LANGUAGE_KEY_PREFIX)) {
            return trimmed.substring(LANGUAGE_KEY_PREFIX.length());
        }
        if (trimmed.startsWith("server." + LANGUAGE_KEY_PREFIX)) {
            return trimmed.substring(("server." + LANGUAGE_KEY_PREFIX).length());
        }
        return trimmed;
    }

    private static String localeFromContext(CommandContext context) {
        if (context == null) return DEFAULT_LOCALE;
        String direct = findLocale(context, 0, new HashSet<>());
        if (direct != null) return normalizeLocale(direct);
        try {
            String senderLocale = findLocale(context.sender(), 0, new HashSet<>());
            return senderLocale == null ? DEFAULT_LOCALE : normalizeLocale(senderLocale);
        } catch (Throwable ignored) {
            return DEFAULT_LOCALE;
        }
    }

    private static String findLocale(Object target, int depth, Set<Object> visited) {
        if (target == null || depth > 4 || visited.contains(target)) return null;
        visited.add(target);
        for (String methodName : LOCALE_METHODS) {
            String locale = readLocaleMethod(target, methodName);
            if (locale != null) return locale;
        }
        for (String methodName : LOCALE_CONTAINER_METHODS) {
            Object nested = invokeNoArgs(target, methodName);
            String locale = findLocale(nested, depth + 1, visited);
            if (locale != null) return locale;
        }
        return null;
    }

    private static String readLocaleMethod(Object target, String methodName) {
        return localeValue(invokeNoArgs(target, methodName));
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) return null;
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String localeValue(Object value) {
        if (value == null) return null;
        if (value instanceof Locale locale) return locale.toLanguageTag();
        if (value instanceof CharSequence sequence) return sequence.toString();
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        Object tag = invokeNoArgs(value, "toLanguageTag");
        if (tag instanceof CharSequence sequence) return sequence.toString();
        Object code = invokeNoArgs(value, "getCode");
        if (code instanceof CharSequence sequence) return sequence.toString();
        Object language = invokeNoArgs(value, "getLanguage");
        if (language instanceof CharSequence sequence) return sequence.toString();
        return null;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;
        String normalized = locale.trim().replace('_', '-').replace(' ', '-').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("de") || normalized.contains("german") || normalized.contains("deutsch")) return "de-DE";
        if (normalized.startsWith("es") || normalized.contains("spanish") || normalized.contains("espan") || normalized.contains("españ")) return "es-ES";
        if (normalized.startsWith("fr") || normalized.contains("french") || normalized.contains("franc")) return "fr-FR";
        if (normalized.startsWith("pt") || normalized.contains("portugu") || normalized.contains("brasil") || normalized.contains("brazil")) return "pt-BR";
        if (normalized.startsWith("ru") || normalized.contains("russian") || normalized.contains("рус")) return "ru-RU";
        if (normalized.startsWith("uk") || normalized.startsWith("ua") || normalized.contains("ukrain") || normalized.contains("укра")) return "uk-UA";
        if (normalized.startsWith("zh") || normalized.contains("chinese") || normalized.contains("hans") || normalized.contains("simplified") || normalized.contains("简体")) return "zh-CN";
        return DEFAULT_LOCALE;
    }
}

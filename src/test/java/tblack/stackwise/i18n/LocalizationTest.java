package tblack.stackwise.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationTest {
    private static final Path LANGUAGES = Path.of("src", "main", "resources", "Server", "Languages");
    private static final List<String> LOCALES = List.of(
            "en-US",
            "de-DE",
            "es-ES",
            "fr-FR",
            "pt-BR",
            "ru-RU",
            "uk-UA",
            "zh-CN"
    );

    @Test
    void everySupportedLocaleContainsTheCompleteEnglishKeySet() throws IOException {
        Properties english = load("en-US");
        Set<String> expected = english.stringPropertyNames();

        assertFalse(expected.isEmpty());
        for (String locale : LOCALES) {
            Properties translated = load(locale);
            assertEquals(expected, translated.stringPropertyNames(), locale);
            for (String key : expected) {
                assertFalse(translated.getProperty(key).isBlank(), locale + " " + key);
            }
        }
    }

    @Test
    void translationKeysRemainAsciiAndMachineStable() throws IOException {
        for (String locale : LOCALES) {
            Path file = LANGUAGES.resolve(locale).resolve("server.lang");
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) continue;
                String key = line.substring(0, line.indexOf('='));
                assertTrue(key.matches("[a-z0-9._-]+"), locale + " " + key);
            }
        }
    }


    @Test
    void uiDocumentsUseRuntimeTranslationWithoutMarkupReferences() throws IOException {
        Path uiDirectory = Path.of("src", "main", "resources", "Common", "UI", "Custom", "StackWise");
        try (var paths = Files.list(uiDirectory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".ui")).toList()) {
                assertFalse(Files.readString(file).contains("%"), file.toString());
            }
        }
    }

    @Test
    void everyRuntimeTranslationReferenceExists() throws IOException {
        Properties english = load("en-US");
        Pattern reference = Pattern.compile("translate\\(\"([a-z0-9._-]+)\"");
        Path javaDirectory = Path.of("src", "main", "java");
        Set<String> referenced = new HashSet<>();
        try (var paths = Files.walk(javaDirectory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                Matcher matcher = reference.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!key.endsWith(".")) referenced.add("stackwise." + key);
                }
            }
        }
        assertFalse(referenced.isEmpty());
        for (String key : referenced) assertTrue(english.containsKey(key), key);
    }

    @Test
    void translationsCoverCommandsInterfaceValidationAndRuleOptions() throws IOException {
        Properties english = load("en-US");

        assertTrue(english.containsKey("stackwise.commands.root.description"));
        assertTrue(english.containsKey("stackwise.messages.report"));
        assertTrue(english.containsKey("stackwise.commands.import.description"));
        assertTrue(english.containsKey("stackwise.commands.import.overstacked.description"));
        assertTrue(english.containsKey("stackwise.messages.import_success"));
        assertTrue(english.containsKey("stackwise.validation.invalid_regex"));
        assertTrue(english.containsKey("stackwise.ui.admin.search_placeholder"));
        assertTrue(english.containsKey("stackwise.ui.admin.global_limit_enabled"));
        assertTrue(english.containsKey("stackwise.ui.admin.global_stack_limit"));
        assertTrue(english.containsKey("stackwise.ui.admin.enabled_hint"));
        assertTrue(english.containsKey("stackwise.ui.admin.delete_confirmation"));
        assertTrue(english.containsKey("stackwise.ui.admin.tab_rules"));
        assertTrue(english.containsKey("stackwise.ui.admin.tab_settings"));
        assertTrue(english.containsKey("stackwise.ui.admin.view_log"));
        assertTrue(english.containsKey("stackwise.ui.log.title"));
        assertTrue(english.containsKey("stackwise.ui.log.level.error"));
        assertTrue(english.containsKey("stackwise.ui.rule.delete_confirmation"));
        assertTrue(english.containsKey("stackwise.ui.action.set"));
        assertTrue(english.containsKey("stackwise.ui.action.exclude"));
        assertTrue(english.containsKey("stackwise.ui.match_type.regex"));
    }


    @Test
    void translatedMessagesKeepTheSamePlaceholders() throws IOException {
        Properties english = load("en-US");
        Pattern placeholder = Pattern.compile("\\{(\\d+)}");
        for (String locale : LOCALES) {
            Properties translated = load(locale);
            for (String key : english.stringPropertyNames()) {
                assertEquals(
                        placeholders(english.getProperty(key), placeholder),
                        placeholders(translated.getProperty(key), placeholder),
                        locale + " " + key
                );
            }
        }
    }

    private Set<String> placeholders(String value, Pattern pattern) {
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) placeholders.add(matcher.group(1));
        return placeholders;
    }

    private Properties load(String locale) throws IOException {
        Path file = LANGUAGES.resolve(locale).resolve("server.lang");
        assertTrue(Files.isRegularFile(file), locale);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}

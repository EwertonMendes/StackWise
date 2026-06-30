package tblack.stackwise.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiContractTest {
    private static final Path DIRECTORY = Path.of("src", "main", "resources", "Common", "UI", "Custom", "StackWise");
    private static final Pattern ELEMENT_ID = Pattern.compile(
            "(?:\\$[A-Za-z][A-Za-z0-9_]*\\.@[A-Za-z][A-Za-z0-9_]*|[A-Za-z][A-Za-z0-9_]*)\\s+#([A-Za-z][A-Za-z0-9_]*)\\s*\\{"
    );
    private static final Pattern STATIC_SELECTOR = Pattern.compile("\"(#[A-Za-z][A-Za-z0-9_]*(?:\\s+#[A-Za-z][A-Za-z0-9_]*)?)\\.[A-Za-z][A-Za-z0-9_]*\"");

    @Test
    void administrationDocumentContainsEveryBoundElement() throws IOException {
        String document = Files.readString(DIRECTORY.resolve("Admin.ui"));
        List<String> ids = List.of(
                "AdminTitle",
                "RulesTabActive",
                "RulesTabInactive",
                "SettingsTabActive",
                "SettingsTabInactive",
                "RulesTabContent",
                "SettingsTabContent",
                "RulesScroll",
                "SettingsScroll",
                "StatusGroup",
                "ViewLogButton",
                "GlobalSettingsLabel",
                "EnabledCheck",
                "EnabledLabel",
                "EnabledHintLabel",
                "SafeModeCheck",
                "SafeModeLabel",
                "RespectExternalChangesCheck",
                "RespectExternalChangesLabel",
                "AllowDecreasesCheck",
                "AllowDecreasesLabel",
                "AllowRuntimeDecreasesCheck",
                "AllowRuntimeDecreasesLabel",
                "RestoreUnmatchedCheck",
                "RestoreUnmatchedLabel",
                "GlobalLimitEnabledCheck",
                "GlobalLimitEnabledLabel",
                "GlobalStackLimitInput",
                "SearchField",
                "SearchButton",
                "ClearSearchButton",
                "NewRuleButton",
                "ReloadButton",
                "SaveGeneralButton",
                "PageLabel",
                "PreviousButton",
                "NextButton",
                "ClosePageButton"
        );
        ids.forEach(id -> assertTrue(document.contains("#" + id), id));
        for (int index = 0; index < 8; index++) {
            assertTrue(document.contains("#RuleRow" + index));
            assertTrue(document.contains("#RuleName" + index));
            assertTrue(document.contains("#RuleDescription" + index));
            assertTrue(document.contains("#EditButton" + index));
            assertTrue(document.contains("#DeleteButton" + index));
            assertTrue(document.contains("#DeleteConfirmation" + index));
            assertTrue(document.contains("#ConfirmDeleteButton" + index));
        }
    }


    @Test
    void stackLimitInputsUseTheSupportedHardMaximum() throws IOException {
        String admin = Files.readString(DIRECTORY.resolve("Admin.ui"));
        String editor = Files.readString(DIRECTORY.resolve("RuleEditor.ui"));

        assertTrue(admin.contains("#GlobalStackLimitInput"));
        assertTrue(admin.contains("MaxValue: 999999"));
        assertTrue(editor.contains("#MaxStackInput"));
        assertTrue(editor.contains("MaxValue: 999999"));
        assertFalse(admin.contains("#MaximumStackInput"));
    }

    @Test
    void exportControlsAndPersistentReportBarAreRemoved() throws IOException {
        String document = Files.readString(DIRECTORY.resolve("Admin.ui"));
        String source = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "ui", "StackWiseAdminPage.java"
        ));

        assertFalse(document.contains("#ExportButton"));
        assertFalse(document.contains("#ReportBar"));
        assertFalse(source.contains("exportCatalog"));
        assertFalse(source.contains("renderReport"));
    }

    @Test
    void ruleEditorDropdownsOpenBelowTheirFields() throws IOException {
        String document = Files.readString(DIRECTORY.resolve("RuleEditor.ui"));
        assertTrue(document.contains("@BottomDropdownStyle = DropdownBoxStyle("));
        assertTrue(document.contains("...$Common.@DefaultDropdownBoxStyle"));
        assertTrue(document.contains("PanelAlign: Bottom"));
        assertTrue(document.contains("PanelWidth: 730"));
        assertTrue(document.contains("#ActionDropdown"));
        assertTrue(document.contains("#MatchTypeDropdown"));
        assertTrue(document.contains("Style: @BottomDropdownStyle"));
    }

    @Test
    void searchSupportsEnterAndClick() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "ui", "StackWiseAdminPage.java"
        ));
        assertTrue(source.contains("CustomUIEventBindingType.Activating"));
        assertTrue(source.contains("\"#SearchButton\""));
        assertTrue(source.contains("CustomUIEventBindingType.Validating"));
        assertTrue(source.contains("\"#SearchField\""));
        assertTrue(source.contains("#SearchField.Value"));
        assertTrue(source.contains("searchEvent()"));
    }

    @Test
    void mainPageUsesSafeButtonTabsInsteadOfBrokenTabNavigationStyles() throws IOException {
        String document = Files.readString(DIRECTORY.resolve("Admin.ui"));
        String source = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "ui", "StackWiseAdminPage.java"
        ));
        assertFalse(document.contains("TabNavigation"));
        assertFalse(document.contains("@TopTabsStyle"));
        assertFalse(document.contains("@HeaderTabsStyle"));
        assertTrue(document.contains("$Common.@TextButton #RulesTabActive"));
        assertTrue(document.contains("$Common.@SecondaryTextButton #RulesTabInactive"));
        assertTrue(source.contains("case \"tab-rules\""));
        assertTrue(source.contains("case \"tab-settings\""));
    }

    @Test
    void scrollingAreasKeepTheirPosition() throws IOException {
        String admin = Files.readString(DIRECTORY.resolve("Admin.ui"));
        String editor = Files.readString(DIRECTORY.resolve("RuleEditor.ui"));
        String log = Files.readString(DIRECTORY.resolve("Log.ui"));
        assertTrue(block(admin, "Group #RulesScroll").contains("KeepScrollPosition: true"));
        assertTrue(block(admin, "Group #SettingsScroll").contains("KeepScrollPosition: true"));
        assertTrue(editor.contains("KeepScrollPosition: true"));
        assertTrue(block(log, "Group #LogScroll").contains("KeepScrollPosition: true"));
    }

    @Test
    void logDocumentProvidesColoredSeverityRows() throws IOException {
        String document = Files.readString(DIRECTORY.resolve("Log.ui"));
        List<String> ids = List.of(
                "LogTitle",
                "LogSubtitle",
                "LegendInfo",
                "LegendSuccess",
                "LegendWarning",
                "LegendError",
                "EmptyLabel",
                "PageLabel",
                "PreviousButton",
                "NextButton",
                "BackButton",
                "ClosePageButton"
        );
        ids.forEach(id -> assertTrue(document.contains("#" + id), id));
        for (int index = 0; index < 10; index++) {
            assertTrue(document.contains("#LogRow" + index));
            assertTrue(document.contains("#LogInfo" + index));
            assertTrue(document.contains("#LogSuccess" + index));
            assertTrue(document.contains("#LogWarning" + index));
            assertTrue(document.contains("#LogError" + index));
        }
        assertTrue(document.contains("#72c878"));
        assertTrue(document.contains("#e9b65f"));
        assertTrue(document.contains("#e56b6f"));
    }

    @Test
    void uiDocumentsUseLiteralFallbackTextOnly() throws IOException {
        for (Path path : uiFiles()) {
            String document = Files.readString(path);
            assertFalse(document.contains("Text: %"), path.toString());
            assertFalse(document.contains("@Text = %"), path.toString());
            assertFalse(document.contains("PlaceholderText: %"), path.toString());
        }
    }

    @Test
    void documentsHaveBalancedBlocksAndUniqueIds() throws IOException {
        for (Path path : uiFiles()) {
            String document = Files.readString(path);
            assertEquals(count(document, '{'), count(document, '}'), path.toString());
            assertEquals(count(document, '('), count(document, ')'), path.toString());
            Set<String> ids = new HashSet<>();
            Matcher matcher = ELEMENT_ID.matcher(document);
            while (matcher.find()) {
                String id = matcher.group(1);
                assertTrue(ids.add(id), path + " duplicate id #" + id);
            }
        }
    }

    @Test
    void everyStaticCommandSelectorTargetsAnElementDeclaredByItsDocument() throws IOException {
        assertStaticSelectorsExist(
                Path.of("src", "main", "java", "tblack", "stackwise", "ui", "StackWiseAdminPage.java"),
                DIRECTORY.resolve("Admin.ui")
        );
        assertStaticSelectorsExist(
                Path.of("src", "main", "java", "tblack", "stackwise", "ui", "StackWiseRulePage.java"),
                DIRECTORY.resolve("RuleEditor.ui")
        );
        assertStaticSelectorsExist(
                Path.of("src", "main", "java", "tblack", "stackwise", "ui", "StackWiseLogPage.java"),
                DIRECTORY.resolve("Log.ui")
        );
    }

    @Test
    void routineActionsDoNotRebuildDocuments() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "ui", "StackWiseAdminPage.java"
        ));
        assertFalse(source.contains("rebuild();"));
        assertTrue(source.contains("sendUpdate(commands, false)"));
        assertTrue(source.contains("edit-slot:"));
        assertTrue(source.contains("delete-request-slot:"));

        String ruleSource = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "ui", "StackWiseRulePage.java"
        ));
        assertFalse(ruleSource.contains("rebuild();"));
        assertTrue(ruleSource.contains("refreshDeleteState()"));
        assertTrue(ruleSource.contains("sendUpdate(updates, false)"));
    }

    private void assertStaticSelectorsExist(Path javaFile, Path uiFile) throws IOException {
        String source = Files.readString(javaFile);
        String document = Files.readString(uiFile);
        Matcher matcher = STATIC_SELECTOR.matcher(source);
        while (matcher.find()) {
            String selector = matcher.group(1);
            for (String segment : selector.split("\\s+")) {
                assertTrue(document.contains(segment), javaFile + " selector " + selector + " missing from " + uiFile);
            }
        }
    }

    private String block(String document, String start) {
        int index = document.indexOf(start);
        assertTrue(index >= 0, start);
        int open = document.indexOf('{', index);
        int depth = 0;
        for (int position = open; position < document.length(); position++) {
            char value = document.charAt(position);
            if (value == '{') depth++;
            if (value != '}') continue;
            depth--;
            if (depth == 0) return document.substring(index, position + 1);
        }
        return "";
    }

    private List<Path> uiFiles() throws IOException {
        try (var paths = Files.list(DIRECTORY)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".ui")).sorted().toList();
        }
    }

    private int count(String value, char character) {
        int total = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) total++;
        }
        return total;
    }
}

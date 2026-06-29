package tblack.stackwise.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonStylingPolicyTest {
    private static final Path UI_DIRECTORY = Path.of("src", "main", "resources", "Common", "UI", "Custom", "StackWise");
    private static final Pattern COMMON_REFERENCE = Pattern.compile("\\$Common\\.@([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern COMMON_DECLARATION = Pattern.compile("(?m)^\\s*@([A-Za-z][A-Za-z0-9_]*)\\s*=");
    private static final Set<String> REQUIRED_COMMON_EXPRESSIONS = Set.of(
            "PageOverlay",
            "DecoratedContainer",
            "DefaultScrollbarStyle",
            "DefaultLabelStyle",
            "DefaultDropdownBoxStyle",
            "CheckBox",
            "NumberField",
            "TextField",
            "DropdownBox",
            "TextButton",
            "SecondaryTextButton",
            "TertiaryTextButton",
            "CancelTextButton"
    );

    @Test
    void everyDocumentImportsCommonUi() throws IOException {
        for (Path file : uiFiles()) {
            String content = Files.readString(file);
            assertTrue(content.startsWith("$Common = \"../Common.ui\";"), file.toString());
        }
    }

    @Test
    void documentsDoNotRecreateButtonOrPatchStyles() throws IOException {
        for (Path file : uiFiles()) {
            String content = Files.readString(file);
            assertFalse(content.contains("TextButtonStyle("), file.toString());
            assertFalse(content.contains("PatchStyle("), file.toString());
            assertFalse(content.contains("ButtonStyle("), file.toString());
        }
    }

    @Test
    void interactiveControlsUseCommonTemplates() throws IOException {
        String combined = combinedUi();
        for (String expression : REQUIRED_COMMON_EXPRESSIONS) {
            assertTrue(combined.contains("$Common.@" + expression), expression);
        }
        assertFalse(Pattern.compile("\\$Common\\.@DefaultScrollbar(?:[^A-Za-z0-9_]|$)").matcher(combined).find());
        assertFalse(combined.contains("Style: $Common.@DefaultInputFieldStyle"));
        assertFalse(combined.contains("Background: $Common.@InputBoxBackground"));
    }

    @Test
    void everyCommonReferenceExistsInTheInstalledCommonDocument() throws IOException {
        Set<String> available = installedCommonExpressions();
        for (Path file : uiFiles()) {
            String content = Files.readString(file);
            Matcher matcher = COMMON_REFERENCE.matcher(content);
            while (matcher.find()) {
                String expression = matcher.group(1);
                assertTrue(available.contains(expression), file + " references missing Common.ui expression " + expression);
            }
        }
    }

    private Set<String> installedCommonExpressions() throws IOException {
        String configuredPath = System.getProperty("stackwise.assetsPath", "");
        if (configuredPath.isBlank()) return REQUIRED_COMMON_EXPRESSIONS;
        Path assetsPath = Path.of(configuredPath);
        if (!Files.isRegularFile(assetsPath)) return REQUIRED_COMMON_EXPRESSIONS;
        try (ZipFile zip = new ZipFile(assetsPath.toFile())) {
            ZipEntry entry = zip.getEntry("Common/UI/Custom/Common.ui");
            if (entry == null) entry = zip.getEntry("Assets/Common/UI/Custom/Common.ui");
            assertTrue(entry != null, "Common/UI/Custom/Common.ui was not found in " + assetsPath);
            String content = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            Set<String> expressions = new HashSet<>();
            Matcher matcher = COMMON_DECLARATION.matcher(content);
            while (matcher.find()) expressions.add(matcher.group(1));
            return expressions;
        }
    }

    private String combinedUi() throws IOException {
        return String.join("\n", uiFiles().stream().map(this::read).toList());
    }

    private List<Path> uiFiles() throws IOException {
        try (var paths = Files.list(UI_DIRECTORY)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".ui")).sorted().toList();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

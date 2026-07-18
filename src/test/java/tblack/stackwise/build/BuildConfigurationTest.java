package tblack.stackwise.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildConfigurationTest {
    @Test
    void buildMatchesTheVoidVaultHytaleDependencyStrategy() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("AppData/Roaming/Hytale"));
        assertTrue(build.contains("install/${patchline}/package/game/latest"));
        assertTrue(build.contains("Server/HytaleServer.jar"));
        assertTrue(build.contains("def assetsPath = (project.findProperty('assets_zip') ?: \"${gameDir}/Assets.zip\").toString()"));
        assertTrue(build.contains("systemProperty 'stackwise.assetsPath', assetsPath"));
        assertTrue(build.contains("compileOnly files(serverJarPath)"));
        assertTrue(build.contains("testCompileOnly files(serverJarPath)"));
        assertTrue(build.contains("testRuntimeOnly files(serverJarPath)"));
        assertTrue(build.contains("'src/test/java/tblack/stackwise/stack/TestItemFactory.java'"));
        assertTrue(build.contains("'src/main/resources/Common/UI/Custom/StackWise/RuleEditor.ui.tmp'"));
    }

    @Test
    void obsoleteFilesAreRemovedBeforeCompilation() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("tasks.register('removeObsoleteProjectFiles')"));
        assertTrue(build.contains("'src/test/java/tblack/stackwise/stack/TestItemFactory.java'"));
        assertTrue(build.contains("include 'Legacy*.java'"));
        assertTrue(build.contains("dependsOn tasks.named('removeObsoleteProjectFiles')"));
        assertTrue(build.contains("exclude 'tblack/stackwise/config/Legacy*.java'"));
    }

    @Test
    void itemEventsUseTheCompleteAssetRegistry() throws IOException {
        String plugin = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "StackWisePlugin.java"
        ));

        assertTrue(plugin.contains("event.getAssetMap().getAssetMap()"));
        assertFalse(plugin.contains("applyService.onAssetsLoaded(event.getLoadedAssets()"));
    }

    @Test
    void noLegacyConfigurationClassesRemain() throws IOException {
        Path configPackage = Path.of("src", "main", "java", "tblack", "stackwise", "config");

        try (var paths = Files.list(configPackage)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith("Legacy")));
        }
    }

    @Test
    void localeDetectionAvoidsDeprecatedPlayerReferenceCall() throws IOException {
        String i18n = Files.readString(Path.of(
                "src", "main", "java", "tblack", "stackwise", "i18n", "I18n.java"
        ));

        assertFalse(i18n.contains("player.getPlayerRef()"));
        assertTrue(i18n.contains("\"getPlayerRef\""));
    }

    @Test
    void releaseVersionIsOnePointTwoPointZero() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        String properties = Files.readString(Path.of("gradle.properties"));
        String manifest = Files.readString(Path.of("src", "main", "resources", "manifest.json"));
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(build.contains("?: '1.2.0'"));
        assertTrue(properties.contains("version=1.2.0"));
        assertTrue(manifest.contains("\"Version\": \"1.2.0\""));
        assertTrue(readme.contains("StackWise-1.2.0.jar"));
    }

}

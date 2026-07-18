package tblack.stackwise.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestIsolationPolicyTest {
    @Test
    void unitTestsDoNotImportHytaleServerClasses() throws IOException {
        Path root = Path.of("src", "test", "java");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.equals(Path.of("src", "test", "java", "tblack", "stackwise", "build", "TestIsolationPolicyTest.java"))) continue;
                assertFalse(Files.readString(file).contains("com.hypixel.hytale"), file.toString());
            }
        }
    }

    @Test
    void buildUsesTheOfficialHytaleInstallationByDefault() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("AppData/Roaming/Hytale"));
        assertTrue(build.contains("install/${patchline}/package/game/latest"));
        assertTrue(build.contains("Server/HytaleServer.jar"));
        assertTrue(build.contains("compileOnly files(serverJarFile)"));
    }

    @Test
    void hytaleServerJarIsNotPresentAtTestRuntime() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertFalse(build.contains("testRuntimeOnly files(serverJar"));
        assertFalse(build.contains("testImplementation files(serverJar"));
        assertFalse(build.contains("testCompileOnly files(serverJar"));
    }
}

package com.checkmarx.intellij.integration.standard;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.when;

public abstract class BaseTest extends BasePlatformTestCase {

    @BeforeEach
    public final void setUp() throws Exception {
        super.setUp();

        // Allow access to test data directory for file-based tests
        String projectRoot = Paths.get("").toAbsolutePath().toString();
        String testDataPath = Paths.get(projectRoot, "src", "test", "java", "com", "checkmarx", "intellij", "integration", "standard", "data").toString();
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), testDataPath);

        // Mock IgnoreFileManager to return a valid temp path
        // This prevents NullPointerException when project.getBasePath() returns null in tests
        IgnoreFileManager mockIgnoreFileManager = Mockito.mock(IgnoreFileManager.class);
        Path tempIgnoreFile = Files.createTempFile("checkmarxIgnoredTempList", ".json");
        Files.writeString(tempIgnoreFile, "[]");
        when(mockIgnoreFileManager.getTempListPath()).thenReturn(tempIgnoreFile);
        ServiceContainerUtil.registerServiceInstance(getProject(), IgnoreFileManager.class, mockIgnoreFileManager);

        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        // Set base URL and tenant from environment variables
        state.setBaseUrl(Environment.BASE_URL);
        state.setTenant(Environment.TENANT);
        state.setApiKeyEnabled(true);

        sensitiveState.setApiKey(System.getenv("CX_APIKEY"));
    }

    @AfterEach
    public final void tearDown() throws Exception {
        super.tearDown();
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        state.loadState(new GlobalSettingsState());
        sensitiveState.reset();
    }

    protected final Project getEnvProject() {
        return Assertions.assertDoesNotThrow(() -> {
            java.util.List<com.checkmarx.ast.project.Project> projects = com.checkmarx.intellij.commands.Project.getList();

            // Debug: Print available projects and expected project name
            System.out.println("=== DEBUG: Project Search ===");
            System.out.println("Looking for project: '" + Environment.PROJECT_NAME + "'");
            System.out.println("Available projects (" + projects.size() + " total):");
            projects.forEach(p -> System.out.println("  - " + p.getName()));
            System.out.println("============================");

            return projects.stream()
                    .filter(p -> p.getName().equals(Environment.PROJECT_NAME))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Project '" + Environment.PROJECT_NAME + "' not found. " +
                            "Available projects: " + projects.stream()
                                    .map(com.checkmarx.ast.project.Project::getName)
                                    .collect(java.util.stream.Collectors.joining(", "))));
        });
    }
}

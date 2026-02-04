package com.checkmarx.intellij.unit.devassist.scanners.secrets;

import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerCommand;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SecretsScannerCommandTest {

    private static class TestSecretsScannerCommand extends SecretsScannerCommand {
        public TestSecretsScannerCommand(Disposable parentDisposable, Project project) {
            super(parentDisposable, project);
        }

        @Override
        protected VirtualFile findVirtualFile(String path) {
            return null;
        }

        public void invokeInitializeScanner() {
            super.initializeScanner();
        }
    }

    private Project project;
    private Disposable parentDisposable;
    private SecretsScannerService secretsScannerServiceSpy;
    private TestSecretsScannerCommand command;

    // Removed invokePrivateScan method since scanAllFilesInProject doesn't exist in current implementation

    @BeforeEach
    void setUp() throws Exception {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        parentDisposable = mock(Disposable.class);
        command = new TestSecretsScannerCommand(parentDisposable, project);

        // Spy on internal service field for verification
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService originalService = (SecretsScannerService) serviceField.get(command);
        secretsScannerServiceSpy = spy(originalService);
        serviceField.set(command, secretsScannerServiceSpy);
    }

    @Test
    @DisplayName("Constructor initializes internal service and project reference")
    void testConstructor() throws Exception {
        // Verify internal service is initialized
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        assertNotNull(serviceField.get(command));

        // Verify project reference is set
        Field projectField = SecretsScannerCommand.class.getDeclaredField("project");
        projectField.setAccessible(true);
        assertSame(project, projectField.get(command));
    }

    @Test
    @DisplayName("Initialize scanner completes successfully and logs initialization")
    void testInitializeScanner() {
        // Secrets scanner initialization just logs a message and doesn't throw exceptions
        assertDoesNotThrow(() -> command.invokeInitializeScanner());
    }

    @Test
    @DisplayName("Service field is properly initialized and accessible")
    void testServiceFieldInitialization() throws Exception {
        // Given & When
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService service = (SecretsScannerService) serviceField.get(command);

        // Then
        assertNotNull(service, "Service should be initialized");
        assertNotNull(service.getConfig(), "Service config should be available");
    }

    @Test
    @DisplayName("Service can determine if files should be scanned")
    void testServiceShouldScanFile() throws Exception {
        // Given
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService service = (SecretsScannerService) serviceField.get(command);

        PsiFile psiFile = mock(PsiFile.class);

        // When & Then - Test that service can evaluate files
        assertDoesNotThrow(() -> service.shouldScanFile("/some/random/file.txt", psiFile));
        assertDoesNotThrow(() -> service.shouldScanFile("/app/.env", psiFile));
        assertDoesNotThrow(() -> service.shouldScanFile("/config/secrets.json", psiFile));
    }

    @Test
    @DisplayName("FindVirtualFile method can be overridden in test subclass")
    void testFindVirtualFileOverride() {
        // Given
        String testPath = "/test/path/file.js";

        // When & Then - Verify that the overridden method works
        VirtualFile result = command.findVirtualFile(testPath);
        assertNull(result, "Overridden findVirtualFile should return null for test");
    }

    @Test
    @DisplayName("Command configuration is properly set up")
    void testCommandConfiguration() throws Exception {
        // Given
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService service = (SecretsScannerService) serviceField.get(command);

        // When & Then
        assertNotNull(service.getConfig(), "Configuration should be available");
        assertEquals("SECRETS", service.getConfig().getEngineName(), "Engine name should be SECRETS");
    }

    @Test
    @DisplayName("Multiple command instances are independent")
    void testMultipleCommandInstances() throws Exception {
        // Given
        SecretsScannerCommand command2 = new SecretsScannerCommand(parentDisposable, project);

        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);

        SecretsScannerService service1 = (SecretsScannerService) serviceField.get(command);
        SecretsScannerService service2 = (SecretsScannerService) serviceField.get(command2);

        // When & Then
        assertNotNull(service1);
        assertNotNull(service2);
        assertNotSame(service1, service2, "Each command should have its own service instance");
    }

    @Test
    @DisplayName("Dispose method handles cleanup gracefully")
    void testDispose() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> command.dispose());
    }

    @Test
    @DisplayName("Command initialization does not throw exceptions")
    void testCommandInitialization() {
        // When & Then - Constructor should complete successfully
        assertDoesNotThrow(() -> {
            SecretsScannerCommand newCommand = new SecretsScannerCommand(parentDisposable, project);
            assertNotNull(newCommand);
        });
    }

    @Test
    @DisplayName("Private constructor with service parameter works correctly")
    void testPrivateConstructorWithService() throws Exception {
        // Given
        SecretsScannerService customService = new SecretsScannerService();

        // When
        java.lang.reflect.Constructor<SecretsScannerCommand> constructor =
            SecretsScannerCommand.class.getDeclaredConstructor(Disposable.class, Project.class, SecretsScannerService.class);
        constructor.setAccessible(true);
        SecretsScannerCommand customCommand = constructor.newInstance(parentDisposable, project, customService);

        // Then
        assertNotNull(customCommand);

        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService actualService = (SecretsScannerService) serviceField.get(customCommand);

        assertSame(customService, actualService, "Custom service should be used");
    }
}

package com.checkmarx.intellij.ast.test.unit.devassist.scanners.asca;

import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerCommand;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class AscaScannerCommandTest {

    private static class TestAscaScannerCommand extends AscaScannerCommand {
        TestAscaScannerCommand(Disposable parentDisposable, Project project) {
            super(parentDisposable, project);
        }

        void invokeInitializeScanner() {
            super.initializeScanner();
        }
    }

    private Project project;
    private Disposable parentDisposable;
    private TestAscaScannerCommand command;

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        parentDisposable = mock(Disposable.class);
        command = new TestAscaScannerCommand(parentDisposable, project);
    }

    @Test
    @DisplayName("Constructor wires scanner service and project reference")
    void testConstructorInitializesFields() throws Exception {
        assertNotNull(command.ascaScannerService, "ASCA service should be created");

        Field projectField = AscaScannerCommand.class.getDeclaredField("project");
        projectField.setAccessible(true);
        assertSame(project, projectField.get(command), "Project reference should be stored");
    }

    @Test
    @DisplayName("initializeScanner completes without exceptions")
    void testInitializeScanner() {
        assertDoesNotThrow(command::invokeInitializeScanner);
    }

    @Test
    @DisplayName("Scanner configuration is available and points to ASCA engine")
    void testCommandConfiguration() {
        assertNotNull(command.config, "Scanner config should be available");
        assertEquals("ASCA", command.config.getEngineName());
    }

    @Test
    @DisplayName("Multiple command instances use separate services")
    void testMultipleInstancesHaveIndependentServices() {
        AscaScannerCommand otherCommand = new AscaScannerCommand(parentDisposable, project);
        assertNotSame(command.ascaScannerService, otherCommand.ascaScannerService);
    }

    @Test
    @DisplayName("Private constructor allows injecting custom service")
    void testPrivateConstructorWithCustomService() throws Exception {
        Constructor<AscaScannerCommand> ctor = AscaScannerCommand.class.getDeclaredConstructor(
                Disposable.class, Project.class, AscaScannerService.class);
        ctor.setAccessible(true);

        AscaScannerService customService = new AscaScannerService();
        AscaScannerCommand customCommand = ctor.newInstance(parentDisposable, project, customService);
        assertSame(customService, customCommand.ascaScannerService, "Custom service should be retained");
    }

    @Test
    @DisplayName("Dispose completes without throwing")
    void testDispose() {
        assertDoesNotThrow(command::dispose);
    }
}



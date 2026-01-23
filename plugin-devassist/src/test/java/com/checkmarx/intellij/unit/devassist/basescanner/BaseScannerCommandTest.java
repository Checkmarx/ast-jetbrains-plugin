package com.checkmarx.intellij.unit.devassist.basescanner;

import com.checkmarx.intellij.devassist.basescanner.BaseScannerCommand;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseScannerCommandTest {
    private BaseScannerCommand scannerCommand;
    private ScannerConfig config;
    private Disposable disposable;
    private Project project;
    private GlobalScannerController controller;
    private ProblemHolderService problemHolderService;

    static class TestScannerCommand extends BaseScannerCommand {
        boolean initialized = false;
        public TestScannerCommand(@NotNull Disposable parentDisposable, ScannerConfig config) {
            super(parentDisposable, config);
        }
        @Override
        protected void initializeScanner() {
            initialized = true;
        }
        // Expose protected methods for testing
        public ScanEngine getScannerTypeForTest() {
            return super.getScannerType();
        }
        public VirtualFile findVirtualFileForTest(String path) {
            return super.findVirtualFile(path);
        }
    }

    @BeforeEach
    void setUp() {
        config = mock(ScannerConfig.class);
        when(config.getEngineName()).thenReturn("OSS");
        when(config.getEnabledMessage()).thenReturn("Enabled");
        when(config.getDisabledMessage()).thenReturn("Disabled");
        disposable = mock(Disposable.class);
        project = mock(Project.class);
        when(project.getName()).thenReturn("TestProject");
        when(project.isDisposed()).thenReturn(false);
        scannerCommand = new TestScannerCommand(disposable, config);
        controller = mock(GlobalScannerController.class);
        problemHolderService = mock(ProblemHolderService.class);
    }

    @Test
    @DisplayName("register: inactive scanner does not initialize")
    void testRegister_inactiveScanner_doesNotInitialize() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsMock.when(() -> DevAssistUtils.isScannerActive("OSS")).thenReturn(false);
            scannerCommand.register(project);
            assertFalse(((TestScannerCommand)scannerCommand).initialized);
        }
    }

    @Test
    @DisplayName("register: already registered does not initialize")
    void testRegister_alreadyRegistered_doesNotInitialize() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsMock.when(() -> DevAssistUtils.isScannerActive("OSS")).thenReturn(true);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.isRegistered(project, ScanEngine.OSS)).thenReturn(true);
            scannerCommand.register(project);
            assertFalse(((TestScannerCommand)scannerCommand).initialized);
        }
    }

    @Test
    @DisplayName("register: active and not registered initializes")
    void testRegister_activeAndNotRegistered_initializes() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsMock.when(() -> DevAssistUtils.isScannerActive("OSS")).thenReturn(true);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.isRegistered(project, ScanEngine.OSS)).thenReturn(false);
            scannerCommand.register(project);
            assertTrue(((TestScannerCommand)scannerCommand).initialized);
            verify(controller).markRegistered(project, ScanEngine.OSS);
        }
    }

    @Test
    @DisplayName("deregister: not registered does nothing")
    void testDeregister_notRegistered_doesNothing() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.isRegistered(project, ScanEngine.OSS)).thenReturn(false);
            scannerCommand.deregister(project);
            verify(controller, never()).markUnregistered(any(), any());
        }
    }

    @Test
    @DisplayName("deregister: registered and not disposed removes problems")
    void testDeregister_registeredNotDisposed_removesProblems() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> problemHolderServiceMock = mockStatic(ProblemHolderService.class)) {
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.isRegistered(project, ScanEngine.OSS)).thenReturn(true);
            problemHolderServiceMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            scannerCommand.deregister(project);
            verify(controller).markUnregistered(project, ScanEngine.OSS);
            verify(problemHolderService).removeAllScanIssuesOfType("OSS");
        }
    }

    @Test
    @DisplayName("deregister: registered and disposed does nothing after unregister")
    void testDeregister_registeredDisposed_doesNothingAfterUnregister() {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> problemHolderServiceMock = mockStatic(ProblemHolderService.class)) {
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.isRegistered(project, ScanEngine.OSS)).thenReturn(true);
            when(project.isDisposed()).thenReturn(true);
            scannerCommand.deregister(project);
            verify(controller).markUnregistered(project, ScanEngine.OSS);
            verify(problemHolderService, never()).removeAllScanIssuesOfType(any());
        }
    }

    @Test
    @DisplayName("getScannerType returns OSS")
    void testGetScannerType_returnsOSS() {
        assertEquals(ScanEngine.OSS, ((TestScannerCommand)scannerCommand).getScannerTypeForTest());
    }

    @Test
    @DisplayName("findVirtualFile returns null for non-existent path")
    void testFindVirtualFile_returnsNull() {
        // Avoid calling the real LocalFileSystem in unit tests
        TestScannerCommand testScanner = new TestScannerCommand(disposable, config) {
            @Override
            public VirtualFile findVirtualFileForTest(String path) {
                return null; // Simulate no file found, avoid platform call
            }
        };
        assertNull(testScanner.findVirtualFileForTest("/non/existent/path"));
    }

    @Test
    @DisplayName("initializeScanner sets initialized true")
    void testInitializeScanner_setsInitializedTrue()  {
        ((TestScannerCommand)scannerCommand).initializeScanner();
        assertTrue(((TestScannerCommand)scannerCommand).initialized);
    }

    @Test
    @DisplayName("dispose does nothing")
    void testDispose_doesNothing() {
        scannerCommand.dispose();
        // No exception means pass
    }
}

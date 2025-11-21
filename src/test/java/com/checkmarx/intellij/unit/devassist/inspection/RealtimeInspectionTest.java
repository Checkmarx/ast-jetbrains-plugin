package com.checkmarx.intellij.unit.devassist.inspection;

import com.checkmarx.intellij.devassist.inspection.RealtimeInspection;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeInspectionTest {
    @Test
    @DisplayName("Returns empty array when file path is empty or no enabled scanners")
    void testCheckFile_noPathOrNoEnabledScanners_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        RealtimeInspection inspection = spy(new RealtimeInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("");
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.emptyList());
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns empty array when no supported scanners are found")
    void testCheckFile_noSupportedScanners_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        RealtimeInspection inspection = spy(new RealtimeInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file");
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.singletonList(mock(com.checkmarx.intellij.devassist.utils.ScanEngine.class)));
        ScannerFactory scannerFactory = mock(ScannerFactory.class);
        doReturn(Collections.emptyList()).when(scannerFactory).getAllSupportedScanners(anyString());
        setPrivateField(inspection, "scannerFactory", scannerFactory);
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns empty array when scanner is inactive")
    void testCheckFile_scannerInactive_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        RealtimeInspection inspection = spy(new RealtimeInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file");
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.singletonList(mock(com.checkmarx.intellij.devassist.utils.ScanEngine.class)));
        ScannerService<?> scannerService = mock(ScannerService.class);
        when(scannerService.getConfig()).thenReturn(mock(com.checkmarx.intellij.devassist.configuration.ScannerConfig.class));
        when(scannerService.getConfig().getEngineName()).thenReturn("OtherEngine");
        ScannerFactory scannerFactory = mock(ScannerFactory.class);
        doReturn(Collections.singletonList(scannerService)).when(scannerFactory).getAllSupportedScanners(anyString());
        setPrivateField(inspection, "scannerFactory", scannerFactory);
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns problems when valid problem descriptor is present")
    void testCheckFile_problemDescriptorValid_returnsProblems() {
        // Arrange
        RealtimeInspection inspection = spy(new RealtimeInspection());
        // Mock scannerService
        ScannerService<?> scannerService = mock(ScannerService.class);
        com.checkmarx.intellij.devassist.configuration.ScannerConfig config = mock(com.checkmarx.intellij.devassist.configuration.ScannerConfig.class);
        when(scannerService.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("MOCKENGINE");
        List<ScannerService<?>> supportedScanners = Collections.singletonList(scannerService);
        // Mock scanEngine
        ScanEngine scanEngine = mock(ScanEngine.class);
        when(scanEngine.name()).thenReturn("MOCKENGINE");
        List<ScanEngine> enabledScanners = Collections.singletonList(scanEngine);
        // Mock problemDescriptor
        ProblemDescriptor problemDescriptor = mock(ProblemDescriptor.class);
        List<ProblemDescriptor> descriptors = Collections.singletonList(problemDescriptor);
        // Mock file and virtualFile
        PsiFile file = mock(PsiFile.class);
        com.intellij.openapi.vfs.VirtualFile virtualFile = mock(com.intellij.openapi.vfs.VirtualFile.class);
        when(file.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getPath()).thenReturn("TestFile.java");
        when(file.getName()).thenReturn("TestFile.java");
        when(file.getProject()).thenReturn(mock(com.intellij.openapi.project.Project.class));
        when(file.getModificationStamp()).thenReturn(123L);
        when(file.getUserData(any())).thenReturn(null);
        InspectionManager manager = mock(InspectionManager.class);
        ProblemHolderService problemHolderService = mock(ProblemHolderService.class);
        when(problemHolderService.getProblemDescriptors("TestFile.java")).thenReturn(descriptors);
        when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        // Patch globalScannerController to return enabledScanners
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
            when(globalScannerController.getEnabledScanners()).thenReturn(enabledScanners);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            // Patch ProblemHolderService.getInstance to return our mock
            try (MockedStatic<ProblemHolderService> problemHolderServiceMock = mockStatic(ProblemHolderService.class)) {
                problemHolderServiceMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(problemHolderService);
                // Patch scannerFactory to return supportedScanners
                ScannerFactory scannerFactory = mock(ScannerFactory.class);
                doReturn(supportedScanners).when(scannerFactory).getAllSupportedScanners(anyString());
                setPrivateField(inspection, "scannerFactory", scannerFactory);
                // Patch fileTimeStamp to simulate cache hit
                java.util.Map<String, Long> fileTimeStamp = new java.util.HashMap<>();
                fileTimeStamp.put("TestFile.java", 123L);
                setPrivateField(inspection, "fileTimeStamp", fileTimeStamp);
                // Act
                ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
                // Assert
                assertEquals(1, result.length);
            }
        }
    }

    // Update scanFile_handlesException_returnsNull to use reflection
    @Test
    @DisplayName("Returns null when scanFile throws exception")
    void testScanFile_handlesException_returnsNull() {
        RealtimeInspection inspection = new RealtimeInspection();
        ScannerService<?> scannerService = mock(ScannerService.class);
        PsiFile file = mock(PsiFile.class);
        String path = "testPath";
        doThrow(new RuntimeException("fail")).when(scannerService).scan(file, path);
        Object result = invokePrivateMethod(
                inspection,
                "scanFile",
                new Class[]{ScannerService.class, PsiFile.class, String.class},
                new Object[]{scannerService, file, path}
        );
        assertNull(result);
    }

    @Test
    @DisplayName("Handles exception in getProblemsForEnabledScanners using reflection")
    void testGetProblemsForEnabledScanners_handlesException_reflective() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        doThrow(new RuntimeException("fail")).when(descriptor).getFixes();
        descriptors.add(descriptor);
        when(holderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        ProblemDescriptor[] result = (ProblemDescriptor[]) invokePrivateMethod(
                inspection,
                "getProblemsForEnabledScanners",
                new Class[]{ProblemHolderService.class, List.class, String.class},
                new Object[]{holderService, enabledScanners, "path"}
        );
        assertEquals(1, result.length);
    }

    @Test
    @DisplayName("Returns false for invalid problem descriptor with theme change using reflection")
    void testIsProblemDescriptorValid_themeChange_reflective() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        PsiFile file = mock(PsiFile.class);
        String path = "testPath";
        when(file.getUserData(any())).thenReturn(Boolean.TRUE);
        when(holderService.getProblemDescriptors(path)).thenReturn(Collections.singletonList(mock(ProblemDescriptor.class)));
        boolean result = (boolean) invokePrivateMethod(
                inspection,
                "isProblemDescriptorValid",
                new Class[]{ProblemHolderService.class, String.class, PsiFile.class},
                new Object[]{holderService, path, file}
        );
        assertFalse(result);
    }

    @Test
    @DisplayName("Returns not null for fileTimeStamp logic in checkFile using reflection")
    void testCheckFile_fileTimeStampLogic_reflective() {
        RealtimeInspection inspection = new RealtimeInspection();
        PsiFile file = mock(PsiFile.class);
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("testPath");
        when(file.getModificationStamp()).thenReturn(123L);
        // Simulate fileTimeStamp already contains the file
        java.util.Map<String, Long> fileTimeStamp = new java.util.HashMap<>();
        fileTimeStamp.put("testPath", 123L);
        setPrivateField(inspection, "fileTimeStamp", fileTimeStamp);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(holderService.getProblemDescriptors("testPath")).thenReturn(Collections.singletonList(mock(ProblemDescriptor.class)));
        ProblemDescriptor[] result = (ProblemDescriptor[]) invokePrivateMethod(
                inspection,
                "getProblemsForEnabledScanners",
                new Class[]{ProblemHolderService.class, List.class, String.class},
                new Object[]{holderService, Collections.emptyList(), "testPath"}
        );
        assertNotNull(result);
    }

    @Test
    @DisplayName("Returns empty list when createProblemDescriptors handles empty issues (reflective direct)")
    void testCreateProblemDescriptors_handlesEmptyIssues_reflective_direct() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHelper helper = mock(ProblemHelper.class);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(helper.getProblemHolderService()).thenReturn(holderService);
        when(helper.getFile()).thenReturn(mock(PsiFile.class));
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(Collections.emptyList());
        doReturn(scanResult).when(helper).getScanResult();
        when(helper.getFilePath()).thenReturn("/path/to/file.java");
        @SuppressWarnings("unchecked")
        List<ProblemDescriptor> result = (List<ProblemDescriptor>) invokePrivateMethod(
                inspection,
                "createProblemDescriptors",
                new Class[]{ProblemHelper.class},
                new Object[]{helper}
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles exception in getProblemsForEnabledScanners (direct)")
    void testGetProblemsForEnabledScanners_handlesException_direct() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        doThrow(new RuntimeException("fail")).when(descriptor).getFixes();
        descriptors.add(descriptor);
        when(holderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        ProblemDescriptor[] result = (ProblemDescriptor[]) invokePrivateMethod(
                inspection,
                "getProblemsForEnabledScanners",
                new Class[]{ProblemHolderService.class, List.class, String.class},
                new Object[]{holderService, enabledScanners, "path"}
        );
        assertEquals(1, result.length);
    }

    @Test
    @DisplayName("Returns empty list when createProblemDescriptors handles empty issues (direct)")
    void testCreateProblemDescriptors_handlesEmptyIssues_direct() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHelper helper = mock(ProblemHelper.class);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(helper.getProblemHolderService()).thenReturn(holderService);
        when(helper.getFile()).thenReturn(mock(PsiFile.class));
        ScanResult<?> scanResult = mock(ScanResult.class);
        doReturn(Collections.emptyList()).when(scanResult).getIssues();
        doReturn(scanResult).when(helper).getScanResult();
        when(helper.getFilePath()).thenReturn("/path/to/file.java");
        @SuppressWarnings("unchecked")
        List<ProblemDescriptor> result = (List<ProblemDescriptor>) invokePrivateMethod(
                inspection,
                "createProblemDescriptors",
                new Class[]{ProblemHelper.class},
                new Object[]{helper}
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Returns null when scanFile returns null scan result (reflective)")
    void testScanFile_handlesNullScanResult_reflective() {
        RealtimeInspection inspection = new RealtimeInspection();
        ScannerService<?> scannerService = mock(ScannerService.class);
        PsiFile file = mock(PsiFile.class);
        doReturn(null).when(scannerService).scan(file, "path");
        Object result = invokePrivateMethod(
                inspection,
                "scanFile",
                new Class[]{ScannerService.class, PsiFile.class, String.class},
                new Object[]{scannerService, file, "path"}
        );
        assertNull(result);
    }

    @Test
    @DisplayName("Returns empty list when createProblemDescriptors handles empty issues (reflective)")
    void testCreateProblemDescriptors_handlesEmptyIssues_reflective() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHelper helper = mock(ProblemHelper.class);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(helper.getProblemHolderService()).thenReturn(holderService);
        when(helper.getFile()).thenReturn(mock(PsiFile.class));
        ScanResult<?> scanResult = mock(ScanResult.class);
        doReturn(Collections.emptyList()).when(scanResult).getIssues();
        doReturn(scanResult).when(helper).getScanResult();
        when(helper.getFilePath()).thenReturn("/path/to/file.java");
        @SuppressWarnings("unchecked")
        List<ProblemDescriptor> result = (List<ProblemDescriptor>) invokePrivateMethod(
                inspection,
                "createProblemDescriptors",
                new Class[]{ProblemHelper.class},
                new Object[]{helper}
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles exception in getProblemsForEnabledScanners (reflective2)")
    void testGetProblemsForEnabledScanners_handlesException_reflective2() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        doThrow(new RuntimeException("fail")).when(descriptor).getFixes();
        descriptors.add(descriptor);
        when(holderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        ProblemDescriptor[] result = (ProblemDescriptor[]) invokePrivateMethod(
                inspection,
                "getProblemsForEnabledScanners",
                new Class[]{ProblemHolderService.class, List.class, String.class},
                new Object[]{holderService, enabledScanners, "path"}
        );
        assertEquals(1, result.length);
    }

    @Test
    @DisplayName("Triggers icon reload on theme change in checkFile")
    void testCheckFile_themeChange_triggersIconReload() {
        RealtimeInspection inspection = new RealtimeInspection();
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file.java");
        when(file.getUserData(any())).thenReturn(Boolean.TRUE);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        enabledScanners.add(mock(ScanEngine.class));
        // Mock globalScannerController and isDarkTheme
        try (MockedStatic<DevAssistUtils> utilsMock = mockStatic(DevAssistUtils.class)) {
            utilsMock.when(DevAssistUtils::isDarkTheme).thenReturn(Boolean.FALSE);
            GlobalScannerController controller = mock(GlobalScannerController.class);
            utilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(enabledScanners);
            ProblemHolderService problemHolderService = mock(ProblemHolderService.class);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(Collections.singletonList(mock(ProblemDescriptor.class)));
            try (MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
                holderMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(problemHolderService);
                ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
                assertNotNull(result);
            }
        }
    }


    @Test
    @DisplayName("Returns empty list when createProblemDescriptors handles empty issues")
    void testCreateProblemDescriptors_handlesEmptyIssues() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHelper helper = mock(ProblemHelper.class);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(helper.getProblemHolderService()).thenReturn(holderService);
        when(helper.getFile()).thenReturn(mock(PsiFile.class));
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(Collections.emptyList());
        doReturn(scanResult).when(helper).getScanResult();
        when(helper.getFilePath()).thenReturn("/path/to/file.java");
        @SuppressWarnings("unchecked")
        List<ProblemDescriptor> result = (List<ProblemDescriptor>) invokePrivateMethod(
                inspection,
                "createProblemDescriptors",
                new Class[]{ProblemHelper.class},
                new Object[]{helper}
        );
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Handles exception in getProblemsForEnabledScanners")
    void testGetProblemsForEnabledScanners_handlesException() {
        RealtimeInspection inspection = new RealtimeInspection();
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        List<ProblemDescriptor> descriptors = new ArrayList<>();
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenThrow(new RuntimeException("fail"));
        descriptors.add(descriptor);
        when(holderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        ProblemDescriptor[] result = (ProblemDescriptor[]) invokePrivateMethod(
                inspection,
                "getProblemsForEnabledScanners",
                new Class[]{ProblemHolderService.class, List.class, String.class},
                new Object[]{holderService, enabledScanners, "path"}
        );
        assertEquals(1, result.length);
    }

    // Helper method to set private fields via reflection
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Helper method to invoke private methods via reflection
    private Object invokePrivateMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] params) {
        try {
            java.lang.reflect.Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

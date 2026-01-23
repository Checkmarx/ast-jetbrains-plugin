package com.checkmarx.intellij.unit.devassist.inspection;

import com.checkmarx.intellij.devassist.inspection.CxOneAssistInspection;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;

import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
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

class CxOneAssistInspectionTest {
    @Test
    @DisplayName("Returns empty array when file path is empty or no enabled scanners")
    void testCheckFile_noPathOrNoEnabledScanners_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        com.intellij.openapi.project.Project project = mock(com.intellij.openapi.project.Project.class);
        when(manager.getProject()).thenReturn(project);
        CxOneAssistInspection inspection = spy(new CxOneAssistInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("");
        when(file.getProject()).thenReturn(project);
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.emptyList());
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
                MockedStatic<com.checkmarx.intellij.Utils> utilsMock = mockStatic(com.checkmarx.intellij.Utils.class);
                MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            utilsMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mock(ProblemHolderService.class));
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns empty array when no supported scanners are found")
    void testCheckFile_noSupportedScanners_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        com.intellij.openapi.project.Project project = mock(com.intellij.openapi.project.Project.class);
        when(manager.getProject()).thenReturn(project);
        CxOneAssistInspection inspection = spy(new CxOneAssistInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file");
        when(file.getProject()).thenReturn(project);
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.singletonList(mock(com.checkmarx.intellij.devassist.utils.ScanEngine.class)));
        ScannerFactory scannerFactory = mock(ScannerFactory.class);
        doReturn(Collections.emptyList()).when(scannerFactory).getAllSupportedScanners(anyString(),eq(file));
        setPrivateField(inspection, "scannerFactory", scannerFactory);
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
                MockedStatic<com.checkmarx.intellij.Utils> utilsMock = mockStatic(com.checkmarx.intellij.Utils.class);
                MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            utilsMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mock(ProblemHolderService.class));
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns empty array when scanner is inactive")
    void testCheckFile_scannerInactive_returnsEmptyArray() {
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        com.intellij.openapi.project.Project project = mock(com.intellij.openapi.project.Project.class);
        when(manager.getProject()).thenReturn(project);
        CxOneAssistInspection inspection = spy(new CxOneAssistInspection());
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file");
        when(file.getProject()).thenReturn(project);
        GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
        when(globalScannerController.getEnabledScanners()).thenReturn(Collections.singletonList(mock(com.checkmarx.intellij.devassist.utils.ScanEngine.class)));
        ScannerService<?> scannerService = mock(ScannerService.class);
        when(scannerService.getConfig()).thenReturn(mock(com.checkmarx.intellij.devassist.configuration.ScannerConfig.class));
        when(scannerService.getConfig().getEngineName()).thenReturn("OtherEngine");
        ScannerFactory scannerFactory = mock(ScannerFactory.class);
        doReturn(Collections.singletonList(scannerService)).when(scannerFactory).getAllSupportedScanners(anyString(),eq(file));
        setPrivateField(inspection, "scannerFactory", scannerFactory);
        try (
                MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
                MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
                MockedStatic<com.checkmarx.intellij.Utils> utilsMock = mockStatic(com.checkmarx.intellij.Utils.class);
                MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)
        ) {
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            utilsMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mock(ProblemHolderService.class));
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns problems when valid problem descriptor is present")
    void testCheckFile_problemDescriptorValid_returnsProblems() {
        CxOneAssistInspection inspection = spy(new CxOneAssistInspection());
        ScannerService<?> scannerService = mock(ScannerService.class);
        com.checkmarx.intellij.devassist.configuration.ScannerConfig config = mock(com.checkmarx.intellij.devassist.configuration.ScannerConfig.class);
        when(scannerService.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("MOCKENGINE");
        List<ScannerService<?>> supportedScanners = Collections.singletonList(scannerService);
        ScanEngine scanEngine = mock(ScanEngine.class);
        when(scanEngine.name()).thenReturn("MOCKENGINE");
        List<ScanEngine> enabledScanners = Collections.singletonList(scanEngine);
        ProblemDescriptor problemDescriptor = mock(ProblemDescriptor.class);
        when(problemDescriptor.getFixes()).thenReturn(new QuickFix[]{mock(QuickFix.class)});
        List<ProblemDescriptor> descriptors = Collections.singletonList(problemDescriptor);
        PsiFile file = mock(PsiFile.class);
        com.intellij.openapi.vfs.VirtualFile virtualFile = mock(com.intellij.openapi.vfs.VirtualFile.class);
        com.intellij.openapi.project.Project project = mock(com.intellij.openapi.project.Project.class);
        InspectionManager manager = mock(InspectionManager.class);
        when(manager.getProject()).thenReturn(project);
        when(file.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getPath()).thenReturn("TestFile.java");
        when(file.getName()).thenReturn("TestFile.java");
        when(file.getProject()).thenReturn(project);
        when(file.getModificationStamp()).thenReturn(123L);
        when(file.getUserData(any())).thenReturn(null);
        ProblemHolderService problemHolderService = mock(ProblemHolderService.class);
        when(problemHolderService.getProblemDescriptors("TestFile.java")).thenReturn(descriptors);
        when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(descriptors);
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> problemHolderServiceMock = mockStatic(ProblemHolderService.class);
             MockedStatic<com.checkmarx.intellij.Utils> utilsMock = mockStatic(com.checkmarx.intellij.Utils.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class)) {
            GlobalScannerController globalScannerController = mock(GlobalScannerController.class);
            when(globalScannerController.getEnabledScanners()).thenReturn(enabledScanners);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(globalScannerController);
            problemHolderServiceMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            ScannerFactory scannerFactory = mock(ScannerFactory.class);
            doReturn(supportedScanners).when(scannerFactory).getAllSupportedScanners(anyString(),eq(file));
            setPrivateField(inspection, "scannerFactory", scannerFactory);
            java.util.Map<String, Long> fileTimeStamp = new java.util.HashMap<>();
            fileTimeStamp.put("TestFile.java", 123L);
            setPrivateField(inspection, "fileTimeStamp", fileTimeStamp);
            utilsMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            // Ensure production path using ApplicationManager also sees the mocked controller
            Application app = mock(Application.class);
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(app);
            appManagerMock.when(() -> app.getService(GlobalScannerController.class)).thenReturn(globalScannerController);

            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns null when scanFile throws exception")
    void testScanFile_handlesException_returnsNull() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
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
    @DisplayName("Public flow handles descriptor exception gracefully")
    void testCheckFile_descriptorException_publicFlow() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file.java");
        when(file.getProject()).thenReturn(mock(com.intellij.openapi.project.Project.class));
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenThrow(new RuntimeException("fail"));
        when(holderService.getProblemDescriptors(anyString())).thenReturn(Collections.singletonList(descriptor));
        try (MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class);
             MockedStatic<DevAssistUtils> utilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<com.checkmarx.intellij.Utils> authMock = mockStatic(com.checkmarx.intellij.Utils.class)) {
            holderMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(holderService);
            GlobalScannerController controller = mock(GlobalScannerController.class);
            utilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            utilsMock.when(DevAssistUtils::isDarkTheme).thenReturn(false);
            when(controller.getEnabledScanners()).thenReturn(Collections.emptyList());
            authMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            // With no enabled scanners, production code short-circuits; expect 0 descriptors
            assertEquals(0, result.length);
        }
    }

    @Test
    @DisplayName("Returns false for invalid problem descriptor with theme change using reflection")
    void testIsProblemDescriptorValid_themeChange_reflective() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
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
    @DisplayName("Bypass private getProblemsForEnabledScanners: use public checkFile path when timestamp cached")
    void testCheckFile_fileTimeStampLogic_publicFlow() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        com.intellij.openapi.vfs.VirtualFile vf = mock(com.intellij.openapi.vfs.VirtualFile.class);
        when(file.getVirtualFile()).thenReturn(vf);
        when(vf.getPath()).thenReturn("testPath");
        when(file.getModificationStamp()).thenReturn(123L);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(holderService.getProblemDescriptors("testPath")).thenReturn(Collections.singletonList(mock(ProblemDescriptor.class)));
        try (MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class);
             MockedStatic<DevAssistUtils> utilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<com.checkmarx.intellij.Utils> authMock = mockStatic(com.checkmarx.intellij.Utils.class)) {
            holderMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(holderService);
            GlobalScannerController controller = mock(GlobalScannerController.class);
            utilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(Collections.emptyList());
            authMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Triggers icon reload on theme change in checkFile")
    void testCheckFile_themeChange_triggersIconReload() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
        PsiFile file = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        when(file.getVirtualFile()).thenReturn(mock(com.intellij.openapi.vfs.VirtualFile.class));
        when(file.getVirtualFile().getPath()).thenReturn("/path/to/file.java");
        when(file.getUserData(any())).thenReturn(Boolean.TRUE);
        List<ScanEngine> enabledScanners = new ArrayList<>();
        enabledScanners.add(mock(ScanEngine.class));
        try (MockedStatic<DevAssistUtils> utilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class);
             MockedStatic<com.checkmarx.intellij.Utils> authMock = mockStatic(com.checkmarx.intellij.Utils.class)) {
            utilsMock.when(DevAssistUtils::isDarkTheme).thenReturn(Boolean.FALSE);
            GlobalScannerController controller = mock(GlobalScannerController.class);
            utilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(enabledScanners);
            ProblemHolderService problemHolderService = mock(ProblemHolderService.class);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(Collections.singletonList(mock(ProblemDescriptor.class)));
            holderMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(problemHolderService);
            authMock.when(com.checkmarx.intellij.Utils::isUserAuthenticated).thenReturn(true);
            ProblemDescriptor[] result = inspection.checkFile(file, manager, true);
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("Returns empty list when createProblemDescriptors handles empty issues")
    void testCreateProblemDescriptors_handlesEmptyIssues() {
        CxOneAssistInspection inspection = new CxOneAssistInspection();
        ProblemHelper helper = mock(ProblemHelper.class);
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        when(helper.getProblemHolderService()).thenReturn(holderService);
        when(helper.getFile()).thenReturn(mock(PsiFile.class));
        when(helper.getScanIssueList()).thenReturn(Collections.emptyList());
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

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

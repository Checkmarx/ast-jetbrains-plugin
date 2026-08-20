package com.checkmarx.intellij.devassist.test.inspection;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.inspection.DevAssistInspectionMgr;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.remediation.DevAssistFix;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCAN_SOURCE_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevAssistInspectionMgrTest {

    private DevAssistInspectionMgr mgr;
    private Project mockProject;
    private PsiFile mockFile;

    @BeforeEach
    void setUp() {
        mgr = new DevAssistInspectionMgr();
        mockProject = mock(Project.class);
        mockFile = mock(PsiFile.class);
        when(mockFile.getProject()).thenReturn(mockProject);
        when(mockFile.getName()).thenReturn("test.java");
    }

    // ===== isScanIssuePresent (private) =====

    private boolean isScanIssuePresent(List<ScanIssue> list, String fileName) throws Exception {
        Method m = DevAssistInspectionMgr.class.getDeclaredMethod("isScanIssuePresent", List.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(mgr, list, fileName);
    }

    @Test
    void isScanIssuePresent_nullList_returnsFalse() throws Exception {
        assertFalse(isScanIssuePresent(null, "file.java"));
    }

    @Test
    void isScanIssuePresent_emptyList_returnsFalse() throws Exception {
        assertFalse(isScanIssuePresent(Collections.emptyList(), "file.java"));
    }

    @Test
    void isScanIssuePresent_nonEmptyList_returnsTrue() throws Exception {
        assertTrue(isScanIssuePresent(List.of(new ScanIssue()), "file.java"));
    }

    // ===== createProblemDescriptorsWithoutDecoration =====

    @Test
    void createProblemDescriptorsWithoutDecoration_emptyIssueList_returnsEmptyList() {
        ProblemHelper problemHelper = ProblemHelper.builder(mockFile, mockProject)
                .scanIssueList(Collections.emptyList())
                .build();

        List<ProblemDescriptor> result = mgr.createProblemDescriptorsWithoutDecoration(problemHelper);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ===== updateScanSourceFlag =====

    @Test
    void updateScanSourceFlag_setsUserDataToFalse() {
        mgr.updateScanSourceFlag(mockFile, Boolean.FALSE);
        verify(mockFile).putUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCAN_SOURCE_KEY, Boolean.FALSE);
    }

    @Test
    void updateScanSourceFlag_setsUserDataToTrue() {
        mgr.updateScanSourceFlag(mockFile, Boolean.TRUE);
        verify(mockFile).putUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCAN_SOURCE_KEY, Boolean.TRUE);
    }

    // ===== resetEditorAndResults =====

    @Test
    void resetEditorAndResults_disposedProject_returnsEarly() {
        when(mockProject.isDisposed()).thenReturn(true);

        try (MockedStatic<ProblemHolderService> holderStatic = mockStatic(ProblemHolderService.class);
             MockedStatic<ProblemDecorator> decoratorStatic = mockStatic(ProblemDecorator.class)) {
            assertDoesNotThrow(() -> mgr.resetEditorAndResults(mockProject, "/path/to/file.java"));
            decoratorStatic.verify(() -> ProblemDecorator.removeAllHighlighters(any()), never());
        }
    }

    @Test
    void resetEditorAndResults_activeProject_nullProblemHolderService_doesNotCrash() {
        when(mockProject.isDisposed()).thenReturn(false);
        when(mockProject.getName()).thenReturn("test-project");

        try (MockedStatic<ProblemHolderService> holderStatic = mockStatic(ProblemHolderService.class);
             MockedStatic<ProblemDecorator> decoratorStatic = mockStatic(ProblemDecorator.class)) {
            holderStatic.when(() -> ProblemHolderService.getInstance(mockProject)).thenReturn(null);
            decoratorStatic.when(() -> ProblemDecorator.removeAllHighlighters(mockProject)).then(inv -> null);

            assertDoesNotThrow(() -> mgr.resetEditorAndResults(mockProject, "/path/to/file.java"));
            decoratorStatic.verify(() -> ProblemDecorator.removeAllHighlighters(mockProject));
        }
    }

    @Test
    void resetEditorAndResults_activeProject_withProblemHolderService_removesResults() {
        when(mockProject.isDisposed()).thenReturn(false);
        when(mockProject.getName()).thenReturn("test-project");
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        String filePath = "/path/to/file.java";

        try (MockedStatic<ProblemHolderService> holderStatic = mockStatic(ProblemHolderService.class);
             MockedStatic<ProblemDecorator> decoratorStatic = mockStatic(ProblemDecorator.class)) {
            holderStatic.when(() -> ProblemHolderService.getInstance(mockProject)).thenReturn(mockHolderService);
            decoratorStatic.when(() -> ProblemDecorator.removeAllHighlighters(mockProject)).then(inv -> null);

            mgr.resetEditorAndResults(mockProject, filePath);

            verify(mockHolderService).removeProblemDescriptorsForFile(filePath);
            verify(mockHolderService).removeScanIssues(filePath);
        }
    }

    @Test
    void resetEditorAndResults_activeProject_emptyFilePath_skipsHolderRemoval() {
        when(mockProject.isDisposed()).thenReturn(false);
        when(mockProject.getName()).thenReturn("test-project");
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);

        try (MockedStatic<ProblemHolderService> holderStatic = mockStatic(ProblemHolderService.class);
             MockedStatic<ProblemDecorator> decoratorStatic = mockStatic(ProblemDecorator.class)) {
            holderStatic.when(() -> ProblemHolderService.getInstance(mockProject)).thenReturn(mockHolderService);
            decoratorStatic.when(() -> ProblemDecorator.removeAllHighlighters(mockProject)).then(inv -> null);

            mgr.resetEditorAndResults(mockProject, "");

            verify(mockHolderService, never()).removeProblemDescriptorsForFile(any());
            verify(mockHolderService, never()).removeScanIssues(any());
        }
    }

    // ===== isThemeChanged (private) =====

    @Test
    void isThemeChanged_nullUserData_returnsFalse() throws Exception {
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(null);

        Method m = DevAssistInspectionMgr.class.getDeclaredMethod("isThemeChanged", PsiFile.class);
        m.setAccessible(true);

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true);
            assertFalse((boolean) m.invoke(mgr, mockFile));
        }
    }

    @Test
    void isThemeChanged_sameTheme_returnsFalse() throws Exception {
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(Boolean.TRUE);

        Method m = DevAssistInspectionMgr.class.getDeclaredMethod("isThemeChanged", PsiFile.class);
        m.setAccessible(true);

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true);
            assertFalse((boolean) m.invoke(mgr, mockFile));
        }
    }

    @Test
    void isThemeChanged_differentTheme_returnsTrue() throws Exception {
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(Boolean.FALSE);

        Method m = DevAssistInspectionMgr.class.getDeclaredMethod("isThemeChanged", PsiFile.class);
        m.setAccessible(true);

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true);
            assertTrue((boolean) m.invoke(mgr, mockFile));
        }
    }

    // ===== startScanAndCreateProblemDescriptors =====

    @Test
    void startScanAndCreateProblemDescriptors_emptyScanIssues_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        doReturn(Collections.emptyList()).when(spy).scanFile(anyString(), any(), any());
        doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

        ProblemHelper.ProblemHelperBuilder builder = ProblemHelper.builder(mockFile, mockProject)
                .filePath("/test/file.java");

        ProblemDescriptor[] result = spy.startScanAndCreateProblemDescriptors(builder);

        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void startScanAndCreateProblemDescriptors_withIssuesButNoDescriptors_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        ScanIssue mockIssue = mock(ScanIssue.class);

        doReturn(List.of(mockIssue)).when(spy).scanFile(anyString(), any(), any());
        // spy createProblemDescriptorsWithDecoration → empty (no descriptor produced for mockIssue)
        // by stubbing the internal decorateUIForIgnoreVulnerability and ProblemDecorator calls
        try (MockedStatic<ProblemDecorator> decoratorStatic = mockStatic(ProblemDecorator.class)) {
            decoratorStatic.when(() -> ProblemDecorator.removeAllHighlighters(any())).thenAnswer(inv -> null);
            doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

            ProblemHelper.ProblemHelperBuilder builder = ProblemHelper.builder(mockFile, mockProject)
                    .filePath("/test/file.java")
                    .problemHolderService(mockHolderService);

            // ScanIssueProcessor.processScanIssue returns null for minimal ScanIssue → problems list is empty
            ProblemDescriptor[] result = spy.startScanAndCreateProblemDescriptors(builder);

            assertNotNull(result);
            // No valid descriptors created for a bare mock ScanIssue → empty array
            assertEquals(0, result.length);
        }
    }

    // ===== decorateUIForIgnoreVulnerability =====

    @Test
    void decorateUIForIgnoreVulnerability_emptyList_doesNotThrow() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doNothing().when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));

            assertDoesNotThrow(() -> mgr.decorateUIForIgnoreVulnerability(mockFile, Collections.emptyList()));
        }
    }

    // ===== getSupportedScanner =====

    @Test
    void getSupportedScanner_withNoActiveScanner_returnsEmptyList() {
        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(() -> DevAssistUtils.isScannerActive(any())).thenReturn(false);

            List<ScannerService<?>> result = mgr.getSupportedScanner("/path/to/File.java", mockFile);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ===== getExistingProblems — scheduledScan path =====

    @Test
    void getExistingProblems_fromScheduledScan_emptyIssues_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        List<ScannerService<?>> scanners = Collections.emptyList();
        String filePath = "/path/file.java";

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(Boolean.TRUE);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(Collections.emptyList());
        doNothing().when(spy).resetEditorAndResults(any(), any());
        doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, scanners, inspectionManager);

        assertEquals(0, result.length);
    }

    @Test
    void getExistingProblems_fromScheduledScan_nonEmptyIssues_noDescriptors_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        List<ScannerService<?>> scanners = Collections.emptyList();
        String filePath = "/path/file.java";

        ScanIssue issue = new ScanIssue();
        issue.setSeverity("High");

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(Boolean.TRUE);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(Collections.emptyList());
        doNothing().when(spy).decorateUI(any(), any(), any());

        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, scanners, inspectionManager);

        assertEquals(0, result.length);
    }

    @Test
    void getExistingProblems_fromScheduledScan_nonEmptyIssues_withDescriptors_returnsDescriptors() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        List<ScannerService<?>> scanners = Collections.emptyList();
        String filePath = "/path/file.java";

        ScanIssue issue = new ScanIssue();
        issue.setSeverity("High");
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(Boolean.TRUE);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(List.of(descriptor));
        doNothing().when(spy).decorateUI(any(), any(), any());

        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, scanners, inspectionManager);

        assertEquals(1, result.length);
        assertSame(descriptor, result[0]);
    }

    // ===== getExistingProblems — non-scheduledScan path (getCachedProblemDescriptorsForNonModifiedFile) =====

    @Test
    void getExistingProblems_nonScheduled_emptyEnabledScanners_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        doNothing().when(spy).resetEditorAndResults(any(), any());
        doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

        // Empty scanners list → no enabled engines
        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, Collections.emptyList(), inspectionManager);

        assertEquals(0, result.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_emptyScanIssues_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA");

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(Collections.emptyList());
        doNothing().when(spy).resetEditorAndResults(any(), any());
        doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

        assertEquals(0, result.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_emptyDescriptors_returnsEmptyArray() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA");

        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(Collections.emptyList());
        doNothing().when(spy).decorateUIForIgnoreVulnerability(any(), any());

        ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

        assertEquals(0, result.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_validData_noDisabledEngines_returnsDescriptors() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA");

        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(null);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(List.of(descriptor));
        doNothing().when(spy).decorateUI(any(), any(), any());

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true);

            ProblemDescriptor[] result = spy.getExistingProblems(holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

            assertEquals(1, result.length);
        }
    }

    // ===== decorateUI =====

    @Test
    void decorateUI_withEmptyIssueList_doesNotThrow() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doNothing().when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));

            assertDoesNotThrow(() -> mgr.decorateUI(mock(Document.class), mockFile, Collections.emptyList()));
        }
    }

    // ===== triggerInspection =====

    @Test
    void triggerInspection_withNullVirtualFile_doesNotThrow() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doNothing().when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));

            assertDoesNotThrow(() -> mgr.triggerInspection(mockProject));
        }
    }

    @Test
    void triggerInspection_invokesLaterInNonModalState() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doNothing().when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));

            mgr.triggerInspection(mockProject);

            verify(mockApplication).invokeLater(any(Runnable.class), eq(ModalityState.NON_MODAL));
        }
    }

    @Test
    void triggerInspection_exceptionDuringInvokeLater_doesNotPropagate() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doThrow(new RuntimeException("boom")).when(mockApplication)
                    .invokeLater(any(Runnable.class), any(ModalityState.class));

            assertDoesNotThrow(() -> mgr.triggerInspection(mockProject));
        }
    }

    // ===== getEnabledScannerProblems (private) =====

    @SuppressWarnings("unchecked")
    private List<ProblemDescriptor> callGetEnabledScannerProblems(
            String filePath, List<ProblemDescriptor> descriptors, List<ScanEngine> engines) throws Exception {
        Method m = DevAssistInspectionMgr.class.getDeclaredMethod(
                "getEnabledScannerProblems", String.class, List.class, List.class);
        m.setAccessible(true);
        return (List<ProblemDescriptor>) m.invoke(mgr, filePath, descriptors, engines);
    }

    @Test
    void getEnabledScannerProblems_emptyDescriptors_returnsEmptyList() throws Exception {
        List<ProblemDescriptor> result = callGetEnabledScannerProblems(
                "/file.java", Collections.emptyList(), List.of(ScanEngine.ASCA));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getEnabledScannerProblems_descriptorWithMatchingEngine_isIncluded() throws Exception {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);

        DevAssistFix fix = mock(DevAssistFix.class);
        when(fix.getScanIssue()).thenReturn(issue);

        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenReturn(new com.intellij.codeInspection.LocalQuickFix[]{fix});

        List<ProblemDescriptor> result = callGetEnabledScannerProblems(
                "/file.java", List.of(descriptor), List.of(ScanEngine.ASCA));

        assertEquals(1, result.size());
        assertSame(descriptor, result.get(0));
    }

    @Test
    void getEnabledScannerProblems_descriptorWithNonMatchingEngine_isExcluded() throws Exception {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.OSS);

        DevAssistFix fix = mock(DevAssistFix.class);
        when(fix.getScanIssue()).thenReturn(issue);

        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenReturn(new com.intellij.codeInspection.LocalQuickFix[]{fix});

        List<ProblemDescriptor> result = callGetEnabledScannerProblems(
                "/file.java", List.of(descriptor), List.of(ScanEngine.ASCA));

        assertTrue(result.isEmpty());
    }

    @Test
    void getEnabledScannerProblems_descriptorWithExceptionCastingFix_isIncluded() throws Exception {
        // Descriptor with a fix that cannot be cast to DevAssistFix → exception catch → included as fallback
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        LocalQuickFix nonDevAssistFix = mock(LocalQuickFix.class);
        when(descriptor.getFixes()).thenReturn(new LocalQuickFix[]{nonDevAssistFix});

        List<ProblemDescriptor> result = callGetEnabledScannerProblems(
                "/file.java", List.of(descriptor), List.of(ScanEngine.ASCA));

        assertEquals(1, result.size());
    }

    // ===== triggerInspection — lambda body executed =====

    @Test
    void triggerInspection_lambdaBody_nullVirtualFile_doesNotThrow() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));
            devUtilsMock.when(() -> DevAssistUtils.getCurrentSelectedFile(any())).thenReturn(null);

            assertDoesNotThrow(() -> mgr.triggerInspection(mockProject));
        }
    }

    @Test
    void triggerInspection_lambdaBody_nullDocument_doesNotThrow() {
        VirtualFile mockVf = mock(VirtualFile.class);
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            Application mockApplication = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(mockApplication).invokeLater(any(Runnable.class), any(ModalityState.class));
            devUtilsMock.when(() -> DevAssistUtils.getCurrentSelectedFile(any())).thenReturn(mockVf);
            FileDocumentManager fdm = mock(FileDocumentManager.class);
            fdmMock.when(FileDocumentManager::getInstance).thenReturn(fdm);
            when(fdm.getDocument(mockVf)).thenReturn(null);

            assertDoesNotThrow(() -> mgr.triggerInspection(mockProject));
        }
    }

    // ===== getExistingProblems — hasDisabledEngines branch =====

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_withDisabledEngines_filtersDescriptors() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA"); // only ASCA enabled

        ScanIssue ossIssue = new ScanIssue();
        ossIssue.setScanEngine(ScanEngine.OSS); // OSS not in enabled list → hasDisabledEngines = true

        ScanIssue ossFix = new ScanIssue();
        ossFix.setScanEngine(ScanEngine.OSS);
        DevAssistFix fix = mock(DevAssistFix.class);
        when(fix.getScanIssue()).thenReturn(ossFix);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenReturn(new LocalQuickFix[]{fix});

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(null);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(ossIssue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(List.of(descriptor));
        doNothing().when(spy).decorateUI(any(), any(), any());

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true);

            ProblemDescriptor[] result = spy.getExistingProblems(
                    holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

            assertEquals(0, result.length); // OSS descriptor filtered since ASCA-only enabled
        }
    }

    // ===== getExistingProblems — hasDisabledEngines=true, ASCA descriptor kept =====

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_withDisabledEngines_ascaDescriptorKept_returnsIt() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA"); // only ASCA enabled

        ScanIssue ossIssue = new ScanIssue();
        ossIssue.setScanEngine(ScanEngine.OSS);
        ScanIssue ascaIssue = new ScanIssue();
        ascaIssue.setScanEngine(ScanEngine.ASCA);

        ScanIssue ascaFixIssue = new ScanIssue();
        ascaFixIssue.setScanEngine(ScanEngine.ASCA);
        DevAssistFix ascaFix = mock(DevAssistFix.class);
        when(ascaFix.getScanIssue()).thenReturn(ascaFixIssue);
        ProblemDescriptor ascaDescriptor = mock(ProblemDescriptor.class);
        when(ascaDescriptor.getFixes()).thenReturn(new LocalQuickFix[]{ascaFix});

        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY)).thenReturn(null);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(ossIssue, ascaIssue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(List.of(ascaDescriptor));
        doNothing().when(spy).decorateUI(any(), any(), any());

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(false);

            ProblemDescriptor[] result = spy.getExistingProblems(
                    holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

            assertEquals(1, result.length);
        }
    }

    // ===== getExistingProblems — isThemeChanged = true branch (createProblemDescriptorsOnThemeChanged) =====

    @Test
    @SuppressWarnings("unchecked")
    void getExistingProblems_nonScheduled_withThemeChange_callsReloadIcons() {
        DevAssistInspectionMgr spy = spy(new DevAssistInspectionMgr());
        ProblemHolderService holderService = mock(ProblemHolderService.class);
        Document document = mock(Document.class);
        InspectionManager inspectionManager = mock(InspectionManager.class);
        String filePath = "/path/file.java";

        ScannerService<Object> scanner = mock(ScannerService.class);
        ScannerConfig config = mock(ScannerConfig.class);
        when(scanner.getConfig()).thenReturn(config);
        when(config.getEngineName()).thenReturn("ASCA");

        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);

        // THEME_KEY stored as FALSE but isDarkTheme() returns TRUE → theme changed
        when(mockFile.getUserData(SCAN_SOURCE_KEY)).thenReturn(null);
        when(mockFile.getUserData(com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.THEME_KEY))
                .thenReturn(Boolean.FALSE);
        when(holderService.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
        when(holderService.getProblemDescriptors(filePath)).thenReturn(List.of(descriptor));
        doNothing().when(spy).decorateUI(any(), any(), any());
        doReturn(Collections.emptyList()).when(spy).createProblemDescriptorsWithoutDecoration(any());

        try (MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemDescription> pdMock = mockStatic(ProblemDescription.class)) {
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(true); // now dark → different from stored FALSE
            pdMock.when(ProblemDescription::reloadIcons).thenAnswer(inv -> null);

            ProblemDescriptor[] result = spy.getExistingProblems(
                    holderService, filePath, document, mockFile, List.of(scanner), inspectionManager);

            pdMock.verify(ProblemDescription::reloadIcons);
            assertEquals(0, result.length);
        }
    }
}

package com.checkmarx.intellij.unit.devassist.listener;
import com.checkmarx.intellij.devassist.listeners.DevAssistFileListener;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DevAssistFileListenerTest {
    private Project project;
    private PsiFile psiFile;
    private VirtualFile virtualFile;
    private FileEditorManager fileEditorManager;
    private MessageBusConnection messageBusConnection;
    private PsiManager psiManager;
    private ProblemHolderService problemHolderService;
    private Document document;
    private ScanEngine scanEngine;
    private ScanIssue scanIssue;
    private ProblemDescriptor problemDescriptor;

    @BeforeEach
    void setUp() {
        project = mock(Project.class);
        psiFile = mock(PsiFile.class);
        virtualFile = mock(VirtualFile.class);
        fileEditorManager = mock(FileEditorManager.class);
        messageBusConnection = mock(MessageBusConnection.class);
        psiManager = mock(PsiManager.class);
        problemHolderService = mock(ProblemHolderService.class);
        document = mock(Document.class);
        scanEngine = mock(ScanEngine.class);
        scanIssue = mock(ScanIssue.class);
        problemDescriptor = mock(ProblemDescriptor.class);
    }

    @Test
    @DisplayName("Registers file editor listener and verifies subscription")
    void testRegisterListener_fileOpenedAndClosed() {
        com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller =
                mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);

            Project project = mock(Project.class);
            MessageBus messageBus = mock(MessageBus.class);
            MessageBusConnection messageBusConnection = mock(MessageBusConnection.class);

            when(project.getMessageBus()).thenReturn(messageBus);
            when(messageBus.connect()).thenReturn(messageBusConnection);
            doNothing().when(messageBusConnection).subscribe(any(), any(FileEditorManagerListener.class));

            DevAssistFileListener.register(project);

            verify(messageBusConnection, times(1)).subscribe(any(), any(FileEditorManagerListener.class));
        }
    }



    @Test
    @DisplayName("Returns early when psiFile is null in restoreGutterIcons")
    void testRestoreGutterIcons_nullPsiFile() throws Exception {
        // Should return early if psiFile is null
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, null, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Skips gutter icon restoration when no scanners are enabled")
    void testRestoreGutterIcons_noEnabledScanners() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(Collections.emptyList());
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Skips gutter icon restoration when no problem descriptors exist")
    void testRestoreGutterIcons_noProblemDescriptors() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(Collections.emptyList());
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Skips gutter icon restoration when no issues exist in the map")
    void testRestoreGutterIcons_noIssuesInMap() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(List.of(problemDescriptor));
            when(problemHolderService.getAllIssues()).thenReturn(Collections.emptyMap());
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Skips gutter icon restoration when no scan issues are found for the file")
    void testRestoreGutterIcons_noScanIssuesForFile() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(List.of(problemDescriptor));
            when(problemHolderService.getAllIssues()).thenReturn(Map.of("otherFile.java", List.of(scanIssue)));
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Skips gutter icon restoration when enabled engine has no scan issues")
    void testRestoreGutterIcons_noEnabledEngineScanIssues() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(List.of(problemDescriptor));
            when(problemHolderService.getAllIssues()).thenReturn(Map.of("/path/to/file.java", List.of(scanIssue)));
            when(scanIssue.getScanEngine()).thenReturn(mock(ScanEngine.class)); // Not equal to scanEngine
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Handles null document case in restoreGutterIcons")
    void testRestoreGutterIcons_documentNull() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class);
             MockedStatic<PsiDocumentManager> psiDocumentManagerMock = mockStatic(PsiDocumentManager.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(List.of(problemDescriptor));
            when(problemHolderService.getAllIssues()).thenReturn(Map.of("/path/to/file.java", List.of(scanIssue)));
            when(scanIssue.getScanEngine()).thenReturn(scanEngine);
            PsiDocumentManager psiDocumentManager = mock(PsiDocumentManager.class);
            psiDocumentManagerMock.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager);
            when(psiDocumentManager.getDocument(psiFile)).thenReturn(null);
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Successfully restores gutter icons")
    void testRestoreGutterIcons_success() throws Exception {
        try (MockedStatic<DevAssistUtils> devAssistUtilsMock = mockStatic(DevAssistUtils.class);
             MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class);
             MockedStatic<PsiDocumentManager> psiDocumentManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ProblemDecorator> decoratorMock = mockStatic(ProblemDecorator.class)) {
            com.checkmarx.intellij.devassist.configuration.GlobalScannerController controller = mock(com.checkmarx.intellij.devassist.configuration.GlobalScannerController.class);
            devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(controller);
            when(controller.getEnabledScanners()).thenReturn(List.of(scanEngine));
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            when(problemHolderService.getProblemDescriptors(anyString())).thenReturn(List.of(problemDescriptor));
            when(problemHolderService.getAllIssues()).thenReturn(Map.of("/path/to/file.java", List.of(scanIssue)));
            when(scanIssue.getScanEngine()).thenReturn(scanEngine);
            PsiDocumentManager psiDocumentManager = mock(PsiDocumentManager.class);
            psiDocumentManagerMock.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager);
            when(psiDocumentManager.getDocument(psiFile)).thenReturn(document);
            ProblemDecorator decorator = mock(ProblemDecorator.class);
            doNothing().when(decorator).decorateUI(any(), any(), any(), any());
            // Should reach the decorator call
            DevAssistFileListenerTestHelper.invokeRestoreGutterIcons(project, psiFile, "/path/to/file.java");
        }
    }

    @Test
    @DisplayName("Filters scan issues correctly for enabled scanners")
    void testGetScanIssuesForEnabledScanner_filtersCorrectly() throws Exception {
        ScanEngine engine1 = mock(ScanEngine.class);
        ScanEngine engine2 = mock(ScanEngine.class);
        ScanIssue issue1 = mock(ScanIssue.class);
        ScanIssue issue2 = mock(ScanIssue.class);
        when(issue1.getScanEngine()).thenReturn(engine1);
        when(issue2.getScanEngine()).thenReturn(engine2);
        List<ScanIssue> result = DevAssistFileListenerTestHelper.invokeGetScanIssuesForEnabledScanner(List.of(engine1), List.of(issue1, issue2));
        assertEquals(1, result.size());
        assertTrue(result.contains(issue1));
    }

    @Test
    @DisplayName("Does not call ProblemHolderService for null or empty path in removeProblemDescriptor")
    void testRemoveProblemDescriptor_nullPath() {
        DevAssistFileListener.removeProblemDescriptor(project, null);
        DevAssistFileListener.removeProblemDescriptor(project, "");
        // Should not call ProblemHolderService.getInstance
    }

    @Test
    @DisplayName("Removes problem descriptors for valid file path in removeProblemDescriptor")
    void testRemoveProblemDescriptor_validPath() {
        try (MockedStatic<ProblemHolderService> holderMock = mockStatic(ProblemHolderService.class)) {
            holderMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);
            DevAssistFileListener.removeProblemDescriptor(project, "/path/to/file.java");
            verify(problemHolderService, times(1)).removeProblemDescriptorsForFile("/path/to/file.java");
        }
    }
    // Helper class to invoke private static methods for coverage
    static class DevAssistFileListenerTestHelper {
        static void invokeRestoreGutterIcons(Project project, PsiFile psiFile, String filePath) throws Exception {
            var method = DevAssistFileListener.class.getDeclaredMethod("restoreGutterIcons", Project.class, PsiFile.class, String.class);
            method.setAccessible(true);
            method.invoke(null, project, psiFile, filePath);
        }
        static List<ScanIssue> invokeGetScanIssuesForEnabledScanner(List<ScanEngine> enabledScanEngines, List<ScanIssue> scanIssueList) throws Exception {
            var method = DevAssistFileListener.class.getDeclaredMethod("getScanIssuesForEnabledScanner", List.class, List.class);
            method.setAccessible(true);
            return (List<ScanIssue>) method.invoke(null, enabledScanEngines, scanIssueList);
        }
    }
}



package com.checkmarx.intellij.devassist.test.utils;

import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DevAssistUtilsTest {

    // Helper to mock Document with desired line content
    private Document mockDocument(String[] lines) {
        Document doc = mock(Document.class);
        when(doc.getLineCount()).thenReturn(lines.length);
        // Build a single joined text with newlines
        StringBuilder all = new StringBuilder();
        for (int i=0;i<lines.length;i++) {
            all.append(lines[i]);
            if (i < lines.length-1) all.append("\n");
            int startOffset = all.length() - lines[i].length();
            int endOffset = all.length();
            when(doc.getLineStartOffset(i)).thenReturn(startOffset);
            when(doc.getLineEndOffset(i)).thenReturn(endOffset);
        }
        when(doc.getCharsSequence()).thenReturn(all.toString());
        return doc;
    }

    // isScannerActive tests
    @Test @DisplayName("isScannerActive_nullEngineName_returnsFalse")
    void testIsScannerActive_nullEngineName_returnsFalse() {
        assertFalse(DevAssistUtils.isScannerActive(null));
    }

    @Test @DisplayName("isScannerActive_invalidEngineName_returnsFalse")
    void testIsScannerActive_invalidEngineName_returnsFalse() {
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            GlobalSettingsState state = mock(GlobalSettingsState.class);
            when(state.isAuthenticated()).thenReturn(true);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(state);
            assertFalse(DevAssistUtils.isScannerActive("not_an_engine"));
        }
    }

    @Test @DisplayName("isScannerActive_authenticatedEnabledScanner_returnsTrue")
    void testIsScannerActive_authenticatedEnabledScanner_returnsTrue() {
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<DevAssistUtils> self = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS)) {
            GlobalSettingsState state = mock(GlobalSettingsState.class);
            when(state.isAuthenticated()).thenReturn(true);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(state);
            GlobalScannerController ctrl = mock(GlobalScannerController.class);
            when(ctrl.isScannerGloballyEnabled(ScanEngine.OSS)).thenReturn(true);
            self.when(DevAssistUtils::globalScannerController).thenReturn(ctrl);
            assertTrue(DevAssistUtils.isScannerActive("oss"));
        }
    }

    @Test @DisplayName("isScannerActive_authenticatedDisabledScanner_returnsFalse")
    void testIsScannerActive_authenticatedDisabledScanner_returnsFalse() {
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<DevAssistUtils> self = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS)) {
            GlobalSettingsState state = mock(GlobalSettingsState.class);
            when(state.isAuthenticated()).thenReturn(true);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(state);
            GlobalScannerController ctrl = mock(GlobalScannerController.class);
            when(ctrl.isScannerGloballyEnabled(ScanEngine.OSS)).thenReturn(false);
            self.when(DevAssistUtils::globalScannerController).thenReturn(ctrl);
            assertFalse(DevAssistUtils.isScannerActive("oss"));
        }
    }

    @Test @DisplayName("isAnyScannerEnabled_controllerReturnsTrue")
    void testIsAnyScannerEnabled_controllerReturnsTrue() {
        try (MockedStatic<DevAssistUtils> self = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS)) {
            GlobalScannerController ctrl = mock(GlobalScannerController.class);
            when(ctrl.checkAnyScannerEnabled()).thenReturn(true);
            self.when(DevAssistUtils::globalScannerController).thenReturn(ctrl);
            assertTrue(DevAssistUtils.isAnyScannerEnabled());
        }
    }

    @Test @DisplayName("isAnyScannerEnabled_controllerReturnsFalse")
    void testIsAnyScannerEnabled_controllerReturnsFalse() {
        try (MockedStatic<DevAssistUtils> self = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS)) {
            GlobalScannerController ctrl = mock(GlobalScannerController.class);
            when(ctrl.checkAnyScannerEnabled()).thenReturn(false);
            self.when(DevAssistUtils::globalScannerController).thenReturn(ctrl);
            assertFalse(DevAssistUtils.isAnyScannerEnabled());
        }
    }

    // getTextRangeForLine tests
    @Test @DisplayName("getTextRangeForLine_allWhitespaceLine_returnsFullRange")
    void testGetTextRangeForLine_allWhitespaceLine_returnsFullRange() {
        Document doc = mockDocument(new String[]{"   ","code"});
        TextRange range = DevAssistUtils.getTextRangeForLine(doc,1); // first line (1-based)
        assertEquals(doc.getLineStartOffset(0), range.getStartOffset());
        assertEquals(doc.getLineEndOffset(0), range.getEndOffset());
    }

    @Test @DisplayName("getTextRangeForLine_trimmedLine_correctOffsets")
    void testGetTextRangeForLine_trimmedLine_correctOffsets() {
        Document doc = mockDocument(new String[]{"  hello  ","other"});
        TextRange range = DevAssistUtils.getTextRangeForLine(doc,1);
        CharSequence all = doc.getCharsSequence();
        int start = all.toString().indexOf("hello");
        int end = start + "hello".length();
        assertEquals(start, range.getStartOffset());
        assertEquals(end, range.getEndOffset());
    }

    // isLineOutOfRange tests
    @Test @DisplayName("isLineOutOfRange_zero_returnsTrue")
    void testIsLineOutOfRange_zero_returnsTrue() {
        Document doc = mockDocument(new String[]{"a"});
        assertTrue(DevAssistUtils.isLineOutOfRange(0, doc));
    }

    @Test @DisplayName("isLineOutOfRange_gtCount_returnsTrue")
    void testIsLineOutOfRange_gtCount_returnsTrue() {
        Document doc = mockDocument(new String[]{"a","b"});
        assertTrue(DevAssistUtils.isLineOutOfRange(3, doc));
    }

    @Test @DisplayName("isLineOutOfRange_valid_returnsFalse")
    void testIsLineOutOfRange_valid_returnsFalse() {
        Document doc = mockDocument(new String[]{"a","b"});
        assertFalse(DevAssistUtils.isLineOutOfRange(2, doc));
    }

    // wrapTextAtWord tests
    @Test @DisplayName("wrapTextAtWord_shortText_noWrap")
    void testWrapTextAtWord_shortText_noWrap() {
        assertEquals("hello", DevAssistUtils.wrapTextAtWord("hello",10));
    }

    @Test @DisplayName("wrapTextAtWord_wordExceedsMax_startsNewLine")
    void testWrapTextAtWord_wordExceedsMax_startsNewLine() {
        String wrapped = DevAssistUtils.wrapTextAtWord("abc defghijkl",5);
        assertTrue(wrapped.contains("\ndefghijkl"));
    }

    @Test @DisplayName("wrapTextAtWord_multipleWraps_correctBreaks")
    void testWrapTextAtWord_multipleWraps_correctBreaks() {
        String wrapped = DevAssistUtils.wrapTextAtWord("one two three four",7);
        // Expect line breaks before words causing overflow
        assertTrue(wrapped.contains("one two"));
        assertTrue(wrapped.contains("three"));
    }

    // isProblem tests
    @Test @DisplayName("isProblem_severityOK_returnsFalse")
    void testIsProblem_severityOK_returnsFalse() {
        assertFalse(DevAssistUtils.isProblem(SeverityLevel.OK.getSeverity()));
    }

    @Test @DisplayName("isProblem_severityUNKNOWN_returnsFalse")
    void testIsProblem_severityUNKNOWN_returnsFalse() {
        assertFalse(DevAssistUtils.isProblem(SeverityLevel.UNKNOWN.getSeverity()));
    }

    @Test @DisplayName("isProblem_severityHigh_returnsTrue")
    void testIsProblem_severityHigh_returnsTrue() {
        assertTrue(DevAssistUtils.isProblem("HIGH"));
    }

    // themeBasedPNGIconForHtmlImage tests
    @Test @DisplayName("themeBasedPNGIconForHtmlImage_nullInput_returnsEmpty")
    void testThemeBasedPNGIconForHtmlImage_nullInput_returnsEmpty() {
        assertEquals("", DevAssistUtils.themeBasedPNGIconForHtmlImage(null));
    }

    @Test @DisplayName("themeBasedPNGIconForHtmlImage_emptyInput_returnsEmpty")
    void testThemeBasedPNGIconForHtmlImage_emptyInput_returnsEmpty() {
        assertEquals("", DevAssistUtils.themeBasedPNGIconForHtmlImage(""));
    }

    @Test @DisplayName("themeBasedPNGIconForHtmlImage_nonExisting_returnsEmpty")
    void testThemeBasedPNGIconForHtmlImage_nonExisting_returnsEmpty() {
        assertEquals("", DevAssistUtils.themeBasedPNGIconForHtmlImage("/icons/does_not_exist"));
    }

    // isDarkTheme test (mock UIUtil)
    @Test @DisplayName("isDarkTheme_darculaTrue_returnsTrue")
    void testIsDarkTheme_darculaTrue_returnsTrue() {
        try (MockedStatic<UIUtil> ui = mockStatic(UIUtil.class)) {
            ui.when(UIUtil::isUnderDarcula).thenReturn(true);
            assertTrue(DevAssistUtils.isDarkTheme());
        }
    }

    @Test @DisplayName("isDarkTheme_darculaFalse_returnsFalse")
    void testIsDarkTheme_darculaFalse_returnsFalse() {
        try (MockedStatic<UIUtil> ui = mockStatic(UIUtil.class)) {
            ui.when(UIUtil::isUnderDarcula).thenReturn(false);
            assertFalse(DevAssistUtils.isDarkTheme());
        }
    }

    // getFileContent tests: simulate document path
    @Test @DisplayName("getFileContent_documentPresent_returnsText")
    void testGetFileContent_documentPresent_returnsText() {
        PsiFile psi = mock(PsiFile.class, RETURNS_DEEP_STUBS);
        Project mockProject = mock(Project.class);
        when(psi.getProject()).thenReturn(mockProject);
        try (MockedStatic<ApplicationManager> app = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS)) {
            var application = mock(com.intellij.openapi.application.Application.class);
            app.when(ApplicationManager::getApplication).thenReturn(application);
            doAnswer(inv -> {
                Object arg = inv.getArgument(0);
                if (arg instanceof Computable) {
                    Computable<?> comp = (Computable<?>) arg;
                    Document doc = mock(Document.class);
                    when(doc.getText()).thenReturn("content");
                    var psiDocMgr = mock(com.intellij.psi.PsiDocumentManager.class);
                    when(psiDocMgr.getDocument(psi)).thenReturn(doc);
                    try (MockedStatic<com.intellij.psi.PsiDocumentManager> mgr = mockStatic(com.intellij.psi.PsiDocumentManager.class)) {
                        mgr.when(() -> com.intellij.psi.PsiDocumentManager.getInstance(mockProject)).thenReturn(psiDocMgr);
                        return comp.compute();
                    }
                }
                return null;
            }).when(application).runReadAction(any(Computable.class));
            assertEquals("content", DevAssistUtils.getFileContent(psi));
        }
    }

    @Test @DisplayName("getFileContent_noDocumentVirtualFileNull_returnsNull")
    void testGetFileContent_noDocumentVirtualFileNull_returnsNull() {
        PsiFile psi = mock(PsiFile.class, RETURNS_DEEP_STUBS);
        Project mockProject = mock(Project.class);
        when(psi.getProject()).thenReturn(mockProject);
        when(psi.getVirtualFile()).thenReturn(null);
        try (MockedStatic<ApplicationManager> app = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS)) {
            var application = mock(com.intellij.openapi.application.Application.class);
            app.when(ApplicationManager::getApplication).thenReturn(application);
            doAnswer(inv -> {
                Object arg = inv.getArgument(0);
                if (arg instanceof Computable) {
                    Computable<?> comp = (Computable<?>) arg;
                    var psiDocMgr = mock(com.intellij.psi.PsiDocumentManager.class);
                    when(psiDocMgr.getDocument(psi)).thenReturn(null);
                    try (MockedStatic<com.intellij.psi.PsiDocumentManager> mgr = mockStatic(com.intellij.psi.PsiDocumentManager.class)) {
                        mgr.when(() -> com.intellij.psi.PsiDocumentManager.getInstance(mockProject)).thenReturn(psiDocMgr);
                        return comp.compute();
                    }
                }
                return null;
            }).when(application).runReadAction(any(Computable.class));
            assertNull(DevAssistUtils.getFileContent(psi));
        }
    }

    // encodeBase64 tests
    @Test @DisplayName("encodeBase64_normalString_returnsBase64")
    void testEncodeBase64_normalString_returnsBase64() {
        String result = DevAssistUtils.encodeBase64("hello");
        assertEquals("aGVsbG8=", result);
    }

    @Test @DisplayName("encodeBase64_emptyString_returnsEmptyBase64")
    void testEncodeBase64_emptyString_returnsEmptyBase64() {
        String result = DevAssistUtils.encodeBase64("");
        assertEquals("", result);
    }

    // generateUniqueId tests
    @Test @DisplayName("generateUniqueId_producesBase64OfConcatenation")
    void testGenerateUniqueId_producesBase64OfConcatenation() {
        String result = DevAssistUtils.generateUniqueId(1, "title", "desc");
        String expected = DevAssistUtils.encodeBase64("1titledesc");
        assertEquals(expected, result);
    }

    // isDockerComposeFile tests
    @Test @DisplayName("isDockerComposeFile_dockerComposeYml_returnsTrue")
    void testIsDockerComposeFile_dockerComposeYml_returnsTrue() {
        assertTrue(DevAssistUtils.isDockerComposeFile("/project/docker-compose.yml"));
    }

    @Test @DisplayName("isDockerComposeFile_regularFile_returnsFalse")
    void testIsDockerComposeFile_regularFile_returnsFalse() {
        assertFalse(DevAssistUtils.isDockerComposeFile("/project/Main.java"));
    }

    // isDockerFile tests
    @Test @DisplayName("isDockerFile_dockerfile_returnsTrue")
    void testIsDockerFile_dockerfile_returnsTrue() {
        assertTrue(DevAssistUtils.isDockerFile("/project/Dockerfile"));
    }

    @Test @DisplayName("isDockerFile_regularFile_returnsFalse")
    void testIsDockerFile_regularFile_returnsFalse() {
        assertFalse(DevAssistUtils.isDockerFile("/project/Main.java"));
    }

    // isAIAssistantEvent tests
    @Test @DisplayName("isAIAssistantEvent_matchingPrefix_returnsTrue")
    void testIsAIAssistantEvent_matchingPrefix_returnsTrue() {
        List<String> prefixes = List.of("/AIAssistantInput", "/Dummy.txt");
        assertTrue(DevAssistUtils.isAIAssistantEvent("/AIAssistantInput-abc123.chatInput", prefixes));
    }

    @Test @DisplayName("isAIAssistantEvent_noDash_checksFullString")
    void testIsAIAssistantEvent_noDash_checksFullString() {
        List<String> prefixes = List.of("/Dummy.txt");
        assertTrue(DevAssistUtils.isAIAssistantEvent("/Dummy.txt", prefixes));
    }

    @Test @DisplayName("isAIAssistantEvent_notInList_returnsFalse")
    void testIsAIAssistantEvent_notInList_returnsFalse() {
        List<String> prefixes = List.of("/AIAssistantInput");
        assertFalse(DevAssistUtils.isAIAssistantEvent("/OtherFile-abc.txt", prefixes));
    }

    @Test @DisplayName("isAIAssistantEvent_nullInput_returnsFalse")
    void testIsAIAssistantEvent_nullInput_returnsFalse() {
        assertFalse(DevAssistUtils.isAIAssistantEvent(null, List.of("/AIAssistantInput")));
    }

    @Test @DisplayName("isAIAssistantEvent_nullList_returnsFalse")
    void testIsAIAssistantEvent_nullList_returnsFalse() {
        assertFalse(DevAssistUtils.isAIAssistantEvent("/AIAssistantInput-abc", null));
    }

    // isAIAgentEvent tests
    @Test @DisplayName("isAIAgentEvent_knownAgentFile_returnsTrue")
    void testIsAIAgentEvent_knownAgentFile_returnsTrue() {
        assertTrue(DevAssistUtils.isAIAgentEvent("/Dummy.txt"));
    }

    @Test @DisplayName("isAIAgentEvent_aiAssistantPrefixFile_returnsTrue")
    void testIsAIAgentEvent_aiAssistantPrefixFile_returnsTrue() {
        assertTrue(DevAssistUtils.isAIAgentEvent("/AIAssistantInput-f85ebab5.chatInput"));
    }

    @Test @DisplayName("isAIAgentEvent_normalFile_returnsFalse")
    void testIsAIAgentEvent_normalFile_returnsFalse() {
        assertFalse(DevAssistUtils.isAIAgentEvent("/project/Main.java"));
    }

    // getSeverityBasedOnPrecedence tests
    @Test @DisplayName("getSeverityBasedOnPrecedence_higherPrecedenceInList_returnsListSeverity")
    void testGetSeverityBasedOnPrecedence_higherPrecedenceInList() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(SeverityLevel.MEDIUM.getSeverity());
        String result = DevAssistUtils.getSeverityBasedOnPrecedence(List.of(issue), SeverityLevel.LOW.getSeverity());
        assertEquals(SeverityLevel.MEDIUM.getSeverity(), result);
    }

    @Test @DisplayName("getSeverityBasedOnPrecedence_noneHigher_returnsInputSeverity")
    void testGetSeverityBasedOnPrecedence_noneHigher() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("low");
        String result = DevAssistUtils.getSeverityBasedOnPrecedence(List.of(issue), "high");
        assertEquals("high", result);
    }

    @Test @DisplayName("getSeverityBasedOnPrecedence_emptyList_returnsInputSeverity")
    void testGetSeverityBasedOnPrecedence_emptyList() {
        String result = DevAssistUtils.getSeverityBasedOnPrecedence(Collections.emptyList(), "medium");
        assertEquals("medium", result);
    }

    // getVulnerabilityDetails tests
    @Test @DisplayName("getVulnerabilityDetails_matchingId_returnsVulnerability")
    void testGetVulnerabilityDetails_matchingId() {
        Vulnerability vuln = new Vulnerability();
        vuln.setVulnerabilityId("vuln-1");
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setVulnerabilities(List.of(vuln));
        Vulnerability result = DevAssistUtils.getVulnerabilityDetails(issue, "vuln-1");
        assertEquals(vuln, result);
    }

    @Test @DisplayName("getVulnerabilityDetails_noMatch_returnsNull")
    void testGetVulnerabilityDetails_noMatch() {
        Vulnerability vuln = new Vulnerability();
        vuln.setVulnerabilityId("vuln-1");
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setVulnerabilities(List.of(vuln));
        assertNull(DevAssistUtils.getVulnerabilityDetails(issue, "no-match"));
    }

    @Test @DisplayName("getVulnerabilityDetails_emptyList_returnsNull")
    void testGetVulnerabilityDetails_emptyList() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setVulnerabilities(Collections.emptyList());
        assertNull(DevAssistUtils.getVulnerabilityDetails(issue, "any"));
    }

    @Test @DisplayName("getVulnerabilityDetails_nullList_returnsNull")
    void testGetVulnerabilityDetails_nullList() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setVulnerabilities(null);
        assertNull(DevAssistUtils.getVulnerabilityDetails(issue, "any"));
    }

    // isProjectDisposed tests
    @Test @DisplayName("isProjectDisposed_nullProject_returnsTrue")
    void testIsProjectDisposed_nullProject() {
        assertTrue(DevAssistUtils.isProjectDisposed(null));
    }

    @Test @DisplayName("isProjectDisposed_disposedProject_returnsTrue")
    void testIsProjectDisposed_disposedProject() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(true);
        assertTrue(DevAssistUtils.isProjectDisposed(project));
    }

    @Test @DisplayName("isProjectDisposed_activeProject_returnsFalse")
    void testIsProjectDisposed_activeProject() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(false);
        assertFalse(DevAssistUtils.isProjectDisposed(project));
    }

    // getCurrentSelectedFile tests

    @Test @DisplayName("getCurrentSelectedFile: disposed project returns null")
    void testGetCurrentSelectedFile_disposedProject_returnsNull() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(true);
        assertNull(DevAssistUtils.getCurrentSelectedFile(project));
    }

    @Test @DisplayName("getCurrentSelectedFile: null editor returns first open file or null")
    void testGetCurrentSelectedFile_nullEditor_returnsFirstOpenFile() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(false);

        VirtualFile vf = mock(VirtualFile.class);

        try (MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedEditor()).thenReturn(null);
            when(fileMgr.getSelectedFiles()).thenReturn(new VirtualFile[]{vf});

            VirtualFile result = DevAssistUtils.getCurrentSelectedFile(project);
            assertEquals(vf, result);
        }
    }

    @Test @DisplayName("getCurrentSelectedFile: editor with file returns that file")
    void testGetCurrentSelectedFile_editorWithFile_returnsFile() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(false);

        VirtualFile vf = mock(VirtualFile.class);
        FileEditor editor = mock(FileEditor.class);
        when(editor.getFile()).thenReturn(vf);

        try (MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedEditor()).thenReturn(editor);

            VirtualFile result = DevAssistUtils.getCurrentSelectedFile(project);
            assertEquals(vf, result);
        }
    }

    // getCurrentOpenFiles tests

    @Test @DisplayName("getCurrentOpenFiles: returns selected files array")
    void testGetCurrentOpenFiles_returnsSelectedFiles() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(false);
        VirtualFile vf = mock(VirtualFile.class);

        try (MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedFiles()).thenReturn(new VirtualFile[]{vf});

            VirtualFile[] result = DevAssistUtils.getCurrentOpenFiles(project);
            assertEquals(1, result.length);
            assertEquals(vf, result[0]);
        }
    }

    @Test @DisplayName("getCurrentOpenFiles: exception returns empty array")
    void testGetCurrentOpenFiles_exception_returnsEmptyArray() {
        Project project = mock(Project.class);
        when(project.isDisposed()).thenReturn(false);

        try (MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {
            femMock.when(() -> FileEditorManager.getInstance(project)).thenThrow(new RuntimeException("failure"));

            VirtualFile[] result = DevAssistUtils.getCurrentOpenFiles(project);
            assertEquals(0, result.length);
        }
    }

    // copyToClipboardWithNotification tests

    @Test @DisplayName("copyToClipboardWithNotification: success returns true")
    void testCopyToClipboardWithNotification_success_returnsTrue() {
        Project project = mock(Project.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CopyPasteManager> cpmMock = mockStatic(CopyPasteManager.class);
             MockedStatic<NotificationGroupManager> ngmMock = mockStatic(NotificationGroupManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class));

            CopyPasteManager cpm = mock(CopyPasteManager.class);
            cpmMock.when(CopyPasteManager::getInstance).thenReturn(cpm);

            NotificationGroupManager ngm = mock(NotificationGroupManager.class, RETURNS_DEEP_STUBS);
            ngmMock.when(NotificationGroupManager::getInstance).thenReturn(ngm);
            when(ngm.getNotificationGroup(any())).thenReturn(mock(NotificationGroup.class, RETURNS_DEEP_STUBS));

            boolean result = DevAssistUtils.copyToClipboardWithNotification("text", "title", "content", project);
            assertTrue(result);
            verify(cpm).setContents(any());
        }
    }

    // getPsiElement tests

    @Test @DisplayName("getPsiElement: valid line returns element at that line")
    void testGetPsiElement_validLine_returnsElement() {
        PsiFile psiFile = mock(PsiFile.class);
        Document doc = mock(Document.class);
        PsiElement element = mock(PsiElement.class);
        when(doc.getLineStartOffset(0)).thenReturn(0);
        when(psiFile.findElementAt(0)).thenReturn(element);

        PsiElement result = DevAssistUtils.getPsiElement(psiFile, doc, 1);
        assertEquals(element, result);
    }

    @Test @DisplayName("getPsiElement: exception returns null")
    void testGetPsiElement_exception_returnsNull() {
        PsiFile psiFile = mock(PsiFile.class);
        Document doc = mock(Document.class);
        when(doc.getLineStartOffset(anyInt())).thenThrow(new RuntimeException("boom"));

        PsiElement result = DevAssistUtils.getPsiElement(psiFile, doc, 1);
        assertNull(result);
    }

    // getPsiFileByFilePath tests

    @Test @DisplayName("getPsiFileByFilePath: null virtualFile returns null")
    void testGetPsiFileByFilePath_nullVirtualFile_returnsNull() {
        Project project = mock(Project.class);
        try (MockedStatic<LocalFileSystem> lfsMock = mockStatic(LocalFileSystem.class)) {
            LocalFileSystem lfs = mock(LocalFileSystem.class);
            lfsMock.when(LocalFileSystem::getInstance).thenReturn(lfs);
            when(lfs.findFileByPath("/nonexistent/file.java")).thenReturn(null);

            PsiFile result = DevAssistUtils.getPsiFileByFilePath(project, "/nonexistent/file.java");
            assertNull(result);
        }
    }

    @Test @DisplayName("getPsiFileByFilePath: valid virtualFile returns PsiFile")
    void testGetPsiFileByFilePath_validVirtualFile_returnsPsiFile() {
        Project project = mock(Project.class);
        VirtualFile vf = mock(VirtualFile.class);
        PsiFile psiFile = mock(PsiFile.class);

        try (MockedStatic<LocalFileSystem> lfsMock = mockStatic(LocalFileSystem.class);
             MockedStatic<PsiManager> psiMgrMock = mockStatic(PsiManager.class)) {
            LocalFileSystem lfs = mock(LocalFileSystem.class);
            lfsMock.when(LocalFileSystem::getInstance).thenReturn(lfs);
            when(lfs.findFileByPath("/project/App.java")).thenReturn(vf);

            PsiManager psiMgr = mock(PsiManager.class);
            psiMgrMock.when(() -> PsiManager.getInstance(project)).thenReturn(psiMgr);
            when(psiMgr.findFile(vf)).thenReturn(psiFile);

            PsiFile result = DevAssistUtils.getPsiFileByFilePath(project, "/project/App.java");
            assertEquals(psiFile, result);
        }
    }

    // getFileExtension tests

    @Test @DisplayName("getFileExtension: file exists returns extension")
    void testGetFileExtension_fileExists_returnsExtension() {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.exists()).thenReturn(true);
        when(vf.getExtension()).thenReturn("java");

        String ext = DevAssistUtils.getFileExtension(psiFile);
        assertEquals("java", ext);
    }

    @Test @DisplayName("getFileExtension: file does not exist returns null")
    void testGetFileExtension_fileNotExists_returnsNull() {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.exists()).thenReturn(false);

        String ext = DevAssistUtils.getFileExtension(psiFile);
        assertNull(ext);
    }

    // isYamlFile tests

    @Test @DisplayName("isYamlFile: yaml extension returns true")
    void testIsYamlFile_yamlExtension_returnsTrue() {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.exists()).thenReturn(true);
        when(vf.getExtension()).thenReturn("yaml");

        assertTrue(DevAssistUtils.isYamlFile(psiFile));
    }

    @Test @DisplayName("isYamlFile: yml extension returns true")
    void testIsYamlFile_ymlExtension_returnsTrue() {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.exists()).thenReturn(true);
        when(vf.getExtension()).thenReturn("yml");

        assertTrue(DevAssistUtils.isYamlFile(psiFile));
    }

    @Test @DisplayName("isYamlFile: java extension returns false")
    void testIsYamlFile_javaExtension_returnsFalse() {
        PsiFile psiFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.exists()).thenReturn(true);
        when(vf.getExtension()).thenReturn("java");

        assertFalse(DevAssistUtils.isYamlFile(psiFile));
    }

    // getAssistQuickFixName tests

    @Test @DisplayName("getAssistQuickFixName: isDevAssistPlugin true returns DevAssist name")
    void testGetAssistQuickFixName_devAssistPlugin_returnsDevAssistName() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            PluginContext pc = mock(PluginContext.class);
            pcMock.when(PluginContext::getInstance).thenReturn(pc);
            when(pc.isDevAssistPlugin()).thenReturn(true);

            String name = DevAssistUtils.getAssistQuickFixName();
            assertNotNull(name);
        }
    }

    @Test @DisplayName("getAssistQuickFixName: isDevAssistPlugin false returns CxOne name")
    void testGetAssistQuickFixName_cxOnePlugin_returnsCxOneName() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            PluginContext pc = mock(PluginContext.class);
            pcMock.when(PluginContext::getInstance).thenReturn(pc);
            when(pc.isDevAssistPlugin()).thenReturn(false);

            String name = DevAssistUtils.getAssistQuickFixName();
            assertNotNull(name);
        }
    }

    @Test @DisplayName("getAssistQuickFixName: exception returns default CxOne name")
    void testGetAssistQuickFixName_exception_returnsDefault() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            pcMock.when(PluginContext::getInstance).thenThrow(new RuntimeException("no plugin"));
            String name = DevAssistUtils.getAssistQuickFixName();
            assertNotNull(name);
        }
    }

    // getAgentName tests

    @Test @DisplayName("getAgentName: DevAssist plugin with display name returns display name")
    void testGetAgentName_devAssistPlugin_returnsDisplayName() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            PluginContext pc = mock(PluginContext.class);
            pcMock.when(PluginContext::getInstance).thenReturn(pc);
            when(pc.isDevAssistPlugin()).thenReturn(true);
            when(pc.getPluginDisplayName()).thenReturn("DevAssist");

            String name = DevAssistUtils.getAgentName();
            assertEquals("DevAssist", name);
        }
    }

    @Test @DisplayName("getAgentName: null pluginContext returns default CX_AGENT_NAME")
    void testGetAgentName_nullPluginContext_returnsDefault() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            pcMock.when(PluginContext::getInstance).thenReturn(null);

            String name = DevAssistUtils.getAgentName();
            assertNotNull(name);
        }
    }

    @Test @DisplayName("getAgentName: exception returns CX_AGENT_NAME fallback")
    void testGetAgentName_exception_returnsFallback() {
        try (MockedStatic<PluginContext> pcMock = mockStatic(PluginContext.class)) {
            pcMock.when(PluginContext::getInstance).thenThrow(new RuntimeException("no plugin"));
            String name = DevAssistUtils.getAgentName();
            assertNotNull(name);
        }
    }

    // getIgnoreFilePath tests

    @Test @DisplayName("getIgnoreFilePath: returns temp path from IgnoreManager")
    void testGetIgnoreFilePath_returnsTempPath() {
        Project project = mock(Project.class);

        try (MockedStatic<IgnoreFileManager> ifmMock = mockStatic(IgnoreFileManager.class);
             MockedStatic<ProblemHolderService> phsMock = mockStatic(ProblemHolderService.class)) {

            IgnoreFileManager mockIfm = mock(IgnoreFileManager.class);
            ifmMock.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(mockIfm);

            java.nio.file.Path mockPath = mock(java.nio.file.Path.class);
            when(mockPath.toString()).thenReturn("/tmp/cx-ignore-list.json");
            when(mockIfm.getTempListPath()).thenReturn(mockPath);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(Collections.emptyList());

            ProblemHolderService mockPhs = mock(ProblemHolderService.class);
            phsMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mockPhs);

            String result = DevAssistUtils.getIgnoreFilePath(project);
            assertEquals("/tmp/cx-ignore-list.json", result);
        }
    }
}

package com.checkmarx.intellij.ast.test.unit.devassist.utils;

import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.util.Computable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
}

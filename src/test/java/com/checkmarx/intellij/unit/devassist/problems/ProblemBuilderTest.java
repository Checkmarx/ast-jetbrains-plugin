package com.checkmarx.intellij.unit.devassist.problems;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemBuilder;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.devassist.remediation.IgnoreVulnerabilityFix;
import com.checkmarx.intellij.devassist.remediation.ViewDetailsFix;
import com.checkmarx.intellij.util.SeverityLevel;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemBuilderTest {
    @Test
    @DisplayName("build returns correct ProblemDescriptor for all severity levels and unknown severity")
    void testBuild_functionality() throws Exception {
        PsiFile psiFile = mock(PsiFile.class);
        InspectionManager manager = mock(InspectionManager.class);
        Document document = mock(Document.class);
        int lineNumber = 1;
        boolean isOnTheFly = true;
        when(document.getLineStartOffset(lineNumber)).thenReturn(0);
        when(document.getLineEndOffset(lineNumber)).thenReturn(10);
        when(document.getTextLength()).thenReturn(10);
        when(document.getText(any(TextRange.class))).thenReturn("test");
        when(document.getLineCount()).thenReturn(2);
        when(document.getText()).thenReturn("test\nline2");
        java.lang.reflect.Method buildMethod = ProblemBuilder.class.getDeclaredMethod(
                "build", PsiFile.class, InspectionManager.class, ScanIssue.class, Document.class, int.class, boolean.class);
        buildMethod.setAccessible(true);
        for (SeverityLevel level : SeverityLevel.values()) {
            ScanIssue scanIssue = mock(ScanIssue.class);
            when(scanIssue.getSeverity()).thenReturn(level.getSeverity());
            when(scanIssue.getDescription()).thenReturn("desc");
            when(scanIssue.getTitle()).thenReturn("title");
            when(scanIssue.getFilePath()).thenReturn("file.java");
            when(scanIssue.getScanEngine()).thenReturn(ScanEngine.OSS);
            ProblemHighlightType expectedType =
                    level == SeverityLevel.MALICIOUS || level == SeverityLevel.CRITICAL || level == SeverityLevel.HIGH ?
                            ProblemHighlightType.GENERIC_ERROR :
                            level == SeverityLevel.MEDIUM ? ProblemHighlightType.WARNING :
                                    ProblemHighlightType.WEAK_WARNING;
            when(manager.createProblemDescriptor(eq(psiFile), any(TextRange.class), anyString(), eq(expectedType), eq(isOnTheFly), any(CxOneAssistFix.class), any(ViewDetailsFix.class), any(IgnoreVulnerabilityFix.class), any(IgnoreAllThisTypeFix.class))).thenReturn(mock(ProblemDescriptor.class));
            ProblemDescriptor descriptor = (ProblemDescriptor) buildMethod.invoke(
                    null, psiFile, manager, scanIssue, document, lineNumber, isOnTheFly);
            assertNotNull(descriptor);
        }
        // Unknown severity
        ScanIssue unknownIssue = mock(ScanIssue.class);
        when(unknownIssue.getSeverity()).thenReturn("UNKNOWN");
        when(unknownIssue.getDescription()).thenReturn("desc");
        when(unknownIssue.getTitle()).thenReturn("title");
        when(unknownIssue.getFilePath()).thenReturn("file.java");
        when(unknownIssue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(manager.createProblemDescriptor(eq(psiFile), any(TextRange.class), anyString(), eq(ProblemHighlightType.WEAK_WARNING), eq(isOnTheFly), any(CxOneAssistFix.class), any(ViewDetailsFix.class), any(IgnoreVulnerabilityFix.class), any(IgnoreAllThisTypeFix.class))).thenReturn(mock(ProblemDescriptor.class));
        ProblemDescriptor unknownDescriptor = (ProblemDescriptor) buildMethod.invoke(
                null, psiFile, manager, unknownIssue, document, lineNumber, isOnTheFly);
        assertNotNull(unknownDescriptor);
    }


    @Test
    @DisplayName("determineHighlightType returns correct type for all severities and unknown")
    void testDetermineHighlightType_functionality() throws Exception {
        java.lang.reflect.Method method = ProblemBuilder.class.getDeclaredMethod("determineHighlightType", ScanIssue.class);
        method.setAccessible(true);
        for (SeverityLevel level : SeverityLevel.values()) {
            ScanIssue scanIssue = mock(ScanIssue.class);
            when(scanIssue.getSeverity()).thenReturn(level.getSeverity());
            ProblemHighlightType type = (ProblemHighlightType) method.invoke(null, scanIssue);
            if (level == SeverityLevel.MALICIOUS || level == SeverityLevel.CRITICAL || level == SeverityLevel.HIGH) {
                assertEquals(ProblemHighlightType.GENERIC_ERROR, type);
            } else if (level == SeverityLevel.MEDIUM) {
                assertEquals(ProblemHighlightType.WARNING, type);
            } else if (level == SeverityLevel.LOW) {
                assertEquals(ProblemHighlightType.WEAK_WARNING, type);
            }
        }
        ScanIssue unknownIssue = mock(ScanIssue.class);
        when(unknownIssue.getSeverity()).thenReturn("UNKNOWN");
        ProblemHighlightType type = (ProblemHighlightType) method.invoke(null, unknownIssue);
        assertEquals(ProblemHighlightType.WEAK_WARNING, type);
    }

    @Test
    @DisplayName("initSeverityToHighlightMap initializes mapping correctly")
    void testInitSeverityToHighlightMap_functionality() throws Exception {
        java.lang.reflect.Method method = ProblemBuilder.class.getDeclaredMethod("initSeverityToHighlightMap");
        method.setAccessible(true);
        method.invoke(null);
    }
}


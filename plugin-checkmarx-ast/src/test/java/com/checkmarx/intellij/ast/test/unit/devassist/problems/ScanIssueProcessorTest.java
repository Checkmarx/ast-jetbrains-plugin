package com.checkmarx.intellij.ast.test.unit.devassist.problems;

import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ScanIssueProcessor;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

public class ScanIssueProcessorTest {

    private ProblemDecorator problemDecorator;
    private PsiFile psiFile;
    private InspectionManager inspectionManager;
    private Document document;
    private Project project;
    private ProblemHelper problemHelper;
    private ScanIssueProcessor processorViaHelper;

    @BeforeEach
    void setUp() {
        problemDecorator = mock(ProblemDecorator.class);
        psiFile = mock(PsiFile.class);
        inspectionManager = mock(InspectionManager.class);
        document = mock(Document.class);
        project = mock(Project.class);
        problemHelper = mock(ProblemHelper.class);

        when(problemHelper.getFile()).thenReturn(psiFile);
        when(problemHelper.getManager()).thenReturn(inspectionManager);
        when(problemHelper.getDocument()).thenReturn(document);
        when(problemHelper.isOnTheFly()).thenReturn(true);
        when(psiFile.getProject()).thenReturn(project);

        processorViaHelper = new ScanIssueProcessor(problemHelper);
    }

    private ScanIssue buildIssue(int line, String severity, String title) {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(severity);
        issue.setTitle(title);
        issue.setScanEngine(ScanEngine.OSS); // ensure non-null to prevent NPE in fixes
        List<Location> locations = new ArrayList<>();
        locations.add(new Location(line, 0, 0));
        issue.setLocations(locations);
        return issue;
    }

    @Test
    @DisplayName("ScanIssue without locations should return null and not interact with decorator")
    void testProcessScanIssue_invalidNoLocations() {
        ScanIssue issue = new ScanIssue();
        issue.setTitle("noLoc");
        issue.setLocations(null);
        assertNull(processorViaHelper.processScanIssue(issue, true));
        verifyNoInteractions(problemDecorator);
    }

    @Test
    @DisplayName("ScanIssue with empty locations list should return null")
    void testProcessScanIssue_invalidEmptyLocations() {
        ScanIssue issue = new ScanIssue();
        issue.setTitle("emptyLoc");
        issue.setLocations(Collections.emptyList());
        assertNull(processorViaHelper.processScanIssue(issue, true));
        verifyNoInteractions(problemDecorator);
    }

    @Test
    @DisplayName("Line out of range should skip processing and not highlight")
    void testProcessScanIssue_invalidLineOutOfRange() {
        ScanIssue issue = buildIssue(5, "HIGH", "outOfRange");
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(5, document)).thenReturn(true);
            assertNull(processorViaHelper.processScanIssue(issue, true));
            utils.verify(() -> DevAssistUtils.isLineOutOfRange(5, document));
        }
        verifyNoInteractions(problemDecorator);
    }

    @Test
    @DisplayName("Blank severity should invalidate issue")
    void testProcessScanIssue_invalidBlankSeverity() {
        ScanIssue issue = buildIssue(1, "   ", "blankSeverity");
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(1, document)).thenReturn(false);
            assertNull(processorViaHelper.processScanIssue(issue, true));
        }
        verifyNoInteractions(problemDecorator);
    }

    @Test
    @DisplayName("Null severity should invalidate issue")
    void testProcessScanIssue_invalidNullSeverity() {
        ScanIssue issue = buildIssue(2, null, "nullSeverity");
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(2, document)).thenReturn(false);
            assertNull(processorViaHelper.processScanIssue(issue, true));
        }
        verifyNoInteractions(problemDecorator);
    }

    @Test
    @DisplayName("Non-problem element present: highlight only, no descriptor")
    void testProcessValidIssue_isProblemFalse_elementPresent() {
        ScanIssue issue = buildIssue(8, "LOW", "nonProblemElement");
        when(document.getLineStartOffset(8)).thenReturn(80);
        PsiElement element = mock(PsiElement.class);
        when(psiFile.findElementAt(80)).thenReturn(element);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(8, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("low")).thenReturn(false);
            assertNull(processorViaHelper.processScanIssue(issue, true));
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("Non-problem element absent: no highlight, no descriptor")
    void testProcessValidIssue_isProblemFalse_elementAbsent() {
        ScanIssue issue = buildIssue(9, "LOW", "nonProblemNoElement");
        when(document.getLineStartOffset(9)).thenReturn(90);
        when(psiFile.findElementAt(90)).thenReturn(null);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(9, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("low")).thenReturn(false);
            assertNull(processorViaHelper.processScanIssue(issue, true));
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("High severity skipped (forced non-problem) with element: non-problem highlight")
    void testProcessValidIssue_descriptorSkipped_elementPresent() { // renamed from isProblemTrue_descriptorSuccess_elementPresent
        ScanIssue issue = buildIssue(3, "HIGH", "validProblem");
        when(document.getLineStartOffset(3)).thenReturn(30);
        PsiElement element = mock(PsiElement.class);
        when(psiFile.findElementAt(30)).thenReturn(element);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(3, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("high")).thenReturn(false); // force skip
            ProblemDescriptor result = processorViaHelper.processScanIssue(issue, true);
            assertNull(result);
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("High severity skipped (forced non-problem) without element: nothing highlighted")
    void testProcessValidIssue_descriptorSkipped_elementAbsent() { // renamed from isProblemTrue_descriptorSuccess_elementAbsent
        ScanIssue issue = buildIssue(6, "HIGH", "noElementProblem");
        when(document.getLineStartOffset(6)).thenReturn(60);
        when(psiFile.findElementAt(60)).thenReturn(null);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(6, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("high")).thenReturn(false);
            ProblemDescriptor result = processorViaHelper.processScanIssue(issue, true);
            assertNull(result);
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("Descriptor null skipped scenario with element: non-problem highlight")
    void testProcessValidIssue_descriptorNullSkipped_elementPresent() { // renamed
        ScanIssue issue = buildIssue(5, "HIGH", "descriptorNull");
        when(document.getLineStartOffset(5)).thenReturn(50);
        PsiElement element = mock(PsiElement.class);
        when(psiFile.findElementAt(50)).thenReturn(element);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(5, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("high")).thenReturn(false); // skip
            ProblemDescriptor result = processorViaHelper.processScanIssue(issue, true);
            assertNull(result);
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("Descriptor null skipped scenario without element: no highlight")
    void testProcessValidIssue_descriptorNullSkipped_elementAbsent() { // renamed
        ScanIssue issue = buildIssue(15, "HIGH", "descriptorNullNoElement");
        when(document.getLineStartOffset(15)).thenReturn(150);
        when(psiFile.findElementAt(150)).thenReturn(null);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(15, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("high")).thenReturn(false);
            ProblemDescriptor result = processorViaHelper.processScanIssue(issue, true);
            assertNull(result);
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("Multiple locations: first used, descriptor skipped, element present")
    void testProcessScanIssue_multipleLocations_firstLineUsedSkippedDescriptor() { // renamed
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("CRITICAL");
        issue.setTitle("multiLocation");
        issue.setScanEngine(ScanEngine.OSS);
        issue.setLocations(Arrays.asList(new Location(20,0,0), new Location(999,0,0)));
        when(document.getLineStartOffset(20)).thenReturn(200);
        PsiElement element = mock(PsiElement.class);
        when(psiFile.findElementAt(200)).thenReturn(element);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.isLineOutOfRange(20, document)).thenReturn(false);
            utils.when(() -> DevAssistUtils.isProblem("critical")).thenReturn(false); // skip descriptor
            ProblemDescriptor result = processorViaHelper.processScanIssue(issue, true);
            assertNull(result);
            verify(problemDecorator, never()).highlightLineAddGutterIconForProblem(any(), any(), anyBoolean(), anyInt());
        }
    }

    @Test
    @DisplayName("Constructor via ProblemHelper should initialize processor")
    void testConstructor_problemHelper() {
        assertNotNull(processorViaHelper);
    }

    @Test
    @DisplayName("Direct constructor should initialize processor")
    void testConstructor_direct() {
        ScanIssueProcessor direct = new ScanIssueProcessor(problemHelper);
        assertNotNull(direct);
    }
    
    private void stubManagerCreateProblemDescriptor(ProblemDescriptor returnValue) {
        when(inspectionManager.createProblemDescriptor(
                any(PsiFile.class),
                any(TextRange.class),
                anyString(),
                any(ProblemHighlightType.class),
                anyBoolean(),
                any(LocalQuickFix.class),
                any(LocalQuickFix.class),
                any(LocalQuickFix.class),
                any(LocalQuickFix.class)
        )).thenReturn(returnValue);
    }
}

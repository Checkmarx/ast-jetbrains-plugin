package com.checkmarx.intellij.devassist.test.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemBuilder;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProblemBuilderTest {

    @Test
    @DisplayName("build delegates to InspectionManager and returns descriptor")
    void testBuildReturnsDescriptor() throws Exception {
        PsiFile psiFile = mock(PsiFile.class);
        Project project = mock(Project.class);
        InspectionManager manager = mock(InspectionManager.class);
        Document document = mock(Document.class);
        ProblemDescriptor expectedDescriptor = mock(ProblemDescriptor.class);

        ProblemHelper helper = ProblemHelper.builder(psiFile, project)
                .manager(manager)
                .document(document)
                .filePath("/repo/file.tf")
                .isOnTheFly(true)
                .supportedScanners(Collections.emptyList())
                .scanIssueList(Collections.emptyList())
                .problemHolderService(mock(ProblemHolderService.class))
                .problemDecorator(mock(ProblemDecorator.class))
                .build();

        ScanIssue scanIssue = mock(ScanIssue.class);
        when(scanIssue.getScanEngine()).thenReturn(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        when(scanIssue.getSeverity()).thenReturn(String.valueOf(SeverityLevel.MEDIUM));
        when(scanIssue.getTitle()).thenReturn("Test Issue");
        when(scanIssue.getPackageVersion()).thenReturn("1.0.0");
        when(scanIssue.getVulnerabilities()).thenReturn(Collections.emptyList());
        when(scanIssue.getScanIssueId()).thenReturn("test-id");

        // Mock createProblemDescriptor with 4 LocalQuickFix parameters (for OSS engine)
        when(manager.createProblemDescriptor(eq(psiFile), any(TextRange.class), anyString(),
                eq(ProblemHighlightType.GENERIC_ERROR), eq(true),
                any(LocalQuickFix.class), any(LocalQuickFix.class), any(LocalQuickFix.class), any(LocalQuickFix.class)))
                .thenReturn(expectedDescriptor);

        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.getTextRangeForLine(document, 1))
                    .thenReturn(TextRange.create(0, 5));

            Method build = ProblemBuilder.class.getDeclaredMethod("build",
                    ProblemHelper.class, ScanIssue.class, int.class);
            build.setAccessible(true);
            ProblemDescriptor descriptor = (ProblemDescriptor) build.invoke(null, helper, scanIssue, 1);
            assertNotNull(descriptor);
            assertSame(expectedDescriptor, descriptor);
        }
    }
}
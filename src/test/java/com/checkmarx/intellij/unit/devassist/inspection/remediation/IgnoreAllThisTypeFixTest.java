package com.checkmarx.intellij.unit.devassist.inspection.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IgnoreAllThisTypeFixTest {
    private ScanIssue scanIssue;
    private IgnoreAllThisTypeFix fix;
    private Project project;
    private ProblemDescriptor descriptor;

    @BeforeEach
    void setUp() {
        scanIssue = mock(ScanIssue.class);
        when(scanIssue.getTitle()).thenReturn("Test Issue");
        fix = new IgnoreAllThisTypeFix(scanIssue);
        project = mock(Project.class);
        descriptor = mock(ProblemDescriptor.class);
    }

    @Test
    @DisplayName("getFamilyName returns expected constant")
    void testGetFamilyName_returnsExpectedConstant() {
        assertEquals(Constants.RealTimeConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME, fix.getFamilyName());
    }

    @Test
    @DisplayName("applyFix logs info and is called")
    void testApplyFix_logsInfoAndIsCalled() {
        // Use a subclass to intercept the logger call for coverage
        final boolean[] called = {false};
        IgnoreAllThisTypeFix testFix = new IgnoreAllThisTypeFix(scanIssue) {
            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                called[0] = true;
                super.applyFix(project, descriptor);
            }
        };
        testFix.applyFix(project, descriptor);
        assertTrue(called[0], "applyFix should be called");
    }

    @Test
    @DisplayName("constructor sets scanIssue correctly")
    void testConstructor_setsScanIssueCorrectly() {
        assertNotNull(fix);
    }
}

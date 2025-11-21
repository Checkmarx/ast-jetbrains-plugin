package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CxOneAssistFixTest {

    private Project project;
    private ProblemDescriptor descriptor;

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        descriptor = mock(ProblemDescriptor.class);
    }

    @Test
    @DisplayName("Constructor stores scanIssue reference")
    void testConstructor_functionality() {
        ScanIssue issue = new ScanIssue();
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertSame(issue, fix.getScanIssue());
    }

    @Test
    @DisplayName("getFamilyName returns expected constant")
    void testGetFamilyName_functionality() {
        CxOneAssistFix fix = new CxOneAssistFix(new ScanIssue());
        assertEquals(Constants.RealTimeConstants.FIX_WITH_CXONE_ASSIST, fix.getFamilyName());
    }

    @Test
    @DisplayName("getIcon returns star action icon")
    void testGetIcon_functionality() {
        CxOneAssistFix fix = new CxOneAssistFix(new ScanIssue());
        Icon icon = fix.getIcon(0);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("applyFix routes to OSS remediation branch")
    void testApplyFix_ossBranch_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.OSS);
        issue.setTitle("OSS Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix routes to ASCA remediation branch")
    void testApplyFix_ascaBranch_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setTitle("ASCA Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix with other engine does nothing in default case")
    void testApplyFix_otherEngineDefaultNoAction_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.IAC); // engine not explicitly handled
        issue.setTitle("IAC Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix with null scanEngine throws NPE (current behavior)")
    void testApplyFix_nullScanEngineThrowsNpe_functionality() {
        ScanIssue issue = new ScanIssue(); // scanEngine left null
        issue.setTitle("Null Engine Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertThrows(NullPointerException.class, () -> fix.applyFix(project, descriptor));
    }
}

package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.checkmarx.intellij.devassist.remediation.prompts.CxOneAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

@DisplayName("RemediationManager unit tests covering all branches")
public class RemediationManagerTest {

    @Test
    @DisplayName("testFixWithCxOneAssist_OSS_CopySuccess")
    void testFixWithCxOneAssist_OSS_CopySuccess() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<CxOneAssistFixPrompts> fixPrompts = mockStatic(CxOneAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
                    anyString(), anyString(), anyString(), anyString())).thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue);

            fixPrompts.verify(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getPackageManager()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("prompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_OSS_CopyFailure")
    void testFixWithCxOneAssist_OSS_CopyFailure() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<CxOneAssistFixPrompts> fixPrompts = mockStatic(CxOneAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);

            manager.fixWithCxOneAssist(project, issue);

            fixPrompts.verify(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getPackageManager()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("prompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_ASCA_Branch")
    void testFixWithCxOneAssist_ASCA_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        manager.fixWithCxOneAssist(project, issue);
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_DefaultBranch")
    void testFixWithCxOneAssist_DefaultBranch() {
        Project project = mock(Project.class);
        RemediationManager manager = new RemediationManager();
        for (ScanEngine engine : new ScanEngine[]{ScanEngine.SECRETS, ScanEngine.CONTAINERS, ScanEngine.IAC}) {
            ScanIssue issue = buildScanIssue(engine);
            manager.fixWithCxOneAssist(project, issue);
        }
    }

    @Test
    @DisplayName("testViewDetails_OSS_CopySuccess")
    void testViewDetails_OSS_CopySuccess() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(anyString(), anyString(), anyString(), any()))
                    .thenReturn("viewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getSeverity()), eq(issue.getVulnerabilities())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("viewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_OSS_CopyFailure")
    void testViewDetails_OSS_CopyFailure() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(anyString(), anyString(), anyString(), any()))
                    .thenReturn("viewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);

            manager.viewDetails(project, issue);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getSeverity()), eq(issue.getVulnerabilities())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("viewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_ASCA_Branch")
    void testViewDetails_ASCA_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        manager.viewDetails(project, issue);
    }

    private static ScanIssue buildScanIssue(ScanEngine engine) {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("High");
        issue.setTitle("VulnTitle");
        issue.setDescription("Desc");
        issue.setRemediationAdvise("Advise");
        issue.setPackageVersion("1.0.0");
        issue.setPackageManager("npm");
        issue.setCve("CVE-123");
        issue.setScanEngine(engine);
        issue.setFilePath("/path/file");
        return issue;
    }
}

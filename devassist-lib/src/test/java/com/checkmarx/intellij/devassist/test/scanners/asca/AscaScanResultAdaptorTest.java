package com.checkmarx.intellij.devassist.test.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AscaScanResultAdaptorTest {

    private ScanResult mockResult(List<ScanDetail> details) {
        ScanResult result = mock(ScanResult.class);
        when(result.getScanDetails()).thenReturn(details);
        return result;
    }

    private Project mockProject() {
        Project mockProject = mock(Project.class);
        // Mock IgnoreFileManager to return empty ignore entries (no filtering)
        IgnoreFileManager mockIgnoreFileManager = mock(IgnoreFileManager.class);
        when(mockIgnoreFileManager.getAllIgnoreEntries()).thenReturn(Collections.emptyList());
        when(mockIgnoreFileManager.normalizePath(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mockProject.getService(IgnoreFileManager.class)).thenReturn(mockIgnoreFileManager);
        return mockProject;
    }

    private ScanDetail mockDetail(int line,
                                  String severity,
                                  String ruleName,
                                  String description,
                                  String remediation) {
        ScanDetail detail = mock(ScanDetail.class);
        when(detail.getLine()).thenReturn(line);
        when(detail.getSeverity()).thenReturn(severity);
        when(detail.getRuleName()).thenReturn(ruleName);
        when(detail.getDescription()).thenReturn(description);
        when(detail.getRemediationAdvise()).thenReturn(remediation);
        when(detail.getFileName()).thenReturn("Main.java");
        when(detail.getRuleID()).thenReturn(1); // Default ruleId
        return detail;
    }

    @Test
    @DisplayName("getResults returns original ScanResult reference")
    void getResultsReturnsOriginal() {
        ScanResult scanResult = mockResult(Collections.emptyList());
        AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(scanResult, "/repo/Main.java", mockProject());
        assertSame(scanResult, adaptor.getResults());
    }

    @Test
    @DisplayName("getIssues returns empty list when results or details are null")
    void getIssuesHandlesNullInputs() {
        AscaScanResultAdaptor nullAdaptor = new AscaScanResultAdaptor(null, "/repo/Main.java", mockProject());
        assertTrue(nullAdaptor.getIssues().isEmpty());

        AscaScanResultAdaptor emptyAdaptor =
                new AscaScanResultAdaptor(mockResult(null), "/repo/Main.java", mockProject());
        assertTrue(emptyAdaptor.getIssues().isEmpty());
    }

    @Test
    @DisplayName("Single scan detail is converted into ScanIssue with proper fields")
    void getIssuesConvertsSingleDetail() {
        ScanDetail detail = mockDetail(
                10,
                "High",
                "ASCA_RULE",
                "description",
                "fix it"
        );

        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(List.of(detail)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        ScanIssue issue = issues.get(0);
        assertEquals("ASCA_RULE", issue.getTitle());
        assertEquals("High", issue.getSeverity());
        assertEquals("description", issue.getDescription());
        assertEquals("fix it", issue.getRemediationAdvise());
        assertEquals("/repo/Main.java", issue.getFilePath());
        assertEquals(ScanEngine.ASCA, issue.getScanEngine());
        assertEquals(1, issue.getLocations().size());
        assertEquals(10, issue.getLocations().get(0).getLine());
        assertEquals(issue.getScanIssueId(),
                issue.getVulnerabilities().get(0).getVulnerabilityId(),
                "First vulnerability should reuse scan issue id");
    }

    @Test
    @DisplayName("Details on same line are grouped, sorted by severity, and titled as multiple issues")
    void getIssuesGroupsMultipleDetailsPerLine() {
        ScanDetail critical = mockDetail(20, "Critical", "CriticalRule", "crit-desc", "crit-fix");
        ScanDetail low = mockDetail(20, "Low", "LowRule", "low-desc", "low-fix");

        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(low, critical)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size(), "Same line entries should be grouped");

        ScanIssue grouped = issues.get(0);
        assertEquals("2" + DevAssistConstants.MULTIPLE_ASCA_ISSUES, grouped.getTitle());
        assertEquals("Critical", grouped.getSeverity(),
                "Highest severity should drive grouped issue severity");
        assertEquals(2, grouped.getVulnerabilities().size());
        assertEquals(grouped.getScanIssueId(),
                grouped.getVulnerabilities().get(0).getVulnerabilityId());
        assertNotEquals(
                grouped.getVulnerabilities().get(0).getVulnerabilityId(),
                grouped.getVulnerabilities().get(1).getVulnerabilityId(),
                "Subsequent vulnerabilities should keep their own ids");
    }

    @Test
    @DisplayName("Null detail entries are skipped gracefully")
    void getIssuesSkipsNullEntries() {
        ScanDetail valid = mockDetail(5, "Medium", "ValidRule", "desc", "remedy");
        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(null, valid)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        assertEquals("ValidRule", issues.get(0).getTitle());
    }

    @Test
    @DisplayName("Severity 'info' is mapped to Low for issue and vulnerability")
    void mapSeverityTreatsInfoAsLow() {
        ScanDetail infoDetail = mockDetail(7, "info", "InfoRule", "desc", "remedy");
        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(List.of(infoDetail)), "/repo/Main.java", mockProject());

        ScanIssue issue = adaptor.getIssues().get(0);
        assertEquals("Low", issue.getSeverity());
        assertEquals("Low", issue.getVulnerabilities().get(0).getSeverity());
    }

    @Test
    @DisplayName("Unfiltered mode includes all vulnerabilities regardless of ignore status")
    void unfilteredModeIncludesAllVulnerabilities() {
        ScanDetail detail1 = mockDetail(15, "High", "SQLInjection", "sql-desc", "sql-fix");
        when(detail1.getRuleID()).thenReturn(100);

        ScanDetail detail2 = mockDetail(15, "Medium", "XSS", "xss-desc", "xss-fix");
        when(detail2.getRuleID()).thenReturn(200);

        // Create unfiltered adaptor (filterIgnored=false)
        AscaScanResultAdaptor unfilteredAdaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(detail1, detail2)), "/repo/Main.java", mockProject(), false);

        List<ScanIssue> issues = unfilteredAdaptor.getIssues();
        assertEquals(1, issues.size(), "Should have one grouped issue");
        assertEquals(2, issues.get(0).getVulnerabilities().size(), "Should include all 2 vulnerabilities in unfiltered mode");
    }

    @Test
    @DisplayName("Filtered mode includes all vulnerabilities when no ignore entries exist")
    void filteredModeWithNoIgnoreEntriesIncludesAll() {
        ScanDetail detail1 = mockDetail(20, "High", "CriticalVuln", "critical-desc", "critical-fix");
        when(detail1.getRuleID()).thenReturn(300);

        ScanDetail detail2 = mockDetail(20, "Low", "MinorVuln", "minor-desc", "minor-fix");
        when(detail2.getRuleID()).thenReturn(400);

        // Create filtered adaptor with mock project (empty ignore list)
        AscaScanResultAdaptor filteredAdaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(detail1, detail2)), "/repo/Main.java", mockProject(), true);

        List<ScanIssue> issues = filteredAdaptor.getIssues();
        assertEquals(1, issues.size(), "Should have one grouped issue");
        assertEquals(2, issues.get(0).getVulnerabilities().size(),
                "Should include all vulnerabilities when no ignore entries exist");
    }

    @Test
    @DisplayName("Multiple vulnerabilities on different lines are not grouped")
    void multipleVulnerabilitiesOnDifferentLinesNotGrouped() {
        ScanDetail line10 = mockDetail(10, "High", "Rule1", "desc1", "fix1");
        when(line10.getRuleID()).thenReturn(500);

        ScanDetail line20 = mockDetail(20, "High", "Rule2", "desc2", "fix2");
        when(line20.getRuleID()).thenReturn(600);

        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(line10, line20)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(2, issues.size(), "Should have two separate issues on different lines");
        // Verify titles regardless of order (sort by line number)
        boolean hasRule1 = issues.stream().anyMatch(issue -> issue.getTitle().equals("Rule1"));
        boolean hasRule2 = issues.stream().anyMatch(issue -> issue.getTitle().equals("Rule2"));
        assertTrue(hasRule1, "Should have issue with Rule1");
        assertTrue(hasRule2, "Should have issue with Rule2");
    }

    @Test
    @DisplayName("RuleId is correctly set on vulnerabilities for per-vulnerability filtering")
    void ruleIdIsSetOnVulnerabilities() {
        ScanDetail detail = mockDetail(25, "High", "TestRule", "test-desc", "test-fix");
        when(detail.getRuleID()).thenReturn(777);

        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(List.of(detail)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        assertEquals(777, issues.get(0).getVulnerabilities().get(0).getRuleId(),
                "RuleId should be set for per-vulnerability ignore tracking");
    }

    @Test
    @DisplayName("Title updates correctly based on filtered vulnerability count")
    void titleUpdatesBasedOnFilteredVulnerabilityCount() {
        ScanDetail high = mockDetail(30, "Critical", "Critical1", "crit-desc", "crit-fix");
        when(high.getRuleID()).thenReturn(111);

        ScanDetail low = mockDetail(30, "Low", "Low1", "low-desc", "low-fix");
        when(low.getRuleID()).thenReturn(222);

        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(Arrays.asList(high, low)), "/repo/Main.java", mockProject());

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        ScanIssue issue = issues.get(0);
        // With 2 vulnerabilities, title should show "2 multiple issues"
        assertEquals("2" + DevAssistConstants.MULTIPLE_ASCA_ISSUES, issue.getTitle());
    }
}

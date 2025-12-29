package com.checkmarx.intellij.unit.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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
        return detail;
    }

    @Test
    @DisplayName("getResults returns original ScanResult reference")
    void getResultsReturnsOriginal() {
        ScanResult scanResult = mockResult(Collections.emptyList());
        AscaScanResultAdaptor adaptor = new AscaScanResultAdaptor(scanResult, "/repo/Main.java");
        assertSame(scanResult, adaptor.getResults());
    }

    @Test
    @DisplayName("getIssues returns empty list when results or details are null")
    void getIssuesHandlesNullInputs() {
        AscaScanResultAdaptor nullAdaptor = new AscaScanResultAdaptor(null, "/repo/Main.java");
        assertTrue(nullAdaptor.getIssues().isEmpty());

        AscaScanResultAdaptor emptyAdaptor =
                new AscaScanResultAdaptor(mockResult(null), "/repo/Main.java");
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
                new AscaScanResultAdaptor(mockResult(List.of(detail)), "/repo/Main.java");

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
                new AscaScanResultAdaptor(mockResult(Arrays.asList(low, critical)), "/repo/Main.java");

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size(), "Same line entries should be grouped");

        ScanIssue grouped = issues.get(0);
        assertEquals("2" + Constants.RealTimeConstants.MULTIPLE_ASCA_ISSUES, grouped.getTitle());
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
                new AscaScanResultAdaptor(mockResult(Arrays.asList(null, valid)), "/repo/Main.java");

        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        assertEquals("ValidRule", issues.get(0).getTitle());
    }

    @Test
    @DisplayName("Severity 'info' is mapped to Low for issue and vulnerability")
    void mapSeverityTreatsInfoAsLow() {
        ScanDetail infoDetail = mockDetail(7, "info", "InfoRule", "desc", "remedy");
        AscaScanResultAdaptor adaptor =
                new AscaScanResultAdaptor(mockResult(List.of(infoDetail)), "/repo/Main.java");

        ScanIssue issue = adaptor.getIssues().get(0);
        assertEquals("Low", issue.getSeverity());
        assertEquals("Low", issue.getVulnerabilities().get(0).getSeverity());
    }
}



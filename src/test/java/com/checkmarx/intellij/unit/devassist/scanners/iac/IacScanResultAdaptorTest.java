package com.checkmarx.intellij.unit.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.scanners.iac.IacScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IacScanResultAdaptorTest {

    private IacRealtimeResults mockResults(List<IacRealtimeResults.Issue> issues) {
        IacRealtimeResults results = mock(IacRealtimeResults.class);
        when(results.getResults()).thenReturn(issues);
        return results;
    }

    private IacRealtimeResults.Issue mockIssue(String title,
                                               String severity,
                                               String description,
                                               String filePath,
                                               String similarity,
                                               List<RealtimeLocation> locations) {
        IacRealtimeResults.Issue issue = mock(IacRealtimeResults.Issue.class);
        when(issue.getTitle()).thenReturn(title);
        when(issue.getSeverity()).thenReturn(severity);
        when(issue.getDescription()).thenReturn(description);
        when(issue.getFilePath()).thenReturn(filePath);
        when(issue.getSimilarityId()).thenReturn(similarity);
        when(issue.getActualValue()).thenReturn("actual-" + title);
        when(issue.getExpectedValue()).thenReturn("expected-" + title);
        when(issue.getLocations()).thenReturn(locations);
        return issue;
    }

    private RealtimeLocation mockLocation(int line, int start, int end) {
        RealtimeLocation location = mock(RealtimeLocation.class);
        when(location.getLine()).thenReturn(line);
        when(location.getStartIndex()).thenReturn(start);
        when(location.getEndIndex()).thenReturn(end);
        return location;
    }

    @Test
    @DisplayName("getResults returns wrapped IaC results")
    void getResultsReturnsOriginal() {
        IacRealtimeResults results = mockResults(List.of());
        IacScanResultAdaptor adaptor = new IacScanResultAdaptor(results, "tf");
        assertSame(results, adaptor.getResults());
    }

    @Test
    @DisplayName("getIssues returns empty when results are null or empty")
    void getIssuesHandlesNullScenarios() {
        IacScanResultAdaptor nullAdaptor = new IacScanResultAdaptor(null, "tf");
        assertTrue(nullAdaptor.getIssues().isEmpty(), "Null results should lead to empty issues list");

        IacScanResultAdaptor emptyAdaptor = new IacScanResultAdaptor(mockResults(Collections.emptyList()), "tf");
        assertTrue(emptyAdaptor.getIssues().isEmpty(), "Empty issue list should be returned when no data exists");
    }

    @Test
    @DisplayName("Single issue is converted with file type, scan engine, and location offset")
    void getIssuesConvertsSingleIssue() {
        RealtimeLocation location = mockLocation(4, 2, 8);
        IacRealtimeResults.Issue issue = mockIssue(
                "Open S3 bucket",
                "HIGH",
                "S3 bucket allows public access",
                "/repo/main.tf",
                "similarity-1",
                List.of(location)
        );

        IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(issue)), "tf");
        List<ScanIssue> issues = adaptor.getIssues();

        assertEquals(1, issues.size());
        ScanIssue scanIssue = issues.get(0);
        assertEquals("Open S3 bucket", scanIssue.getTitle());
        assertEquals("HIGH", scanIssue.getSeverity());
        assertEquals("/repo/main.tf", scanIssue.getFilePath());
        assertEquals("tf", scanIssue.getFileType());
        assertEquals(ScanEngine.IAC, scanIssue.getScanEngine());
        assertEquals(1, scanIssue.getLocations().size());
        assertEquals(5, scanIssue.getLocations().get(0).getLine(), "Line numbers should be incremented by 1");
        assertEquals(scanIssue.getScanIssueId(), scanIssue.getVulnerabilities().get(0).getVulnerabilityId(),
                "First vulnerability should reuse the ScanIssue id");
    }

    @Test
    @DisplayName("Multiple issues on same location are grouped and sorted by severity precedence")
    void getIssuesGroupsByLocationAndSortsSeverity() {
        RealtimeLocation shared = mockLocation(3, 10, 20);
        IacRealtimeResults.Issue critical = mockIssue(
                "Critical exposure",
                Constants.CRITICAL_SEVERITY,
                "critical",
                "/repo/main.tf",
                "sim-critical",
                List.of(shared)
        );
        IacRealtimeResults.Issue low = mockIssue(
                "Low risk",
                Constants.LOW_SEVERITY,
                "low",
                "/repo/main.tf",
                "sim-low",
                List.of(shared)
        );

        IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(List.of(low, critical)), "tf");
        List<ScanIssue> issues = adaptor.getIssues();

        assertEquals(1, issues.size(), "Issues with identical locations should be grouped");
        ScanIssue grouped = issues.get(0);

        assertEquals("2" + DevAssistConstants.MULTIPLE_IAC_ISSUES, grouped.getTitle());
        assertEquals(Constants.CRITICAL_SEVERITY, grouped.getSeverity(),
                "The most severe entry should define the grouped issue severity");
        assertEquals(2, grouped.getVulnerabilities().size());
        assertEquals(grouped.getScanIssueId(), grouped.getVulnerabilities().get(0).getVulnerabilityId());
        assertNotEquals(grouped.getVulnerabilities().get(0).getVulnerabilityId(),
                grouped.getVulnerabilities().get(1).getVulnerabilityId(),
                "Subsequent vulnerabilities should keep their own IDs");
    }

    @Test
    @DisplayName("Null issue entries are ignored gracefully")
    void getIssuesSkipsNullEntries() {
        RealtimeLocation location = mockLocation(1, 0, 5);
        IacRealtimeResults.Issue valid = mockIssue(
                "Valid Issue",
                "MEDIUM",
                "desc",
                "/repo/main.tf",
                "sim-valid",
                List.of(location)
        );

        IacScanResultAdaptor adaptor = new IacScanResultAdaptor(mockResults(Arrays.asList(null, valid)), "tf");
        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        assertEquals("Valid Issue", issues.get(0).getTitle());
    }
}



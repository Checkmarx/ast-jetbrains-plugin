package com.checkmarx.intellij.ast.test.unit.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.scanners.oss.OssScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OssScanResultAdaptorTest {

    @Test
    @DisplayName("getIssues_resultsNull_returnsEmptyList")
    void testGetIssues_resultsNull_returnsEmptyList() {
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(null, "/repo/Main.java");
        assertTrue(adaptor.getIssues().isEmpty());
    }

    @Test
    @DisplayName("getIssues_packagesNull_returnsEmptyList")
    void testGetIssues_packagesNull_returnsEmptyList() {
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(null); // packages null
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        assertTrue(adaptor.getIssues().isEmpty());
    }

    @Test
    @DisplayName("getIssues_packagesEmpty_returnsEmptyList")
    void testGetIssues_packagesEmpty_returnsEmptyList() {
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(List.of()); // empty list
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        assertTrue(adaptor.getIssues().isEmpty());
    }

    @Test
    @DisplayName("getIssues_singlePackageNoLocationsNoVulns_fieldsMapped")
    void testGetIssues_singlePackageNoLocationsNoVulns_fieldsMapped() {
        OssRealtimeScanPackage pkg = mock(OssRealtimeScanPackage.class);
        when(pkg.getPackageName()).thenReturn("mypackage");
        when(pkg.getPackageVersion()).thenReturn("1.2.3");
        when(pkg.getStatus()).thenReturn("LOW");
        when(pkg.getLocations()).thenReturn(List.of());
        when(pkg.getVulnerabilities()).thenReturn(List.of());
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(List.of(pkg));
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        ScanIssue issue = issues.get(0);
        assertEquals("mypackage", issue.getTitle());
        assertEquals("1.2.3", issue.getPackageVersion());
        assertEquals("LOW", issue.getSeverity());
        assertEquals(ScanEngine.OSS, issue.getScanEngine());
        assertTrue(issue.getLocations().isEmpty());
        assertTrue(issue.getVulnerabilities().isEmpty());
    }

    @Test
    @DisplayName("getIssues_singlePackageWithLocationsAndVulns_mappingAndLineIncrement")
    void testGetIssues_singlePackageWithLocationsAndVulns_mappingAndLineIncrement() {
        // Mock vulnerability
        OssRealtimeVulnerability vul = mock(OssRealtimeVulnerability.class);
        doReturn("Test vulnerability").when(vul).getDescription();
        doReturn("CRITICAL").when(vul).getSeverity();
        doReturn("9.9.9").when(vul).getFixVersion();
        // Mock location (line zero-based 0 should become 1)
        RealtimeLocation loc = mock(RealtimeLocation.class);
        doReturn(0).when(loc).getLine();
        doReturn(2).when(loc).getStartIndex();
        doReturn(5).when(loc).getEndIndex();
        OssRealtimeScanPackage pkg = mock(OssRealtimeScanPackage.class);
        when(pkg.getPackageName()).thenReturn("libA");
        when(pkg.getPackageVersion()).thenReturn("0.0.1");
        when(pkg.getStatus()).thenReturn("HIGH");
        when(pkg.getLocations()).thenReturn(List.of(loc));
        when(pkg.getVulnerabilities()).thenReturn(List.of(vul));
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(List.of(pkg));
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(1, issues.size());
        ScanIssue issue = issues.get(0);
        assertEquals("libA", issue.getTitle());
        assertEquals("0.0.1", issue.getPackageVersion());
        assertEquals("HIGH", issue.getSeverity());
        assertEquals(ScanEngine.OSS, issue.getScanEngine());
        assertEquals(1, issue.getLocations().size());
        Location mappedLoc = issue.getLocations().get(0);
        assertEquals(1, mappedLoc.getLine()); // incremented
        assertEquals(2, mappedLoc.getStartIndex());
        assertEquals(5, mappedLoc.getEndIndex());
        assertEquals(1, issue.getVulnerabilities().size());
        var mappedVul = issue.getVulnerabilities().get(0);
        assertEquals("Test vulnerability", mappedVul.getDescription());
        assertEquals("CRITICAL", mappedVul.getSeverity());
        assertEquals("9.9.9", mappedVul.getFixVersion());
    }

    @Test
    @DisplayName("getIssues_multiplePackages_mixedLists_allCollected")
    void testGetIssues_multiplePackages_mixedLists_allCollected() {
        OssRealtimeScanPackage pkg1 = mock(OssRealtimeScanPackage.class);
        when(pkg1.getPackageName()).thenReturn("pkg1");
        when(pkg1.getPackageVersion()).thenReturn("1.0");
        when(pkg1.getStatus()).thenReturn("LOW");
        when(pkg1.getLocations()).thenReturn(null); // null lists should be treated as empty
        when(pkg1.getVulnerabilities()).thenReturn(null);
        OssRealtimeScanPackage pkg2 = mock(OssRealtimeScanPackage.class);
        when(pkg2.getPackageName()).thenReturn("pkg2");
        when(pkg2.getPackageVersion()).thenReturn("2.0");
        when(pkg2.getStatus()).thenReturn("MEDIUM");
        when(pkg2.getLocations()).thenReturn(List.of());
        when(pkg2.getVulnerabilities()).thenReturn(List.of());
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(List.of(pkg1, pkg2));
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        List<ScanIssue> issues = adaptor.getIssues();
        assertEquals(2, issues.size());
        assertEquals("pkg1", issues.get(0).getTitle());
        assertEquals("pkg2", issues.get(1).getTitle());
    }

    @Test
    @DisplayName("getResults_returnsOriginalInstance")
    void testGetResults_returnsOriginalInstance() {
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results, "");
        assertSame(results, adaptor.getResults());
    }
}

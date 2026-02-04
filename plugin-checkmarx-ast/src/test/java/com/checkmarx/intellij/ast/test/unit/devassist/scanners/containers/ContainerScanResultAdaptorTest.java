package com.checkmarx.intellij.ast.test.unit.devassist.scanners.containers;


import com.checkmarx.ast.containersrealtime.ContainersRealtimeImage;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.scanners.containers.ContainerScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ContainerScanResultAdaptorTest {

    @Test
    void getIssuesReturnsEmptyWhenNoImages() {
        ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
        when(results.getImages()).thenReturn(null);

        ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results,"dockerfile", "");

        assertTrue(adaptor.getIssues().isEmpty(), "Expected empty issues when images are null");
    }

    @Test
    void getIssuesReturnsEmptyWhenImagesListEmpty() {
        ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
        when(results.getImages()).thenReturn(Collections.emptyList());

        ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results,"docker-compose", "");

        assertTrue(adaptor.getIssues().isEmpty(), "Expected empty issues when image list is empty");
    }

    @Test
    void getIssuesMapsImagesLocationsAndVulnerabilities() {
        ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
        ContainersRealtimeImage image = mock(ContainersRealtimeImage.class);
        RealtimeLocation realtimeLocation = mock(RealtimeLocation.class);
        ContainersRealtimeVulnerability vulnerability = mock(ContainersRealtimeVulnerability.class);

        when(results.getImages()).thenReturn(Collections.singletonList(image));
        when(image.getImageName()).thenReturn("nginx");
        when(image.getImageTag()).thenReturn("1.25");
        when(image.getStatus()).thenReturn(Constants.CRITICAL_SEVERITY);

        when(image.getLocations()).thenReturn(List.of(realtimeLocation));
        when(realtimeLocation.getLine()).thenReturn(9);
        when(realtimeLocation.getStartIndex()).thenReturn(3);
        when(realtimeLocation.getEndIndex()).thenReturn(15);

        when(image.getVulnerabilities()).thenReturn(List.of(vulnerability));
        when(vulnerability.getCve()).thenReturn("CVE-2024-1234");
        when(vulnerability.getSeverity()).thenReturn(Constants.CRITICAL_SEVERITY);

        ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results,"dockerfile", "");
        List<ScanIssue> issues = adaptor.getIssues();

        assertEquals(1, issues.size(), "Expected exactly one scan issue");
        ScanIssue issue = issues.get(0);
        assertEquals(ScanEngine.CONTAINERS, issue.getScanEngine());
        assertEquals("nginx", issue.getTitle());
        assertEquals("1.25", issue.getImageTag());
        assertEquals(Constants.CRITICAL_SEVERITY, issue.getSeverity());

        assertEquals(1, issue.getLocations().size(), "Location should be populated from realtime location");
        Location location = issue.getLocations().get(0);
        assertEquals(10, location.getLine(), "Line number should be 1-indexed in ScanIssue");
        assertEquals(3, location.getStartIndex());
        assertEquals(15, location.getEndIndex());

        assertEquals(1, issue.getVulnerabilities().size(), "Vulnerabilities should be transferred");
        Vulnerability adaptedVulnerability = issue.getVulnerabilities().get(0);
        assertEquals("CVE-2024-1234", adaptedVulnerability.getCve());
        assertEquals(Constants.CRITICAL_SEVERITY, adaptedVulnerability.getSeverity());
        assertEquals("Critical-risk container image", adaptedVulnerability.getDescription(),
                "Description should match severity-based message");
    }

    @Test
    void getIssuesHandlesNullLocationsAndVulnerabilities() {
        ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
        ContainersRealtimeImage image = mock(ContainersRealtimeImage.class);

        when(results.getImages()).thenReturn(List.of(image));
        when(image.getImageName()).thenReturn("redis");
        when(image.getImageTag()).thenReturn("7");
        when(image.getStatus()).thenReturn(Constants.MEDIUM_SEVERITY);
        when(image.getLocations()).thenReturn(null);
        when(image.getVulnerabilities()).thenReturn(null);

        ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results,"dockerfile", "");
        List<ScanIssue> issues = adaptor.getIssues();

        assertEquals(1, issues.size());
        ScanIssue issue = issues.get(0);
        assertTrue(issue.getLocations().isEmpty(), "Locations should be empty when source locations are null");
        assertTrue(issue.getVulnerabilities().isEmpty(), "Vulnerabilities should be empty when source vulnerabilities are null");
    }

    @Test
    void getIssuesUsesSeverityTextWhenNoMappingExists() {
        ContainersRealtimeResults results = mock(ContainersRealtimeResults.class);
        ContainersRealtimeImage image = mock(ContainersRealtimeImage.class);
        ContainersRealtimeVulnerability vulnerability = mock(ContainersRealtimeVulnerability.class);

        when(results.getImages()).thenReturn(List.of(image));
        when(image.getImageName()).thenReturn("custom");
        when(image.getImageTag()).thenReturn("latest");
        when(image.getStatus()).thenReturn("INFO");
        when(image.getLocations()).thenReturn(Collections.emptyList());
        when(image.getVulnerabilities()).thenReturn(List.of(vulnerability));

        when(vulnerability.getCve()).thenReturn("CVE-0000-0000");
        when(vulnerability.getSeverity()).thenReturn("INFO");

        ContainerScanResultAdaptor adaptor = new ContainerScanResultAdaptor(results,"dockerfile", "");
        List<ScanIssue> issues = adaptor.getIssues();

        Vulnerability adaptedVulnerability = issues.get(0).getVulnerabilities().get(0);
        assertEquals("INFO", adaptedVulnerability.getDescription(),
                "For unmapped severities description should fallback to raw severity");
    }
}

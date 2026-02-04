package com.checkmarx.intellij.ast.test.unit.devassist.scanners.secrets;

import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScanResultAdaptor;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SecretsScanResultAdaptorTest {

    private SecretsRealtimeResults mockResults;
    private SecretsScanResultAdaptor adaptor;

    @BeforeEach
    void setUp() {
        mockResults = mock(SecretsRealtimeResults.class);
        adaptor = new SecretsScanResultAdaptor(mockResults, "");
    }

    @Test
    @DisplayName("Should return wrapped results")
    void testGetResults() {
        // When
        SecretsRealtimeResults result = adaptor.getResults();

        // Then
        assertEquals(mockResults, result);
    }

    @Test
    @DisplayName("Should return empty list for null or empty results")
    void testGetIssues_EmptyScenarios() {
        // Test null results
        SecretsScanResultAdaptor nullAdaptor = new SecretsScanResultAdaptor(null, "");
        List<ScanIssue> nullIssues = nullAdaptor.getIssues();
        assertNotNull(nullIssues);
        assertTrue(nullIssues.isEmpty());

        // Test empty secrets list
        when(mockResults.getSecrets()).thenReturn(Collections.emptyList());
        List<ScanIssue> emptyIssues = adaptor.getIssues();
        assertNotNull(emptyIssues);
        assertTrue(emptyIssues.isEmpty());

        // Test null secrets list
        when(mockResults.getSecrets()).thenReturn(null);
        List<ScanIssue> nullSecretsIssues = adaptor.getIssues();
        assertNotNull(nullSecretsIssues);
        assertTrue(nullSecretsIssues.isEmpty());
    }

    @Test
    @DisplayName("Should convert single secret to scan issue successfully")
    void testGetIssues_SingleSecret() {
        // Given
        SecretsRealtimeResults.Secret mockSecret = createMockSecret(
                "API Key Detected",
                "HIGH",
                "Hardcoded API key found",
                "sk-1234567890",
                "test.js",
                Collections.singletonList(createMockLocation(5, 10, 25))
        );
        when(mockResults.getSecrets()).thenReturn(Collections.singletonList(mockSecret));
        SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults, "test.js");
        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(1, issues.size());

        ScanIssue issue = issues.get(0);
        assertEquals("API Key Detected", issue.getTitle());
        assertEquals(ScanEngine.SECRETS, issue.getScanEngine());
        assertEquals("HIGH", issue.getSeverity());
        assertEquals("test.js", issue.getFilePath());
        assertEquals("Hardcoded API key found", issue.getDescription());

        // Verify locations
        assertEquals(1, issue.getLocations().size());
        Location location = issue.getLocations().get(0);
        assertEquals(6, location.getLine()); // Should be incremented by 1
        assertEquals(10, location.getStartIndex());
        assertEquals(25, location.getEndIndex());

        // Verify vulnerabilities
        assertEquals(1, issue.getVulnerabilities().size());
        Vulnerability vulnerability = issue.getVulnerabilities().get(0);
        assertEquals("Hardcoded API key found", vulnerability.getDescription());
        assertEquals("HIGH", vulnerability.getSeverity());
    }

    @Test
    @DisplayName("Should convert multiple secrets to scan issues successfully")
    void testGetIssues_MultipleSecrets() {
        // Given
        SecretsRealtimeResults.Secret secret1 = createMockSecret(
                "API Key",
                "HIGH",
                "API key detected",
                "key1",
                "file1.js",
                Collections.singletonList(createMockLocation(1, 5, 15))
        );

        SecretsRealtimeResults.Secret secret2 = createMockSecret(
                "Database Password",
                "CRITICAL",
                "Database password detected",
                "pwd123",
                "file2.js",
                Collections.singletonList(createMockLocation(3, 20, 30))
        );

        when(mockResults.getSecrets()).thenReturn(Arrays.asList(secret1, secret2));
        SecretsScanResultAdaptor adaptor= new SecretsScanResultAdaptor(mockResults, "file1.js");
        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(2, issues.size());

        // Verify first issue
        ScanIssue issue1 = issues.get(0);
        assertEquals("API Key", issue1.getTitle());
        assertEquals("HIGH", issue1.getSeverity());
        assertEquals("file1.js", issue1.getFilePath());

        // Verify second issue - both issues should have the same file path from constructor
        ScanIssue issue2 = issues.get(1);
        assertEquals("Database Password", issue2.getTitle());
        assertEquals("CRITICAL", issue2.getSeverity());
        assertEquals("file1.js", issue2.getFilePath());
    }

    @Test
    @DisplayName("Should handle secret with multiple locations")
    void testGetIssues_SecretWithMultipleLocations() {
        // Given
        List<RealtimeLocation> locations = Arrays.asList(
                createMockLocation(1, 5, 15),
                createMockLocation(3, 20, 30),
                createMockLocation(5, 10, 25)
        );

        SecretsRealtimeResults.Secret mockSecret = createMockSecret(
                "API Key",
                "HIGH",
                "API key detected",
                "key1",
                "test.js",
                locations
        );
        when(mockResults.getSecrets()).thenReturn(Collections.singletonList(mockSecret));
        SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults, "");

        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(1, issues.size());

        ScanIssue issue = issues.get(0);
        assertEquals(3, issue.getLocations().size());

        // Verify all locations are properly converted
        Location loc1 = issue.getLocations().get(0);
        assertEquals(2, loc1.getLine()); // 1 + 1
        assertEquals(5, loc1.getStartIndex());
        assertEquals(15, loc1.getEndIndex());

        Location loc2 = issue.getLocations().get(1);
        assertEquals(4, loc2.getLine()); // 3 + 1
        assertEquals(20, loc2.getStartIndex());
        assertEquals(30, loc2.getEndIndex());

        Location loc3 = issue.getLocations().get(2);
        assertEquals(6, loc3.getLine()); // 5 + 1
        assertEquals(10, loc3.getStartIndex());
        assertEquals(25, loc3.getEndIndex());
    }

    @Test
    @DisplayName("Should handle secret with null locations")
    void testGetIssues_SecretWithNullLocations() {
        // Given
        SecretsRealtimeResults.Secret mockSecret = createMockSecret(
                "API Key",
                "HIGH",
                "API key detected",
                "key1",
                "test.js",
                null
        );
        when(mockResults.getSecrets()).thenReturn(Collections.singletonList(mockSecret));
        SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults, "");

        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(1, issues.size());

        ScanIssue issue = issues.get(0);
        assertTrue(issue.getLocations().isEmpty());
    }


    //@Test
    @DisplayName("Should handle secret with null values gracefully")
    void testGetIssues_SecretWithNullValues() {
        // Given
        SecretsRealtimeResults.Secret mockSecret = mock(SecretsRealtimeResults.Secret.class);
        when(mockSecret.getTitle()).thenReturn(null);
        when(mockSecret.getSeverity()).thenReturn(null);
        when(mockSecret.getDescription()).thenReturn(null);
        when(mockSecret.getSecretValue()).thenReturn(null);
        when(mockSecret.getFilePath()).thenReturn(null);
        when(mockSecret.getLocations()).thenReturn(null);

        when(mockResults.getSecrets()).thenReturn(Collections.singletonList(mockSecret));

        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(1, issues.size());

        ScanIssue issue = issues.get(0);
        assertNull(issue.getTitle());
        assertNull(issue.getSeverity());
        assertNull(issue.getFilePath());
        assertNull(issue.getDescription());
        assertEquals(ScanEngine.SECRETS, issue.getScanEngine());
        assertTrue(issue.getLocations().isEmpty());
        assertEquals(1, issue.getVulnerabilities().size());
    }

    @Test
    @DisplayName("Should handle empty locations list gracefully")
    void testGetIssues_EmptyLocations() {
        // Given
        SecretsRealtimeResults.Secret mockSecret = createMockSecret(
                "API Key",
                "HIGH",
                "API key detected",
                "key1",
                "test.js",
                Collections.emptyList() // Empty locations list
        );
        when(mockResults.getSecrets()).thenReturn(Collections.singletonList(mockSecret));
        SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults, "");

        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertNotNull(issues);
        assertEquals(1, issues.size());

        ScanIssue issue = issues.get(0);
        assertTrue(issue.getLocations().isEmpty());
        assertEquals("API Key", issue.getTitle());
        assertEquals(ScanEngine.SECRETS, issue.getScanEngine());
    }

    @Test
    @DisplayName("Should handle different severity levels and verify vulnerability creation")
    void testGetIssues_DifferentSeverityLevels() {
        // Given
        SecretsRealtimeResults.Secret lowSeverity = createMockSecret(
                "Config Value", "LOW", "Configuration value", "config123", "config.js",
                Collections.singletonList(createMockLocation(0, 0, 9))
        );

        SecretsRealtimeResults.Secret criticalSeverity = createMockSecret(
                "Database Password", "CRITICAL", "Critical password leak", "pass456", "db.js",
                Collections.singletonList(createMockLocation(5, 10, 17))
        );

        when(mockResults.getSecrets()).thenReturn(Arrays.asList(lowSeverity, criticalSeverity));
        SecretsScanResultAdaptor adaptor = new SecretsScanResultAdaptor(mockResults, "");

        // When
        List<ScanIssue> issues = adaptor.getIssues();

        // Then
        assertEquals(2, issues.size());

        // Verify LOW severity issue
        ScanIssue lowIssue = issues.get(0);
        assertEquals("LOW", lowIssue.getSeverity());
        assertEquals("Configuration value", lowIssue.getDescription());
        assertEquals(1, lowIssue.getVulnerabilities().size());
        Vulnerability lowVuln = lowIssue.getVulnerabilities().get(0);
        assertEquals("LOW", lowVuln.getSeverity());

        // Verify CRITICAL severity issue
        ScanIssue criticalIssue = issues.get(1);
        assertEquals("CRITICAL", criticalIssue.getSeverity());
        assertEquals("Critical password leak", criticalIssue.getDescription());
        assertEquals(6, criticalIssue.getLocations().get(0).getLine()); // 5 + 1

        // Verify vulnerability creation consistency
        Vulnerability criticalVuln = criticalIssue.getVulnerabilities().get(0);
        assertEquals("CRITICAL", criticalVuln.getSeverity());
    }

    private SecretsRealtimeResults.Secret createMockSecret(String title, String severity,
                                                         String description, String secretValue,
                                                         String filePath, List<RealtimeLocation> locations) {
        SecretsRealtimeResults.Secret secret = mock(SecretsRealtimeResults.Secret.class);
        when(secret.getTitle()).thenReturn(title);
        when(secret.getSeverity()).thenReturn(severity);
        when(secret.getDescription()).thenReturn(description);
        when(secret.getSecretValue()).thenReturn(secretValue);
        when(secret.getFilePath()).thenReturn(filePath);
        when(secret.getLocations()).thenReturn(locations);
        return secret;
    }

    private RealtimeLocation createMockLocation(int line, int startIndex, int endIndex) {
        RealtimeLocation location = mock(RealtimeLocation.class);
        when(location.getLine()).thenReturn(line);
        when(location.getStartIndex()).thenReturn(startIndex);
        when(location.getEndIndex()).thenReturn(endIndex);
        return location;
    }
}

package com.checkmarx.intellij.devassist.test.telemetry;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TelemetryService unit tests")
class TelemetryServiceTest {

    // ===== null guards =====

    @Test
    @DisplayName("logViewDetailsAction_nullScanIssue_doesNotThrow")
    void logViewDetailsAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logViewDetailsAction(null));
    }

    @Test
    @DisplayName("logIgnorePackageAction_nullScanIssue_doesNotThrow")
    void logIgnorePackageAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logIgnorePackageAction(null));
    }

    @Test
    @DisplayName("logIgnoreAllAction_nullScanIssue_doesNotThrow")
    void logIgnoreAllAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logIgnoreAllAction(null));
    }

    @Test
    @DisplayName("logRemediation_nullScanIssue_doesNotThrow")
    void logRemediation_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logRemediation(null, AiAgent.GITHUB_COPILOT, null));
    }

    @Test
    @DisplayName("logRemediation_failure_doesNotThrow")
    void logRemediation_failure_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.OSS);
        issue.setSeverity(Constants.HIGH_SEVERITY);
        assertDoesNotThrow(() -> TelemetryService.logRemediation(issue, AiAgent.JETBRAINS_AI_ASSISTANT, "chat did not open"));
    }

    // ===== logScanResults =====

    @Test
    @DisplayName("logScanResults_nullScanResult_doesNotThrow")
    void logScanResults_nullScanResult_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanResults(null, ScanEngine.OSS, "/project/pom.xml"));
    }

    @Test
    @DisplayName("logScanResults_nullScanEngine_doesNotThrow")
    void logScanResults_nullScanEngine_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, null, "/project/pom.xml"));
    }

    @Test
    @DisplayName("logScanResults_emptyIssues_doesNotThrow")
    void logScanResults_emptyIssues_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.SECRETS, "/project/a.env"));
    }

    @Test
    @DisplayName("logScanResults_nullIssues_doesNotThrow")
    void logScanResults_nullIssues_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(null);
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.IAC, null));
    }

    @Test
    @DisplayName("logScanResults_withIssues_doesNotThrow")
    void logScanResults_withIssues_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(Constants.CRITICAL_SEVERITY);
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(issue));
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.ASCA, "/project/Main.java"));
    }

    @Test
    @DisplayName("logScanResults_nullSeverity_doesNotThrow")
    void logScanResults_nullSeverity_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(null);
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(issue));
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.CONTAINERS, "/project/Dockerfile"));
    }

    // ===== logScanFailure / logLoginEvent =====

    @Test
    @DisplayName("logScanFailure_nullEngine_doesNotThrow")
    void logScanFailure_nullEngine_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanFailure(null, "/project/pom.xml", new RuntimeException("boom")));
    }

    @Test
    @DisplayName("logScanFailure_nullError_doesNotThrow")
    void logScanFailure_nullError_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanFailure(ScanEngine.OSS, "/project/pom.xml", null));
    }

    @Test
    @DisplayName("logLoginEvent_dispatchesWithoutThrowing")
    void logLoginEvent_dispatchesWithoutThrowing() {
        assertDoesNotThrow(() -> TelemetryService.logLoginEvent("APIKey", true, null));
        assertDoesNotThrow(() -> TelemetryService.logLoginEvent("OAuth", false, "invalid token"));
    }
}

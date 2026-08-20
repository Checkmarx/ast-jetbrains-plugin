package com.checkmarx.intellij.devassist.test.telemetry;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@DisplayName("TelemetryService unit tests")
class TelemetryServiceTest {

    // ===== setUserEventDataForDetectionLogs early-return branch =====

    @Test
    @DisplayName("setUserEventDataForDetectionLogs_zeroCount_returnsEarlyWithoutAsync")
    void setUserEventDataForDetectionLogs_zeroCount_returnsEarly() {
        assertDoesNotThrow(() ->
                TelemetryService.setUserEventDataForDetectionLogs("secrets", "High", 0));
    }

    @Test
    @DisplayName("setUserEventDataForDetectionLogs_negativeCount_returnsEarlyWithoutAsync")
    void setUserEventDataForDetectionLogs_negativeCount_returnsEarly() {
        assertDoesNotThrow(() ->
                TelemetryService.setUserEventDataForDetectionLogs("iac", "Medium", -5));
    }

    // ===== logFixWithCxOneAssistAction null-guard =====

    @Test
    @DisplayName("logFixWithCxOneAssistAction_nullScanIssue_doesNotThrow")
    void logFixWithCxOneAssistAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logFixWithCxOneAssistAction(null));
    }

    // ===== logViewDetailsAction null-guard =====

    @Test
    @DisplayName("logViewDetailsAction_nullScanIssue_doesNotThrow")
    void logViewDetailsAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logViewDetailsAction(null));
    }

    // ===== logIgnorePackageAction null-guard =====

    @Test
    @DisplayName("logIgnorePackageAction_nullScanIssue_doesNotThrow")
    void logIgnorePackageAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logIgnorePackageAction(null));
    }

    // ===== logIgnoreAllAction null-guard =====

    @Test
    @DisplayName("logIgnoreAllAction_nullScanIssue_doesNotThrow")
    void logIgnoreAllAction_nullScanIssue_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logIgnoreAllAction(null));
    }

    // ===== logScanResults(String, List) early-return branches =====

    @Test
    @DisplayName("logScanResults_stringEngine_nullList_doesNotThrow")
    void logScanResults_stringEngine_nullList_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanResults("Oss", null));
    }

    @Test
    @DisplayName("logScanResults_stringEngine_emptyList_doesNotThrow")
    void logScanResults_stringEngine_emptyList_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanResults("Oss", Collections.emptyList()));
    }

    @Test
    @DisplayName("logScanResults_stringEngine_withIssues_doesNotThrow")
    void logScanResults_stringEngine_withIssues_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(Constants.HIGH_SEVERITY);
        assertDoesNotThrow(() -> TelemetryService.logScanResults("Oss", List.of(issue)));
    }

    @Test
    @DisplayName("logScanResults_stringEngine_unknownSeverity_doesNotThrow")
    void logScanResults_stringEngine_unknownSeverity_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("completely-unknown");
        assertDoesNotThrow(() -> TelemetryService.logScanResults("Secrets", List.of(issue)));
    }

    @Test
    @DisplayName("logScanResults_stringEngine_nullSeverity_doesNotThrow")
    void logScanResults_stringEngine_nullSeverity_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(null);
        assertDoesNotThrow(() -> TelemetryService.logScanResults("IaC", List.of(issue)));
    }

    // ===== logScanResults(ScanResult, ScanEngine) early-return branches =====

    @Test
    @DisplayName("logScanResults_scanResult_nullScanResult_doesNotThrow")
    void logScanResults_scanResult_nullScanResult_doesNotThrow() {
        assertDoesNotThrow(() -> TelemetryService.logScanResults((ScanResult<?>) null, ScanEngine.OSS));
    }

    @Test
    @DisplayName("logScanResults_scanResult_nullScanEngine_doesNotThrow")
    void logScanResults_scanResult_nullScanEngine_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, null));
    }

    @Test
    @DisplayName("logScanResults_scanResult_emptyIssues_doesNotThrow")
    void logScanResults_scanResult_emptyIssues_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.SECRETS));
    }

    @Test
    @DisplayName("logScanResults_scanResult_nullIssues_doesNotThrow")
    void logScanResults_scanResult_nullIssues_doesNotThrow() {
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(null);
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.IAC));
    }

    @Test
    @DisplayName("logScanResults_scanResult_withIssues_doesNotThrow")
    void logScanResults_scanResult_withIssues_doesNotThrow() {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(Constants.CRITICAL_SEVERITY);
        ScanResult<?> scanResult = mock(ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(issue));
        assertDoesNotThrow(() -> TelemetryService.logScanResults(scanResult, ScanEngine.ASCA));
    }

    // ===== setUserEventDataForLogs dispatches async without throwing =====

    @Test
    @DisplayName("setUserEventDataForLogs_doesNotThrowOnDispatch")
    void setUserEventDataForLogs_doesNotThrowOnDispatch() {
        assertDoesNotThrow(() ->
                TelemetryService.setUserEventDataForLogs("click", "fixWithAIChat", "Oss", "High"));
    }
}

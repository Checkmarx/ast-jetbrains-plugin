package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelemetryEventFactory unit tests")
class TelemetryEventFactoryTest {

    // ===== scan event =====

    @Test
    @DisplayName("scanEvent_mixedSeverities_countsAndHighestSeverity")
    void scanEvent_mixedSeverities_countsAndHighestSeverity() {
        List<String> severities = Arrays.asList("High", "Medium", "Medium", "Low", "Critical", "Malicious", "Unknown");

        TelemetryEvent event = TelemetryEventFactory.scanEvent("Oss", severities, "https://github.com/org/repo").build();

        assertEquals("Scan", event.getEventType());
        assertEquals("scantriggerd", event.getSubType());
        assertEquals("Oss", event.getEngine());
        assertEquals("Critical", event.getProblemSeverity());
        assertEquals(Integer.valueOf(7), event.getTotalCount());
        assertEquals("success", event.getStatus());
        assertEquals("Critical=2, High=1, Medium=2, Low=1, Info=1", event.getStatusResult());
        assertEquals("https://github.com/org/repo", event.getRepoUrl());
        assertEquals("v2", event.getApiVersion());
        assertEquals("v2", event.getEventAttributes().get("apiVersion"));
    }

    @Test
    @DisplayName("scanEvent_noFindings_isStillBuiltWithZeroCount")
    void scanEvent_noFindings_isStillBuiltWithZeroCount() {
        TelemetryEvent event = TelemetryEventFactory.scanEvent("Secrets", Collections.emptyList(), "").build();

        assertEquals(Integer.valueOf(0), event.getTotalCount());
        assertEquals("", event.getProblemSeverity());
        assertEquals("Critical=0, High=0, Medium=0, Low=0, Info=0", event.getStatusResult());
    }

    @Test
    @DisplayName("scanEvent_okSeverityIsNotCounted_nullSeverityIsInfo")
    void scanEvent_okSeverityIsNotCounted_nullSeverityIsInfo() {
        TelemetryEvent event = TelemetryEventFactory.scanEvent("Containers", Arrays.asList("OK", "ok", null), "").build();

        assertEquals(Integer.valueOf(1), event.getTotalCount());
        assertEquals("Info", event.getProblemSeverity());
        assertEquals("Critical=0, High=0, Medium=0, Low=0, Info=1", event.getStatusResult());
    }

    @Test
    @DisplayName("scanEvent_nullSeverityList_isTreatedAsEmpty")
    void scanEvent_nullSeverityList_isTreatedAsEmpty() {
        TelemetryEvent event = TelemetryEventFactory.scanEvent("IaC", null, "").build();

        assertEquals(Integer.valueOf(0), event.getTotalCount());
    }

    @Test
    @DisplayName("scanFailureEvent_hasFailureStatusAndSanitizedReason")
    void scanFailureEvent_hasFailureStatusAndSanitizedReason() {
        TelemetryEvent event = TelemetryEventFactory.scanFailureEvent("Asca",
                new RuntimeException("failed with token=abc123secret"), "https://github.com/org/repo").build();

        assertEquals("Scan", event.getEventType());
        assertEquals("failure", event.getStatus());
        assertEquals("Asca", event.getEngine());
        assertNull(event.getTotalCount());
        assertEquals("Scan failed. Reason: failed with token=***, Error Code: N/A", event.getStatusResult());
        assertFalse(event.getStatusResult().contains("abc123secret"));
    }

    @Test
    @DisplayName("scanFailureEvent_nullError_usesUnknownReason")
    void scanFailureEvent_nullError_usesUnknownReason() {
        TelemetryEvent event = TelemetryEventFactory.scanFailureEvent("Oss", null, "").build();

        assertEquals("Scan failed. Reason: unknown, Error Code: N/A", event.getStatusResult());
    }

    // ===== login and install events =====

    @Test
    @DisplayName("loginEvent_success_hasAuthMethodAndTimestamp")
    void loginEvent_success_hasAuthMethodAndTimestamp() {
        TelemetryEvent event = TelemetryEventFactory.loginEvent("APIKey", true, null, "2026-11-19T10:00:00Z").build();

        assertEquals("authentication", event.getEventType());
        assertEquals("authentication", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("Authentication successful.", event.getStatusResult());
        assertEquals("APIKey", event.getEventAttributes().get("authMethod"));
        assertEquals("2026-11-19T10:00:00Z", event.getEventAttributes().get("loginTimestamp"));
    }

    @Test
    @DisplayName("loginEvent_failure_hasSanitizedReason")
    void loginEvent_failure_hasSanitizedReason() {
        TelemetryEvent event = TelemetryEventFactory.loginEvent("OAuth", false,
                "Bearer eyJhbGciOi.eyJzdWIiOi.c2lnbmF0dXJl rejected", "2026-11-19T10:00:00Z").build();

        assertEquals("failure", event.getStatus());
        assertEquals("Authentication failed. Reason: Bearer *** rejected, Error Code: N/A", event.getStatusResult());
    }

    @Test
    @DisplayName("loginEvent_failureWithoutReason_usesUnknown")
    void loginEvent_failureWithoutReason_usesUnknown() {
        TelemetryEvent event = TelemetryEventFactory.loginEvent("OAuth", false, null, "t").build();

        assertEquals("Authentication failed. Reason: unknown, Error Code: N/A", event.getStatusResult());
    }

    @Test
    @DisplayName("installEvent_hasInstallationIdAndTimestamp")
    void installEvent_hasInstallationIdAndTimestamp() {
        TelemetryEvent event = TelemetryEventFactory.installEvent("intellij-idea-1234", "2026-11-19T09:00:00Z").build();

        assertEquals("install", event.getEventType());
        assertEquals("installation", event.getSubType());
        assertEquals("success", event.getStatus());
        assertEquals("Plugin installed successfully.", event.getStatusResult());
        assertEquals("intellij-idea-1234", event.getEventAttributes().get("installationId"));
        assertEquals("2026-11-19T09:00:00Z", event.getEventAttributes().get("installationTimestamp"));
    }

    // ===== remediation and click events =====

    @Test
    @DisplayName("remediationEvent_success")
    void remediationEvent_success() {
        TelemetryEvent event = TelemetryEventFactory.remediationEvent("Oss", "High", "Copilot", "lodash@4.17.20", null,
                "https://github.com/org/repo", "fixWithAIChat").build();

        assertEquals("click", event.getEventType());
        assertEquals("fixWithAIChat", event.getSubType());
        assertEquals("Oss", event.getEngine());
        assertEquals("High", event.getProblemSeverity());
        assertEquals("Copilot", event.getAiProvider());
        assertEquals("success", event.getStatus());
        assertEquals("Remediation completed successfully.", event.getStatusResult());
        assertEquals("https://github.com/org/repo", event.getRepoUrl());
        Map<String, String> attributes = event.getEventAttributes();
        assertEquals("lodash@4.17.20", attributes.get("vulnerabilityId"));
        assertEquals("DevAssist", attributes.get("remediationMode"));
    }

    @Test
    @DisplayName("remediationEvent_failure_hasReason")
    void remediationEvent_failure_hasReason() {
        TelemetryEvent event = TelemetryEventFactory.remediationEvent("Secrets", "Critical", "JetBrains AI Assistant",
                "AWS key", "chat did not open", "", "fixWithAIChat").build();

        assertEquals("failure", event.getStatus());
        assertEquals("Remediation failed. Reason: chat did not open, Error Code: N/A", event.getStatusResult());
    }

    @Test
    @DisplayName("clickEvent_carriesFindingContextButNoRemediationMode")
    void clickEvent_carriesFindingContextButNoRemediationMode() {
        TelemetryEvent event = TelemetryEventFactory.clickEvent("viewDetails", "IaC", "Medium", "Copilot",
                "sim-123", "https://github.com/acme/repo").build();

        assertEquals("click", event.getEventType());
        assertEquals("viewDetails", event.getSubType());
        assertEquals("IaC", event.getEngine());
        assertEquals("Medium", event.getProblemSeverity());
        assertEquals("success", event.getStatus());
        assertEquals("Vulnerability details opened successfully.", event.getStatusResult());
        assertEquals("https://github.com/acme/repo", event.getRepoUrl());
        assertEquals(Map.of("vulnerabilityId", "sim-123"), event.getEventAttributes());
    }

    @Test
    @DisplayName("clickEvent_statusResultPerAction")
    void clickEvent_statusResultPerAction() {
        assertEquals("Vulnerability ignored successfully.",
                TelemetryEventFactory.clickEvent("Ignore", "Oss", "High", "Copilot", "lodash@4.17.20", "").build().getStatusResult());
        assertEquals("All vulnerabilities of this type ignored successfully.",
                TelemetryEventFactory.clickEvent("ignoreAll", "Oss", "High", "Copilot", "lodash@4.17.20", "").build().getStatusResult());
        assertEquals("",
                TelemetryEventFactory.clickEvent("somethingElse", "Oss", "High", "Copilot", "x", "").build().getStatusResult());
    }

    // ===== severity helpers =====

    @Test
    @DisplayName("toSeverityLabel_mapsAllKnownValues")
    void toSeverityLabel_mapsAllKnownValues() {
        assertEquals("Critical", TelemetryEventFactory.toSeverityLabel("critical"));
        assertEquals("Critical", TelemetryEventFactory.toSeverityLabel(" Malicious "));
        assertEquals("High", TelemetryEventFactory.toSeverityLabel("HIGH"));
        assertEquals("Medium", TelemetryEventFactory.toSeverityLabel("Medium"));
        assertEquals("Low", TelemetryEventFactory.toSeverityLabel("low"));
        assertEquals("Info", TelemetryEventFactory.toSeverityLabel("Unknown"));
        assertEquals("Info", TelemetryEventFactory.toSeverityLabel("something else"));
        assertEquals("Info", TelemetryEventFactory.toSeverityLabel(null));
        assertNull(TelemetryEventFactory.toSeverityLabel("OK"));
    }

    // ===== sanitizeText =====

    @Test
    @DisplayName("sanitizeText_removesCredentialsAndSecrets")
    void sanitizeText_removesCredentialsAndSecrets() {
        assertEquals("clone https://***@github.com/org/repo failed",
                TelemetryEventFactory.sanitizeText("clone https://user:ghp_secret@github.com/org/repo failed"));
        assertEquals("apikey=*** and password=***",
                TelemetryEventFactory.sanitizeText("apikey=abc123 and password: hunter2"));
        assertEquals("Authorization Bearer ***", TelemetryEventFactory.sanitizeText("Authorization Bearer abcdef"));
        assertEquals("token ***", TelemetryEventFactory.sanitizeText("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln"));
        assertEquals("key ***", TelemetryEventFactory.sanitizeText("key " + "A".repeat(40)));
    }

    @Test
    @DisplayName("sanitizeText_collapsesWhitespaceAndTruncates")
    void sanitizeText_collapsesWhitespaceAndTruncates() {
        assertEquals("a b c", TelemetryEventFactory.sanitizeText("  a \n\t b   c "));
        assertEquals(256, TelemetryEventFactory.sanitizeText("word ".repeat(100)).length());
        assertEquals("", TelemetryEventFactory.sanitizeText(null));
    }
}

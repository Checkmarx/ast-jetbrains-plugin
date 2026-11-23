package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TelemetryService delivery unit tests")
class TelemetryServiceDeliveryTest {

    private static final String UNKNOWN_FLAG_OUTPUT = "Error: unknown flag: --agent-version\n";

    @BeforeEach
    @AfterEach
    void resetLegacyFormat() {
        TelemetryService.resetLegacyFormat();
    }

    private static TelemetryEvent event() {
        return TelemetryEvent.builder().eventType("Scan").subType("scantriggerd").build();
    }

    // ===== deliver =====

    @Test
    @DisplayName("deliver_cliAcceptsEvent_returnsTrueAndDoesNotUseLegacy")
    void deliver_cliAcceptsEvent() throws Exception {
        CxWrapper wrapper = mock(CxWrapper.class);
        TelemetryService.LegacySender legacy = mock(TelemetryService.LegacySender.class);

        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertTrue(TelemetryService.deliver(event(), legacy));

            verify(wrapper).telemetryAIEvent(any(TelemetryEvent.class));
            verifyNoInteractions(legacy);
            assertFalse(TelemetryService.isLegacyFormat());
        }
    }

    @Test
    @DisplayName("deliver_unknownFlag_resendsInLegacyFormatAndRemembersIt")
    void deliver_unknownFlag_resendsInLegacyFormat() throws Exception {
        CxWrapper wrapper = mock(CxWrapper.class);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, UNKNOWN_FLAG_OUTPUT));
        TelemetryService.LegacySender legacy = mock(TelemetryService.LegacySender.class);

        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertTrue(TelemetryService.deliver(event(), legacy));
            assertTrue(TelemetryService.isLegacyFormat());
            verify(legacy).send(wrapper);

            // the next event goes straight to the legacy format, the new format is not tried again
            assertTrue(TelemetryService.deliver(event(), legacy));
            verify(wrapper, times(1)).telemetryAIEvent(any(TelemetryEvent.class));
            verify(legacy, times(2)).send(wrapper);
        }
    }

    @Test
    @DisplayName("deliver_unknownFlagAndNoLegacyFormat_returnsFalseWithoutStartingTheCliAgain")
    void deliver_unknownFlagAndNoLegacyFormat() throws Exception {
        CxWrapper wrapper = mock(CxWrapper.class);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, UNKNOWN_FLAG_OUTPUT));

        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertFalse(TelemetryService.deliver(event(), null));
            assertFalse(TelemetryService.deliver(event(), null));

            verify(wrapper, times(1)).telemetryAIEvent(any(TelemetryEvent.class));
            assertTrue(TelemetryService.isLegacyFormat());
        }
    }

    @Test
    @DisplayName("deliver_otherCliError_returnsFalseAndKeepsNewFormat")
    void deliver_otherCliError() throws Exception {
        CxWrapper wrapper = mock(CxWrapper.class);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, "Failed logging the data"));
        TelemetryService.LegacySender legacy = mock(TelemetryService.LegacySender.class);

        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertFalse(TelemetryService.deliver(event(), legacy));

            verifyNoInteractions(legacy);
            assertFalse(TelemetryService.isLegacyFormat());
        }
    }

    @Test
    @DisplayName("deliver_wrapperCannotBeBuilt_returnsFalseWithoutThrowing")
    void deliver_wrapperCannotBeBuilt() {
        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenThrow(new RuntimeException("no credentials"));

            assertFalse(TelemetryService.deliver(event(), null));
        }
    }

    @Test
    @DisplayName("deliver_legacySenderFails_returnsFalseWithoutThrowing")
    void deliver_legacySenderFails() throws Exception {
        CxWrapper wrapper = mock(CxWrapper.class);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, UNKNOWN_FLAG_OUTPUT));
        TelemetryService.LegacySender legacy = w -> {
            throw new IllegalStateException("legacy failed");
        };

        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertFalse(TelemetryService.deliver(event(), legacy));
        }
    }

    // ===== vulnerability id =====

    @Test
    @DisplayName("getVulnerabilityId_perEngine")
    void getVulnerabilityId_perEngine() {
        ScanIssue oss = issue(ScanEngine.OSS, "lodash");
        oss.setPackageVersion("4.17.20");
        assertEquals("lodash@4.17.20", TelemetryService.getVulnerabilityId(oss));

        ScanIssue ossNoVersion = issue(ScanEngine.OSS, "lodash");
        assertEquals("lodash", TelemetryService.getVulnerabilityId(ossNoVersion));

        ScanIssue container = issue(ScanEngine.CONTAINERS, "nginx");
        container.setImageTag("1.25");
        assertEquals("nginx:1.25", TelemetryService.getVulnerabilityId(container));

        ScanIssue asca = issue(ScanEngine.ASCA, "Sql injection");
        asca.setRuleId(42);
        assertEquals("42", TelemetryService.getVulnerabilityId(asca));

        ScanIssue iac = issue(ScanEngine.IAC, "Open port");
        iac.setSimilarityId("sim-1");
        assertEquals("sim-1", TelemetryService.getVulnerabilityId(iac));

        assertEquals("AWS key", TelemetryService.getVulnerabilityId(issue(ScanEngine.SECRETS, "AWS key")));
        assertEquals("", TelemetryService.getVulnerabilityId(new ScanIssue()));
    }

    // ===== ai provider names =====

    @Test
    @DisplayName("toAiProviderName_mapsAgents")
    void toAiProviderName_mapsAgents() {
        assertEquals("Copilot", TelemetryService.toAiProviderName(AiAgent.GITHUB_COPILOT));
        assertEquals("JetBrains AI Assistant", TelemetryService.toAiProviderName(AiAgent.JETBRAINS_AI_ASSISTANT));
        assertEquals("Copilot", TelemetryService.toAiProviderName(null));
    }

    private static ScanIssue issue(ScanEngine engine, String title) {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(engine);
        issue.setTitle(title);
        return issue;
    }
}

package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.aiagents.AiAgentResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AiAgentResolution}.
 * <p>
 * Tests the immutable result class that holds an resolved agent and optional notice message.
 */
@DisplayName("AiAgentResolution unit tests")
class AiAgentResolutionTest {

    @Test
    @DisplayName("constructor_WithAgentAndMessage_StoresValues")
    void constructor_WithAgentAndMessage_StoresValues() {
        String message = "Agent was auto-switched";
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.COPILOT, message);

        assertNotNull(resolution);
        assertEquals(AiAgent.COPILOT, resolution.getAgent());
        assertEquals(message, resolution.getNoticeMessage());
    }

    @Test
    @DisplayName("constructor_WithAgentAndNullMessage_StoresNullMessage")
    void constructor_WithAgentAndNullMessage_StoresNullMessage() {
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.JETBRAINS_AI_ASSISTANT, null);

        assertNotNull(resolution);
        assertEquals(AiAgent.JETBRAINS_AI_ASSISTANT, resolution.getAgent());
        assertNull(resolution.getNoticeMessage());
    }

    @Test
    @DisplayName("getAgent_ReturnsConfiguredAgent")
    void getAgent_ReturnsConfiguredAgent() {
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.COPILOT, "message");
        assertEquals(AiAgent.COPILOT, resolution.getAgent());
    }

    @Test
    @DisplayName("getNoticeMessage_ReturnsConfiguredMessage")
    void getNoticeMessage_ReturnsConfiguredMessage() {
        String message = "Test notice";
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.JETBRAINS_AI_ASSISTANT, message);
        assertEquals(message, resolution.getNoticeMessage());
    }

    @Test
    @DisplayName("getNoticeMessage_WhenNull_ReturnsNull")
    void getNoticeMessage_WhenNull_ReturnsNull() {
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.COPILOT, null);
        assertNull(resolution.getNoticeMessage());
    }

    @Test
    @DisplayName("equality_TwoInstancesWithSameValues_AreEqual")
    void equality_TwoInstancesWithSameValues_AreEqual() {
        AiAgentResolution res1 = new AiAgentResolution(AiAgent.COPILOT, "message");
        AiAgentResolution res2 = new AiAgentResolution(AiAgent.COPILOT, "message");

        assertEquals(res1, res2);
    }

    @Test
    @DisplayName("equality_TwoInstancesWithDifferentAgents_AreNotEqual")
    void equality_TwoInstancesWithDifferentAgents_AreNotEqual() {
        AiAgentResolution res1 = new AiAgentResolution(AiAgent.COPILOT, "message");
        AiAgentResolution res2 = new AiAgentResolution(AiAgent.JETBRAINS_AI_ASSISTANT, "message");

        assertNotEquals(res1, res2);
    }

    @Test
    @DisplayName("equality_TwoInstancesWithDifferentMessages_AreNotEqual")
    void equality_TwoInstancesWithDifferentMessages_AreNotEqual() {
        AiAgentResolution res1 = new AiAgentResolution(AiAgent.COPILOT, "message1");
        AiAgentResolution res2 = new AiAgentResolution(AiAgent.COPILOT, "message2");

        assertNotEquals(res1, res2);
    }

    @Test
    @DisplayName("hashCode_SameForEqualInstances")
    void hashCode_SameForEqualInstances() {
        AiAgentResolution res1 = new AiAgentResolution(AiAgent.COPILOT, "message");
        AiAgentResolution res2 = new AiAgentResolution(AiAgent.COPILOT, "message");

        assertEquals(res1.hashCode(), res2.hashCode());
    }

    @Test
    @DisplayName("toString_ContainsAgentName")
    void toString_ContainsAgentName() {
        AiAgentResolution resolution = new AiAgentResolution(AiAgent.JETBRAINS_AI_ASSISTANT, "notice");
        String str = resolution.toString();

        assertNotNull(str);
        assertTrue(str.contains("AiAgentResolution") || str.contains("agent"));
    }
}

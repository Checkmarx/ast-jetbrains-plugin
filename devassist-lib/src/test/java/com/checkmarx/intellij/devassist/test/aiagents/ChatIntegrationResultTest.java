package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatIntegrationResult unit tests")
class ChatIntegrationResultTest {

    @Test
    @DisplayName("success_ReturnsResultWithSuccessTrueAndGivenMessage")
    void success_ReturnsResultWithSuccessTrueAndGivenMessage() {
        ChatIntegrationResult result = ChatIntegrationResult.success("Sent to Copilot");

        assertTrue(result.isSuccess());
        assertEquals("Sent to Copilot", result.getMessage());
    }

    @Test
    @DisplayName("notAvailable_ReturnsResultWithSuccessFalseAndGivenMessage")
    void notAvailable_ReturnsResultWithSuccessFalseAndGivenMessage() {
        ChatIntegrationResult result = ChatIntegrationResult.notAvailable("Copilot not installed");

        assertFalse(result.isSuccess());
        assertEquals("Copilot not installed", result.getMessage());
    }

    @Test
    @DisplayName("success_NullMessage_IsPreservedAsNull")
    void success_NullMessage_IsPreservedAsNull() {
        ChatIntegrationResult result = ChatIntegrationResult.success(null);

        assertTrue(result.isSuccess());
        assertNull(result.getMessage());
    }

    @Test
    @DisplayName("notAvailable_NullMessage_IsPreservedAsNull")
    void notAvailable_NullMessage_IsPreservedAsNull() {
        ChatIntegrationResult result = ChatIntegrationResult.notAvailable(null);

        assertFalse(result.isSuccess());
        assertNull(result.getMessage());
    }
}

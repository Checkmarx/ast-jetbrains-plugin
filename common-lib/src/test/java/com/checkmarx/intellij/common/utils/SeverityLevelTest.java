package com.checkmarx.intellij.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SeverityLevel - fromValue and enum coverage")
class SeverityLevelTest {

    @Test
    @DisplayName("fromValue_Malicious_ReturnsMalicious")
    void fromValue_Malicious_ReturnsMalicious() {
        assertEquals(SeverityLevel.MALICIOUS, SeverityLevel.fromValue(SeverityLevel.MALICIOUS.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Critical_ReturnsCritical")
    void fromValue_Critical_ReturnsCritical() {
        assertEquals(SeverityLevel.CRITICAL, SeverityLevel.fromValue(SeverityLevel.CRITICAL.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_High_ReturnsHigh")
    void fromValue_High_ReturnsHigh() {
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromValue(SeverityLevel.HIGH.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Medium_ReturnsMedium")
    void fromValue_Medium_ReturnsMedium() {
        assertEquals(SeverityLevel.MEDIUM, SeverityLevel.fromValue(SeverityLevel.MEDIUM.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Low_ReturnsLow")
    void fromValue_Low_ReturnsLow() {
        assertEquals(SeverityLevel.LOW, SeverityLevel.fromValue(SeverityLevel.LOW.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Unknown_ReturnsUnknown")
    void fromValue_Unknown_ReturnsUnknown() {
        assertEquals(SeverityLevel.UNKNOWN, SeverityLevel.fromValue(SeverityLevel.UNKNOWN.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Ok_ReturnsOk")
    void fromValue_Ok_ReturnsOk() {
        assertEquals(SeverityLevel.OK, SeverityLevel.fromValue(SeverityLevel.OK.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_Ignored_ReturnsIgnored")
    void fromValue_Ignored_ReturnsIgnored() {
        assertEquals(SeverityLevel.IGNORED, SeverityLevel.fromValue(SeverityLevel.IGNORED.getSeverity()));
    }

    @Test
    @DisplayName("fromValue_UnrecognizedValue_ReturnsUnknown")
    void fromValue_UnrecognizedValue_ReturnsUnknown() {
        assertEquals(SeverityLevel.UNKNOWN, SeverityLevel.fromValue("completely-unknown-level"));
    }

    @Test
    @DisplayName("fromValue_CaseInsensitive_ReturnsCorrectLevel")
    void fromValue_CaseInsensitive_ReturnsCorrectLevel() {
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromValue("HIGH"));
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromValue("high"));
        assertEquals(SeverityLevel.HIGH, SeverityLevel.fromValue("High"));
        assertEquals(SeverityLevel.CRITICAL, SeverityLevel.fromValue("CRITICAL"));
        assertEquals(SeverityLevel.MALICIOUS, SeverityLevel.fromValue("MALICIOUS"));
    }

    @Test
    @DisplayName("getSeverity_ReturnsExpectedStringValues")
    void getSeverity_ReturnsExpectedStringValues() {
        assertAll(
                () -> assertNotNull(SeverityLevel.MALICIOUS.getSeverity()),
                () -> assertNotNull(SeverityLevel.CRITICAL.getSeverity()),
                () -> assertNotNull(SeverityLevel.HIGH.getSeverity()),
                () -> assertNotNull(SeverityLevel.MEDIUM.getSeverity()),
                () -> assertNotNull(SeverityLevel.LOW.getSeverity()),
                () -> assertNotNull(SeverityLevel.UNKNOWN.getSeverity()),
                () -> assertNotNull(SeverityLevel.OK.getSeverity()),
                () -> assertNotNull(SeverityLevel.IGNORED.getSeverity())
        );
    }

    @Test
    @DisplayName("getPrecedence_ReturnsExpectedOrdering")
    void getPrecedence_ReturnsExpectedOrdering() {
        assertTrue(SeverityLevel.MALICIOUS.getPrecedence() < SeverityLevel.CRITICAL.getPrecedence(),
                "MALICIOUS should have higher precedence (lower number) than CRITICAL");
        assertTrue(SeverityLevel.CRITICAL.getPrecedence() < SeverityLevel.HIGH.getPrecedence(),
                "CRITICAL should have higher precedence than HIGH");
        assertTrue(SeverityLevel.HIGH.getPrecedence() < SeverityLevel.MEDIUM.getPrecedence(),
                "HIGH should have higher precedence than MEDIUM");
        assertTrue(SeverityLevel.MEDIUM.getPrecedence() < SeverityLevel.LOW.getPrecedence(),
                "MEDIUM should have higher precedence than LOW");
    }
}

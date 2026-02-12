package com.checkmarx.intellij.devassist.test.remediation.prompts;

import com.checkmarx.intellij.devassist.remediation.prompts.DevAssistFixPrompts;
import com.checkmarx.intellij.common.utils.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CxOneAssistFixPrompts Tests - Full Branch Coverage")
public class DevAssistFixPromptsTest {

    @Test
    @DisplayName("scaRemediationPrompt_IncludesAllDynamicValues")
    void buildSCARemediationPrompt_IncludesAllDynamicValues() {
        String pkg = "lodash";
        String version = "4.17.21";
        String manager = "npm";
        String severity = SeverityLevel.HIGH.getSeverity();

        String prompt = DevAssistFixPrompts.buildSCARemediationPrompt(pkg, version, manager, severity);

        assertAll(
                () -> assertTrue(prompt.contains(pkg + "@" + version), "Should embed package@version"),
                () -> assertTrue(prompt.contains("package manager: `" + manager + "`"), "Should embed package manager"),
                () -> assertTrue(prompt.contains("**Severity:** `" + severity + "`"), "Should embed severity"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should contain remediation summary section"),
                () -> assertTrue(prompt.contains("Remediation failed for " + pkg + "@" + version), "Should include failure path wording"),
                () -> assertTrue(prompt.contains("Remediation completed for " + pkg + "@" + version), "Should include success path wording")
        );
    }

    @Test
    @DisplayName("scaRemediationPrompt_ContainsJsonToolInvocationBlock")
    void buildSCARemediationPrompt_ContainsJsonToolInvocationBlock() {
        String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("express", "1.2.3", "maven", "Critical");
        assertTrue(prompt.contains("```json"), "Should contain json fenced block start");
        assertTrue(prompt.contains("\"packageName\": \"express\""), "JSON should include packageName");
        assertTrue(prompt.contains("\"packageVersion\": \"1.2.3\""), "JSON should include packageVersion");
        assertTrue(prompt.contains("\"packageManager\": \"maven\""), "JSON should include packageManager");
    }

    @Test
    @DisplayName("generateSecretRemediationPrompt_NullDescriptionAndSeverity_GracefulFallback")
    void generateBuildSecretRemediationPrompt_NullDescriptionAndSeverity_GracefulFallback() {
        String title = "HARD_CODED_SECRET";
        String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt(title, null, null);
        assertTrue(prompt.contains("A secret has been detected: \"" + title + "\""), "Should mention title");
        assertTrue(prompt.contains("Severity level: ``"), "Severity line should show empty backticks for null severity");
        assertTrue(prompt.contains("Likely invalid"), "Fallback assessment should be for invalid secret");
    }

    @Test
    @DisplayName("generateSecretRemediationPrompt_CriticalSeverity_AssessmentBranch")
    void generateBuildSecretRemediationPrompt_CriticalSeverity_AssessmentBranch() {
        String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt("DB_PASSWORD", "desc", SeverityLevel.CRITICAL.getSeverity());
        assertTrue(prompt.contains("Confirmed valid secret"), "Critical severity should map to confirmed valid");
    }

    @Test
    @DisplayName("generateSecretRemediationPrompt_HighSeverity_AssessmentBranch")
    void generateBuildSecretRemediationPrompt_HighSeverity_AssessmentBranch() {
        String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt("API_KEY", "desc", SeverityLevel.HIGH.getSeverity());
        assertTrue(prompt.contains("Possibly valid"), "High severity should map to possibly valid branch");
    }

    @Test
    @DisplayName("generateSecretRemediationPrompt_LowSeverity_AssessmentBranch")
    void generateBuildSecretRemediationPrompt_LowSeverity_AssessmentBranch() {
        String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt("TEST_KEY", "desc", SeverityLevel.LOW.getSeverity());
        assertTrue(prompt.contains("Likely invalid"), "Low severity should map to likely invalid branch");
    }

    @Test
    @DisplayName("generateSecretRemediationPrompt_IncludesStructuredMarkdownSections")
    void generateBuildSecretRemediationPrompt_IncludesStructuredMarkdownSections() {
        String prompt = DevAssistFixPrompts.buildSecretRemediationPrompt("SECRET_TOKEN", "description here", SeverityLevel.MALICIOUS.getSeverity());
        assertAll(
                () -> assertTrue(prompt.contains("Secret Remediation Summary"), "Should include summary header"),
                () -> assertTrue(prompt.contains("Remediation Actions Taken"), "Should list remediation actions section"),
                () -> assertTrue(prompt.contains("Next Steps"), "Should include Next Steps section"),
                () -> assertTrue(prompt.contains("Best Practices"), "Should include best practices section"),
                () -> assertTrue(prompt.contains("CONSTRAINTS"), "Should include constraints section")
        );
    }
}

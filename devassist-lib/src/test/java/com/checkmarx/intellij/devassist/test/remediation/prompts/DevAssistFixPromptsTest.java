package com.checkmarx.intellij.devassist.test.remediation.prompts;

import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.checkmarx.intellij.devassist.remediation.prompts.DevAssistFixPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

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
    @DisplayName("scaRemediationPrompt_MaliciousSeverity_ContainsIssueTypeBranch")
    void buildSCARemediationPrompt_MaliciousSeverity_ContainsIssueTypeBranch() {
        String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("evil-pkg", "1.0.0", "npm",
                SeverityLevel.MALICIOUS.getSeverity());
        assertAll(
                () -> assertTrue(prompt.contains("evil-pkg@1.0.0"), "Should embed package@version"),
                () -> assertTrue(prompt.contains("\"Malicious\""), "JSON block should show Malicious severity"),
                () -> assertTrue(prompt.contains("issueType = \"malicious\""), "Analysis section should have malicious issueType")
        );
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
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should include summary header"),
                () -> assertTrue(prompt.contains("Remediation Actions Taken"), "Should list remediation actions section"),
                () -> assertTrue(prompt.contains("Next Steps"), "Should include Next Steps section"),
                () -> assertTrue(prompt.contains("Best Practices"), "Should include best practices section"),
                () -> assertTrue(prompt.contains("CONSTRAINTS"), "Should include constraints section")
        );
    }

    @Test
    @DisplayName("buildIACRemediationPrompt_WithLineNumber_IncludesLineAndAllSections")
    void buildIACRemediationPrompt_WithLineNumber_IncludesLineAndAllSections() {
        String title = "S3 Bucket Public Access";
        String description = "Bucket allows public read access";
        String severity = SeverityLevel.HIGH.getSeverity();
        String fileType = "Terraform";
        String expectedValue = "true";
        String actualValue = "false";
        Integer lineNumber = 41;

        String prompt = DevAssistFixPrompts.buildIACRemediationPrompt(title, description, severity,
                fileType, expectedValue, actualValue, lineNumber);

        assertAll(
                () -> assertTrue(prompt.contains(title), "Should contain the issue title"),
                () -> assertTrue(prompt.contains(severity), "Should contain severity"),
                () -> assertTrue(prompt.contains(fileType), "Should contain file type"),
                () -> assertTrue(prompt.contains("42"), "Should contain line number + 1"),
                () -> assertTrue(prompt.contains(expectedValue), "Should contain expected value"),
                () -> assertTrue(prompt.contains(actualValue), "Should contain actual value"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should include remediation summary"),
                () -> assertTrue(prompt.contains("CONSTRAINTS"), "Should include constraints section")
        );
    }

    @Test
    @DisplayName("buildIACRemediationPrompt_NullLineNumber_UsesUnknownPlaceholder")
    void buildIACRemediationPrompt_NullLineNumber_UsesUnknownPlaceholder() {
        String prompt = DevAssistFixPrompts.buildIACRemediationPrompt("Issue", "Desc", "Medium",
                "CloudFormation", "enabled", "disabled", null);

        assertAll(
                () -> assertTrue(prompt.contains("[unknown]"), "Null line number should show [unknown]"),
                () -> assertTrue(prompt.contains("Issue"), "Should contain issue title"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should have remediation summary")
        );
    }

    @Test
    @DisplayName("buildContainersRemediationPrompt_IncludesImageAndSections")
    void buildContainersRemediationPrompt_IncludesImageAndSections() {
        String fileType = "Dockerfile";
        String imageName = "nginx";
        String imageTag = "1.19.0";
        String severity = SeverityLevel.CRITICAL.getSeverity();

        String prompt = DevAssistFixPrompts.buildContainersRemediationPrompt(fileType, imageName, imageTag, severity);

        assertAll(
                () -> assertTrue(prompt.contains(fileType), "Should include file type"),
                () -> assertTrue(prompt.contains(imageName + ":" + imageTag), "Should include image:tag"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains("imageRemediation"), "Should reference imageRemediation tool"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should have remediation summary"),
                () -> assertTrue(prompt.contains("Remediation completed for " + imageName + ":" + imageTag), "Should have success path"),
                () -> assertTrue(prompt.contains("Remediation failed for " + imageName + ":" + imageTag), "Should have failure path"),
                () -> assertTrue(prompt.contains("CONSTRAINTS"), "Should have constraints section")
        );
    }

    @Test
    @DisplayName("buildContainersRemediationPrompt_ContainsJsonToolBlock")
    void buildContainersRemediationPrompt_ContainsJsonToolBlock() {
        String prompt = DevAssistFixPrompts.buildContainersRemediationPrompt("docker-compose.yml", "redis", "6.2", "Medium");

        assertAll(
                () -> assertTrue(prompt.contains("```json"), "Should have json block"),
                () -> assertTrue(prompt.contains("\"fileType\": \"docker-compose.yml\""), "JSON should include fileType"),
                () -> assertTrue(prompt.contains("\"imageName\": \"redis\""), "JSON should include imageName"),
                () -> assertTrue(prompt.contains("\"imageTag\": \"6.2\""), "JSON should include imageTag")
        );
    }

    @Test
    @DisplayName("buildASCARemediationPrompt_WithLineNumber_IncludesRuleAndLine")
    void buildASCARemediationPrompt_WithLineNumber_IncludesRuleAndLine() {
        String ruleName = "SQL_INJECTION";
        String description = "Unsanitized user input used in SQL query";
        String severity = SeverityLevel.HIGH.getSeverity();
        String remediationAdvise = "Use parameterized queries";
        Integer lineNumber = 99;

        String prompt = DevAssistFixPrompts.buildASCARemediationPrompt(ruleName, description, severity,
                remediationAdvise, lineNumber);

        assertAll(
                () -> assertTrue(prompt.contains(ruleName), "Should include rule name"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains(description), "Should include description"),
                () -> assertTrue(prompt.contains(remediationAdvise), "Should include remediation advice"),
                () -> assertTrue(prompt.contains("100"), "Should have line number + 1 = 100"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should have remediation summary"),
                () -> assertTrue(prompt.contains("Remediation completed for security rule " + ruleName), "Should have success status"),
                () -> assertTrue(prompt.contains("CONSTRAINTS"), "Should have constraints section")
        );
    }

    @Test
    @DisplayName("buildASCARemediationPrompt_NullLineNumber_UsesUnknownInPrompt")
    void buildASCARemediationPrompt_NullLineNumber_UsesUnknownInPrompt() {
        String prompt = DevAssistFixPrompts.buildASCARemediationPrompt("XSS", "Cross-site scripting", "High",
                "Escape output", null);

        assertAll(
                () -> assertTrue(prompt.contains("[problematic line number]"), "Null line should show placeholder"),
                () -> assertTrue(prompt.contains("XSS"), "Should include rule name"),
                () -> assertTrue(prompt.contains("Remediation Summary"), "Should have summary")
        );
    }

    @Test
    @DisplayName("buildASCARemediationPrompt_ContainsJsonToolBlock")
    void buildASCARemediationPrompt_ContainsJsonToolBlock() {
        String prompt = DevAssistFixPrompts.buildASCARemediationPrompt("CSRF", "CSRF vulnerability", "Medium",
                "Add CSRF token", 10);

        assertAll(
                () -> assertTrue(prompt.contains("```json"), "Should contain json block"),
                () -> assertTrue(prompt.contains("\"ruleID\": \"CSRF\""), "JSON should include ruleID"),
                () -> assertTrue(prompt.contains("\"type\": \"sast\""), "JSON should include type sast")
        );
    }

    @Test
    @DisplayName("getMcpDisplayName_NullContext_ReturnsCheckmarx")
    void getMcpDisplayName_NullPluginContext_ReturnsCheckmarx() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            ctx.when(PluginContext::getInstance).thenReturn(null);

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains("Checkmarx"), "Null context should fall back to 'Checkmarx'");
        }
    }

    @Test
    @DisplayName("getMcpDisplayName_DevAssistPlugin_NonEmptyName_ReturnsPluginName")
    void getMcpDisplayName_DevAssistPlugin_NonEmptyName_ReturnsPluginName() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            PluginContext mockCtx = mock(PluginContext.class);
            ctx.when(PluginContext::getInstance).thenReturn(mockCtx);
            when(mockCtx.isDevAssistPlugin()).thenReturn(true);
            when(mockCtx.getPluginDisplayName()).thenReturn("My DevAssist Plugin");

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains("My DevAssist Plugin"),
                    "DevAssist plugin with display name should use the display name");
        }
    }

    @Test
    @DisplayName("getMcpDisplayName_DevAssistPlugin_NullName_ReturnsCxDevAssistAgentName")
    void getMcpDisplayName_DevAssistPlugin_NullName_ReturnsCxDevAssistAgentName() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            PluginContext mockCtx = mock(PluginContext.class);
            ctx.when(PluginContext::getInstance).thenReturn(mockCtx);
            when(mockCtx.isDevAssistPlugin()).thenReturn(true);
            when(mockCtx.getPluginDisplayName()).thenReturn(null);

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains(DevAssistConstants.CX_DEVASSIST_AGENT_NAME),
                    "DevAssist plugin with null display name should fall back to CX_DEVASSIST_AGENT_NAME");
        }
    }

    @Test
    @DisplayName("getMcpDisplayName_DevAssistPlugin_EmptyName_ReturnsCxDevAssistAgentName")
    void getMcpDisplayName_DevAssistPlugin_EmptyName_ReturnsCxDevAssistAgentName() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            PluginContext mockCtx = mock(PluginContext.class);
            ctx.when(PluginContext::getInstance).thenReturn(mockCtx);
            when(mockCtx.isDevAssistPlugin()).thenReturn(true);
            when(mockCtx.getPluginDisplayName()).thenReturn("");

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains(DevAssistConstants.CX_DEVASSIST_AGENT_NAME),
                    "DevAssist plugin with empty display name should fall back to CX_DEVASSIST_AGENT_NAME");
        }
    }

    @Test
    @DisplayName("getMcpDisplayName_NonDevAssistPlugin_ReturnsCxAgentName")
    void getMcpDisplayName_NonDevAssistPlugin_ReturnsCxAgentName() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            PluginContext mockCtx = mock(PluginContext.class);
            ctx.when(PluginContext::getInstance).thenReturn(mockCtx);
            when(mockCtx.isDevAssistPlugin()).thenReturn(false);

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains(DevAssistConstants.CX_AGENT_NAME),
                    "Non-devAssist plugin should use CX_AGENT_NAME");
        }
    }

    @Test
    @DisplayName("getMcpDisplayName_ExceptionThrown_ReturnsCheckmarxFallback")
    void getMcpDisplayName_ExceptionThrown_ReturnsCheckmarxFallback() {
        try (MockedStatic<PluginContext> ctx = mockStatic(PluginContext.class)) {
            ctx.when(PluginContext::getInstance).thenThrow(new RuntimeException("singleton error"));

            String prompt = DevAssistFixPrompts.buildSCARemediationPrompt("pkg", "1.0", "npm", "High");

            assertTrue(prompt.contains("Checkmarx"),
                    "Exception in getMcpDisplayName should fall back to 'Checkmarx'");
        }
    }
}

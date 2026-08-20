package com.checkmarx.intellij.devassist.test.remediation.prompts;

import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ViewDetailsPrompts Tests - Full Branch Coverage")
public class ViewDetailsPromptsTest {

    @Test
    @DisplayName("privateConstructor_ThrowsIllegalStateException")
    void privateConstructor_ThrowsIllegalStateException() {
        Constructor<?> ctor = ViewDetailsPrompts.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        try {
            ctor.newInstance();
            fail("Expected IllegalStateException to be thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertInstanceOf(IllegalStateException.class, cause, "Cause should be IllegalStateException");
            assertTrue(cause.getMessage().contains("Cannot instantiate"));
        } catch (Exception e) {
            fail("Unexpected exception type: " + e.getClass());
        }
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_MaliciousBranch_ContentAndVersionReference")
    void generateSCAExplanationPrompt_MaliciousBranch_ContentAndVersionReference() {
        String version = "9.9.9";
        String pkg = "evil-lib";
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt(pkg, version, SeverityLevel.MALICIOUS.getSeverity(), List.of());
        assertAll(
                () -> assertTrue(prompt.contains("Malicious Package Detected"), "Should include malicious header"),
                () -> assertTrue(prompt.contains("Never install or use"), "Should warn against use"),
                () -> assertTrue(prompt.contains(version), "Version should appear in guidance"),
                () -> assertFalse(prompt.contains("Known Vulnerabilities"), "Should not include vulnerability section when malicious")
        );
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_MaliciousBranch_CaseInsensitiveMatch")
    void generateSCAExplanationPrompt_MaliciousBranch_CaseInsensitiveMatch() {
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("pkg", "1.0", SeverityLevel.MALICIOUS.getSeverity().toUpperCase(), List.of());
        assertTrue(prompt.contains("Malicious Package Detected"), "Upper-case MALICIOUS should trigger malicious branch");
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_VulnerabilitiesBranch_WithNonEmptyList")
    void generateSCAExplanationPrompt_VulnerabilitiesBranch_WithNonEmptyList() {
        Vulnerability vuln1 = new Vulnerability();
        vuln1.setCve("CVE-2023-0001");
        vuln1.setSeverity("High");
        vuln1.setDescription("Remote code execution");

        Vulnerability vuln2 = new Vulnerability();
        vuln2.setCve("CVE-2023-0002");
        vuln2.setSeverity("Medium");
        vuln2.setDescription("Denial of service");

        List<Vulnerability> vulns = new ArrayList<>();
        vulns.add(vuln1);
        vulns.add(vuln2);

        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("safe-lib", "1.2.3", "vulnerable", vulns);
        assertAll(
                () -> assertTrue(prompt.contains("Known Vulnerabilities"), "Should include vulnerability section"),
                () -> assertTrue(prompt.contains("CVE-2023-0001"), "First CVE should be listed"),
                () -> assertTrue(prompt.contains("CVE-2023-0002"), "Second CVE should be listed"),
                () -> assertTrue(prompt.contains("Remote code execution"), "First description should appear"),
                () -> assertTrue(prompt.contains("Denial of service"), "Second description should appear"),
                () -> assertTrue(prompt.contains("High"), "First severity should appear"),
                () -> assertTrue(prompt.contains("Medium"), "Second severity should appear")
        );
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_VulnerabilitiesBranch_EmptyList")
    void generateSCAExplanationPrompt_VulnerabilitiesBranch_EmptyList() {
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("pkg", "0.0.1", "vulnerable", List.of());
        assertTrue(prompt.contains("No CVEs were provided"), "Empty list should trigger 'No CVEs' message");
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_VulnerabilitiesBranch_NullList")
    void generateSCAExplanationPrompt_VulnerabilitiesBranch_NullList() {
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("pkg", "0.0.2", "vulnerable", null);
        assertTrue(prompt.contains("No CVEs were provided"), "Null list should trigger 'No CVEs' message");
    }

    @Test
    @DisplayName("generateSCAExplanationPrompt_CommonSectionsAlwaysPresent")
    void generateSCAExplanationPrompt_CommonSectionsAlwaysPresent() {
        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("shared-lib", "2.3.4", "vulnerable", null);
        assertAll(
                () -> assertTrue(prompt.contains("Remediation Guidance"), "Remediation guidance section should appear"),
                () -> assertTrue(prompt.contains("Summary Section"), "Summary section should appear"),
                () -> assertTrue(prompt.contains("Output Formatting"), "Output formatting section should appear")
        );
    }

    @Test
    @DisplayName("buildSecretsExplanationPrompt_IncludesAllSections")
    void buildSecretsExplanationPrompt_IncludesAllSections() {
        String title = "AWS_SECRET_KEY";
        String description = "Hardcoded AWS secret key detected";
        String severity = SeverityLevel.CRITICAL.getSeverity();

        String prompt = ViewDetailsPrompts.buildSecretsExplanationPrompt(title, description, severity);

        assertAll(
                () -> assertTrue(prompt.contains(title), "Should include secret name"),
                () -> assertTrue(prompt.contains(description), "Should include description"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains("Secret Overview"), "Should include Secret Overview section"),
                () -> assertTrue(prompt.contains("Risk Understanding"), "Should include Risk Understanding section"),
                () -> assertTrue(prompt.contains("Why This Matters"), "Should include Why This Matters section"),
                () -> assertTrue(prompt.contains("Recommended Remediation Steps"), "Should include Remediation Steps section"),
                () -> assertTrue(prompt.contains("Next Steps Checklist"), "Should include Next Steps Checklist section"),
                () -> assertTrue(prompt.contains("Output Format Guidelines"), "Should include Output Format Guidelines")
        );
    }

    @Test
    @DisplayName("buildSecretsExplanationPrompt_ContainsMarkdownChecklist")
    void buildSecretsExplanationPrompt_ContainsMarkdownChecklist() {
        String prompt = ViewDetailsPrompts.buildSecretsExplanationPrompt("API_TOKEN", "desc", "High");

        assertAll(
                () -> assertTrue(prompt.contains("- [ ] Rotate"), "Checklist should have rotate step"),
                () -> assertTrue(prompt.contains("- [ ] Move secret"), "Checklist should have move secret step"),
                () -> assertTrue(prompt.contains("- [ ] Clean secret"), "Checklist should have clean history step")
        );
    }

    @Test
    @DisplayName("buildContainersExplanationPrompt_VulnerableBranch_IncludesVulnerabilityContent")
    void buildContainersExplanationPrompt_VulnerableBranch_IncludesVulnerabilityContent() {
        String fileType = "Dockerfile";
        String imageName = "ubuntu";
        String imageTag = "20.04";
        String severity = SeverityLevel.HIGH.getSeverity();

        String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt(fileType, imageName, imageTag, severity);

        assertAll(
                () -> assertTrue(prompt.contains(fileType), "Should include file type"),
                () -> assertTrue(prompt.contains(imageName + ":" + imageTag), "Should include image:tag"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains("Vulnerable Container Image"), "Non-malicious should show Vulnerable type"),
                () -> assertTrue(prompt.contains("Container Vulnerabilities"), "Should include vulnerabilities section"),
                () -> assertFalse(prompt.contains("Malicious Container Detected"), "Should not include malicious content"),
                () -> assertTrue(prompt.contains("Remediation Guidance"), "Should include remediation guidance"),
                () -> assertTrue(prompt.contains("Summary Section"), "Should include summary section")
        );
    }

    @Test
    @DisplayName("buildContainersExplanationPrompt_MaliciousBranch_IncludesMaliciousContent")
    void buildContainersExplanationPrompt_MaliciousBranch_IncludesMaliciousContent() {
        String imageTag = "1.0";
        String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt("Dockerfile", "evil-image",
                imageTag, SeverityLevel.MALICIOUS.getSeverity());

        assertAll(
                () -> assertTrue(prompt.contains("Malicious Container Image"), "Should show Malicious type"),
                () -> assertTrue(prompt.contains("Malicious Container Detected"), "Should include malicious content section"),
                () -> assertTrue(prompt.contains("Never deploy or use this container"), "Should warn against deployment"),
                () -> assertTrue(prompt.contains(imageTag), "Should reference the image tag"),
                () -> assertFalse(prompt.contains("Container Vulnerabilities"), "Should not include vulnerability section")
        );
    }

    @Test
    @DisplayName("buildContainersExplanationPrompt_MaliciousBranch_CaseInsensitive")
    void buildContainersExplanationPrompt_MaliciousBranch_CaseInsensitive() {
        String prompt = ViewDetailsPrompts.buildContainersExplanationPrompt("Dockerfile", "img",
                "latest", SeverityLevel.MALICIOUS.getSeverity().toUpperCase());

        assertTrue(prompt.contains("Malicious Container Detected"),
                "MALICIOUS in uppercase should trigger malicious branch");
    }

    @Test
    @DisplayName("buildIACExplanationPrompt_IncludesAllSections")
    void buildIACExplanationPrompt_IncludesAllSections() {
        String title = "S3 Bucket Public Access";
        String description = "S3 bucket allows public read access";
        String severity = SeverityLevel.HIGH.getSeverity();
        String fileType = "Terraform";
        String expectedValue = "true";
        String actualValue = "false";

        String prompt = ViewDetailsPrompts.buildIACExplanationPrompt(title, description, severity,
                fileType, expectedValue, actualValue);

        assertAll(
                () -> assertTrue(prompt.contains(title), "Should include issue title"),
                () -> assertTrue(prompt.contains(description), "Should include description"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains(fileType), "Should include file type"),
                () -> assertTrue(prompt.contains(expectedValue), "Should include expected value"),
                () -> assertTrue(prompt.contains(actualValue), "Should include actual value"),
                () -> assertTrue(prompt.contains("IaC Security Issue Overview"), "Should include overview section"),
                () -> assertTrue(prompt.contains("Infrastructure Security Issue Analysis"), "Should include analysis section"),
                () -> assertTrue(prompt.contains("Security Risks"), "Should include security risks section"),
                () -> assertTrue(prompt.contains("Remediation Guidance"), "Should include remediation guidance"),
                () -> assertTrue(prompt.contains("Summary Section"), "Should include summary section"),
                () -> assertTrue(prompt.contains("Output Formatting"), "Should include output formatting section"),
                () -> assertTrue(prompt.contains("Preventative Measures"), "Should include preventative measures")
        );
    }

    @Test
    @DisplayName("buildIACExplanationPrompt_FileTypeReferencedInRemediation")
    void buildIACExplanationPrompt_FileTypeReferencedInRemediation() {
        String fileType = "CloudFormation";
        String prompt = ViewDetailsPrompts.buildIACExplanationPrompt("Issue", "Desc", "High",
                fileType, "enabled", "disabled");

        assertTrue(prompt.contains("For " + fileType + " configurations:"),
                "Remediation guidance should reference the file type");
    }

    @Test
    @DisplayName("buildASCAExplanationPrompt_IncludesAllSections")
    void buildASCAExplanationPrompt_IncludesAllSections() {
        String ruleName = "SQL_INJECTION";
        String description = "Unsanitized user input in SQL query";
        String severity = SeverityLevel.HIGH.getSeverity();

        String prompt = ViewDetailsPrompts.buildASCAExplanationPrompt(ruleName, description, severity);

        assertAll(
                () -> assertTrue(prompt.contains(ruleName), "Should include rule name"),
                () -> assertTrue(prompt.contains(description), "Should include description twice (header and body)"),
                () -> assertTrue(prompt.contains(severity), "Should include severity"),
                () -> assertTrue(prompt.contains("Security Issue Overview"), "Should include overview section"),
                () -> assertTrue(prompt.contains("Detailed Explanation"), "Should include detailed explanation section"),
                () -> assertTrue(prompt.contains("Why This Matters"), "Should include Why This Matters section"),
                () -> assertTrue(prompt.contains("Security Best Practices"), "Should include best practices section"),
                () -> assertTrue(prompt.contains("Additional Resources"), "Should include additional resources section"),
                () -> assertTrue(prompt.contains("Output Format Guidelines"), "Should include output format guidelines")
        );
    }

    @Test
    @DisplayName("buildASCAExplanationPrompt_ContainsRuleNameInHeader")
    void buildASCAExplanationPrompt_ContainsRuleNameInHeader() {
        String ruleName = "XSS_VULNERABILITY";
        String prompt = ViewDetailsPrompts.buildASCAExplanationPrompt(ruleName, "desc", "Medium");

        assertTrue(prompt.contains("**Rule:** `" + ruleName + "`"),
                "Rule name should appear in the rule header");
    }
}

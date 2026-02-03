package com.checkmarx.intellij.unit.devassist.remediation.prompts;

import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.util.SeverityLevel;
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

//    @Test
//    @DisplayName("generateSCAExplanationPrompt_VulnerabilitiesBranch_WithList")
//    void generateSCAExplanationPrompt_VulnerabilitiesBranch_WithList() {
//        List<Vulnerability> vulns = new ArrayList<>();
//        vulns.add(new Vulnerability("vuln-id-1", "CVE-123", "desc1", "High", "adv1", "2.0.0", null, null, "CVE-123 Title"));
//        vulns.add(new Vulnerability("vuln-id-2", "CVE-456", "desc2", "Medium", "adv2", "3.0.0", null, null, "CVE-456 Title"));
//        String prompt = ViewDetailsPrompts.buildSCAExplanationPrompt("safe-lib", "1.2.3", "vulnerable", vulns);
//        assertAll(
//                () -> assertTrue(prompt.contains("Known Vulnerabilities"), "Should include vulnerability section"),
//                () -> assertTrue(prompt.contains("CVE-123"), "First CVE should be listed"),
//                () -> assertTrue(prompt.contains("CVE-456"), "Second CVE should be listed"),
//                () -> assertTrue(prompt.contains("desc1"), "First description should appear"),
//                () -> assertTrue(prompt.contains("desc2"), "Second description should appear")
//        );
//    }

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
}

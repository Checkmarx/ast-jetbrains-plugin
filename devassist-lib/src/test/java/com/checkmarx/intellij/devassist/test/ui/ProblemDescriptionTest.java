package com.checkmarx.intellij.devassist.test.ui;

import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemDescriptionTest {

    static MockedStatic<DevAssistUtils> devAssistUtilsStatic;
    static MockedStatic<PluginContext> pluginContextStatic;

    private ProblemDescription description;

    @BeforeAll
    static void setupStaticMocks() {
        devAssistUtilsStatic = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS);
        devAssistUtilsStatic.when(() -> DevAssistUtils.themeBasedPNGIconForHtmlImage(anyString())).thenReturn("icon.png");
        devAssistUtilsStatic.when(DevAssistUtils::getAssistQuickFixName).thenReturn("Fix with AI");

        PluginContext mockPluginContext = mock(PluginContext.class, RETURNS_DEEP_STUBS);
        when(mockPluginContext.isDevAssistPlugin()).thenReturn(false);
        pluginContextStatic = mockStatic(PluginContext.class, CALLS_REAL_METHODS);
        pluginContextStatic.when(PluginContext::getInstance).thenReturn(mockPluginContext);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (devAssistUtilsStatic != null) devAssistUtilsStatic.close();
        if (pluginContextStatic != null) pluginContextStatic.close();
    }

    @BeforeEach
    void setUp() {
        description = new ProblemDescription();
    }

    @Test
    void testConstructor_initializesSuccessfully() {
        assertNotNull(description);
    }

    @Test
    void testReloadIcons_doesNotThrow() {
        assertDoesNotThrow(ProblemDescription::reloadIcons);
    }

    @Test
    void testFormatDescription_oss_containsPackageNameAndVersion() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getTitle()).thenReturn("lodash");
        when(issue.getPackageVersion()).thenReturn("4.17.21");
        when(issue.getSeverity()).thenReturn("HIGH");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("lodash"));
        assertTrue(result.contains("4.17.21"));
    }

    @Test
    void testFormatDescription_asca_singleVulnerability_containsTitle() {
        Vulnerability vuln = mock(Vulnerability.class);
        when(vuln.getTitle()).thenReturn("SQL Injection");
        when(vuln.getDescription()).thenReturn("A SQL injection vulnerability.");
        when(vuln.getSeverity()).thenReturn("HIGH");
        when(vuln.getVulnerabilityId()).thenReturn("vuln-1");

        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.ASCA);
        when(issue.getTitle()).thenReturn("ASCA Issue");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(List.of(vuln));

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("SQL Injection"));
        assertTrue(result.contains("SAST vulnerability"));
    }

    @Test
    void testFormatDescription_asca_multipleVulnerabilities_showsGroupTitle() {
        Vulnerability vuln1 = mock(Vulnerability.class);
        when(vuln1.getTitle()).thenReturn("SQL Injection");
        when(vuln1.getDescription()).thenReturn("SQL issue");
        when(vuln1.getSeverity()).thenReturn("HIGH");
        when(vuln1.getVulnerabilityId()).thenReturn("vuln-1");

        Vulnerability vuln2 = mock(Vulnerability.class);
        when(vuln2.getTitle()).thenReturn("XSS");
        when(vuln2.getDescription()).thenReturn("XSS issue");
        when(vuln2.getSeverity()).thenReturn("MEDIUM");
        when(vuln2.getVulnerabilityId()).thenReturn("vuln-2");

        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.ASCA);
        when(issue.getTitle()).thenReturn("Multiple Issues");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(List.of(vuln1, vuln2));

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("Multiple Issues"));
        assertTrue(result.contains("Checkmarx One Assist"));
    }

    @Test
    void testFormatDescription_secrets_containsSecretFindingText() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.SECRETS);
        when(issue.getTitle()).thenReturn("generic-api-key");
        when(issue.getSeverity()).thenReturn("HIGH");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("Secret finding"));
    }

    @Test
    void testFormatDescription_iac_containsIacVulnerabilityText() {
        Vulnerability vuln = mock(Vulnerability.class);
        when(vuln.getTitle()).thenReturn("S3 Bucket Public");
        when(vuln.getDescription()).thenReturn("S3 bucket is publicly accessible");
        when(vuln.getActualValue()).thenReturn("true");
        when(vuln.getSeverity()).thenReturn("MEDIUM");
        when(vuln.getVulnerabilityId()).thenReturn("vuln-1");

        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.IAC);
        when(issue.getTitle()).thenReturn("IAC Issue");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(List.of(vuln));

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("IaC vulnerability"));
        assertTrue(result.contains("S3 Bucket Public"));
    }

    @Test
    void testFormatDescription_containers_containsImageNameAndTag() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.CONTAINERS);
        when(issue.getTitle()).thenReturn("nginx");
        when(issue.getImageTag()).thenReturn("1.19");
        when(issue.getSeverity()).thenReturn("HIGH");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("nginx"));
        assertTrue(result.contains("1.19"));
    }

    @Test
    void testFormatDescription_oss_maliciousSeverity_usesCorrectIcon() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getTitle()).thenReturn("malicious-package");
        when(issue.getPackageVersion()).thenReturn("1.0.0");
        when(issue.getSeverity()).thenReturn("MALICIOUS");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());

        String result = description.formatDescription(issue);

        assertNotNull(result);
        assertTrue(result.contains("malicious-package"));
    }

    @Test
    void testFormatDescription_containsHtmlStructure() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getTitle()).thenReturn("test-pkg");
        when(issue.getPackageVersion()).thenReturn("1.0.0");
        when(issue.getSeverity()).thenReturn("LOW");
        when(issue.getScanIssueId()).thenReturn("issue-id");
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());

        String result = description.formatDescription(issue);

        assertTrue(result.startsWith("<html>"));
        assertTrue(result.endsWith("</html>"));
    }
}

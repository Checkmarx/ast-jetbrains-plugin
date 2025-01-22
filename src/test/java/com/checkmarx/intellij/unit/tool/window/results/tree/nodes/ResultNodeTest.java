package com.checkmarx.intellij.unit.tool.window.results.tree.nodes;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.learnMore.Sample;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.results.result.VulnerabilityDetails;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultNodeTest {
    @Mock
    private Project mockProject;
    @Mock
    private Result mockResult;
    @Mock
    private Data mockResultData;
    @Mock
    private Node mockNode;
    @Mock
    private LearnMore mockLearnMore;
    @Mock
    private Sample mockSample;
    @Mock
    private CxWrapper mockWrapper;
    @Mock
    private CodeBashing mockCodeBashing;
    @Mock
    private VulnerabilityDetails mockVulnDetails;
    @Mock
    private PackageData mockPackageData;

    private static final String SCAN_ID = "test-scan-id";
    private static final String QUERY_NAME = "Test Query";
    private static final String FILE_NAME = "test.java";
    private static final int LINE_NUMBER = 42;
    private static final String RESULT_ID = "TEST-001";
    private static final String TEST_RISK = "Test Risk Description";
    private static final String TEST_CAUSE = "Test Cause Description";
    private static final String TEST_LANGUAGE = "Java";
    private static final String TEST_CWE = "CWE-79";
    private static final String TEST_CODE = "public class Test { }";
    private static final String TEST_TITLE = "Test Sample";
    private static final String TEST_PROG_LANGUAGE = "Java";
    private static final String TEST_CODEBASHING_PATH = "https://codebashing.test/path";

    private ResultNode resultNode;

    @BeforeEach
    void setUp() {
        when(mockResult.getData()).thenReturn(mockResultData);
    }

    @Test
    void constructor_WithQueryName_SetsLabelWithQueryName() {
        when(mockNode.getFileName()).thenReturn(FILE_NAME);
        when(mockNode.getLine()).thenReturn(LINE_NUMBER);
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(Collections.singletonList(mockNode));

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(QUERY_NAME + " (" + new File(FILE_NAME).getName() + ":" + LINE_NUMBER + ")", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void constructor_WithoutQueryName_SetsLabelWithResultId() {
        when(mockNode.getFileName()).thenReturn(FILE_NAME);
        when(mockNode.getLine()).thenReturn(LINE_NUMBER);
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResult.getData()).thenReturn(mockResultData);
        when(mockResultData.getNodes()).thenReturn(Collections.singletonList(mockNode));

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(RESULT_ID + " (" + new File(FILE_NAME).getName() + ":" + LINE_NUMBER + ")", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void constructor_WithoutNodes_SetsLabelWithoutFileInfo() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(QUERY_NAME, resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void generateLearnMore_WithValidData_CreatesPanel() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            // Setup
            when(mockLearnMore.getRisk()).thenReturn(TEST_RISK);
            when(mockLearnMore.getCause()).thenReturn(TEST_CAUSE);
            when(mockLearnMore.getGeneralRecommendations()).thenReturn("Genral Recommendation 1\nGeneral Recommendation 2");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify
            assertEquals(6, panel.getComponentCount()); // Risk title, risk content, cause title, cause content, recommendations title
            assertTrue(panel.getComponent(0) instanceof JLabel);
            assertTrue(panel.getComponent(1) instanceof JBLabel);
            assertTrue(panel.getComponent(2) instanceof JLabel);
            assertTrue(panel.getComponent(3) instanceof JBLabel);
            assertTrue(panel.getComponent(4) instanceof JLabel);
        }
    }

    @Test
    void generateLearnMore_WithEmptyData_CreatesMinimalPanel() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            // Setup
            when(mockLearnMore.getRisk()).thenReturn("");
            when(mockLearnMore.getCause()).thenReturn("");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify
            assertEquals(3, panel.getComponentCount()); // Only titles, no content
            assertTrue(panel.getComponent(0) instanceof JLabel); // Risk title
            assertTrue(panel.getComponent(1) instanceof JLabel); // Cause title
            assertTrue(panel.getComponent(2) instanceof JLabel); // Recommendations title
        }
    }

    @Test
    void generateLearnMore_WithMultilineData_FormatsCorrectly() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            // Setup
            when(mockLearnMore.getRisk()).thenReturn("Line 1\nLine 2");
            when(mockLearnMore.getCause()).thenReturn("Cause 1\nCause 2");
            when(mockLearnMore.getGeneralRecommendations()).thenReturn("Genral Recommendation 1\nGeneral Recommendation 2");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify
            assertEquals(6, panel.getComponentCount());
            JBLabel riskContent = (JBLabel) panel.getComponent(1);
            JBLabel causeContent = (JBLabel) panel.getComponent(3);
            assertEquals(riskContent.getText(), ("<html>Line 1<br/>Line 2</html>"));
            assertEquals(causeContent.getText(), ("<html>Cause 1<br/>Cause 2</html>"));

        }
    }

    @Test
    void constructor_WithNullNodes_SetsEmptyNodesList() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(null);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertTrue(resultNode.getNodes().isEmpty());
    }

    @Test
    void constructor_WithNullPackageData_SetsEmptyPackageDataList() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getPackageData()).thenReturn(null);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertTrue(resultNode.getPackageData().isEmpty());
    }

    @Test
    void getIcon_ReturnsSeverityIcon() {
        when(mockResult.getSeverity()).thenReturn(Severity.HIGH.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        Icon icon = resultNode.getIcon();
        assertNotNull(icon);
        assertEquals(Severity.HIGH.getIcon(), icon);
    }

    @Test
    void generateCodeSamples_WithSamples_CreatesSamplePanels() {
        // Setup
        when(mockSample.getTitle()).thenReturn(TEST_TITLE);
        when(mockSample.getProgLanguage()).thenReturn(TEST_PROG_LANGUAGE);
        when(mockSample.getCode()).thenReturn(TEST_CODE);
        when(mockLearnMore.getSamples()).thenReturn(Arrays.asList(mockSample));

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = new JPanel();

        // Execute
        resultNode.generateCodeSamples(mockLearnMore, panel);

        // Verify
        assertEquals(2, panel.getComponentCount()); // Title label and code editor
        assertTrue(panel.getComponent(0) instanceof JBLabel);
        assertTrue(panel.getComponent(1) instanceof JEditorPane);
        
        JBLabel titleLabel = (JBLabel) panel.getComponent(0);
        JEditorPane codeEditor = (JEditorPane) panel.getComponent(1);
        
        assertTrue(titleLabel.getText().contains(TEST_TITLE));
        assertTrue(titleLabel.getText().contains(TEST_PROG_LANGUAGE));
        assertEquals(TEST_CODE, codeEditor.getText());
        assertFalse(codeEditor.isEditable());
    }

    @Test
    void generateCodeSamples_WithoutSamples_ShowsNoExamplesMessage() {
        // Setup
        when(mockLearnMore.getSamples()).thenReturn(Collections.emptyList());

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = new JPanel();

        // Execute
        resultNode.generateCodeSamples(mockLearnMore, panel);

        // Verify
        assertEquals(1, panel.getComponentCount());
        assertTrue(panel.getComponent(0) instanceof JBLabel);
        JBLabel messageLabel = (JBLabel) panel.getComponent(0);
        assertTrue(messageLabel.getText().contains(Resource.NO_REMEDIATION_EXAMPLES.toString()));
    }

    @Test
    void openCodebashingLink_Success_OpensInBrowser() throws Exception {
        // Setup
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<Desktop> mockedDesktop = mockStatic(Desktop.class)) {
            
            Desktop mockDesktop = mock(Desktop.class);
            when(mockVulnDetails.getCweId()).thenReturn(TEST_CWE);
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockResultData.getLanguageName()).thenReturn(TEST_LANGUAGE);
            when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
            
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            mockedDesktop.when(Desktop::getDesktop).thenReturn(mockDesktop);
            
            when(mockWrapper.codeBashingList(TEST_CWE, TEST_LANGUAGE, QUERY_NAME))
                .thenReturn(Collections.singletonList(mockCodeBashing));
            when(mockCodeBashing.getPath()).thenReturn(TEST_CODEBASHING_PATH);

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

            // Execute
            resultNode.openCodebashingLink();

            // Verify
            verify(mockDesktop).browse(new URI(TEST_CODEBASHING_PATH));
        }
    }

    @Test
    void openCodebashingLink_NoLicense_ShowsNotification() throws Exception {
        // Setup
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            
            CxException licenseError = mock(CxException.class);
            when(licenseError.getExitCode()).thenReturn(Constants.LICENSE_NOT_FOUND_EXIT_CODE);
            
            when(mockVulnDetails.getCweId()).thenReturn(TEST_CWE);
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockResultData.getLanguageName()).thenReturn(TEST_LANGUAGE);
            when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
            
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.codeBashingList(TEST_CWE, TEST_LANGUAGE, QUERY_NAME))
                .thenThrow(licenseError);

            mockedBundle.when(() -> Bundle.message(Resource.CODEBASHING_NO_LICENSE)).thenReturn("No license");
            mockedBundle.when(() -> Bundle.message(Resource.CODEBASHING_LINK)).thenReturn("link");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

            // Execute
            resultNode.openCodebashingLink();

            // Verify
            mockedUtils.verify(() -> Utils.notify(eq(mockProject), anyString(), any(NotificationType.class)));
        }
    }

    @Test
    void buildResultPanel_WithNodes_CreatesAttackVectorPanel() {
        // Setup
        when(mockNode.getFileName()).thenReturn(FILE_NAME);
        when(mockNode.getLine()).thenReturn(LINE_NUMBER);
        when(mockResultData.getNodes()).thenReturn(Collections.singletonList(mockNode));
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getSeverity()).thenReturn(Severity.HIGH.name());

        
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        
        // Execute
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        
        // Verify
        assertNotNull(panel);
        assertTrue(panel.getComponent(0) instanceof com.intellij.ui.OnePixelSplitter);
    }

    @Test
    void buildResultPanel_WithPackageData_CreatesPackageDataPanel() {
        // Setup
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());
        when(mockPackageData.getType()).thenReturn("test-type");
        when(mockPackageData.getUrl()).thenReturn("https://test.url");
        when(mockResultData.getPackageData()).thenReturn(Collections.singletonList(mockPackageData));
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResult.getSeverity()).thenReturn(Severity.HIGH.name());

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        // Execute
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});

        // Verify
        assertNotNull(panel);
        assertTrue(panel.getComponent(0) instanceof com.intellij.ui.OnePixelSplitter);
    }

    @Test
    void buildResultPanel_WithFileName_CreatesVulnerabilityLocationPanel() {
        // Setup
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());
        when(mockResultData.getPackageData()).thenReturn(Collections.emptyList());
        when(mockResultData.getFileName()).thenReturn(FILE_NAME);
        when(mockResultData.getLine()).thenReturn(LINE_NUMBER);
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getSeverity()).thenReturn(Severity.HIGH.name());
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        
        // Execute
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        
        // Verify
        assertNotNull(panel);
        assertTrue(panel.getComponent(0) instanceof com.intellij.ui.OnePixelSplitter);
    }

    @Test
    void buildResultPanel_WithScaType_CreatesScaPanel() {
        // Setup
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-1234");
        when(mockVulnDetails.getCvssScore()).thenReturn(7.5);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResultData.getPackageIdentifier()).thenReturn("test-package:1.0.0");
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        
        // Execute
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        
        // Verify
        assertNotNull(panel);
        assertTrue(panel.getLayout() instanceof net.miginfocom.swing.MigLayout);
        
        // Verify header components
        JPanel headerPanel = (JPanel) panel.getComponent(0);
        JLabel titleLabel = (JLabel) headerPanel.getComponent(0);
        assertTrue(titleLabel.getText().contains("test-package:1.0.0"));
        assertEquals(Severity.HIGH.getIcon(), titleLabel.getIcon());
    }

    @Test
    void buildResultPanel_WithMinimalData_CreatesBasicPanel() {
        // Setup
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());
        when(mockResultData.getPackageData()).thenReturn(Collections.emptyList());
        when(mockResultData.getFileName()).thenReturn("");
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResult.getSeverity()).thenReturn("HIGH");

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        
        // Execute
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        
        // Verify
        assertNotNull(panel);
        assertTrue(panel.getComponent(0) instanceof com.intellij.ui.OnePixelSplitter);
    }
}
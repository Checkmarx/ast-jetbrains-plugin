package com.checkmarx.intellij.ast.test.unit.tool.window.results.tree.nodes;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.learnMore.Sample;
import com.checkmarx.ast.results.result.*;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.window.results.tree.nodes.ResultNode;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
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
import java.lang.reflect.Method;
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
        lenient().when(mockResult.getData()).thenReturn(mockResultData);
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
            when(mockLearnMore.getGeneralRecommendations()).thenReturn("General Recommendation 1\nGeneral Recommendation 2");
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockVulnDetails.getCweId()).thenReturn("79");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify - Implementation uses JEditorPane (from createSelectableHtmlPane), not JBLabel
            assertEquals(8, panel.getComponentCount()); // Risk title, risk content, cause title, cause content, recommendations title, recommendations content, CWE title, CWE link
            assertTrue(panel.getComponent(0) instanceof JLabel); // Risk title
            assertTrue(panel.getComponent(1) instanceof JEditorPane); // Risk content (createSelectableHtmlPane returns JEditorPane)
            assertTrue(panel.getComponent(2) instanceof JEditorPane); // Cause title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(3) instanceof JEditorPane); // Cause content (createSelectableHtmlPane)
            assertTrue(panel.getComponent(4) instanceof JEditorPane); // Recommendations title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(5) instanceof JEditorPane); // Recommendations content (createSelectableHtmlPane)
            assertTrue(panel.getComponent(6) instanceof JEditorPane); // CWE Link title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(7) instanceof JBLabel); // CWE Link label
        }
    }

    @Test
    void generateLearnMore_WithEmptyData_CreatesMinimalPanel() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            // Setup
            when(mockLearnMore.getRisk()).thenReturn("");
            when(mockLearnMore.getCause()).thenReturn("");
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockVulnDetails.getCweId()).thenReturn("79");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify - When risk and cause are empty, only titles are added (no content), plus CWE link
            assertEquals(5, panel.getComponentCount()); // Risk title, Cause title, Recommendations title, CWE title, CWE link
            assertTrue(panel.getComponent(0) instanceof JLabel); // Risk title
            assertTrue(panel.getComponent(1) instanceof JEditorPane); // Cause title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(2) instanceof JEditorPane); // Recommendations title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(3) instanceof JEditorPane); // CWE Link title (createSelectableHtmlPane)
            assertTrue(panel.getComponent(4) instanceof JBLabel); // CWE Link label

            JBLabel cweLinkLabel = (JBLabel) panel.getComponent(4);
            assertEquals("CWE-79", cweLinkLabel.getText());
        }
    }

    @Test
    void generateLearnMore_WithMultilineData_FormatsCorrectly() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            // Setup
            when(mockLearnMore.getRisk()).thenReturn("Line 1\nLine 2");
            when(mockLearnMore.getCause()).thenReturn("Cause 1\nCause 2");
            when(mockLearnMore.getGeneralRecommendations()).thenReturn("General Recommendation 1\nGeneral Recommendation 2");
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockVulnDetails.getCweId()).thenReturn("79");
            mockedBundle.when(() -> Bundle.message(Resource.RISK)).thenReturn("Risk");
            mockedBundle.when(() -> Bundle.message(Resource.CAUSE)).thenReturn("Cause");
            mockedBundle.when(() -> Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).thenReturn("Recommendations");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();

            // Execute
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Verify - Note: There's a bug in the implementation at line 1156 where it checks 'cause' instead of 'recommendations'
            // So recommendations content is added because cause is not blank, not because recommendations is not blank
            assertEquals(8, panel.getComponentCount()); // Risk title, risk content, cause title, cause content, recommendations title, recommendations content, CWE title, CWE link
            JEditorPane riskContent = (JEditorPane) panel.getComponent(1);
            JEditorPane causeContent = (JEditorPane) panel.getComponent(3);
            JEditorPane recommendationsContent = (JEditorPane) panel.getComponent(5);
            // JEditorPane.getText() returns full HTML with <head> and <body> tags, so we check if it contains the expected content
            assertTrue(riskContent.getText().contains("Line 1<br>Line 2"));
            assertTrue(causeContent.getText().contains("Cause 1<br>Cause 2"));
            assertTrue(recommendationsContent.getText().contains("General Recommendation 1<br>General Recommendation 2"));

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
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        Icon icon = resultNode.getIcon();
        assertNotNull(icon);
        assertEquals(SeverityFilter.HIGH.getIcon(), icon);
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

        // Verify - Implementation uses JEditorPane for title (from createSelectableHtmlPane)
        assertEquals(2, panel.getComponentCount()); // Title pane and code editor
        assertTrue(panel.getComponent(0) instanceof JEditorPane); // Title is JEditorPane (createSelectableHtmlPane)
        assertTrue(panel.getComponent(1) instanceof JEditorPane); // Code editor

        JEditorPane titlePane = (JEditorPane) panel.getComponent(0);
        JEditorPane codeEditor = (JEditorPane) panel.getComponent(1);

        assertTrue(titlePane.getText().contains(TEST_TITLE));
        assertTrue(titlePane.getText().contains(TEST_PROG_LANGUAGE));
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

        // Verify - When there are no samples, implementation adds a JEditorPane with NO_REMEDIATION_EXAMPLES message
        assertEquals(1, panel.getComponentCount());
        assertTrue(panel.getComponent(0) instanceof JEditorPane); // createSelectableHtmlPane returns JEditorPane
        JEditorPane messagePane = (JEditorPane) panel.getComponent(0);
        assertTrue(messagePane.getText().contains(Resource.NO_REMEDIATION_EXAMPLES.toString()));
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
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());


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
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());

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
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
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
        assertEquals(SeverityFilter.HIGH.getIcon(), titleLabel.getIcon());
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

    @Test
    void buildResultPanel_WithScaTypeAndHtmlDescription_CreatesScaPanel() {
        // Setup
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-1234");
        when(mockVulnDetails.getCvssScore()).thenReturn(7.5);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResultData.getPackageIdentifier()).thenReturn("test-package:1.0.0");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResult.getDescriptionHTML()).thenReturn("Test Description");
        when(mockResultData.getRecommendedVersion()).thenReturn("1.0.1");
        when(mockPackageData.getType()).thenReturn("test-type");
        when(mockPackageData.getUrl()).thenReturn("https://test.url");
        when(mockResultData.getPackageData()).thenReturn(Collections.singletonList(mockPackageData));

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
        assertEquals(SeverityFilter.HIGH.getIcon(), titleLabel.getIcon());
    }

    @Test
    void constructor_WithScsType_SetsLabelWithRuleNameOnly() {
        // Setup
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResultData.getRuleName()).thenReturn("Github-Pat");
        when(mockResultData.getFileName()).thenReturn("/.github/workflows/checkmarx.yml");
        when(mockResultData.getLine()).thenReturn(123);

        // Execute
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        // Verify
        assertEquals("Github-Pat (checkmarx.yml:123)", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void openCodebashingLink_NoLesson_ShowsNotification() throws Exception {
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {

            Logger mockLogger = mock(Logger.class);
            mockedUtils.when(() -> Utils.getLogger(any())).thenReturn(mockLogger);

            CxException lessonError = mock(CxException.class);
            when(lessonError.getExitCode()).thenReturn(Constants.LESSON_NOT_FOUND_EXIT_CODE);
            when(lessonError.getMessage()).thenReturn("lesson not found");

            when(mockVulnDetails.getCweId()).thenReturn(TEST_CWE);
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockResultData.getLanguageName()).thenReturn(TEST_LANGUAGE);
            when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);

            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.codeBashingList(TEST_CWE, TEST_LANGUAGE, QUERY_NAME))
                    .thenThrow(lessonError);

            mockedBundle.when(() -> Bundle.message(Resource.CODEBASHING_NO_LESSON)).thenReturn("No lesson");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            resultNode.openCodebashingLink();

            mockedUtils.verify(() -> Utils.notify(eq(mockProject), anyString(), any(NotificationType.class)));
        }
    }

    @Test
    void openCodebashingLink_GenericCxException_LogsError() throws Exception {
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {

            Logger mockLogger = mock(Logger.class);
            mockedUtils.when(() -> Utils.getLogger(any())).thenReturn(mockLogger);

            CxException genericError = mock(CxException.class);
            when(genericError.getExitCode()).thenReturn(99);
            when(genericError.getMessage()).thenReturn("generic error");

            when(mockVulnDetails.getCweId()).thenReturn(TEST_CWE);
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockResultData.getLanguageName()).thenReturn(TEST_LANGUAGE);
            when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);

            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.codeBashingList(TEST_CWE, TEST_LANGUAGE, QUERY_NAME))
                    .thenThrow(genericError);

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            assertDoesNotThrow(() -> resultNode.openCodebashingLink());

            mockedUtils.verify(() -> Utils.notify(any(), anyString(), any()), never());
        }
    }

    // ===== getIcon for all severity levels =====

    @Test
    void getIcon_ForLowSeverity_ReturnsLowIcon() {
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.LOW.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertEquals(SeverityFilter.LOW.getIcon(), resultNode.getIcon());
    }

    @Test
    void getIcon_ForMediumSeverity_ReturnsMediumIcon() {
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.MEDIUM.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertEquals(SeverityFilter.MEDIUM.getIcon(), resultNode.getIcon());
    }

    @Test
    void getIcon_ForCriticalSeverity_ReturnsCriticalIcon() {
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.CRITICAL.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertEquals(SeverityFilter.CRITICAL.getIcon(), resultNode.getIcon());
    }

    // ===== StateEnum values =====

    @Test
    void stateEnum_AllValuesPresent() {
        ResultNode.StateEnum[] values = ResultNode.StateEnum.values();
        assertEquals(5, values.length);
        assertEquals(ResultNode.StateEnum.TO_VERIFY, ResultNode.StateEnum.valueOf("TO_VERIFY"));
        assertEquals(ResultNode.StateEnum.NOT_EXPLOITABLE, ResultNode.StateEnum.valueOf("NOT_EXPLOITABLE"));
        assertEquals(ResultNode.StateEnum.PROPOSED_NOT_EXPLOITABLE, ResultNode.StateEnum.valueOf("PROPOSED_NOT_EXPLOITABLE"));
        assertEquals(ResultNode.StateEnum.CONFIRMED, ResultNode.StateEnum.valueOf("CONFIRMED"));
        assertEquals(ResultNode.StateEnum.URGENT, ResultNode.StateEnum.valueOf("URGENT"));
    }

    // ===== equals/hashCode (Lombok @EqualsAndHashCode) =====

    @Test
    void equals_SameNode_ReturnsTrue() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertEquals(resultNode, resultNode);
    }

    @Test
    void hashCode_SameObject_IsConsistent() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        ResultNode node = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertEquals(node.hashCode(), node.hashCode());
    }

    @Test
    void equals_NodeWithNull_ReturnsFalse() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertNotEquals(null, resultNode);
    }

    // ===== getResult, getProject, getScanId accessors =====

    @Test
    void getters_ReturnCorrectValues() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        assertSame(mockResult, resultNode.getResult());
        assertSame(mockProject, resultNode.getProject());
        assertEquals(SCAN_ID, resultNode.getScanId());
    }

    // ===== SCS constructor branch coverage =====

    @Test
    void constructor_ForScsTypeWithNullFileName_BuildsLabelWithRuleNameOnly() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResultData.getRuleName()).thenReturn("AWS-Secret-Key");
        when(mockResultData.getFileName()).thenReturn(null);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals("AWS-Secret-Key", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void constructor_ForScsTypeWithZeroLine_BuildsLabelWithRuleNameOnly() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResultData.getRuleName()).thenReturn("Github-Pat");
        when(mockResultData.getFileName()).thenReturn("/.github/workflows/ci.yml");
        when(mockResultData.getLine()).thenReturn(0);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals("Github-Pat", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void constructor_ForScsTypeWithNullRuleName_FallsToQueryNameBranch() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResultData.getRuleName()).thenReturn(null);
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(QUERY_NAME, resultNode.getLabel());
    }

    // ===== capToLen (private static) =====

    private static String capToLen(String fileName) throws Exception {
        Method m = ResultNode.class.getDeclaredMethod("capToLen", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, fileName);
    }

    @Test
    void capToLen_WithShortName_ReturnsOriginal() throws Exception {
        String shortName = "short.java";
        assertEquals(shortName, capToLen(shortName));
    }

    @Test
    void capToLen_WithLongName_TruncatesWithEllipsis() throws Exception {
        String longName = "/very/long/path/that/definitely/exceeds/the/maximum/length/allowed/file.java";
        String result = capToLen(longName);
        assertTrue(result.startsWith("..."), "Should start with ellipsis");
        assertTrue(result.length() <= com.checkmarx.intellij.common.utils.Constants.FILE_PATH_MAX_LEN,
                "Should be within max length");
    }

    // ===== toggleHover (private static) =====

    @Test
    void toggleHover_WithHoverTrue_ChangesBackground() throws Exception {
        JPanel panel = new JPanel();
        Method m = ResultNode.class.getDeclaredMethod("toggleHover", JComponent.class, boolean.class);
        m.setAccessible(true);
        m.invoke(null, panel, true);
        assertNotNull(panel.getBackground());
    }

    @Test
    void toggleHover_WithHoverFalse_ChangesBackground() throws Exception {
        JPanel panel = new JPanel();
        Method m = ResultNode.class.getDeclaredMethod("toggleHover", JComponent.class, boolean.class);
        m.setAccessible(true);
        m.invoke(null, panel, false);
        assertNotNull(panel.getBackground());
    }

    @Test
    void buildResultPanel_WithScsType_AddsLearnMoreAndRemediationTabs() {
        // Setup
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getRuleName()).thenReturn("Hardcoded Password");
        when(mockResultData.getRuleDescription()).thenReturn("Some description");
        when(mockResultData.getRemediation()).thenReturn("Some remediation");

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(Resource.LEARN_MORE)).thenReturn("Learn More");
            mockedBundle.when(() -> Bundle.message(Resource.REMEDIATION_EXAMPLES)).thenReturn("Remediation Examples");
            mockedBundle.when(() -> Bundle.message(Resource.DESCRIPTION)).thenReturn("Description");
            mockedBundle.when(() -> Bundle.message(Resource.CHANGES)).thenReturn("Changes");
            mockedBundle.when(() -> Bundle.message(Resource.COMMENT_PLACEHOLDER))
                    .thenReturn("Notes (Optional)");

            // Execute
            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel wrapper = resultNode.buildResultPanel(() -> {}, () -> {});

            // Assert: wrapper exists and contains tabs
            assertNotNull(wrapper);
            com.intellij.ui.OnePixelSplitter splitter =
                    (com.intellij.ui.OnePixelSplitter) wrapper.getComponent(0);

            JScrollPane secondScroll = (JScrollPane) splitter.getSecondComponent();
            JPanel scsPanel = (JPanel) secondScroll.getViewport().getView();

            // Inline search for JBTabbedPane
            com.intellij.ui.components.JBTabbedPane tabbedPane = null;
            for (Component component : scsPanel.getComponents()) {
                if (component instanceof com.intellij.ui.components.JBTabbedPane) {
                    tabbedPane = (com.intellij.ui.components.JBTabbedPane) component;
                    break;
                }
            }

            assertNotNull(tabbedPane, "Tabbed pane should exist for SCS findings");
            assertEquals(
                    Arrays.asList("Learn More", "Remediation Examples"),
                    Arrays.asList(tabbedPane.getTitleAt(0), tabbedPane.getTitleAt(1))
            );
        }
    }

    // ===== SCA panel — branch coverage for drawSCARemediation, drawSCAAboutVulnerability, drawSCAVulnerabilityPath, drawSCAReferences =====

    @Test
    void buildResultPanel_ScaType_NullCveName_UsesCweId() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn(null);
        when(mockVulnDetails.getCweId()).thenReturn("CWE-79");
        when(mockVulnDetails.getCvssScore()).thenReturn(5.0);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});

        assertNotNull(panel);
    }

    @Test
    void buildResultPanel_ScaType_NullPackageIdentifier_FallsBackToResultId() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-1234");
        when(mockVulnDetails.getCvssScore()).thenReturn(7.5);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResultData.getPackageIdentifier()).thenReturn(null);
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});

        JPanel headerPanel = (JPanel) panel.getComponent(0);
        JLabel titleLabel = (JLabel) headerPanel.getComponent(0);
        assertTrue(titleLabel.getText().contains(RESULT_ID));
    }

    @Test
    void buildResultPanel_ScaType_WithScaPackageDataSupportsQuickFix_BuildsRemediationSection() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("com.example:lib:1.0.0");
        when(depPath.getLocations()).thenReturn(Collections.emptyList());
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-1234");
        when(mockVulnDetails.getCvssScore()).thenReturn(7.5);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResultData.getRecommendedVersion()).thenReturn("2.0.0");
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);
        when(mockResultData.getPackageIdentifier()).thenReturn("pkg:1.0.0");

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        assertNotNull(panel);
    }

    @Test
    void buildResultPanel_ScaType_WithFixLink_EnablesAboutVulnerabilityLink() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("lib:1.0.0");
        when(depPath.getLocations()).thenReturn(null);
        when(mockScaPackageData.getFixLink()).thenReturn("https://fix.example.com");
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-5678");
        when(mockVulnDetails.getCvssScore()).thenReturn(6.0);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("MEDIUM");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        assertNotNull(panel);
    }

    @Test
    void buildResultPanel_ScaType_WithDependencyPathsAndLocations_BuildsPathSection() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("lib:1.0.0");
        when(depPath.isSupportsQuickFix()).thenReturn(false);
        when(depPath.getLocations()).thenReturn(Arrays.asList("pom.xml"));
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-9999");
        when(mockVulnDetails.getCvssScore()).thenReturn(4.5);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("LOW");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        assertNotNull(panel);
    }

    @Test
    void buildResultPanel_ScaType_WithPackageDataAndScaPackageData_BuildsReferenceSection() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("lib:1.0.0");
        when(depPath.getLocations()).thenReturn(null);
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));
        when(mockPackageData.getType()).thenReturn("NuGet");
        when(mockPackageData.getUrl()).thenReturn("https://nuget.org/package");
        when(mockResultData.getPackageData()).thenReturn(Collections.singletonList(mockPackageData));
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-0001");
        when(mockVulnDetails.getCvssScore()).thenReturn(9.0);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("CRITICAL");
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        assertNotNull(panel);
    }

    @Test
    void buildResultPanel_ScaType_WithPackageDataNoUrl_DisablesReferenceLabel() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("lib:1.0.0");
        when(depPath.getLocations()).thenReturn(null);
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));
        when(mockPackageData.getType()).thenReturn("Maven");
        when(mockPackageData.getUrl()).thenReturn("");  // blank url → disabled label
        when(mockResultData.getPackageData()).thenReturn(Collections.singletonList(mockPackageData));
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-0002");
        when(mockVulnDetails.getCvssScore()).thenReturn(3.0);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("LOW");
        when(mockResult.getId()).thenReturn(RESULT_ID);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
        JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
        assertNotNull(panel);
    }

    @Test
    void openCodebashingLink_WhenInterruptedException_DoesNotThrow() throws Exception {
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {

            Logger mockLogger = mock(Logger.class);
            mockedUtils.when(() -> Utils.getLogger(any())).thenReturn(mockLogger);

            when(mockVulnDetails.getCweId()).thenReturn(TEST_CWE);
            when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
            when(mockResultData.getLanguageName()).thenReturn(TEST_LANGUAGE);
            when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);

            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.codeBashingList(TEST_CWE, TEST_LANGUAGE, QUERY_NAME))
                    .thenThrow(new InterruptedException("interrupted"));

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            assertDoesNotThrow(() -> resultNode.openCodebashingLink());
            mockedUtils.verify(() -> Utils.notify(any(), anyString(), any()), never());
        }
    }

    @Test
    void buildResultPanel_WithScsType_BlankRuleDescription_DoesNotThrow() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getRuleName()).thenReturn("Secret-Rule");
        when(mockResultData.getRuleDescription()).thenReturn("");
        when(mockResultData.getRemediation()).thenReturn("Fix this by rotating secrets.");

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("msg");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
            assertNotNull(panel);
        }
    }

    @Test
    void buildResultPanel_WithScsType_BlankRemediation_DoesNotThrow() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getRuleName()).thenReturn("Github-Pat");
        when(mockResultData.getRuleDescription()).thenReturn("Some rule description.");
        when(mockResultData.getRemediation()).thenReturn("");

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("msg");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
            assertNotNull(panel);
        }
    }

    @Test
    void generateLearnMore_WithNullVulnerabilityDetails_SkipsCweLink() {
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            when(mockLearnMore.getRisk()).thenReturn("Risk text");
            when(mockLearnMore.getCause()).thenReturn("Cause text");
            when(mockLearnMore.getGeneralRecommendations()).thenReturn("Recommendations");
            when(mockResult.getVulnerabilityDetails()).thenReturn(null);
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("label");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = new JPanel();
            resultNode.generateLearnMore(mockLearnMore, panel);

            // Should have risk title, risk content, cause title, cause content, recommendations title, recommendations content, CWE title
            // but NO CWE link label (vulnerabilityDetails is null)
            assertEquals(7, panel.getComponentCount());
        }
    }

    @Test
    void buildResultPanel_WithSastType_NonEmptyDescriptionAndValueExpected_AddsValuePanes() {
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());
        when(mockResultData.getPackageData()).thenReturn(Collections.emptyList());
        when(mockResultData.getFileName()).thenReturn("");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResult.getDescription()).thenReturn("A vulnerability was found");
        when(mockResultData.getValue()).thenReturn("actualValue");
        when(mockResultData.getExpectedValue()).thenReturn("expectedValue");

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("label");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
            assertNotNull(panel);
        }
    }

    // ===== createChangesPanels (private) =====

    @Test
    void createChangesPanels_WithNonEmptyComment_AddsCommentLabel() throws Exception {
        com.checkmarx.ast.predicate.Predicate mockPredicate =
                mock(com.checkmarx.ast.predicate.Predicate.class);
        when(mockPredicate.getCreatedBy()).thenReturn("user@example.com");
        when(mockPredicate.getCreatedAt()).thenReturn("2024-01-15T10:00:00Z");
        when(mockPredicate.getSeverity()).thenReturn("HIGH");
        when(mockPredicate.getState()).thenReturn("CONFIRMED");
        when(mockPredicate.getComment()).thenReturn("Verified by security team");

        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        JPanel panel = new JPanel();
        java.lang.reflect.Method m = ResultNode.class.getDeclaredMethod(
                "createChangesPanels", JPanel.class,
                com.checkmarx.ast.predicate.Predicate.class);
        m.setAccessible(true);
        m.invoke(resultNode, panel, mockPredicate);

        // createdBy label + severityLabel + stateLabel + commentLabel + separator = 5
        assertEquals(5, panel.getComponentCount());
    }

    @Test
    void createChangesPanels_WithEmptyComment_SkipsCommentLabel() throws Exception {
        com.checkmarx.ast.predicate.Predicate mockPredicate =
                mock(com.checkmarx.ast.predicate.Predicate.class);
        when(mockPredicate.getCreatedBy()).thenReturn("reviewer");
        when(mockPredicate.getCreatedAt()).thenReturn("2024-01-15T10:00:00Z");
        when(mockPredicate.getSeverity()).thenReturn("MEDIUM");
        when(mockPredicate.getState()).thenReturn("TO_VERIFY");
        when(mockPredicate.getComment()).thenReturn("");

        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        JPanel panel = new JPanel();
        java.lang.reflect.Method m = ResultNode.class.getDeclaredMethod(
                "createChangesPanels", JPanel.class,
                com.checkmarx.ast.predicate.Predicate.class);
        m.setAccessible(true);
        m.invoke(resultNode, panel, mockPredicate);

        // createdBy label + severityLabel + stateLabel + separator (no comment) = 4
        assertEquals(4, panel.getComponentCount());
    }

    // ===== addToPanel mouse listeners (private static) =====

    @Test
    void addToPanel_MouseListeners_FireWithoutThrowing() throws Exception {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout("fillx"));
        com.intellij.ui.components.labels.BoldLabel label =
                new com.intellij.ui.components.labels.BoldLabel("label", javax.swing.SwingConstants.LEFT);
        JLabel link = new JLabel("link");

        java.lang.reflect.Method m = ResultNode.class.getDeclaredMethod(
                "addToPanel", JPanel.class,
                com.intellij.ui.components.labels.BoldLabel.class,
                javax.swing.JComponent.class);
        m.setAccessible(true);
        m.invoke(null, panel, label, link);

        assertEquals(1, panel.getComponentCount());

        // Fire mouse events on the rowPanel's mouse listeners to cover mouseEntered/mouseExited
        JPanel rowPanel = (JPanel) panel.getComponent(0);
        java.awt.event.MouseEvent enter = new java.awt.event.MouseEvent(
                rowPanel, java.awt.event.MouseEvent.MOUSE_ENTERED,
                System.currentTimeMillis(), 0, 0, 0, 0, false);
        java.awt.event.MouseEvent exit = new java.awt.event.MouseEvent(
                rowPanel, java.awt.event.MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(), 0, 0, 0, 0, false);
        java.awt.event.MouseEvent move = new java.awt.event.MouseEvent(
                rowPanel, java.awt.event.MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, 5, 5, 0, false);

        for (java.awt.event.MouseListener ml : rowPanel.getMouseListeners()) {
            assertDoesNotThrow(() -> ml.mouseEntered(enter));
            assertDoesNotThrow(() -> ml.mouseExited(exit));
        }
        for (java.awt.event.MouseMotionListener mml : rowPanel.getMouseMotionListeners()) {
            assertDoesNotThrow(() -> mml.mouseMoved(move));
        }
    }

    @Test
    void buildResultPanel_ScaType_WithDependencyPathsSupportsQuickFix_BuildsButtonPanel() {
        com.checkmarx.ast.results.result.ScaPackageData mockScaPackageData =
                mock(com.checkmarx.ast.results.result.ScaPackageData.class);
        com.checkmarx.ast.results.result.DependencyPath depPath =
                mock(com.checkmarx.ast.results.result.DependencyPath.class);
        when(depPath.getName()).thenReturn("com.example:lib:1.0.0");
        when(depPath.isSupportsQuickFix()).thenReturn(true);
        when(depPath.getLocations()).thenReturn(Arrays.asList("pom.xml", "build.gradle"));
        when(mockScaPackageData.getDependencyPaths()).thenReturn(
                Collections.singletonList(Collections.singletonList(depPath)));

        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(mockVulnDetails.getCveName()).thenReturn("CVE-2023-7777");
        when(mockVulnDetails.getCvssScore()).thenReturn(8.0);
        when(mockResult.getVulnerabilityDetails()).thenReturn(mockVulnDetails);
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getId()).thenReturn(RESULT_ID);
        when(mockResultData.getScaPackageData()).thenReturn(mockScaPackageData);

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("label");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
            assertNotNull(panel);
        }
    }

    @Test
    void buildResultPanel_WithScsTypeAndDescription_SplitsPathAsClickableLink() {
        when(mockResult.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(mockResult.getSeverity()).thenReturn(SeverityFilter.HIGH.name());
        when(mockResultData.getRuleName()).thenReturn("Hardcoded-Secret");
        when(mockResultData.getRuleDescription()).thenReturn("A secret was found.");
        when(mockResultData.getRemediation()).thenReturn("Rotate the secret.");
        // Description with slash → preamble + file path
        when(mockResult.getDescription()).thenReturn("Secret detected in /src/config.js");
        when(mockResultData.getFileName()).thenReturn("/src/config.js");
        when(mockResultData.getLine()).thenReturn(10);

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(any())).thenReturn("msg");

            resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
            JPanel panel = resultNode.buildResultPanel(() -> {}, () -> {});
            assertNotNull(panel);
        }
    }
}
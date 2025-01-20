package com.checkmarx.intellij.unit.inspections;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.ASCA.AscaService;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.inspections.AscaInspection;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AscaInspectionTest {

    private static MockedStatic<ApplicationManager> appManagerMock;
    private static MockedStatic<GlobalSettingsState> settingsMock;

    @Mock
    private AscaService mockAscaService;

    @Mock
    private Logger mockLogger;

    @Mock
    private PsiFile mockPsiFile;

    @Mock
    private Project mockProject;

    @Mock
    private InspectionManager mockManager;

    @Mock
    private Document mockDocument;

    @Mock
    private PsiDocumentManager mockPsiDocumentManager;

    @Mock
    private GlobalSettingsState mockSettings;

    @Mock
    private ScanResult mockScanResult;

    @Mock
    private PsiElement mockPsiElement;

    @Mock
    private Application mockApplication;

    private AscaInspection ascaInspection;

    @BeforeAll
    static void beforeAll() {
        appManagerMock = mockStatic(ApplicationManager.class);
        settingsMock = mockStatic(GlobalSettingsState.class);
    }

    @BeforeEach
    void setUp() {
        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
        settingsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

        ascaInspection = new AscaInspection();
        ascaInspection.setAscaService(mockAscaService);
    }

    @Test
    void checkFile_WhenAscaDisabled_ReturnsEmptyArray() {
        // Arrange
        when(mockSettings.isAsca()).thenReturn(false);

        // Act
        ProblemDescriptor[] result = ascaInspection.checkFile(mockPsiFile, mockManager, true);

        // Assert
        assertEquals(0, result.length);
        verify(mockAscaService, never()).runAscaScan(any(), any(), anyBoolean(), anyString());
    }

    @Test
    void checkFile_WhenScanResultIsNull_ReturnsEmptyArray() {
        // Arrange
        when(mockSettings.isAsca()).thenReturn(true);
        when(mockAscaService.runAscaScan(mockPsiFile, mockProject, false, Constants.JET_BRAINS_AGENT_NAME))
                .thenReturn(null);
        when(mockPsiFile.getProject()).thenReturn(mockProject);

        // Act
        ProblemDescriptor[] result = ascaInspection.checkFile(mockPsiFile, mockManager, true);

        // Assert
        assertEquals(0, result.length);
    }

    @Test
    void checkFile_WithValidScanResult_CreatesProblemDescriptors() {
        // Arrange
        ScanDetail mockDetail = mock(ScanDetail.class);
        when(mockDetail.getLine()).thenReturn(1);
        when(mockDetail.getRuleName()).thenReturn("Test Rule");
        when(mockDetail.getRemediationAdvise()).thenReturn("Fix this");
        when(mockDetail.getSeverity()).thenReturn(Constants.ASCA_HIGH_SEVERITY);

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class)) {
            when(mockSettings.isAsca()).thenReturn(true);
            when(mockAscaService.runAscaScan(mockPsiFile, mockProject, false, Constants.JET_BRAINS_AGENT_NAME))
                    .thenReturn(mockScanResult);
            when(mockScanResult.getScanDetails()).thenReturn(Collections.singletonList(mockDetail));
            when(mockPsiFile.getProject()).thenReturn(mockProject);

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);
            when(mockPsiDocumentManager.getDocument(mockPsiFile)).thenReturn(mockDocument);
            when(mockDocument.getLineCount()).thenReturn(10);
            when(mockDocument.getLineStartOffset(0)).thenReturn(0);
            when(mockDocument.getLineEndOffset(0)).thenReturn(10);
            when(mockDocument.getTextLength()).thenReturn(100);
            when(mockDocument.getText(any(TextRange.class))).thenReturn("test code");
            when(mockPsiFile.findElementAt(anyInt())).thenReturn(mockPsiElement);

            ProblemDescriptor mockProblemDescriptor = mock(ProblemDescriptor.class);
            when(mockManager.createProblemDescriptor(
                    eq(mockPsiFile),
                    any(TextRange.class),
                    anyString(),
                    eq(ProblemHighlightType.GENERIC_ERROR),
                    eq(true),
                    any()
            )).thenReturn(mockProblemDescriptor);

            // Act
            ProblemDescriptor[] result = ascaInspection.checkFile(mockPsiFile, mockManager, true);

            // Assert
            assertEquals(1, result.length);
            verify(mockManager).createProblemDescriptor(
                    eq(mockPsiFile),
                    any(TextRange.class),
                    contains("Test Rule"),
                    eq(ProblemHighlightType.GENERIC_ERROR),
                    eq(true),
                    any()
            );
        }
    }

    @Test
    void formatDescription_WithValidInput_ReturnsFormattedHtml() {
        // Arrange
        String ruleName = "Test Rule";
        String advice = "Fix this issue";

        // Act
        String result = ascaInspection.formatDescription(ruleName, advice);

        // Assert
        assertTrue(result.contains(ruleName));
        assertTrue(result.contains(advice));
        assertTrue(result.contains(AscaInspection.ASCA_INSPECTION_ID));
        assertTrue(result.startsWith("<html>"));
        assertTrue(result.endsWith("</html>"));
    }

    @Test
    void formatDescription_WithHtmlCharacters_EscapesCharacters() {
        // Arrange
        String ruleName = "Test & Rule";
        String advice = "Fix < this > issue";

        // Act
        String result = ascaInspection.formatDescription(ruleName, advice);

        // Assert
        assertTrue(result.contains("Test &amp; Rule"));
        assertTrue(result.contains("Fix &lt; this &gt; issue"));
    }
} 
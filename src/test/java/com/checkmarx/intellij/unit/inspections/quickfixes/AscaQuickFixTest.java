package com.checkmarx.intellij.unit.inspections.quickfixes;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.inspections.quickfixes.AscaQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AscaQuickFixTest {

    @Mock
    private ScanDetail mockDetail;

    @Mock
    private Project mockProject;

    @Mock
    private ProblemDescriptor mockDescriptor;

    @Mock
    private Application mockApplication;

    @Mock
    private NotificationGroupManager mockNotificationGroupManager;

    @Mock
    private NotificationGroup mockNotificationGroup;

    @Mock
    private Notification mockNotification;

    @Mock
    private Toolkit mockToolkit;

    @Mock
    private Clipboard mockClipboard;

    private AscaQuickFix ascaQuickFix;

    @BeforeEach
    void setUp() {
        ascaQuickFix = new AscaQuickFix(mockDetail);
    }

    @Test
    void getFamilyName_ReturnsCorrectName() {
        // Act
        String familyName = ascaQuickFix.getFamilyName();

        // Assert
        assertEquals("ASCA - Copy fix prompt", familyName);
    }

    @Test
    void stripHtml_RemovesHtmlTagsAndEscapedCharacters() {
        // Arrange
        String htmlText = "<html><b>Test &amp; Rule</b> - Fix &lt;this&gt; issue<br><font color='gray'>ASCA</font></html>";

        // Act
        String result = ascaQuickFix.stripHtml(htmlText);

        // Assert
        assertEquals("Test & Rule - Fix <this> issue", result);
    }

    @Test
    void stripHtml_HandlesNullInput() {
        // Act
        String result = ascaQuickFix.stripHtml(null);

        // Assert
        assertEquals("", result);
    }

    @Test
    void stripHtml_RemovesAscaSuffix() {
        // Arrange
        String htmlText = "<html>Test message ASCA</html>";

        // Act
        String result = ascaQuickFix.stripHtml(htmlText);

        // Assert
        assertEquals("Test message", result);
    }
} 
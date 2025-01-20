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
    void applyFix_SuccessfullyGeneratesAndCopiesPrompt() {
        // Arrange
        String problematicLine = "problematic code line";
        String description = "<html><b>Rule</b> - Description<br><font color='gray'>ASCA</font></html>";
        when(mockDetail.getProblematicLine()).thenReturn(problematicLine);
        when(mockDescriptor.getDescriptionTemplate()).thenReturn(description);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<NotificationGroupManager> notificationManagerMock = mockStatic(NotificationGroupManager.class);
             MockedStatic<Toolkit> toolkitMock = mockStatic(Toolkit.class)) {

            appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return null;
            }).when(mockApplication).invokeLater(runnableCaptor.capture());

            notificationManagerMock.when(NotificationGroupManager::getInstance).thenReturn(mockNotificationGroupManager);
            when(mockNotificationGroupManager.getNotificationGroup(Constants.NOTIFICATION_GROUP_ID))
                    .thenReturn(mockNotificationGroup);
            when(mockNotificationGroup.createNotification(anyString(), anyString(), eq(NotificationType.INFORMATION)))
                    .thenReturn(mockNotification);

            toolkitMock.when(Toolkit::getDefaultToolkit).thenReturn(mockToolkit);
            when(mockToolkit.getSystemClipboard()).thenReturn(mockClipboard);

            // Act
            ascaQuickFix.applyFix(mockProject, mockDescriptor);

            // Assert
            verify(mockClipboard).setContents(any(StringSelection.class), isNull());
            verify(mockNotification).notify(mockProject);
        }
    }

    @Test
    void applyFix_HandlesClipboardError() {
        // Arrange
        String problematicLine = "problematic code line";
        String description = "Description";
        when(mockDetail.getProblematicLine()).thenReturn(problematicLine);
        when(mockDescriptor.getDescriptionTemplate()).thenReturn(description);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<NotificationGroupManager> notificationManagerMock = mockStatic(NotificationGroupManager.class);
             MockedStatic<Toolkit> toolkitMock = mockStatic(Toolkit.class)) {

            appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            doAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return null;
            }).when(mockApplication).invokeLater(runnableCaptor.capture());

            notificationManagerMock.when(NotificationGroupManager::getInstance).thenReturn(mockNotificationGroupManager);
            when(mockNotificationGroupManager.getNotificationGroup(Constants.NOTIFICATION_GROUP_ID))
                    .thenReturn(mockNotificationGroup);
            when(mockNotificationGroup.createNotification(anyString(), anyString(), eq(NotificationType.ERROR)))
                    .thenReturn(mockNotification);

            toolkitMock.when(Toolkit::getDefaultToolkit).thenReturn(mockToolkit);
            when(mockToolkit.getSystemClipboard()).thenReturn(mockClipboard);
            doThrow(new IllegalStateException("Clipboard error")).when(mockClipboard)
                    .setContents(any(StringSelection.class), isNull());

            // Act
            ascaQuickFix.applyFix(mockProject, mockDescriptor);

            // Assert
            verify(mockNotification).notify(null);
        }
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
package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.ast.settings.GlobalSettingsConfigurable;
import com.checkmarx.intellij.ast.window.actions.OpenSettingsAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenSettingsAction.
 */
@ExtendWith(MockitoExtension.class)
class OpenSettingsActionTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private Project mockProject;

    @Mock
    private ShowSettingsUtil mockShowSettingsUtil;

    private OpenSettingsAction openSettingsAction;

    @BeforeEach
    void setUp() throws Exception {
        // Bypass Bundle.messagePointer() in constructor via Unsafe
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        openSettingsAction = (OpenSettingsAction) unsafe.allocateInstance(OpenSettingsAction.class);
    }

    @Test
    void getActionUpdateThread_ReturnsEDT() {
        assertEquals(ActionUpdateThread.EDT, openSettingsAction.getActionUpdateThread());
    }

    @Test
    void actionPerformed_CallsShowSettingsDialog_WithGlobalSettingsConfigurableClass() {
        when(mockEvent.getProject()).thenReturn(mockProject);

        try (MockedStatic<ShowSettingsUtil> mockedUtil = mockStatic(ShowSettingsUtil.class)) {
            mockedUtil.when(ShowSettingsUtil::getInstance).thenReturn(mockShowSettingsUtil);

            openSettingsAction.actionPerformed(mockEvent);

            verify(mockShowSettingsUtil).showSettingsDialog(mockProject, GlobalSettingsConfigurable.class);
        }
    }

    @Test
    void actionPerformed_WhenProjectNull_PassesNullToShowSettingsDialog() {
        when(mockEvent.getProject()).thenReturn(null);

        try (MockedStatic<ShowSettingsUtil> mockedUtil = mockStatic(ShowSettingsUtil.class)) {
            mockedUtil.when(ShowSettingsUtil::getInstance).thenReturn(mockShowSettingsUtil);

            openSettingsAction.actionPerformed(mockEvent);

            verify(mockShowSettingsUtil).showSettingsDialog(null, GlobalSettingsConfigurable.class);
        }
    }
}


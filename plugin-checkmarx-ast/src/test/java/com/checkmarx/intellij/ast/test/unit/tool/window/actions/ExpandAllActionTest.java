package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.ast.window.actions.ExpandAllAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import sun.misc.Unsafe;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExpandAllAction.
 */
@ExtendWith(MockitoExtension.class)
class ExpandAllActionTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private Project mockProject;

    @Mock
    private ToolWindowManager mockToolWindowManager;

    @Mock
    private ToolWindow mockToolWindow;

    @Mock
    private ContentManager mockContentManager;

    @Mock
    private Content mockContent;

    private ExpandAllAction expandAllAction;

    @BeforeEach
    void setUp() throws Exception {
        // Bypass Bundle.messagePointer() call in constructor
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        expandAllAction = (ExpandAllAction) unsafe.allocateInstance(ExpandAllAction.class);
    }

    @Test
    void actionPerformed_WhenProjectNull_DoesNothing() {
        when(mockEvent.getProject()).thenReturn(null);
        assertDoesNotThrow(() -> expandAllAction.actionPerformed(mockEvent));
    }

    @Test
    void actionPerformed_WhenToolWindowNull_DoesNothing() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        try (MockedStatic<ToolWindowManager> mockedManager = mockStatic(ToolWindowManager.class)) {
            mockedManager.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockToolWindowManager);
            when(mockToolWindowManager.getToolWindow("Checkmarx")).thenReturn(null);
            assertDoesNotThrow(() -> expandAllAction.actionPerformed(mockEvent));
        }
    }

    @Test
    void actionPerformed_WhenContentNull_DoesNothing() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        try (MockedStatic<ToolWindowManager> mockedManager = mockStatic(ToolWindowManager.class)) {
            mockedManager.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockToolWindowManager);
            when(mockToolWindowManager.getToolWindow("Checkmarx")).thenReturn(mockToolWindow);
            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getSelectedContent()).thenReturn(null);
            assertDoesNotThrow(() -> expandAllAction.actionPerformed(mockEvent));
        }
    }

    @Test
    void actionPerformed_WhenTreeFound_ExpandsAllRows() {
        JTree tree = new JTree(new Object[]{"node1", "node2"});
        JPanel panel = new JPanel();
        panel.add(tree);

        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockContent.getComponent()).thenReturn(panel);

        try (MockedStatic<ToolWindowManager> mockedManager = mockStatic(ToolWindowManager.class)) {
            mockedManager.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockToolWindowManager);
            when(mockToolWindowManager.getToolWindow("Checkmarx")).thenReturn(mockToolWindow);
            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getSelectedContent()).thenReturn(mockContent);

            assertDoesNotThrow(() -> expandAllAction.actionPerformed(mockEvent));
        }
    }

    @Test
    void findTreeInComponent_DirectlyJTree_ReturnsTree() throws Exception {
        Method method = ExpandAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JTree tree = new JTree();
        JTree result = (JTree) method.invoke(expandAllAction, tree);
        assertSame(tree, result);
    }

    @Test
    void findTreeInComponent_NestedInPanel_ReturnsTree() throws Exception {
        Method method = ExpandAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JTree tree = new JTree();
        JPanel outer = new JPanel();
        JPanel inner = new JPanel();
        inner.add(tree);
        outer.add(inner);

        JTree result = (JTree) method.invoke(expandAllAction, outer);
        assertSame(tree, result);
    }

    @Test
    void findTreeInComponent_NoTree_ReturnsNull() throws Exception {
        Method method = ExpandAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JPanel panel = new JPanel();
        panel.add(new JLabel("no tree here"));

        Object result = method.invoke(expandAllAction, panel);
        assertNull(result);
    }
}



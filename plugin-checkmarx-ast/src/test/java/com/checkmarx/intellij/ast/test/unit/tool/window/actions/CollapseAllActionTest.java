package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.ast.window.actions.CollapseAllAction;
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
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CollapseAllAction.
 */
@ExtendWith(MockitoExtension.class)
class CollapseAllActionTest {

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

    private CollapseAllAction collapseAllAction;

    @BeforeEach
    void setUp() throws Exception {
        // Bypass Bundle.messagePointer() call in constructor
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        collapseAllAction = (CollapseAllAction) unsafe.allocateInstance(CollapseAllAction.class);
    }

    @Test
    void actionPerformed_WhenProjectNull_DoesNothing() {
        when(mockEvent.getProject()).thenReturn(null);
        assertDoesNotThrow(() -> collapseAllAction.actionPerformed(mockEvent));
    }

    @Test
    void actionPerformed_WhenToolWindowNull_DoesNothing() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        try (MockedStatic<ToolWindowManager> mockedManager = mockStatic(ToolWindowManager.class)) {
            mockedManager.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockToolWindowManager);
            when(mockToolWindowManager.getToolWindow("Checkmarx")).thenReturn(null);
            assertDoesNotThrow(() -> collapseAllAction.actionPerformed(mockEvent));
        }
    }

    @Test
    void actionPerformed_WhenTreeFound_CollapsesAllRowsBottomUp() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child1"));
        root.add(new DefaultMutableTreeNode("child2"));
        JTree tree = new JTree(root);
        JPanel panel = new JPanel();
        panel.add(tree);

        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockContent.getComponent()).thenReturn(panel);

        try (MockedStatic<ToolWindowManager> mockedManager = mockStatic(ToolWindowManager.class)) {
            mockedManager.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockToolWindowManager);
            when(mockToolWindowManager.getToolWindow("Checkmarx")).thenReturn(mockToolWindow);
            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getSelectedContent()).thenReturn(mockContent);

            assertDoesNotThrow(() -> collapseAllAction.actionPerformed(mockEvent));
        }
    }

    @Test
    void findTreeInComponent_NestedContainer_FindsTree() throws Exception {
        Method method = CollapseAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JTree tree = new JTree();
        JPanel outer = new JPanel();
        JPanel inner = new JPanel();
        inner.add(tree);
        outer.add(inner);

        JTree result = (JTree) method.invoke(collapseAllAction, outer);
        assertSame(tree, result);
    }

    @Test
    void findTreeInComponent_NonContainerComponent_ReturnsNull() throws Exception {
        Method method = CollapseAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JLabel label = new JLabel("just a label");
        Object result = method.invoke(collapseAllAction, label);
        assertNull(result);
    }

    @Test
    void findTreeInComponent_DirectlyJTree_ReturnsTree() throws Exception {
        Method method = CollapseAllAction.class.getDeclaredMethod("findTreeInComponent", Component.class);
        method.setAccessible(true);

        JTree tree = new JTree();
        JTree result = (JTree) method.invoke(collapseAllAction, tree);
        assertSame(tree, result);
    }
}



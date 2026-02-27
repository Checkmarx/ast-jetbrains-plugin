package com.checkmarx.intellij.ast.test.unit.components;

import com.checkmarx.intellij.common.components.TreeUtils;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TreeUtilsTest {

    private static final String TEST_LABEL = "Test Label";

    @Test
    void labelTreePanel_CreatesTreePanelWithLabel() {
        // Arrange
        JPanel mockPanel = mock(JPanel.class);
        try (MockedStatic<TreeUtils> treeUtilsMock = mockStatic(TreeUtils.class, CALLS_REAL_METHODS)) {
            treeUtilsMock.when(() -> TreeUtils.treePanel(any(Tree.class))).thenReturn(mockPanel);

            // Act
            JComponent result = TreeUtils.labelTreePanel(TEST_LABEL);

            // Assert
            assertSame(mockPanel, result, "Should return the panel from treePanel");
            treeUtilsMock.verify(() -> TreeUtils.treePanel(any(Tree.class)));
            
            // Verify the tree node was created with correct label
            ArgumentCaptor<Tree> treeCaptor = ArgumentCaptor.forClass(Tree.class);
            treeUtilsMock.verify(() -> TreeUtils.treePanel(treeCaptor.capture()));
            Tree capturedTree = treeCaptor.getValue();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) capturedTree.getModel().getRoot();
            assertEquals(TEST_LABEL, root.getUserObject(), "Tree root should contain the label text");
        }
    }

    @Test
    void labelTreePanel_WithNullText_CreatesTreePanel() {
        // Arrange
        String nullText = null;
        JPanel mockPanel = mock(JPanel.class);
        try (MockedStatic<TreeUtils> treeUtilsMock = mockStatic(TreeUtils.class, CALLS_REAL_METHODS)) {
            treeUtilsMock.when(() -> TreeUtils.treePanel(any(Tree.class))).thenReturn(mockPanel);

            // Act
            JComponent result = TreeUtils.labelTreePanel(nullText);

            // Assert
            assertSame(mockPanel, result, "Should return the panel from treePanel");
            treeUtilsMock.verify(() -> TreeUtils.treePanel(any(Tree.class)));
            
            // Verify the tree node was created with null label
            ArgumentCaptor<Tree> treeCaptor = ArgumentCaptor.forClass(Tree.class);
            treeUtilsMock.verify(() -> TreeUtils.treePanel(treeCaptor.capture()));
            Tree capturedTree = treeCaptor.getValue();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) capturedTree.getModel().getRoot();
            assertNull(root.getUserObject(), "Tree root should contain null text");
        }
    }
}
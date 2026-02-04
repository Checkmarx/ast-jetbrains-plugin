package com.checkmarx.intellij.ast.test.unit.tool.window.results.tree;

import com.checkmarx.intellij.tool.window.results.tree.ResultsTreeCellRenderer;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultsTreeCellRendererTest {

    @Mock
    private Tree mockTree;

    @Mock
    private ResultNode mockResultNode;

    @Mock
    private Icon mockIcon;

    private ResultsTreeCellRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ResultsTreeCellRenderer();
    }

    @Test
    void customizeCellRenderer_WhenValueIsResultNode_SetsIcon() {
        // Arrange
        when(mockResultNode.getIcon()).thenReturn(mockIcon);

        // Act
        renderer.customizeCellRenderer(mockTree, mockResultNode, false, false, true, 0, false);

        // Assert
        verify(mockResultNode).getIcon();
    }

    @Test
    void customizeCellRenderer_WhenValueIsNotResultNode_DoesNotSetIcon() {
        // Arrange
        Object nonResultNode = new Object();

        // Act
        renderer.customizeCellRenderer(mockTree, nonResultNode, false, false, true, 0, false);

        // Assert
        verifyNoInteractions(mockResultNode);
    }
} 
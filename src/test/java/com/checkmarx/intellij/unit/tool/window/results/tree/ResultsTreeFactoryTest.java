package com.checkmarx.intellij.unit.tool.window.results.tree;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.checkmarx.intellij.tool.window.results.tree.ResultsTreeFactory;
import com.checkmarx.intellij.tool.window.results.tree.nodes.NonLeafNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultsTreeFactoryTest {

    private static final String SCAN_ID = "test-scan-123";

    @Mock
    private Project mockProject;

    @Mock
    private Results mockResults;

    @Mock
    private Result mockResult;

    @Mock
    private Data mockData;

    private List<GroupBy> groupByList;
    private Set<Filterable> enabledFilters;

    @BeforeEach
    void setUp() {
        groupByList = new ArrayList<>();
        groupByList.add(GroupBy.SCA_TYPE);

        enabledFilters = new HashSet<>();
        enabledFilters.add(Severity.HIGH);
        enabledFilters.add(ResultState.TO_VERIFY);

        // Set up default mock result
        when(mockResult.getSeverity()).thenReturn("HIGH");
        when(mockResult.getState()).thenReturn("TO_VERIFY");
        when(mockResult.getType()).thenReturn("SAST");
        when(mockResult.getData()).thenReturn(mockData);
        when(mockResults.getResults()).thenReturn(Collections.singletonList(mockResult));
    }

    @Test
    void buildResultsTree_WhenResultMatchesFilters_AddsToTree() {
        // Act
        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID,
                mockResults,
                mockProject,
                groupByList,
                enabledFilters,
                true
        );

        // Assert
        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        assertEquals(1, root.getChildCount(), "Root should have one child (SAST engine)");

        DefaultMutableTreeNode engineNode = (DefaultMutableTreeNode) root.getChildAt(0);
        assertTrue(engineNode instanceof NonLeafNode, "Engine node should be NonLeafNode");
        assertEquals("SAST", engineNode.getUserObject(), "Engine node should be SAST");
        assertTrue(engineNode.toString().contains("(1)"), "Engine node should have one result");
    }
} 
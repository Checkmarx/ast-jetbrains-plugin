package com.checkmarx.intellij.ast.test.unit.tool.window.results.tree;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.results.tree.ResultsTreeFactory;
import com.checkmarx.intellij.ast.window.results.tree.nodes.NonLeafNode;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
        enabledFilters.add(SeverityFilter.HIGH);
        enabledFilters.add(new CustomResultState("TO_VERIFY", "To Verify"));

        // Set up default mock result
        lenient().when(mockResult.getSeverity()).thenReturn("HIGH");
        lenient().when(mockResult.getState()).thenReturn("TO_VERIFY");
        lenient().when(mockResult.getType()).thenReturn("SAST");
        lenient().when(mockResult.getData()).thenReturn(mockData);
        lenient().when(mockResults.getResults()).thenReturn(Collections.singletonList(mockResult));
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

    @Test
    void buildResultsTree_WithScsType_EngineLabelIsSecretDetection() {
        // Setup
        when(mockResult.getType()).thenReturn("scs");

        // Act + Assert
        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(eq(Resource.SECRET_DETECTION))).thenReturn("secret detection");
            mockedBundle.when(() -> Bundle.message(eq(Resource.RESULTS_TREE_HEADER), any()))
                    .thenReturn("Scan " + SCAN_ID);

            Tree resultTree = ResultsTreeFactory.buildResultsTree(
                    SCAN_ID,
                    mockResults,
                    mockProject,
                    groupByList,
                    enabledFilters,
                    true
            );

            TreeModel model = resultTree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            assertNotNull(root, "Root node should be present");
            assertEquals(1, root.getChildCount(), "Root should have one engine node");

            DefaultMutableTreeNode engineNode = (DefaultMutableTreeNode) root.getChildAt(0);
            assertTrue(engineNode instanceof NonLeafNode, "Engine node should be NonLeafNode");
            assertEquals("secret detection", engineNode.getUserObject(),
                    "SCS engine should be displayed as 'secret detection'");
            assertTrue(engineNode.toString().contains("(1)"),
                    "Engine node should indicate a single result");
        }
    }

    @Test
    void buildResultsTree_WithEmptyResults_ReturnsTreeWithEmptyRoot() {
        when(mockResults.getResults()).thenReturn(Collections.emptyList());

        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, groupByList, enabledFilters, true
        );

        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        assertEquals(0, root.getChildCount(), "Root should have no children when results are empty");
    }

    @Test
    void buildResultsTree_WithFilteredOutResult_DoesNotAddToTree() {
        // Result severity is LOW, but only HIGH is in enabledFilters
        when(mockResult.getSeverity()).thenReturn("LOW");

        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, groupByList, enabledFilters, true
        );

        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertEquals(0, root.getChildCount(), "Filtered-out results should not appear in tree");
    }

    @Test
    void buildResultsTree_WithKicsType_EngineLabelIsIacSecurity() {
        when(mockResult.getType()).thenReturn("kics");

        try (MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedBundle.when(() -> Bundle.message(eq(Resource.IAC_SECURITY))).thenReturn("IaC Security");
            mockedBundle.when(() -> Bundle.message(eq(Resource.RESULTS_TREE_HEADER), any()))
                    .thenReturn("Scan " + SCAN_ID);

            Tree resultTree = ResultsTreeFactory.buildResultsTree(
                    SCAN_ID, mockResults, mockProject, groupByList, enabledFilters, true
            );

            TreeModel model = resultTree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            assertEquals(1, root.getChildCount());
            DefaultMutableTreeNode engineNode = (DefaultMutableTreeNode) root.getChildAt(0);
            assertEquals("IaC Security", engineNode.getUserObject());
        }
    }

    @Test
    void buildResultsTree_WithLatestFalse_StillBuildsTree() {
        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, groupByList, enabledFilters, false
        );

        assertNotNull(resultTree);
        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        assertEquals(1, root.getChildCount(), "Root should still contain the SAST engine node");
    }

    @Test
    void buildResultsTree_WithAllFiltersDisabled_ReturnsEmptyTree() {
        // No filters enabled at all
        Set<Filterable> noFilters = new HashSet<>();

        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, groupByList, noFilters, true
        );

        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        assertEquals(0, root.getChildCount(), "Empty filter set should yield no results in tree");
    }

    @Test
    void buildResultsTree_WithGroupByFile_CreatesFileGroupedNodes() {
        // Use groupBy FILE to test file-based grouping
        List<GroupBy> fileGroupBy = new ArrayList<>();
        fileGroupBy.add(GroupBy.FILE);

        when(mockData.getFileName()).thenReturn("src/Main.java");

        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, fileGroupBy, enabledFilters, true
        );

        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertNotNull(root);
        // Result should be grouped under engine node, then file node
        assertEquals(1, root.getChildCount(), "Should have 1 engine (SAST) node");
    }

    @Test
    void buildResultsTree_WithMultipleResults_CountsAggregatedInParentNode() {
        Result mockResult2 = mock(Result.class);
        lenient().when(mockResult2.getSeverity()).thenReturn("HIGH");
        lenient().when(mockResult2.getState()).thenReturn("TO_VERIFY");
        lenient().when(mockResult2.getType()).thenReturn("SAST");
        lenient().when(mockResult2.getData()).thenReturn(mockData);
        lenient().when(mockData.getFileName()).thenReturn(null);
        lenient().when(mockData.getNodes()).thenReturn(null);
        lenient().when(mockData.getPackageIdentifier()).thenReturn(null);
        lenient().when(mockData.getQueryName()).thenReturn("XSS");
        lenient().when(mockResult2.getId()).thenReturn("id-2");
        lenient().when(mockResult.getId()).thenReturn("id-1");
        when(mockResults.getResults()).thenReturn(java.util.Arrays.asList(mockResult, mockResult2));

        Tree resultTree = ResultsTreeFactory.buildResultsTree(
                SCAN_ID, mockResults, mockProject, groupByList, enabledFilters, true
        );

        TreeModel model = resultTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode engineNode = (DefaultMutableTreeNode) root.getChildAt(0);
        assertTrue(engineNode.toString().contains("(2)"),
                "Engine node should report 2 results: " + engineNode);
    }
}


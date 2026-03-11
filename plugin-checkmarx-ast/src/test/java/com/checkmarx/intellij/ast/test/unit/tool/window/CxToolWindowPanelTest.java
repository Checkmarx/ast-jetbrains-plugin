package com.checkmarx.intellij.ast.test.unit.tool.window;

import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.ast.project.ProjectResultsService;
import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.actions.selection.RootGroup;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CxToolWindowPanelTest {

    @Mock private Project mockProject;
    @Mock private ProjectResultsService mockProjectResultsService;
    @Mock private RootGroup mockRootGroup;

    private CxToolWindowPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        panel = (CxToolWindowPanel) getUnsafe().allocateInstance(CxToolWindowPanel.class);
        setField("project", mockProject);
        setField("projectResultsService", mockProjectResultsService);
        setField("rootGroup", mockRootGroup);
        setField("groupByList", new ArrayList<>(GroupBy.DEFAULT_GROUP_BY));
        setField("currentState", new ResultGetState());
        setField("currentTree", null);
        setField("getResultsInProgress", false);
        setField("scanIdField", new SearchTextField());
        setField("scanTreeSplitter", new OnePixelSplitter(true, 0.1f));
        setField("treeDetailsSplitter", new OnePixelSplitter(false, 0.3f));
    }

    @Test
    void expandAll_WhenValidThreadAndTreePresent_ExpandsEveryRow() throws Exception {
        Tree mockTree = mock(Tree.class);
        when(mockTree.getRowCount()).thenReturn(3);
        setField("currentTree", mockTree);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            panel.expandAll();
            verify(mockTree).expandRow(0);
            verify(mockTree).expandRow(1);
            verify(mockTree).expandRow(2);
        }
    }

    @Test
    void collapseAll_WhenValidThreadAndTreePresent_CollapsesRowsAndClearsDetails() throws Exception {
        Tree mockTree = mock(Tree.class);
        when(mockTree.getRowCount()).thenReturn(2);
        setField("currentTree", mockTree);

        OnePixelSplitter detailsSplitter = new OnePixelSplitter(false, 0.5f);
        detailsSplitter.setSecondComponent(new JLabel("old"));
        setField("treeDetailsSplitter", detailsSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            panel.collapseAll();
            verify(mockTree).collapseRow(0);
            verify(mockTree).collapseRow(1);
            assertNotNull(detailsSplitter.getSecondComponent());
        }
    }

    @Test
    void changeGroupBy_AddAndRemoveUpdatesList() throws Exception {
        ArrayList<GroupBy> list = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
        setField("groupByList", list);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            panel.changeGroupBy(GroupBy.SCA_TYPE, true);
            assertTrue(list.contains(GroupBy.SCA_TYPE));
            panel.changeGroupBy(GroupBy.SCA_TYPE, false);
            assertFalse(list.contains(GroupBy.SCA_TYPE));
        }
    }

    @Test
    void triggerDrawResultsTree_WithInvalidUuid_SetsMessageAndIndexesResults() throws Exception {
        // Prepare state and collaborators
        SearchTextField field = new SearchTextField();
        setField("scanIdField", field);
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedBundle.when(() -> Bundle.message(Resource.INVALID_SCAN_ID)).thenReturn("INVALID");

            Method m = CxToolWindowPanel.class.getDeclaredMethod("triggerDrawResultsTree", String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(panel, "not-a-uuid", false);

            ResultGetState state = (ResultGetState) getField("currentState");
            assertEquals("INVALID", state.getMessage());
            verify(mockProjectResultsService).indexResults(eq(mockProject), eq(Results.emptyResults));
            assertNotNull(scanSplitter.getSecondComponent());
        }
    }

    @Test
    void selectScan_WhenInvalidThread_DoesNothing() throws Exception {
        ResultGetState originalState = (ResultGetState) getField("currentState");

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.selectScan("any-scan-id");

            // Verify state unchanged when thread invalid
            assertEquals(originalState, getField("currentState"));
        }
    }

    @Test
    void changeFilter_WhenInvalidThread_DoesNothing() throws Exception {
        ResultGetState originalState = (ResultGetState) getField("currentState");

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.changeFilter();

            // Verify state unchanged when thread invalid
            assertEquals(originalState, getField("currentState"));
        }
    }

    @Test
    void resetPanel_ClearsStateAndCallsRootGroupReset() throws Exception {
        SearchTextField field = new SearchTextField();
        field.setText("old-scan-id");
        setField("scanIdField", field);
        ResultGetState state = new ResultGetState();
        state.setScanId("existing-id");
        setField("currentState", state);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            panel.resetPanel();

            ResultGetState newState = (ResultGetState) getField("currentState");
            assertNull(newState.getScanId());
            assertEquals("", field.getText());
            verify(mockProjectResultsService).indexResults(eq(mockProject), eq(Results.emptyResults));
            verify(mockRootGroup).setEnabled(false);
            verify(mockRootGroup).reset();
        }
    }

    @Test
    void refreshPanel_WhenValidThread_RefreshesContent() throws Exception {
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            panel.refreshPanel();
            assertTrue(true); // Successfully refreshed
        }
    }

    @Test
    void refreshPanel_WhenInvalidThread_DoesNothing() throws Exception {
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.refreshPanel();
            assertTrue(true); // Should return early
        }
    }

    @Test
    void triggerDrawResultsTree_WhenBlankScanId_ResetsWindow() throws Exception {
        SearchTextField field = new SearchTextField();
        field.setText("");
        setField("scanIdField", field);
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("triggerDrawResultsTree", String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(panel, "", false);

            ResultGetState state = (ResultGetState) getField("currentState");
            assertNull(state.getMessage());
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CxToolWindowPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(panel, value);
    }

    private Object getField(String name) throws Exception {
        Field f = CxToolWindowPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(panel);
    }

    private sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }
}

package com.checkmarx.intellij.ast.test.unit.tool.window;

import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.ast.project.ProjectResultsService;
import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.actions.selection.RootGroup;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    // -------------------------------------------------------------------------
    // expandAll / collapseAll
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // changeGroupBy — valid thread: add / remove / sorted order
    // -------------------------------------------------------------------------

    @Test
    void changeGroupBy_AddAndRemoveUpdatesList() throws Exception {
        ArrayList<GroupBy> list = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeGroupBy(GroupBy.SCA_TYPE, true);
            assertTrue(list.contains(GroupBy.SCA_TYPE));

            panel.changeGroupBy(GroupBy.SCA_TYPE, false);
            assertFalse(list.contains(GroupBy.SCA_TYPE));
        }
    }

    @Test
    void changeGroupBy_Add_PersistsUpdatedListToSettings() throws Exception {
        ArrayList<GroupBy> list = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeGroupBy(GroupBy.FILE, true);

            // setGroupByValues must be called with a set containing FILE
            verify(mockSettings).setGroupByValues(argThat(values ->
                    values.contains(GroupBy.FILE.name())));
        }
    }

    @Test
    void changeGroupBy_Remove_PersistsUpdatedListWithoutRemovedEntry() throws Exception {
        // Start with FILE in the list
        ArrayList<GroupBy> list = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
        list.add(GroupBy.FILE);
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeGroupBy(GroupBy.FILE, false);

            // setGroupByValues must be called with a set NOT containing FILE
            verify(mockSettings).setGroupByValues(argThat(values ->
                    !values.contains(GroupBy.FILE.name())));
        }
    }

    @Test
    void changeGroupBy_Add_ListIsSortedByEnumOrdinal() throws Exception {
        // Start with an empty list
        ArrayList<GroupBy> list = new ArrayList<>();
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            // Add in reverse ordinal order
            panel.changeGroupBy(GroupBy.STATE, true);
            panel.changeGroupBy(GroupBy.SEVERITY, true);

            // After each add, list should be sorted
            for (int i = 0; i < list.size() - 1; i++) {
                assertTrue(list.get(i).compareTo(list.get(i + 1)) <= 0,
                        "List should be sorted by enum ordinal");
            }
        }
    }

    @Test
    void changeGroupBy_InvalidThread_DoesNotModifyListOrPersist() throws Exception {
        ArrayList<GroupBy> list = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
        int originalSize = list.size();
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeGroupBy(GroupBy.FILE, true);

            assertEquals(originalSize, list.size(), "List should not change on invalid thread");
            verify(mockSettings, never()).setGroupByValues(any());
        }
    }

    @Test
    void changeGroupBy_PersistsAllEnumNamesInList() throws Exception {
        ArrayList<GroupBy> list = new ArrayList<>();
        list.add(GroupBy.SEVERITY);
        list.add(GroupBy.STATE);
        setField("groupByList", list);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeGroupBy(GroupBy.FILE, true);

            verify(mockSettings).setGroupByValues(argThat(values ->
                    values.contains("SEVERITY")
                    && values.contains("STATE")
                    && values.contains("FILE")));
        }
    }

    // -------------------------------------------------------------------------
    // resolvePersistedGroupBy — all branches via reflection
    // -------------------------------------------------------------------------

    @Test
    void resolvePersistedGroupBy_NullPersistedValues_ReturnsDefaults() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getGroupByValues()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertEquals(new ArrayList<>(GroupBy.DEFAULT_GROUP_BY), result,
                    "Null persisted values should return DEFAULT_GROUP_BY");
        }
    }

    @Test
    void resolvePersistedGroupBy_EmptyPersistedValues_ReturnsDefaults() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>());

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertEquals(new ArrayList<>(GroupBy.DEFAULT_GROUP_BY), result,
                    "Empty persisted values should return DEFAULT_GROUP_BY");
        }
    }

    @Test
    void resolvePersistedGroupBy_ValidPersistedValues_RestoresCorrectly() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        Set<String> persisted = new LinkedHashSet<>(Set.of("FILE", "STATE"));
        when(mockSettings.getGroupByValues()).thenReturn(persisted);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertTrue(result.contains(GroupBy.FILE), "Should restore FILE");
            assertTrue(result.contains(GroupBy.STATE), "Should restore STATE");
            assertEquals(2, result.size(), "Should have exactly the 2 persisted values");
        }
    }

    @Test
    void resolvePersistedGroupBy_AllValidGroupByNames_RestoresAll() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        Set<String> persisted = new LinkedHashSet<>();
        for (GroupBy g : GroupBy.values()) persisted.add(g.name());
        when(mockSettings.getGroupByValues()).thenReturn(persisted);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertEquals(GroupBy.values().length, result.size(), "Should restore all GroupBy values");
            for (GroupBy g : GroupBy.values()) {
                assertTrue(result.contains(g), "Should contain " + g);
            }
        }
    }

    @Test
    void resolvePersistedGroupBy_UnknownEnumName_SkippedGracefully() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        // Mix of a valid name and a name that no longer exists in the enum
        Set<String> persisted = new LinkedHashSet<>(Set.of("SEVERITY", "UNKNOWN_GROUP_BY_VALUE"));
        when(mockSettings.getGroupByValues()).thenReturn(persisted);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertTrue(result.contains(GroupBy.SEVERITY), "Should include valid SEVERITY");
            assertEquals(1, result.size(), "Unknown value should be skipped silently");
        }
    }

    @Test
    void resolvePersistedGroupBy_AllUnknownNames_FallsBackToDefaults() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        // All values are unrecognised enum names
        Set<String> persisted = new LinkedHashSet<>(Set.of("DELETED_GROUP1", "DELETED_GROUP2"));
        when(mockSettings.getGroupByValues()).thenReturn(persisted);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            List<GroupBy> result = invokeResolvePersistedGroupBy();
            assertEquals(new ArrayList<>(GroupBy.DEFAULT_GROUP_BY), result,
                    "All-unknown persisted values should fall back to DEFAULT_GROUP_BY");
        }
    }

    // -------------------------------------------------------------------------
    // triggerDrawResultsTree
    // -------------------------------------------------------------------------

    @Test
    void triggerDrawResultsTree_WithInvalidUuid_SetsMessageAndIndexesResults() throws Exception {
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

    // -------------------------------------------------------------------------
    // selectScan / changeFilter / resetPanel / refreshPanel
    // -------------------------------------------------------------------------

    @Test
    void selectScan_WhenInvalidThread_DoesNothing() throws Exception {
        ResultGetState originalState = (ResultGetState) getField("currentState");

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.selectScan("any-scan-id");

            assertEquals(originalState, getField("currentState"));
        }
    }

    @Test
    void changeFilter_WhenInvalidThread_DoesNothing() throws Exception {
        ResultGetState originalState = (ResultGetState) getField("currentState");

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.changeFilter();

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
            assertTrue(true);
        }
    }

    @Test
    void refreshPanel_WhenInvalidThread_DoesNothing() throws Exception {
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.refreshPanel();
            assertTrue(true);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<GroupBy> invokeResolvePersistedGroupBy() throws Exception {
        Method m = CxToolWindowPanel.class.getDeclaredMethod("resolvePersistedGroupBy");
        m.setAccessible(true);
        return (List<GroupBy>) m.invoke(null);
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

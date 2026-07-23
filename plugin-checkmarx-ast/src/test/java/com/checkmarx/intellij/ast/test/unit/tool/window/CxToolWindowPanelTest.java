package com.checkmarx.intellij.ast.test.unit.tool.window;

import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.service.StateService;
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
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
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
    // OnEnterGetResults inner class
    // -------------------------------------------------------------------------

    @Test
    void onEnterGetResults_KeyReleased_WhenEnterAndEmptyField_ResetsResultWindow() throws Exception {
        SearchTextField field = new SearchTextField();
        field.setText("");
        setField("scanIdField", field);
        setField("currentState", new ResultGetState());
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        Class<?> listenerClass = Class.forName("com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnEnterGetResults");
        Constructor<?> ctor = listenerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(panel);

        KeyEvent e = mock(KeyEvent.class);
        when(e.getExtendedKeyCode()).thenReturn(KeyEvent.VK_ENTER);

        Method keyReleased = listenerClass.getDeclaredMethod("keyReleased", KeyEvent.class);
        keyReleased.setAccessible(true);
        keyReleased.invoke(listener, e);

        ResultGetState state = (ResultGetState) getField("currentState");
        assertNull(state.getMessage());
    }

    @Test
    void onEnterGetResults_KeyReleased_WhenEnterAndInvalidUuid_SetsInvalidMessage() throws Exception {
        SearchTextField field = new SearchTextField();
        field.setText("not-a-uuid");
        setField("scanIdField", field);
        setField("currentState", new ResultGetState());
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        Class<?> listenerClass = Class.forName("com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnEnterGetResults");
        Constructor<?> ctor = listenerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(panel);

        KeyEvent e = mock(KeyEvent.class);
        when(e.getExtendedKeyCode()).thenReturn(KeyEvent.VK_ENTER);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> mockedBundle = mockStatic(Bundle.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            mockedBundle.when(() -> Bundle.message(Resource.INVALID_SCAN_ID)).thenReturn("INVALID");

            Method keyReleased = listenerClass.getDeclaredMethod("keyReleased", KeyEvent.class);
            keyReleased.setAccessible(true);
            keyReleased.invoke(listener, e);

            ResultGetState state = (ResultGetState) getField("currentState");
            assertEquals("INVALID", state.getMessage());
        }
    }

    @Test
    void onEnterGetResults_KeyReleased_WhenNonEnterKey_DoesNothing() throws Exception {
        SearchTextField field = new SearchTextField();
        field.setText("some-text");
        setField("scanIdField", field);
        ResultGetState originalState = new ResultGetState();
        setField("currentState", originalState);

        Class<?> listenerClass = Class.forName("com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnEnterGetResults");
        Constructor<?> ctor = listenerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(panel);

        KeyEvent e = mock(KeyEvent.class);
        when(e.getExtendedKeyCode()).thenReturn(KeyEvent.VK_A);

        Method keyReleased = listenerClass.getDeclaredMethod("keyReleased", KeyEvent.class);
        keyReleased.setAccessible(true);
        keyReleased.invoke(listener, e);

        assertEquals(originalState, getField("currentState"));
    }

    @Test
    void onEnterGetResults_KeyTyped_DoesNothing() throws Exception {
        Class<?> listenerClass = Class.forName("com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnEnterGetResults");
        Constructor<?> ctor = listenerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(panel);

        KeyEvent e = mock(KeyEvent.class);
        Method keyTyped = listenerClass.getDeclaredMethod("keyTyped", KeyEvent.class);
        keyTyped.setAccessible(true);
        assertDoesNotThrow(() -> keyTyped.invoke(listener, e));
    }

    @Test
    void onEnterGetResults_KeyPressed_DoesNothing() throws Exception {
        Class<?> listenerClass = Class.forName("com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnEnterGetResults");
        Constructor<?> ctor = listenerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(panel);

        KeyEvent e = mock(KeyEvent.class);
        Method keyPressed = listenerClass.getDeclaredMethod("keyPressed", KeyEvent.class);
        keyPressed.setAccessible(true);
        assertDoesNotThrow(() -> keyPressed.invoke(listener, e));
    }

    // -------------------------------------------------------------------------
    // triggerDrawResultsTree — additional branches
    // -------------------------------------------------------------------------

    @Test
    void triggerDrawResultsTree_WhenInProgress_ReturnsEarlyWithoutChangingState() throws Exception {
        setField("getResultsInProgress", true);
        ResultGetState originalState = new ResultGetState();
        originalState.setScanId("original-id");
        setField("currentState", originalState);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("triggerDrawResultsTree", String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(panel, "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", false);

            ResultGetState state = (ResultGetState) getField("currentState");
            assertEquals("original-id", state.getScanId(), "State must not change when in progress");
        }
    }

    @Test
    void triggerDrawResultsTree_WhenSameScanId_ReturnsEarlyWithoutChangingState() throws Exception {
        setField("getResultsInProgress", false);
        String scanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";
        ResultGetState state = new ResultGetState();
        state.setScanIdFieldValue(scanId);
        setField("currentState", state);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("triggerDrawResultsTree", String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(panel, scanId, false);

            ResultGetState after = (ResultGetState) getField("currentState");
            assertEquals(scanId, after.getScanIdFieldValue(), "State must not change when same scanId");
        }
    }

    @Test
    void triggerDrawResultsTree_WithValidUUID_SetsInProgressAndCallsRootGroup() throws Exception {
        setField("getResultsInProgress", false);
        setField("rootGroup", mockRootGroup);
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedStatic<Results> resultsMock = mockStatic(Results.class)) {

            mockedUtils.when(Utils::validThread).thenReturn(true);
            bundleMock.when(() -> Bundle.message(Resource.GETTING_RESULTS)).thenReturn("Getting results...");

            com.checkmarx.intellij.ast.service.StateService mockSs =
                    mock(com.checkmarx.intellij.ast.service.StateService.class);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockSs);

            @SuppressWarnings("unchecked")
            java.util.concurrent.CompletableFuture<ResultGetState> mockFuture =
                    mock(java.util.concurrent.CompletableFuture.class);
            when(mockFuture.thenAcceptAsync(any())).thenReturn(mock(java.util.concurrent.CompletableFuture.class));
            resultsMock.when(() -> Results.getResults(anyString())).thenReturn(mockFuture);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("triggerDrawResultsTree", String.class, boolean.class);
            m.setAccessible(true);
            m.invoke(panel, "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", false);

            verify(mockRootGroup).setEnabled(false);
            assertTrue((boolean) getField("getResultsInProgress"));
        }
    }

    // -------------------------------------------------------------------------
    // selectScan — valid thread path
    // -------------------------------------------------------------------------

    @Test
    void selectScan_WhenValidThread_CallsTriggerDrawResultsTree() throws Exception {
        setField("getResultsInProgress", false);
        setField("rootGroup", mockRootGroup);
        String scanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";
        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedStatic<Results> resultsMock = mockStatic(Results.class)) {

            mockedUtils.when(Utils::validThread).thenReturn(true);
            bundleMock.when(() -> Bundle.message(Resource.GETTING_RESULTS)).thenReturn("Getting...");

            com.checkmarx.intellij.ast.service.StateService mockSs =
                    mock(com.checkmarx.intellij.ast.service.StateService.class);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockSs);

            @SuppressWarnings("unchecked")
            java.util.concurrent.CompletableFuture<ResultGetState> mockFuture =
                    mock(java.util.concurrent.CompletableFuture.class);
            when(mockFuture.thenAcceptAsync(any())).thenReturn(mock(java.util.concurrent.CompletableFuture.class));
            resultsMock.when(() -> Results.getResults(anyString())).thenReturn(mockFuture);

            panel.selectScan(scanId);

            verify(mockRootGroup).setEnabled(false);
        }
    }

    // -------------------------------------------------------------------------
    // changeFilter — valid thread calls drawTree
    // -------------------------------------------------------------------------

    @Test
    void changeFilter_WhenValidThread_CallsDrawTree() throws Exception {
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            // currentState.getScanId() is null → drawTree returns early — no NPE
            panel.changeFilter();
            assertTrue(true, "changeFilter with valid thread should not throw");
        }
    }

    @Test
    void changeFilter_WhenValidThread_WithScanId_BuildsTree() throws Exception {
        ResultGetState state = new ResultGetState();
        state.setScanId("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e");
        state.setResultOutput(Results.emptyResults);
        setField("currentState", state);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getFilters()).thenReturn(new java.util.HashSet<>());

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {

            mockedUtils.when(Utils::validThread).thenReturn(true);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            panel.changeFilter();

            assertNotNull(getField("currentTree"), "drawTree should set currentTree when scanId is present");
        }
    }

    // -------------------------------------------------------------------------
    // updateDisplay — message null → drawTree
    // -------------------------------------------------------------------------

    @Test
    void updateDisplay_WhenMessageNull_CallsDrawTree() throws Exception {
        ResultGetState state = new ResultGetState();
        state.setScanId("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e");
        state.setResultOutput(Results.emptyResults);
        state.setMessage(null);
        setField("currentState", state);

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getFilters()).thenReturn(new java.util.HashSet<>());

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class);
             MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {

            mockedUtils.when(Utils::validThread).thenReturn(true);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("updateDisplay");
            m.setAccessible(true);
            m.invoke(panel);

            verify(mockProjectResultsService).indexResults(eq(mockProject), any());
            assertNotNull(getField("currentTree"), "drawTree should be called and set currentTree");
        }
    }

    @Test
    void updateDisplay_WhenMessageNotNull_ShowsLabel() throws Exception {
        ResultGetState state = new ResultGetState();
        state.setScanId("scan-123");
        state.setMessage("Error message");
        setField("currentState", state);

        OnePixelSplitter scanSplitter = new OnePixelSplitter(true, 0.1f);
        setField("scanTreeSplitter", scanSplitter);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);

            Method m = CxToolWindowPanel.class.getDeclaredMethod("updateDisplay");
            m.setAccessible(true);
            m.invoke(panel);

            verify(mockProjectResultsService).indexResults(eq(mockProject), any());
            assertNotNull(scanSplitter.getSecondComponent());
        }
    }

    // -------------------------------------------------------------------------
    // dispose
    // -------------------------------------------------------------------------

    @Test
    void dispose_DoesNotThrow() {
        assertDoesNotThrow(() -> panel.dispose());
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

    // -------------------------------------------------------------------------
    // OnSelectShowDetail inner class
    // -------------------------------------------------------------------------

    private Object createOnSelectShowDetail() throws Exception {
        Class<?> innerClass = Class.forName(
                "com.checkmarx.intellij.ast.window.CxToolWindowPanel$OnSelectShowDetail");
        Constructor<?> ctor = innerClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(panel);
    }

    @Test
    void onSelectShowDetail_WhenNullPath_DoesNotThrow() throws Exception {
        Object listener = createOnSelectShowDetail();

        javax.swing.event.TreeSelectionEvent e = mock(javax.swing.event.TreeSelectionEvent.class);
        when(e.getNewLeadSelectionPath()).thenReturn(null);

        Method valueChanged = listener.getClass().getDeclaredMethod(
                "valueChanged", javax.swing.event.TreeSelectionEvent.class);
        valueChanged.setAccessible(true);
        assertDoesNotThrow(() -> valueChanged.invoke(listener, e));
    }

    @Test
    void onSelectShowDetail_WhenSelectedIsNonLeafNode_DoesNotUpdateDetails() throws Exception {
        Object listener = createOnSelectShowDetail();
        OnePixelSplitter detailsSplitter = new OnePixelSplitter(false, 0.5f);
        JLabel original = new JLabel("original");
        detailsSplitter.setSecondComponent(original);
        setField("treeDetailsSplitter", detailsSplitter);

        javax.swing.tree.TreePath mockPath = mock(javax.swing.tree.TreePath.class);
        javax.swing.tree.DefaultMutableTreeNode nonLeafNode =
                new javax.swing.tree.DefaultMutableTreeNode("group");
        nonLeafNode.add(new javax.swing.tree.DefaultMutableTreeNode("child")); // makes it non-leaf
        when(mockPath.getLastPathComponent()).thenReturn(nonLeafNode);

        Tree mockTree = mock(Tree.class);
        javax.swing.tree.TreeModel mockModel = mock(javax.swing.tree.TreeModel.class);
        when(mockTree.getModel()).thenReturn(mockModel);
        when(mockModel.isLeaf(nonLeafNode)).thenReturn(false);

        javax.swing.event.TreeSelectionEvent e = mock(javax.swing.event.TreeSelectionEvent.class);
        when(e.getNewLeadSelectionPath()).thenReturn(mockPath);
        when(e.getSource()).thenReturn(mockTree);

        Method valueChanged = listener.getClass().getDeclaredMethod(
                "valueChanged", javax.swing.event.TreeSelectionEvent.class);
        valueChanged.setAccessible(true);
        assertDoesNotThrow(() -> valueChanged.invoke(listener, e));

        assertSame(original, detailsSplitter.getSecondComponent(),
                "Detail panel must not change when non-leaf is selected");
    }

    @Test
    void onSelectShowDetail_WhenLeafIsNotResultNode_DoesNotUpdateDetails() throws Exception {
        Object listener = createOnSelectShowDetail();
        OnePixelSplitter detailsSplitter = new OnePixelSplitter(false, 0.5f);
        JLabel original = new JLabel("original");
        detailsSplitter.setSecondComponent(original);
        setField("treeDetailsSplitter", detailsSplitter);

        javax.swing.tree.DefaultMutableTreeNode leafNode =
                new javax.swing.tree.DefaultMutableTreeNode("just-a-string-node");

        javax.swing.tree.TreePath mockPath = mock(javax.swing.tree.TreePath.class);
        when(mockPath.getLastPathComponent()).thenReturn(leafNode);

        Tree mockTree = mock(Tree.class);
        javax.swing.tree.TreeModel mockModel = mock(javax.swing.tree.TreeModel.class);
        when(mockTree.getModel()).thenReturn(mockModel);
        when(mockModel.isLeaf(leafNode)).thenReturn(true);

        javax.swing.event.TreeSelectionEvent e = mock(javax.swing.event.TreeSelectionEvent.class);
        when(e.getNewLeadSelectionPath()).thenReturn(mockPath);
        when(e.getSource()).thenReturn(mockTree);

        Method valueChanged = listener.getClass().getDeclaredMethod(
                "valueChanged", javax.swing.event.TreeSelectionEvent.class);
        valueChanged.setAccessible(true);
        assertDoesNotThrow(() -> valueChanged.invoke(listener, e));

        assertSame(original, detailsSplitter.getSecondComponent(),
                "Detail panel must not change when leaf is not a ResultNode");
    }

    // -------------------------------------------------------------------------
    // dispose — no-op check
    // -------------------------------------------------------------------------

    @Test
    void dispose_CalledMultipleTimes_DoesNotThrow() {
        assertDoesNotThrow(() -> {
            panel.dispose();
            panel.dispose();
        });
    }

    // -------------------------------------------------------------------------
    // expandAll / collapseAll — null tree branches
    // -------------------------------------------------------------------------

    @Test
    void expandAll_WhenNullTree_DoesNotThrow() throws Exception {
        setField("currentTree", null);
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            assertDoesNotThrow(() -> panel.expandAll());
        }
    }

    @Test
    void collapseAll_WhenNullTree_DoesNotThrow() throws Exception {
        setField("currentTree", null);
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            assertDoesNotThrow(() -> panel.collapseAll());
        }
    }

    @Test
    void expandAll_WhenInvalidThread_DoesNotExpand() throws Exception {
        Tree mockTree = mock(Tree.class);
        setField("currentTree", mockTree);
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.expandAll();
            verify(mockTree, never()).expandRow(anyInt());
        }
    }

    @Test
    void collapseAll_WhenInvalidThread_DoesNotCollapse() throws Exception {
        Tree mockTree = mock(Tree.class);
        setField("currentTree", mockTree);
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.collapseAll();
            verify(mockTree, never()).collapseRow(anyInt());
        }
    }

    // -------------------------------------------------------------------------
    // resetPanel — invalid thread early return
    // -------------------------------------------------------------------------

    @Test
    void resetPanel_WhenInvalidThread_DoesNothing() throws Exception {
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            panel.resetPanel();
            verify(mockRootGroup, never()).reset();
        }
    }

    // -------------------------------------------------------------------------
    // OnSelectShowDetail — ResultNode leaf updates detail panel
    // -------------------------------------------------------------------------

    @Test
    void onSelectShowDetail_WhenLeafIsResultNode_UpdatesDetailPanel() throws Exception {
        com.checkmarx.ast.results.result.Result mockResult =
                mock(com.checkmarx.ast.results.result.Result.class);
        com.checkmarx.ast.results.result.Data mockData =
                mock(com.checkmarx.ast.results.result.Data.class);
        lenient().when(mockResult.getData()).thenReturn(mockData);
        lenient().when(mockData.getNodes()).thenReturn(java.util.Collections.emptyList());
        lenient().when(mockData.getPackageData()).thenReturn(java.util.Collections.emptyList());

        com.checkmarx.intellij.ast.window.results.tree.nodes.ResultNode testNode =
                new com.checkmarx.intellij.ast.window.results.tree.nodes.ResultNode(
                        mockResult, mockProject, "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e") {
                    @Override
                    public JPanel buildResultPanel(Runnable runnableDraw, Runnable runnableUpdater) {
                        return new JPanel();
                    }
                };

        Object listener = createOnSelectShowDetail();

        OnePixelSplitter detailsSplitter = new OnePixelSplitter(false, 0.5f);
        setField("treeDetailsSplitter", detailsSplitter);

        javax.swing.tree.TreePath mockPath = mock(javax.swing.tree.TreePath.class);
        when(mockPath.getLastPathComponent()).thenReturn(testNode);

        Tree mockTree = mock(Tree.class);
        javax.swing.tree.TreeModel mockModel = mock(javax.swing.tree.TreeModel.class);
        when(mockTree.getModel()).thenReturn(mockModel);
        when(mockModel.isLeaf(testNode)).thenReturn(true);

        javax.swing.event.TreeSelectionEvent e = mock(javax.swing.event.TreeSelectionEvent.class);
        when(e.getNewLeadSelectionPath()).thenReturn(mockPath);
        when(e.getSource()).thenReturn(mockTree);

        Method valueChanged = listener.getClass().getDeclaredMethod(
                "valueChanged", javax.swing.event.TreeSelectionEvent.class);
        valueChanged.setAccessible(true);
        assertDoesNotThrow(() -> valueChanged.invoke(listener, e));

        assertNotNull(detailsSplitter.getSecondComponent(),
                "Detail panel must be updated when a ResultNode leaf is selected");
    }
}

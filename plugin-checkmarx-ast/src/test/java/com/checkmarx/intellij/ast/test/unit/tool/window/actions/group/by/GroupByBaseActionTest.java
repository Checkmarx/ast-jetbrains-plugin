package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByBaseAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByDirectDependency;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByFileAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByPackageAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBySeverityAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByStateAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByVulnerabilityTypeAction;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupByBaseAction.
 *
 * NOTE: isPersistedOrDefault() is called as a field initializer during construction,
 * so GlobalSettingsState must be mocked BEFORE any action is instantiated.
 * We therefore open a class-level MockedStatic in @BeforeEach and close it in @AfterEach.
 * The default stub returns null (first-launch path → DEFAULT_GROUP_BY fallback).
 */
@ExtendWith(MockitoExtension.class)
class GroupByBaseActionTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private CxToolWindowPanel mockToolWindowPanel;

    /** Class-level mock kept open for the duration of every test. */
    private MockedStatic<GlobalSettingsState> mockedSettingsState;
    private GlobalSettingsState mockSettings;

    @BeforeEach
    void setUp() {
        mockSettings = mock(GlobalSettingsState.class);
        // Default: no persisted values → isPersistedOrDefault falls back to DEFAULT_GROUP_BY
        when(mockSettings.getGroupByValues()).thenReturn(null);

        mockedSettingsState = mockStatic(GlobalSettingsState.class);
        mockedSettingsState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
    }

    @AfterEach
    void tearDown() {
        mockedSettingsState.close();
    }

    // -------------------------------------------------------------------------
    // Helpers — constructed AFTER setUp() opens the mock
    // -------------------------------------------------------------------------

    private TestGroupByAction actionWithPanel() {
        return new TestGroupByAction(mockToolWindowPanel);
    }

    private TestGroupByAction actionWithNullPanel() {
        return new TestGroupByAction(null);
    }

    private TestGroupByActionForEnum actionForEnum(GroupBy groupBy) {
        return TestGroupByActionForEnum.create(groupBy);
    }

    // -------------------------------------------------------------------------
    // isSelected — initial state derived from DEFAULT_GROUP_BY (null persisted)
    // -------------------------------------------------------------------------

    @Test
    void isSelected_WhenNullPersisted_FileNotInDefaults_ReturnsFalse() {
        // FILE is NOT in DEFAULT_GROUP_BY, and persisted is null → falls back to defaults
        TestGroupByActionForEnum action = actionForEnum(GroupBy.FILE);
        assertFalse(action.isSelected(mockEvent),
                "FILE is not in DEFAULT_GROUP_BY so should start unselected");
    }

    @Test
    void isSelected_WhenNullPersisted_SeverityInDefaults_ReturnsTrue() {
        // SEVERITY IS in DEFAULT_GROUP_BY, persisted is null → falls back to defaults
        TestGroupByActionForEnum action = actionForEnum(GroupBy.SEVERITY);
        assertTrue(action.isSelected(mockEvent),
                "SEVERITY is in DEFAULT_GROUP_BY so should start selected");
    }

    // -------------------------------------------------------------------------
    // setSelected — state update and panel notification
    // -------------------------------------------------------------------------

    @Test
    void setSelected_WhenStateIsTrue_UpdatesStateAndNotifiesPanel() {
        TestGroupByAction action = actionWithPanel();

        action.setSelected(mockEvent, true);

        assertTrue(action.isSelected(mockEvent));
        verify(mockToolWindowPanel).changeGroupBy(GroupBy.FILE, true);
    }

    @Test
    void setSelected_WhenStateIsFalse_UpdatesStateAndNotifiesPanel() {
        TestGroupByAction action = actionWithPanel();

        action.setSelected(mockEvent, true);
        action.setSelected(mockEvent, false);

        assertFalse(action.isSelected(mockEvent));
        verify(mockToolWindowPanel).changeGroupBy(GroupBy.FILE, false);
    }

    @Test
    void setSelected_WhenPanelIsNull_OnlyUpdatesState() {
        TestGroupByAction action = actionWithNullPanel();

        action.setSelected(mockEvent, true);

        assertTrue(action.isSelected(mockEvent));
        verifyNoInteractions(mockToolWindowPanel);
    }

    @Test
    void setSelected_ToggledTrueAndFalse_StateFollowsLastCall() {
        TestGroupByAction action = actionWithPanel();

        action.setSelected(mockEvent, true);
        assertTrue(action.isSelected(mockEvent));

        action.setSelected(mockEvent, false);
        assertFalse(action.isSelected(mockEvent));

        action.setSelected(mockEvent, true);
        assertTrue(action.isSelected(mockEvent));
    }

    // -------------------------------------------------------------------------
    // getActionUpdateThread
    // -------------------------------------------------------------------------

    @Test
    void getActionUpdateThread_ReturnsEDT() {
        assertEquals(ActionUpdateThread.EDT, actionWithPanel().getActionUpdateThread());
    }

    // -------------------------------------------------------------------------
    // isPersistedOrDefault — null persisted → fallback to DEFAULT_GROUP_BY
    // -------------------------------------------------------------------------

    @Test
    void isPersistedOrDefault_NullPersisted_AllGroupBys_MatchDefaultGroupBy() {
        // mockSettings already returns null — set up in @BeforeEach
        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            assertEquals(
                    GroupBy.DEFAULT_GROUP_BY.contains(groupBy),
                    action.isSelected(mockEvent),
                    "Null persisted: " + groupBy + " should match DEFAULT_GROUP_BY membership"
            );
        }
    }

    // -------------------------------------------------------------------------
    // isPersistedOrDefault — empty persisted → fallback to DEFAULT_GROUP_BY
    // -------------------------------------------------------------------------

    @Test
    void isPersistedOrDefault_EmptyPersisted_SeverityInDefaults_ReturnsTrue() {
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>());

        TestGroupByActionForEnum action = actionForEnum(GroupBy.SEVERITY);
        assertTrue(action.isSelected(mockEvent),
                "SEVERITY should be selected when persisted is empty (falls back to DEFAULT_GROUP_BY)");
    }

    @Test
    void isPersistedOrDefault_EmptyPersisted_DirectDependencyNotInDefaults_ReturnsFalse() {
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>());

        TestGroupByActionForEnum action = actionForEnum(GroupBy.DIRECT_DEPENDENCY);
        assertFalse(action.isSelected(mockEvent),
                "DIRECT_DEPENDENCY should not be selected when persisted is empty");
    }

    @Test
    void isPersistedOrDefault_EmptyPersisted_AllGroupBys_MatchDefaultGroupBy() {
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>());

        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            assertEquals(
                    GroupBy.DEFAULT_GROUP_BY.contains(groupBy),
                    action.isSelected(mockEvent),
                    "Empty persisted: " + groupBy + " should match DEFAULT_GROUP_BY membership"
            );
        }
    }

    // -------------------------------------------------------------------------
    // isPersistedOrDefault — persisted values present → use them (not defaults)
    // -------------------------------------------------------------------------

    @Test
    void isPersistedOrDefault_PersistedContainsGroupBy_ReturnsTrue() {
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>(Set.of("FILE", "STATE")));

        assertTrue(actionForEnum(GroupBy.FILE).isSelected(mockEvent),
                "FILE should be selected because it is in persisted values");
        assertTrue(actionForEnum(GroupBy.STATE).isSelected(mockEvent),
                "STATE should be selected because it is in persisted values");
    }

    @Test
    void isPersistedOrDefault_PersistedDoesNotContainGroupBy_ReturnsFalse() {
        // Only FILE persisted — SEVERITY is in DEFAULT_GROUP_BY but NOT persisted
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>(Set.of("FILE")));

        assertFalse(actionForEnum(GroupBy.SEVERITY).isSelected(mockEvent),
                "SEVERITY should NOT be selected even if in DEFAULT_GROUP_BY — persisted overrides");
    }

    @Test
    void isPersistedOrDefault_PersistedContainsNonDefaultGroupBy_ReturnsTrue() {
        // DIRECT_DEPENDENCY is NOT in DEFAULT_GROUP_BY but IS persisted
        when(mockSettings.getGroupByValues()).thenReturn(new LinkedHashSet<>(Set.of("DIRECT_DEPENDENCY")));

        assertTrue(actionForEnum(GroupBy.DIRECT_DEPENDENCY).isSelected(mockEvent),
                "DIRECT_DEPENDENCY should be selected because it is persisted");
    }

    @Test
    void isPersistedOrDefault_PersistedPartialSet_OnlyPersistedOnesSelected() {
        Set<String> persisted = new LinkedHashSet<>(Set.of("STATE", "FILE"));
        when(mockSettings.getGroupByValues()).thenReturn(persisted);

        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            assertEquals(
                    persisted.contains(groupBy.name()),
                    action.isSelected(mockEvent),
                    "Mismatch for GroupBy." + groupBy + " — persisted: " + persisted
            );
        }
    }

    @Test
    void isPersistedOrDefault_AllGroupBysPersisted_AllSelected() {
        Set<String> allPersisted = new LinkedHashSet<>();
        for (GroupBy g : GroupBy.values()) allPersisted.add(g.name());
        when(mockSettings.getGroupByValues()).thenReturn(allPersisted);

        for (GroupBy groupBy : GroupBy.values()) {
            assertTrue(actionForEnum(groupBy).isSelected(mockEvent),
                    groupBy + " should be selected when all are persisted");
        }
    }

    // -------------------------------------------------------------------------
    // Enum-backed action helpers — toggle behaviour
    // -------------------------------------------------------------------------

    @Test
    void enumBackedActions_SetSelectedTogglesState() {
        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            action.setSelected(mockEvent, true);
            assertTrue(action.isSelected(mockEvent),
                    "Expected selected after setSelected(true) for " + groupBy);
            action.setSelected(mockEvent, false);
            assertFalse(action.isSelected(mockEvent),
                    "Expected unselected after setSelected(false) for " + groupBy);
        }
    }

    @Test
    void enumBackedActions_SetSelectedFalseThenTrue_FinalStateIsTrue() {
        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            action.setSelected(mockEvent, false);
            action.setSelected(mockEvent, true);
            assertTrue(action.isSelected(mockEvent),
                    "Final state should be true for " + groupBy);
        }
    }

    // -------------------------------------------------------------------------
    // getGroupBy — via reflection on inner test subclass
    // -------------------------------------------------------------------------

    @Test
    void getGroupBy_ReturnsExpectedEnumForEachConcreteTestAction() throws Exception {
        for (GroupBy groupBy : GroupBy.values()) {
            TestGroupByActionForEnum action = actionForEnum(groupBy);
            Method method = action.getClass().getDeclaredMethod("getGroupBy");
            method.setAccessible(true);
            assertEquals(groupBy, method.invoke(action),
                    "getGroupBy() should return " + groupBy);
        }
    }

    // -------------------------------------------------------------------------
    // Concrete GroupBy action classes — constructor + getGroupBy()
    // Each concrete class has 9 missed instructions at 0% — tested here.
    // -------------------------------------------------------------------------

    @Test
    void concreteActions_GroupBySeverityAction_ConstructsAndActionUpdateThreadIsEDT() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupBySeverityAction action = new GroupBySeverityAction();
            assertEquals(ActionUpdateThread.EDT, action.getActionUpdateThread());
            // getGroupBy() is protected — verify via reflection
            java.lang.reflect.Method m = GroupBySeverityAction.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.SEVERITY, m.invoke(action));
        }
    }

    @Test
    void concreteActions_GroupByFileAction_ConstructsAndReturnsFileGroupBy() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupByFileAction action = new GroupByFileAction();
            java.lang.reflect.Method m = GroupByFileAction.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.FILE, m.invoke(action));
        }
    }

    @Test
    void concreteActions_GroupByStateAction_ConstructsAndReturnsStateGroupBy() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupByStateAction action = new GroupByStateAction();
            java.lang.reflect.Method m = GroupByStateAction.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.STATE, m.invoke(action));
        }
    }

    @Test
    void concreteActions_GroupByPackageAction_ConstructsAndReturnsPackageGroupBy() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupByPackageAction action = new GroupByPackageAction();
            java.lang.reflect.Method m = GroupByPackageAction.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.PACKAGE, m.invoke(action));
        }
    }

    @Test
    void concreteActions_GroupByVulnerabilityTypeAction_ConstructsAndReturnsVulnerabilityTypeName() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupByVulnerabilityTypeAction action = new GroupByVulnerabilityTypeAction();
            java.lang.reflect.Method m = GroupByVulnerabilityTypeAction.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.VULNERABILITY_TYPE_NAME, m.invoke(action));
        }
    }

    @Test
    void concreteActions_GroupByDirectDependency_ConstructsAndReturnsDirectDependencyGroupBy() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.messagePointer(any(Resource.class)))
                      .thenReturn((java.util.function.Supplier<String>) () -> "label");

            GroupByDirectDependency action = new GroupByDirectDependency();
            java.lang.reflect.Method m = GroupByDirectDependency.class.getDeclaredMethod("getGroupBy");
            m.setAccessible(true);
            assertEquals(GroupBy.DIRECT_DEPENDENCY, m.invoke(action));
        }
    }

    // -------------------------------------------------------------------------
    // Inner test subclasses
    // -------------------------------------------------------------------------

    /** Uses GroupBy.FILE — for basic state tests that need a real panel mock. */
    private static class TestGroupByAction extends GroupByBaseAction {
        private final CxToolWindowPanel panel;

        TestGroupByAction(CxToolWindowPanel panel) {
            super(() -> "Test Group By");
            this.panel = panel;
        }

        @Override
        protected GroupBy getGroupBy() {
            return GroupBy.FILE;
        }

        @Override
        public CxToolWindowPanel getCxToolWindowPanel(AnActionEvent e) {
            return panel;
        }
    }

    /**
     * Configurable GroupBy — for persisted-state and enum-sweep tests.
     *
     * <p>The GroupBy value is passed via a ThreadLocal rather than a constructor-assigned
     * instance field. This is necessary because {@code GroupByBaseAction} has a field
     * initializer {@code private boolean selected = isPersistedOrDefault()} that calls
     * {@code getGroupBy()} during {@code super()} — before any subclass instance fields
     * are assigned. Using a ThreadLocal makes the value available at that point.
     */
    private static class TestGroupByActionForEnum extends GroupByBaseAction {
        private static final ThreadLocal<GroupBy> GROUP_BY_HOLDER = new ThreadLocal<>();

        private final GroupBy groupBy;

        static TestGroupByActionForEnum create(GroupBy groupBy) {
            GROUP_BY_HOLDER.set(groupBy);
            try {
                return new TestGroupByActionForEnum(groupBy);
            } finally {
                GROUP_BY_HOLDER.remove();
            }
        }

        private TestGroupByActionForEnum(GroupBy groupBy) {
            super(() -> "Test " + groupBy);
            this.groupBy = groupBy;
        }

        @Override
        protected GroupBy getGroupBy() {
            // During super() construction the instance field is not yet set; fall back to holder.
            GroupBy fromHolder = GROUP_BY_HOLDER.get();
            return fromHolder != null ? fromHolder : groupBy;
        }

        @Override
        public CxToolWindowPanel getCxToolWindowPanel(AnActionEvent e) {
            return null;
        }
    }
}

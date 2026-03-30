package com.checkmarx.intellij.ast.test.unit.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.service.StateService;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.checkmarx.intellij.common.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StateService singleton class.
 * Tests cover singleton behavior, state initialization, default filters,
 * resolveFilterByValue, pruneStaleCustomStates, and buildCustomStateFilters.
 */
@ExtendWith(MockitoExtension.class)
class StateServiceTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Mock the IntelliJ Application + CxWrapperFactory so buildCustomStateFilters() falls back to defaults. */
    private void withMockedInfra(MockedStatic<ApplicationManager> mockedApp,
                                 MockedStatic<CxWrapperFactory> mockedFactory) {
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        mockedApp.when(ApplicationManager::getApplication).thenReturn(mockApp);
        mockedFactory.when(CxWrapperFactory::build).thenThrow(new RuntimeException("no connection"));
    }

    /** Creates a real {@link CustomState} with the given name. */
    private CustomState mockCustomState(String name) {
        return new CustomState(null, name, null);
    }

    // -------------------------------------------------------------------------
    // Singleton & initialization
    // -------------------------------------------------------------------------

    @Test
    void getInstance_AndStateInitialization_WorksCorrectly() {
        StateService instance1 = StateService.getInstance();
        StateService instance2 = StateService.getInstance();

        assertNotNull(instance1, "getInstance should return non-null instance");
        assertSame(instance1, instance2, "getInstance should return the same singleton instance");

        Set<CustomResultState> states = instance1.getStates();
        Set<String> defaultLabels = instance1.getDefaultLabels();

        assertNotNull(states, "States should not be null");
        assertNotNull(defaultLabels, "Default labels should not be null");
        assertEquals(8, states.size(), "Should have 8 default states");
        assertEquals(8, defaultLabels.size(), "Should have 8 default labels");

        assertTrue(defaultLabels.contains(CONFIRMED));
        assertTrue(defaultLabels.contains(IGNORE_LABEL));
        assertTrue(defaultLabels.contains(NOT_EXPLOITABLE_LABEL));
        assertTrue(defaultLabels.contains(NOT_IGNORE_LABEL));
        assertTrue(defaultLabels.contains(PROPOSED_NOT_EXPLOITABLE_LABEL));
        assertTrue(defaultLabels.contains(SCA_HIDE_DEV_TEST_DEPENDENCIES));
        assertTrue(defaultLabels.contains(TO_VERIFY));
        assertTrue(defaultLabels.contains(URGENT));

        // States must be sorted alphabetically
        String[] labels = states.stream().map(CustomResultState::getLabel).toArray(String[]::new);
        for (int i = 0; i < labels.length - 1; i++) {
            assertTrue(labels[i].compareTo(labels[i + 1]) <= 0,
                    "States should be sorted: " + labels[i] + " <= " + labels[i + 1]);
        }
    }

    // -------------------------------------------------------------------------
    // getDefaultFilters
    // -------------------------------------------------------------------------

    @Test
    void getDefaultFilters_IncludesAllSeveritiesAndExpectedCustomStates() {
        Set<Filterable> defaultFilters = StateService.getInstance().getDefaultFilters();

        assertNotNull(defaultFilters);
        assertTrue(defaultFilters.size() >= SeverityFilter.DEFAULT_SEVERITIES.size());

        for (Filterable severity : SeverityFilter.DEFAULT_SEVERITIES) {
            assertTrue(defaultFilters.contains(severity), "Missing severity: " + severity);
        }

        assertTrue(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(CONFIRMED)));
        assertTrue(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(TO_VERIFY)));
        assertTrue(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(URGENT)));
        assertTrue(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(IGNORE_LABEL)));
        assertTrue(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(NOT_IGNORE_LABEL)));
    }

    @Test
    void getDefaultFilters_ExcludesNotExploitableProposedAndScaHide() {
        Set<Filterable> defaultFilters = StateService.getInstance().getDefaultFilters();

        assertFalse(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(NOT_EXPLOITABLE_LABEL)),
                "NOT_EXPLOITABLE should be excluded from defaults");

        assertFalse(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(PROPOSED_NOT_EXPLOITABLE_LABEL)),
                "PROPOSED_NOT_EXPLOITABLE should be excluded from defaults");

        assertFalse(defaultFilters.stream().anyMatch(f -> f instanceof CustomResultState
                && ((CustomResultState) f).getLabel().equals(SCA_HIDE_DEV_TEST_DEPENDENCIES)),
                "SCA_HIDE_DEV_TEST_DEPENDENCIES should be excluded from defaults");
    }

    @Test
    void getDefaultFilters_CalledTwice_ReturnsSameSizeEachTime() {
        StateService service = StateService.getInstance();
        Set<Filterable> first = service.getDefaultFilters();
        Set<Filterable> second = service.getDefaultFilters();
        assertEquals(first.size(), second.size(), "getDefaultFilters should be idempotent");
    }

    // -------------------------------------------------------------------------
    // resolveFilterByValue — severity path
    // -------------------------------------------------------------------------

    @Test
    void resolveFilterByValue_SeverityValue_ReturnsSeverityFilter() {
        StateService service = StateService.getInstance();

        Optional<Filterable> result = service.resolveFilterByValue("CRITICAL");

        assertTrue(result.isPresent());
        assertSame(SeverityFilter.CRITICAL, result.get(), "Should resolve CRITICAL to SeverityFilter.CRITICAL");
    }

    @Test
    void resolveFilterByValue_AllDefaultSeverities_ResolvedCorrectly() {
        StateService service = StateService.getInstance();
        String[] severities = {"MALICIOUS", "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"};

        for (String severity : severities) {
            Optional<Filterable> result = service.resolveFilterByValue(severity);
            assertTrue(result.isPresent(), "Should resolve severity: " + severity);
            assertTrue(result.get() instanceof SeverityFilter, severity + " should be a SeverityFilter");
            assertEquals(severity, result.get().getFilterValue());
        }
    }

    // -------------------------------------------------------------------------
    // resolveFilterByValue — known custom state path
    // -------------------------------------------------------------------------

    @Test
    void resolveFilterByValue_KnownCustomState_ReturnsMatchingState() {
        StateService service = StateService.getInstance();

        Optional<Filterable> result = service.resolveFilterByValue(CONFIRMED);

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof CustomResultState);
        assertEquals(CONFIRMED, ((CustomResultState) result.get()).getLabel());
    }

    @Test
    void resolveFilterByValue_AllPredefinedStates_ResolvedToCustomResultState() {
        StateService service = StateService.getInstance();
        String[] predefined = {
                CONFIRMED, IGNORE_LABEL, NOT_EXPLOITABLE_LABEL, NOT_IGNORE_LABEL,
                PROPOSED_NOT_EXPLOITABLE_LABEL, SCA_HIDE_DEV_TEST_DEPENDENCIES, TO_VERIFY, URGENT
        };

        for (String label : predefined) {
            Optional<Filterable> result = service.resolveFilterByValue(label);
            assertTrue(result.isPresent(), "Should resolve predefined state: " + label);
            assertTrue(result.get() instanceof CustomResultState,
                    label + " should resolve to CustomResultState");
            assertEquals(label, result.get().getFilterValue());
        }
    }

    // -------------------------------------------------------------------------
    // resolveFilterByValue — unknown/server-side state path
    // -------------------------------------------------------------------------

    @Test
    void resolveFilterByValue_UnknownValue_CreatesNewCustomResultState() {
        StateService service = StateService.getInstance();

        Optional<Filterable> result = service.resolveFilterByValue("atish3");

        assertTrue(result.isPresent(), "Should never return empty for unknown values");
        assertTrue(result.get() instanceof CustomResultState,
                "Unknown value should fall back to new CustomResultState");
        assertEquals("atish3", result.get().getFilterValue());
    }

    @Test
    void resolveFilterByValue_DifferentUnknownValues_EachCreatesDistinctCustomResultState() {
        StateService service = StateService.getInstance();

        Optional<Filterable> result1 = service.resolveFilterByValue("CustomA");
        Optional<Filterable> result2 = service.resolveFilterByValue("CustomB");

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertNotEquals(result1.get().getFilterValue(), result2.get().getFilterValue());
    }

    // -------------------------------------------------------------------------
    // getStatesNameListForSastTriage
    // -------------------------------------------------------------------------

    @Test
    void getStatesNameListForSastTriage_ExcludesIgnoreAndNotIgnoreAndScaHide() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            List<String> triageList = service.getStatesNameListForSastTriage();

            assertNotNull(triageList);
            assertFalse(triageList.contains(IGNORE_LABEL), "Should exclude IGNORE_LABEL");
            assertFalse(triageList.contains(NOT_IGNORE_LABEL), "Should exclude NOT_IGNORE_LABEL");
            assertFalse(triageList.contains(SCA_HIDE_DEV_TEST_DEPENDENCIES), "Should exclude SCA_HIDE");
        }
    }

    @Test
    void getStatesNameListForSastTriage_IncludesConfirmedAndToVerify() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            List<String> triageList = service.getStatesNameListForSastTriage();

            assertTrue(triageList.contains(CONFIRMED));
            assertTrue(triageList.contains(TO_VERIFY));
        }
    }

    // -------------------------------------------------------------------------
    // getCustomStateFilters — caching & server merge
    // -------------------------------------------------------------------------

    @Test
    void getCustomStateFilters_CalledTwice_ReturnsSameCachedList() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            var filters1 = service.getCustomStateFilters();
            var filters2 = service.getCustomStateFilters();
            assertSame(filters1, filters2, "Second call should return same cached list");
        }
    }

    @Test
    void getCustomStateFilters_ServerReturnsAdditionalCustomState_AppendsToList() throws Exception {
        StateService service = StateService.getInstance();

        CxWrapper mockWrapper = mock(CxWrapper.class);
        when(mockWrapper.triageGetStates(anyBoolean()))
                .thenReturn(List.of(mockCustomState("atish3")));

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class)) {

            Application mockApp = mock(Application.class);
            when(mockApp.getMessageBus()).thenReturn(mock(MessageBus.class));
            mockedApp.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            service.refreshCustomStateFilters();
            var filters = service.getCustomStateFilters();

            // 8 predefined + 1 server-side custom state
            assertEquals(9, filters.size(), "Should have 8 predefined + 1 server-side custom state");
            assertTrue(filters.stream().anyMatch(f -> f.getFilterable().getFilterValue().equals("atish3")),
                    "Should include server-side custom state 'atish3'");
        }
    }

    @Test
    void getCustomStateFilters_ServerReturnsStateAlreadyInDefaults_NotDuplicated() throws Exception {
        StateService service = StateService.getInstance();

        // Server returns a state whose label is already in the 8 predefined defaults
        CxWrapper mockWrapper = mock(CxWrapper.class);
        when(mockWrapper.triageGetStates(anyBoolean()))
                .thenReturn(List.of(mockCustomState(CONFIRMED)));

        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class);
             MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class)) {

            Application mockApp = mock(Application.class);
            when(mockApp.getMessageBus()).thenReturn(mock(MessageBus.class));
            mockedApp.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            service.refreshCustomStateFilters();
            var filters = service.getCustomStateFilters();

            // Should still be only 8 — CONFIRMED not added again
            assertEquals(8, filters.size(), "Predefined state returned by server should not be duplicated");
        }
    }

    @Test
    void getCustomStateFilters_ServerCallFails_FallsBackToDefaultStatesOnly() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            var filters = service.getCustomStateFilters();

            assertNotNull(filters);
            assertEquals(8, filters.size(), "Should fall back to 8 predefined states when server call fails");
        }
    }

    // -------------------------------------------------------------------------
    // refreshCustomStateFilters
    // -------------------------------------------------------------------------

    @Test
    void refreshCustomStateFilters_RebuildsList() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();
            var filters1 = service.getCustomStateFilters();

            service.refreshCustomStateFilters();
            var filters2 = service.getCustomStateFilters();

            assertNotNull(filters2);
            assertNotSame(filters1, filters2, "Refresh should produce a new list instance");
            assertEquals(filters1.size(), filters2.size());
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — null / empty filterValues (no-op)
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_NullFilterValues_DoesNothing() {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getFilterValues()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            // Should complete without calling setFilterValues
            StateService.getInstance().pruneStaleCustomStates();
            verify(mockSettings, never()).setFilterValues(any());
        }
    }

    @Test
    void pruneStaleCustomStates_EmptyFilterValues_DoesNothing() {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        when(mockSettings.getFilterValues()).thenReturn(new LinkedHashSet<>());

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);

            StateService.getInstance().pruneStaleCustomStates();
            verify(mockSettings, never()).setFilterValues(any());
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — server call fails (no-op)
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_ServerCallFails_LeavesFilterValuesUnchanged() {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);

        Set<String> original = new LinkedHashSet<>(Set.of(CONFIRMED, "atish3"));
        when(mockSettings.getFilterValues()).thenReturn(original);

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any()))
                         .thenThrow(new RuntimeException("network error"));

            StateService.getInstance().pruneStaleCustomStates();

            // filterValues must not be modified
            verify(mockSettings, never()).setFilterValues(any());
            verify(mockSettings, never()).setFilters(any());
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — stale custom states removed
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_StaleCustomStateNotOnServer_IsRemoved() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        CxWrapper mockWrapper = mock(CxWrapper.class);

        // filterValues contains a stale custom state "Atish2" not returned by the server
        Set<String> filterValues = new LinkedHashSet<>(Set.of(CONFIRMED, "atish3", "Atish2"));
        when(mockSettings.getFilterValues()).thenReturn(filterValues);
        // Server only knows "atish3"
        when(mockWrapper.triageGetStates(anyBoolean()))
                .thenReturn(List.of(mockCustomState("atish3")));

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            StateService.getInstance().pruneStaleCustomStates();

            // filterValues should be updated without "Atish2"
            verify(mockSettings).setFilterValues(argThat(pruned ->
                    pruned.contains(CONFIRMED)
                    && pruned.contains("atish3")
                    && !pruned.contains("Atish2")));
            // Runtime cache must be invalidated
            verify(mockSettings).setFilters(null);
        }
    }

    @Test
    void pruneStaleCustomStates_MultipleStaleStates_AllRemoved() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        CxWrapper mockWrapper = mock(CxWrapper.class);

        Set<String> filterValues = new LinkedHashSet<>(Set.of(CONFIRMED, "Atish2", "Custom1", "atish3"));
        when(mockSettings.getFilterValues()).thenReturn(filterValues);
        // Server returns no custom states at all
        when(mockWrapper.triageGetStates(anyBoolean())).thenReturn(List.of());

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            StateService.getInstance().pruneStaleCustomStates();

            verify(mockSettings).setFilterValues(argThat(pruned ->
                    pruned.contains(CONFIRMED)
                    && !pruned.contains("Atish2")
                    && !pruned.contains("Custom1")
                    && !pruned.contains("atish3")));
            verify(mockSettings).setFilters(null);
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — severity values always retained
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_SeverityValues_AlwaysRetained() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        CxWrapper mockWrapper = mock(CxWrapper.class);

        // filterValues includes severity values and a stale custom state
        Set<String> filterValues = new LinkedHashSet<>(Set.of("CRITICAL", "HIGH", "staleState"));
        when(mockSettings.getFilterValues()).thenReturn(filterValues);
        // Server returns no custom states
        when(mockWrapper.triageGetStates(anyBoolean())).thenReturn(List.of());

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            StateService.getInstance().pruneStaleCustomStates();

            verify(mockSettings).setFilterValues(argThat(pruned ->
                    pruned.contains("CRITICAL")
                    && pruned.contains("HIGH")
                    && !pruned.contains("staleState")));
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — predefined states always retained
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_PredefinedStates_AlwaysRetainedEvenIfNotOnServer() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        CxWrapper mockWrapper = mock(CxWrapper.class);

        // filterValues has predefined states + stale custom state; server returns nothing
        Set<String> filterValues = new LinkedHashSet<>(
                Set.of(CONFIRMED, NOT_EXPLOITABLE_LABEL, PROPOSED_NOT_EXPLOITABLE_LABEL, "staleCustom"));
        when(mockSettings.getFilterValues()).thenReturn(filterValues);
        when(mockWrapper.triageGetStates(anyBoolean())).thenReturn(List.of());

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            StateService.getInstance().pruneStaleCustomStates();

            verify(mockSettings).setFilterValues(argThat(pruned ->
                    pruned.contains(CONFIRMED)
                    && pruned.contains(NOT_EXPLOITABLE_LABEL)
                    && pruned.contains(PROPOSED_NOT_EXPLOITABLE_LABEL)
                    && !pruned.contains("staleCustom")));
        }
    }

    // -------------------------------------------------------------------------
    // pruneStaleCustomStates — all values valid, no update needed
    // -------------------------------------------------------------------------

    @Test
    void pruneStaleCustomStates_AllValuesValid_NoUpdatePerformed() throws Exception {
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        CxWrapper mockWrapper = mock(CxWrapper.class);

        // filterValues contains only predefined + severity + a custom state that exists on server
        Set<String> filterValues = new LinkedHashSet<>(Set.of(CONFIRMED, "CRITICAL", "atish3"));
        when(mockSettings.getFilterValues()).thenReturn(filterValues);
        when(mockWrapper.triageGetStates(anyBoolean()))
                .thenReturn(List.of(mockCustomState("atish3")));

        try (MockedStatic<GlobalSettingsState> mockedState = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> mockedSensitive = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            mockedState.when(GlobalSettingsState::getInstance).thenReturn(mockSettings);
            mockedSensitive.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mockedFactory.when(() -> CxWrapperFactory.build(any(), any())).thenReturn(mockWrapper);

            StateService.getInstance().pruneStaleCustomStates();

            // Nothing changed — setFilterValues should NOT be called
            verify(mockSettings, never()).setFilterValues(any());
            verify(mockSettings, never()).setFilters(any());
        }
    }
}

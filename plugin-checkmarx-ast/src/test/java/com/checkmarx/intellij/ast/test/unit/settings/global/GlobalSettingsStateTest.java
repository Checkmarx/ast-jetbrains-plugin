package com.checkmarx.intellij.ast.test.unit.settings.global;

import com.checkmarx.intellij.common.settings.FilterProvider;
import com.checkmarx.intellij.common.settings.FilterProviderRegistry;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.openapi.application.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsStateTest {

    @Mock
    private Application mockApplication;

    private GlobalSettingsState globalSettingsState;

    @BeforeEach
    void setUp() {
        // Ensure every test starts without a module-specific provider.
        FilterProviderRegistry.getInstance().registerProvider(null);
        globalSettingsState = new GlobalSettingsState();
    }

    // -------------------------------------------------------------------------
    // getState
    // -------------------------------------------------------------------------

    @Test
    void getState_ReturnsSelf() {
        GlobalSettingsState result = globalSettingsState.getState();
        assertSame(globalSettingsState, result);
    }

    @Test
    void getFilters_WhenFilterValuesMissing_InitializesAndSyncsToFilterValues() {
        // Arrange
        GlobalSettingsState state = new GlobalSettingsState();
        state.setFilterValues(null);

        // Act: getFilters() is the initialisation entry point (not getState())
        Set<Filterable> filters = state.getFilters();

        // getState() should now sync filterValues from the resolved set
        state.getState();

        assertNotNull(state.getFilterValues());
        assertFalse(state.getFilterValues().isEmpty());
        assertEquals(filterValues(filters), state.getFilterValues());
    }

    // -------------------------------------------------------------------------
    // constructor
    // -------------------------------------------------------------------------

    @Test
    void constructor_InitializesDefaultValues() {
        assertEquals("", globalSettingsState.getAdditionalParameters());
        assertFalse(globalSettingsState.isAsca());
        // No provider registered: fallback defaults = DEFAULT_SEVERITIES only
        assertEquals(SeverityFilter.DEFAULT_SEVERITIES, globalSettingsState.getFilters());
    }

    // -------------------------------------------------------------------------
    // loadState — filter persistence
    // -------------------------------------------------------------------------

    @Test
    void loadState_CopiesNonFilterFieldsCorrectly() {
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);

        globalSettingsState.loadState(newState);

        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
    }

    @Test
    void loadState_WhenPersistedFiltersEmpty_FallsBackToDefaults() {
        // Empty filterValues = user cleared everything → treat as first launch
        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>());

        globalSettingsState.loadState(persisted);

        // No provider: defaults = DEFAULT_SEVERITIES
        assertEquals(SeverityFilter.DEFAULT_SEVERITIES, globalSettingsState.getFilters());
        globalSettingsState.getState();
        assertEquals(filterValues(SeverityFilter.DEFAULT_SEVERITIES), globalSettingsState.getFilterValues());
    }

    @Test
    void loadState_WhenProviderRegistered_RestoresExactPersistedFilters() {
        // Both categories present in storage → restore exactly what was saved
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));

        globalSettingsState.loadState(persisted);

        assertEquals(Set.of("HIGH", "TO_VERIFY"), filterValues(globalSettingsState.getFilters()));
    }

    @Test
    void loadState_AddsDefaultSeverities_WhenNoneStoredButCustomStatesPresent() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        // Only a custom state in storage — no severity
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("TO_VERIFY")));

        globalSettingsState.loadState(persisted);

        Set<String> result = filterValues(globalSettingsState.getFilters());
        // Default severities should be added alongside the persisted custom state
        assertTrue(result.containsAll(filterValues(SeverityFilter.DEFAULT_SEVERITIES)));
        assertTrue(result.contains("TO_VERIFY"));
    }

    @Test
    void loadState_AddsDefaultCustomStates_WhenNoneStoredButSeveritiesPresent() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        // Only a severity in storage — no custom state
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH")));

        globalSettingsState.loadState(persisted);

        Set<String> result = filterValues(globalSettingsState.getFilters());
        // Custom state defaults should be added alongside the persisted severity
        assertTrue(result.contains("HIGH"));
        assertTrue(result.contains("TO_VERIFY")); // default custom state from TestFilterProvider
    }

    // -------------------------------------------------------------------------
    // apply
    // -------------------------------------------------------------------------

    @Test
    void apply_LoadsStateCorrectly() {
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);

        globalSettingsState.apply(newState);

        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
    }

    // -------------------------------------------------------------------------
    // setFilters — minimum-per-category enforcement
    // -------------------------------------------------------------------------

    @Test
    void setFilters_PreservesSelection_WhenBothCategoriesPresent() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        // Start: persisted { HIGH, TO_VERIFY }
        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));
        globalSettingsState.loadState(persisted);

        // User removes TO_VERIFY but there is still a custom-state: none here, so defaults added.
        // Let's keep HIGH + TO_VERIFY and remove HIGH instead to keep at least one custom state.
        Set<Filterable> updatedFilters = globalSettingsState.getFilters().stream()
                .filter(f -> !"HIGH".equals(f.getFilterValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        globalSettingsState.setFilters(updatedFilters);

        Set<String> result = filterValues(globalSettingsState.getFilters());
        // TO_VERIFY is still there; default severities are added because HIGH was the only severity
        assertTrue(result.containsAll(filterValues(SeverityFilter.DEFAULT_SEVERITIES)));
        assertTrue(result.contains("TO_VERIFY"));
    }

    @Test
    void setFilters_AddsDefaultCustomStates_WhenAllCustomStatesRemoved() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));
        globalSettingsState.loadState(persisted);

        // User removes the only custom-state filter
        Set<Filterable> severityOnly = globalSettingsState.getFilters().stream()
                .filter(f -> f instanceof SeverityFilter)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        globalSettingsState.setFilters(severityOnly);

        Set<String> result = filterValues(globalSettingsState.getFilters());
        // Default custom states must be re-added (requirement 4 / 6)
        assertTrue(result.contains("TO_VERIFY"));
        // The user's severity selection is preserved
        assertTrue(result.contains("HIGH"));

        // Persisted values must be consistent
        globalSettingsState.getState();
        assertEquals(result, globalSettingsState.getFilterValues());
    }

    @Test
    void setFilters_AddsDefaultSeverities_WhenAllSeveritiesRemoved() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));
        globalSettingsState.loadState(persisted);

        // User removes ALL severity filters
        Set<Filterable> customOnly = globalSettingsState.getFilters().stream()
                .filter(f -> !(f instanceof SeverityFilter))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        globalSettingsState.setFilters(customOnly);

        Set<String> result = filterValues(globalSettingsState.getFilters());
        // Default severities must be re-added (requirement 5 / 6)
        assertTrue(result.containsAll(filterValues(SeverityFilter.DEFAULT_SEVERITIES)));
        // The custom-state selection is preserved
        assertTrue(result.contains("TO_VERIFY"));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Set<String> filterValues(Set<Filterable> filters) {
        return filters.stream().map(Filterable::getFilterValue).collect(Collectors.toSet());
    }

    /**
     * A simple FilterProvider used by tests.
     * Defaults = all DEFAULT_SEVERITIES + a single custom state "TO_VERIFY".
     */
    private static class TestFilterProvider implements FilterProvider {
        private static final Filterable TO_VERIFY_FILTER = new Filterable() {
            @Override
            public Supplier<String> tooltipSupplier() {
                return () -> "To Verify";
            }

            @Override
            public String getFilterValue() {
                return "TO_VERIFY";
            }
        };

        @Override
        public Set<Filterable> getDefaultFilters() {
            Set<Filterable> defaults = new HashSet<>(SeverityFilter.DEFAULT_SEVERITIES);
            defaults.add(TO_VERIFY_FILTER);
            return defaults;
        }

        @Override
        public Optional<Filterable> resolveFilterByValue(String filterValue) {
            if ("TO_VERIFY".equals(filterValue)) {
                return Optional.of(TO_VERIFY_FILTER);
            }
            return Optional.empty();
        }
    }
}

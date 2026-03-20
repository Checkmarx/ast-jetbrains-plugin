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

    @Test
    void getState_ReturnsSelf() {
        // Act
        GlobalSettingsState result = globalSettingsState.getState();

        // Assert
        assertSame(globalSettingsState, result);
    }

    @Test
    void loadState_CopiesStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);
        Set<Filterable> newFilters = new HashSet<>();
        newFilters.add(SeverityFilter.HIGH);
        newState.setFilters(newFilters);

        // Act
        globalSettingsState.loadState(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
        assertEquals(newFilters, globalSettingsState.getFilters());
    }

    @Test
    void apply_LoadsStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);

        // Act
        globalSettingsState.apply(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
    }


    @Test
    void constructor_InitializesDefaultValues() {
        assertEquals("", globalSettingsState.getAdditionalParameters());
        assertFalse(globalSettingsState.isAsca());
        assertEquals(SeverityFilter.DEFAULT_SEVERITIES, globalSettingsState.getFilters());
    }

    @Test
    void getState_WhenPersistedFiltersMissing_InitializesDefaultFilterValues() {
        GlobalSettingsState state = new GlobalSettingsState();
        state.setFilterValues(null);

        state.getState();

        assertNotNull(state.getFilterValues());
        assertEquals(filterValues(state.getFilters()), state.getFilterValues());
        assertFalse(state.getFilterValues().isEmpty());
    }

    @Test
    void loadState_WhenPersistedFiltersEmpty_HonorsUserDeselection() {
        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>());

        globalSettingsState.loadState(persisted);

        assertTrue(globalSettingsState.getFilters().isEmpty());
        globalSettingsState.getState();
        assertEquals(new LinkedHashSet<>(), globalSettingsState.getFilterValues());
    }

    @Test
    void loadState_PreservesPersistedCustomFiltersUntilProviderIsRegistered() {
        // Arrange: persisted state from previous IDE run has severity + custom state.
        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));

        globalSettingsState.loadState(persisted);

        // Without provider, only severity can be resolved at runtime.
        assertEquals(Set.of("HIGH"), filterValues(globalSettingsState.getFilters()));

        // Act: save before provider exists; persisted custom value must not be dropped.
        globalSettingsState.getState();

        // Assert: both values are still persisted for later restoration.
        assertEquals(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")), globalSettingsState.getFilterValues());

        // Register provider late (typical after plugin services initialize).
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        // Next access should recover the missing custom selection from persisted values.
        assertEquals(Set.of("HIGH", "TO_VERIFY"), filterValues(globalSettingsState.getFilters()));
    }

    @Test
    void setFilters_WhenCustomStateDeselected_PersistsAndKeepsUserSelection() {
        FilterProviderRegistry.getInstance().registerProvider(new TestFilterProvider());

        GlobalSettingsState persisted = new GlobalSettingsState();
        persisted.setFilterValues(new LinkedHashSet<>(Set.of("HIGH", "TO_VERIFY")));
        globalSettingsState.loadState(persisted);

        Set<Filterable> updatedFilters = globalSettingsState.getFilters().stream()
                .filter(filter -> !"TO_VERIFY".equals(filter.getFilterValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        globalSettingsState.setFilters(updatedFilters);

        assertEquals(Set.of("HIGH"), filterValues(globalSettingsState.getFilters()));
        assertEquals(new LinkedHashSet<>(Set.of("HIGH")), globalSettingsState.getFilterValues());

        globalSettingsState.getState();
        assertEquals(Set.of("HIGH"), filterValues(globalSettingsState.getFilters()));
        assertEquals(new LinkedHashSet<>(Set.of("HIGH")), globalSettingsState.getFilterValues());
    }

    private Set<String> filterValues(Set<Filterable> filters) {
        return filters.stream().map(Filterable::getFilterValue).collect(Collectors.toSet());
    }

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
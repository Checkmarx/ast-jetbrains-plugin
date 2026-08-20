package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilterProviderRegistryTest {

    @Mock
    private FilterProvider mockProvider;

    @BeforeEach
    void resetRegistry() {
        // Re-register null to clear any provider left from a previous test
        FilterProviderRegistry.getInstance().registerProvider(null);
    }

    @Test
    @DisplayName("getInstance returns a non-null singleton")
    void getInstance_returnsNonNull() {
        assertNotNull(FilterProviderRegistry.getInstance());
    }

    @Test
    @DisplayName("getInstance always returns the same object")
    void getInstance_returnsSameInstance() {
        assertSame(FilterProviderRegistry.getInstance(), FilterProviderRegistry.getInstance());
    }

    @Test
    @DisplayName("hasProvider returns false when no provider is registered")
    void hasProvider_noProvider_returnsFalse() {
        assertFalse(FilterProviderRegistry.getInstance().hasProvider());
    }

    @Test
    @DisplayName("hasProvider returns true after registerProvider is called")
    void hasProvider_afterRegister_returnsTrue() {
        FilterProviderRegistry.getInstance().registerProvider(mockProvider);
        assertTrue(FilterProviderRegistry.getInstance().hasProvider());
    }

    @Test
    @DisplayName("getDefaultFilters returns non-empty set from fallback when no provider registered")
    void getDefaultFilters_noProvider_returnsFallbackSet() {
        Set<Filterable> filters = FilterProviderRegistry.getInstance().getDefaultFilters();

        assertNotNull(filters);
        assertFalse(filters.isEmpty());
    }

    @Test
    @DisplayName("getDefaultFilters delegates to registered provider")
    void getDefaultFilters_withProvider_delegatesToProvider() {
        Filterable mockFilter = mock(Filterable.class);
        when(mockProvider.getDefaultFilters()).thenReturn(Set.of(mockFilter));
        FilterProviderRegistry.getInstance().registerProvider(mockProvider);

        Set<Filterable> filters = FilterProviderRegistry.getInstance().getDefaultFilters();

        assertEquals(1, filters.size());
        assertTrue(filters.contains(mockFilter));
    }

    @Test
    @DisplayName("resolveFilterByValue falls back to SeverityFilter for valid enum name")
    void resolveFilterByValue_validSeverity_noProvider_returnsPresent() {
        Optional<Filterable> result = FilterProviderRegistry.getInstance().resolveFilterByValue("HIGH");

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("resolveFilterByValue returns empty for unknown value when no provider registered")
    void resolveFilterByValue_unknownValue_noProvider_returnsEmpty() {
        Optional<Filterable> result = FilterProviderRegistry.getInstance().resolveFilterByValue("UNKNOWN_FILTER");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("resolveFilterByValue delegates to provider first")
    void resolveFilterByValue_withProvider_delegatesToProvider() {
        Filterable mockFilter = mock(Filterable.class);
        when(mockProvider.resolveFilterByValue("CUSTOM")).thenReturn(Optional.of(mockFilter));
        FilterProviderRegistry.getInstance().registerProvider(mockProvider);

        Optional<Filterable> result = FilterProviderRegistry.getInstance().resolveFilterByValue("CUSTOM");

        assertTrue(result.isPresent());
        assertSame(mockFilter, result.get());
    }
}

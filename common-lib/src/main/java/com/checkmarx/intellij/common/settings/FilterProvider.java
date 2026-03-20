package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.window.actions.filter.Filterable;

import java.util.Optional;
import java.util.Set;

/**
 * Interface for providing default filters to GlobalSettingsState.
 * This allows the common-lib module to remain independent of plugin-specific implementations.
 *
 * Plugin modules (like plugin-checkmarx-ast) can implement this interface and register
 * their implementation via FilterProviderRegistry.
 */
public interface FilterProvider {

    /**
     * Returns the default set of filters to be used by GlobalSettingsState.
     * This typically includes severity filters and custom state filters.
     *
     * @return Set of default Filterable objects
     */
    Set<Filterable> getDefaultFilters();

    /**
     * Resolves a persisted filter value string back to a Filterable instance.
     * Plugin modules should override this to handle their specific filter types.
     *
     * @param filterValue the value returned by {@link Filterable#getFilterValue()}
     * @return Optional containing the matching Filterable, or empty if not recognized
     */
    default Optional<Filterable> resolveFilterByValue(String filterValue) {
        return Optional.empty();
    }
}


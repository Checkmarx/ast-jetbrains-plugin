package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for managing the FilterProvider implementation.
 * This allows plugin modules to register their filter provider implementation
 * while keeping common-lib independent of plugin-specific code.
 * 
 * Uses a singleton pattern with lazy initialization.
 */
public class FilterProviderRegistry {
    
    private static final FilterProviderRegistry INSTANCE = new FilterProviderRegistry();
    
    private FilterProvider provider;
    
    private FilterProviderRegistry() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance of the registry.
     * 
     * @return The FilterProviderRegistry instance
     */
    public static FilterProviderRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Registers a FilterProvider implementation.
     * This should be called by plugin modules during initialization.
     * 
     * @param provider The FilterProvider implementation to register
     */
    public void registerProvider(FilterProvider provider) {
        this.provider = provider;
    }
    
    /**
     * Gets the default filters from the registered provider.
     * If no provider is registered, returns a basic set of severity filters.
     * 
     * @return Set of default Filterable objects
     */
    public Set<Filterable> getDefaultFilters() {
        if (provider != null) {
            return provider.getDefaultFilters();
        }
        
        // Fallback: return basic severity filters if no provider is registered
        return getBasicSeverityFilters();
    }
    
    /**
     * Returns a basic set of severity filters as a fallback.
     * This ensures GlobalSettingsState can function even if no provider is registered.
     * Uses SeverityFilter.DEFAULT_SEVERITIES which includes MALICIOUS, CRITICAL, HIGH, MEDIUM, LOW.
     * 
     * @return Set of basic severity filters
     */
    private Set<Filterable> getBasicSeverityFilters() {
        return new HashSet<>(SeverityFilter.DEFAULT_SEVERITIES);
    }
    
    /**
     * Checks if a provider has been registered.
     * 
     * @return true if a provider is registered, false otherwise
     */
    public boolean hasProvider() {
        return provider != null;
    }
}


package com.checkmarx.intellij.devassist.remediation.providers;

import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Registry for managing available AI providers.
 * 
 * <p>
 * This singleton maintains a list of registered AI providers and provides
 * methods to query available providers based on the current IDE state and
 * user preferences.
 * 
 * <p>
 * Providers are registered at plugin startup and can be queried by:
 * <ul>
 * <li>Availability (is the provider's plugin installed?)</li>
 * <li>User preference (which provider did the user select?)</li>
 * <li>Priority (automatic selection order)</li>
 * </ul>
 */
public final class AIProviderRegistry {

    private static final Logger LOGGER = Utils.getLogger(AIProviderRegistry.class);
    private static final AIProviderRegistry INSTANCE = new AIProviderRegistry();

    /** Auto-select provider based on availability and priority */
    public static final String AUTO_PROVIDER_ID = "auto";

    private final List<AIProvider> providers = new CopyOnWriteArrayList<>();

    private AIProviderRegistry() {
        // Private constructor for singleton
    }

    /**
     * Returns the singleton instance of the registry.
     */
    public static AIProviderRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a new AI provider.
     * Providers are automatically sorted by priority after registration.
     * 
     * @param provider the provider to register
     */
    public void registerProvider(@NotNull AIProvider provider) {
        LOGGER.info("AI Provider Registry: Registering provider '" + provider.getId() +
                "' (" + provider.getDisplayName() + ")");
        providers.add(provider);
        providers.sort(Comparator.comparingInt(AIProvider::getPriority));
    }

    /**
     * Unregisters an AI provider by its ID.
     * 
     * @param providerId the ID of the provider to unregister
     */
    public void unregisterProvider(@NotNull String providerId) {
        providers.removeIf(p -> p.getId().equals(providerId));
    }

    /**
     * Returns all registered providers.
     */
    public List<AIProvider> getAllProviders() {
        return List.copyOf(providers);
    }

    /**
     * Returns all providers that are currently available in the IDE.
     * 
     * @param project the project context
     * @return list of available providers, sorted by priority
     */
    public List<AIProvider> getAvailableProviders(@Nullable Project project) {
        return providers.stream()
                .filter(p -> p.isAvailable(project))
                .sorted(Comparator.comparingInt(AIProvider::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * Gets a provider by its ID.
     * 
     * @param providerId the provider ID
     * @return the provider, or empty if not found
     */
    public Optional<AIProvider> getProviderById(@NotNull String providerId) {
        return providers.stream()
                .filter(p -> p.getId().equals(providerId))
                .findFirst();
    }

    /**
     * Gets the preferred provider based on availability.
     * 
     * <p>
     * Returns the highest-priority available provider (currently only Copilot).
     * 
     * @param project the project context
     * @return the preferred provider, or empty if none available
     */
    public Optional<AIProvider> getPreferredProvider(@Nullable Project project) {
        // Return first available provider by priority (currently only Copilot)
        return getAvailableProviders(project).stream().findFirst();
    }

    /**
     * Checks if any AI provider is available.
     * 
     * @param project the project context
     * @return true if at least one provider is available
     */
    public boolean hasAvailableProvider(@Nullable Project project) {
        return providers.stream().anyMatch(p -> p.isAvailable(project));
    }

    /**
     * Clears all registered providers.
     * Primarily used for testing.
     */
    public void clear() {
        providers.clear();
    }
}

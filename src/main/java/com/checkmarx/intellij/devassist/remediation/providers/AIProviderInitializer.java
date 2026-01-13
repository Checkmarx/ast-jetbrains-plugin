package com.checkmarx.intellij.devassist.remediation.providers;

import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Initializes and registers all AI providers with the registry.
 * 
 * <p>
 * This class should be called during plugin startup to register
 * all available AI providers for the "Fix with AI" feature.
 */
public final class AIProviderInitializer {

    private static final Logger LOGGER = Utils.getLogger(AIProviderInitializer.class);
    private static boolean initialized = false;

    private AIProviderInitializer() {
        // Prevent instantiation
    }

    /**
     * Initializes and registers all AI providers.
     * This method is idempotent - calling it multiple times has no effect.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.info("AI Provider Initializer: Registering AI providers");

        AIProviderRegistry registry = AIProviderRegistry.getInstance();

        // Register Copilot provider (only supported provider for now)
        registry.registerProvider(new CopilotProvider()); // Priority 100

        initialized = true;
        LOGGER.info("AI Provider Initializer: Registered " + registry.getAllProviders().size() + " providers");
    }

    /**
     * Resets the initializer state.
     * Primarily used for testing.
     */
    public static synchronized void reset() {
        AIProviderRegistry.getInstance().clear();
        initialized = false;
    }
}

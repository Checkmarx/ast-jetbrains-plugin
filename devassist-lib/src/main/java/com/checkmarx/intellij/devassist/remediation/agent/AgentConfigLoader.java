package com.checkmarx.intellij.devassist.remediation.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application-level service that loads, caches, and provides AI agent definitions from
 * {@code agents-config.json}.
 * <p>
 * Load order:
 * <ol>
 *   <li>External override: {@code ~/.checkmarx/agents-config.json}</li>
 *   <li>Bundled fallback: {@code /agents-config.json} resource in plugin JAR</li>
 * </ol>
 * <p>
 * Only enabled agents are returned, sorted by priority (ascending = higher preference).
 * <p>
 * Register in {@code plugin.xml} as:
 * <pre>{@code <applicationService serviceImplementation="...AgentConfigLoader"/>}</pre>
 */
@Service(Service.Level.APP)
public final class AgentConfigLoader {

    private static final Logger LOG = Logger.getInstance(AgentConfigLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXTERNAL_CONFIG_DIR = ".checkmarx";
    private static final String CONFIG_FILE_NAME = "agents-config.json";
    private static final String BUNDLED_RESOURCE_PATH = "/" + CONFIG_FILE_NAME;

    private volatile List<AgentDefinition> agents;

    public AgentConfigLoader() {
        // Lazy-load on first access
    }

    /**
     * Returns the singleton instance.
     */
    public static AgentConfigLoader getInstance() {
        return ApplicationManager.getApplication().getService(AgentConfigLoader.class);
    }

    /**
     * Returns all enabled agent definitions, sorted by priority (lowest first = highest preference).
     * Loads from disk on first call; cached thereafter.
     */
    @NotNull
    public List<AgentDefinition> getAgents() {
        if (agents == null) {
            synchronized (this) {
                if (agents == null) {
                    load();
                }
            }
        }
        return agents;
    }

    /**
     * Forces a reload of the configuration from disk.
     * Call this if the external config file was modified at runtime.
     */
    public void reload() {
        synchronized (this) {
            load();
        }
    }

    // ==================== Loading ====================

    private void load() {
        List<AgentDefinition> loaded = null;

        // 1. Try external override
        Path externalPath = resolveExternalConfigPath();
        if (Files.exists(externalPath)) {
            LOG.info("Loading agent config from external file: " + externalPath);
            loaded = parseFile(externalPath);
        }

        // 2. Fallback to bundled resource
        if (loaded == null || loaded.isEmpty()) {
            LOG.info("Loading agent config from bundled resource: " + BUNDLED_RESOURCE_PATH);
            loaded = parseResource(BUNDLED_RESOURCE_PATH);
        }

        // 3. Filter enabled, sort by priority
        if (loaded != null) {
            agents = loaded.stream()
                    .filter(AgentDefinition::isEnabled)
                    .sorted(Comparator.comparingInt(AgentDefinition::getPriority))
                    .collect(Collectors.toList());
            LOG.info("Loaded " + agents.size() + " enabled agent definition(s): " +
                    agents.stream().map(AgentDefinition::getId).collect(Collectors.joining(", ")));
        } else {
            agents = Collections.emptyList();
            LOG.warn("No agent definitions loaded -- AI remediation will fall back to clipboard.");
        }
    }

    private static Path resolveExternalConfigPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, EXTERNAL_CONFIG_DIR, CONFIG_FILE_NAME);
    }

    private static List<AgentDefinition> parseFile(Path path) {
        try {
            String content = Files.readString(path);
            return parseJson(content);
        } catch (Exception e) {
            LOG.warn("Failed to parse external agent config at " + path + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static List<AgentDefinition> parseResource(String resourcePath) {
        try (InputStream is = AgentConfigLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.warn("Bundled agent config resource not found: " + resourcePath);
                return null;
            }
            String content = new String(is.readAllBytes());
            return parseJson(content);
        } catch (Exception e) {
            LOG.warn("Failed to parse bundled agent config: " + e.getMessage(), e);
            return null;
        }
    }

    private static List<AgentDefinition> parseJson(String json) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode agentsNode = root.get("agents");
        if (agentsNode == null || !agentsNode.isArray()) {
            LOG.warn("Agent config JSON missing 'agents' array");
            return Collections.emptyList();
        }
        return MAPPER.readValue(
                agentsNode.toString(),
                new TypeReference<List<AgentDefinition>>() {}
        );
    }
}

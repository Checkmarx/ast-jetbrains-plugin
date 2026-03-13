package com.checkmarx.intellij.devassist.remediation.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration-driven MCP server installer that manages Checkmarx MCP entries for ALL
 * registered AI agents.
 * <p>
 * For each agent defined in {@code agents-config.json}, this class:
 * <ul>
 *   <li>Resolves the agent's MCP config file path (OS-aware, with environment variable expansion)</li>
 *   <li>Reads the existing JSON config (or creates a new one)</li>
 *   <li>Merges/removes the Checkmarx server entry using the agent's configured template</li>
 *   <li>Writes the updated JSON back</li>
 * </ul>
 * <p>
 * NO agent-specific code. All behavior is driven by the MCP section of each {@link AgentDefinition}.
 */
public final class GenericMcpInstaller {

    private static final Logger LOG = Logger.getInstance(GenericMcpInstaller.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FALLBACK_BASE = "https://ast-master-components.dev.cxast.net";
    private static final String MCP_ENDPOINT_PATH = "/api/security-mcp/mcp";

    private GenericMcpInstaller() {
        // Static utility class
    }

    // ==================== Public API ====================

    /**
     * Installs MCP configuration for ALL enabled agents.
     *
     * @param token the JWT / API key for Authorization header
     * @return true if any agent's config was modified
     */
    public static boolean installForAllAgents(@NotNull String token) {
        String issuer = tryExtractIssuer(token);
        String baseUrl = deriveBaseUrl(issuer);
        String mcpUrl = baseUrl + MCP_ENDPOINT_PATH;
        boolean anyChanged = false;

        List<AgentDefinition> agents = AgentConfigLoader.getInstance().getAgents();
        LOG.info("MCP install: processing " + agents.size() + " agent(s)");

        for (AgentDefinition agent : agents) {
            try {
                boolean changed = installForAgent(agent, mcpUrl, token);
                anyChanged |= changed;
                LOG.info("MCP install for " + agent.getDisplayName() + ": " + (changed ? "updated" : "unchanged"));
            } catch (Exception e) {
                LOG.warn("MCP install failed for " + agent.getDisplayName() + ": " + e.getMessage(), e);
            }
        }
        return anyChanged;
    }

    /**
     * Uninstalls MCP configuration from ALL agents.
     *
     * @return true if any agent's config was modified
     */
    public static boolean uninstallFromAllAgents() {
        boolean anyRemoved = false;

        List<AgentDefinition> agents = AgentConfigLoader.getInstance().getAgents();
        for (AgentDefinition agent : agents) {
            try {
                boolean removed = uninstallFromAgent(agent);
                anyRemoved |= removed;
                LOG.info("MCP uninstall for " + agent.getDisplayName() + ": " + (removed ? "removed" : "nothing to remove"));
            } catch (Exception e) {
                LOG.warn("MCP uninstall failed for " + agent.getDisplayName() + ": " + e.getMessage(), e);
            }
        }
        return anyRemoved;
    }

    // ==================== Per-Agent Install/Uninstall ====================

    @SuppressWarnings("unchecked")
    private static boolean installForAgent(@NotNull AgentDefinition agent, @NotNull String mcpUrl, @NotNull String token) throws Exception {
        AgentDefinition.McpConfig mcp = agent.getMcp();
        if (mcp == null || mcp.getConfigPath() == null) {
            LOG.debug("MCP install skipped for " + agent.getId() + ": no MCP config defined");
            return false;
        }

        Path configPath = resolveConfigPath(mcp.getConfigPath());
        if (configPath == null) {
            LOG.debug("MCP install skipped for " + agent.getId() + ": could not resolve config path");
            return false;
        }

        // Read existing JSON or create empty root
        Map<String, Object> root = readJsonSafe(configPath);

        // Navigate to the root property (e.g., "servers" for Copilot, "mcpServers" for Claude)
        String rootProp = mcp.getRootProperty();
        Map<String, Object> serversMap = (Map<String, Object>) root.computeIfAbsent(rootProp, k -> new LinkedHashMap<>());

        // Build the server entry from the template with placeholder substitution
        Map<String, String> substitutions = new LinkedHashMap<>();
        substitutions.put("${mcp_url}", mcpUrl);
        substitutions.put("${token}", token);
        Map<String, Object> entry = deepCopyWithSubstitution(mcp.getEntryTemplate(), substitutions);

        // Check if unchanged
        Map<String, Object> existing = (Map<String, Object>) serversMap.get(mcp.getServerKey());
        if (Objects.equals(existing, entry)) {
            return false; // no change needed
        }

        // Write updated config
        serversMap.put(mcp.getServerKey(), entry);
        root.put(rootProp, serversMap);

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        LOG.info("MCP config written for " + agent.getDisplayName() + " at: " + configPath);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean uninstallFromAgent(@NotNull AgentDefinition agent) throws Exception {
        AgentDefinition.McpConfig mcp = agent.getMcp();
        if (mcp == null || mcp.getConfigPath() == null) return false;

        Path configPath = resolveConfigPath(mcp.getConfigPath());
        if (configPath == null || !Files.exists(configPath)) return false;

        Map<String, Object> root = readJsonSafe(configPath);
        Object serversObj = root.get(mcp.getRootProperty());
        if (!(serversObj instanceof Map)) return false;

        Map<String, Object> servers = (Map<String, Object>) serversObj;
        boolean removed = servers.remove(mcp.getServerKey()) != null;
        if (!removed) return false;

        root.put(mcp.getRootProperty(), servers);
        Files.writeString(configPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        LOG.info("MCP config removed for " + agent.getDisplayName() + " at: " + configPath);
        return true;
    }

    // ==================== Path Resolution ====================

    /**
     * Resolves the OS-specific config path with environment variable expansion.
     * Supports: {@code ${ENV_VAR}}, {@code ${ENV_VAR:-default}}, and {@code ~} for home directory.
     */
    @Nullable
    static Path resolveConfigPath(@NotNull AgentDefinition.McpConfigPath configPath) {
        String rawPath = getOsSpecificPath(configPath);
        if (rawPath == null || rawPath.isEmpty()) return null;

        String expanded = expandEnvironmentVariables(rawPath);
        return Path.of(expanded);
    }

    private static String getOsSpecificPath(@NotNull AgentDefinition.McpConfigPath configPath) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        if (os.contains("win")) {
            return configPath.getWindows();
        } else if (os.contains("mac")) {
            return configPath.getMacos();
        } else {
            return configPath.getLinux();
        }
    }

    /**
     * Expands environment variables in a path string.
     * <ul>
     *   <li>{@code ${VAR}} → value of VAR</li>
     *   <li>{@code ${VAR:-default}} → value of VAR, or "default" if VAR is unset/blank</li>
     *   <li>{@code ~} → user home directory</li>
     * </ul>
     */
    static String expandEnvironmentVariables(@NotNull String path) {
        String result = path;

        // Expand ${VAR:-default} patterns
        while (result.contains("${")) {
            int start = result.indexOf("${");
            int end = result.indexOf("}", start);
            if (end < 0) break;

            String varExpr = result.substring(start + 2, end);
            String varName;
            String defaultValue;

            int colonDash = varExpr.indexOf(":-");
            if (colonDash >= 0) {
                varName = varExpr.substring(0, colonDash);
                defaultValue = varExpr.substring(colonDash + 2);
            } else {
                varName = varExpr;
                defaultValue = "";
            }

            String envValue = System.getenv(varName);
            String replacement;
            if (envValue != null && !envValue.isBlank()) {
                replacement = envValue;
            } else if (!defaultValue.isEmpty()) {
                replacement = expandEnvironmentVariables(defaultValue); // recursive expansion
            } else {
                replacement = "";
            }

            result = result.substring(0, start) + replacement + result.substring(end + 1);
        }

        // Expand ~ to user home
        if (result.startsWith("~")) {
            String home = System.getProperty("user.home");
            result = home + result.substring(1);
        }

        return result;
    }

    // ==================== Template Substitution ====================

    /**
     * Deep-copies a map structure, replacing all string values that match placeholders.
     * This processes the {@code entry_template} from the agent's MCP config.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopyWithSubstitution(
            @NotNull Map<String, Object> template,
            @NotNull Map<String, String> substitutions) {

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                for (Map.Entry<String, String> sub : substitutions.entrySet()) {
                    strValue = strValue.replace(sub.getKey(), sub.getValue());
                }
                result.put(entry.getKey(), strValue);
            } else if (value instanceof Map) {
                result.put(entry.getKey(), deepCopyWithSubstitution((Map<String, Object>) value, substitutions));
            } else {
                result.put(entry.getKey(), value); // numbers, booleans, etc.
            }
        }
        return result;
    }

    // ==================== JSON I/O ====================

    private static Map<String, Object> readJsonSafe(@NotNull Path path) {
        if (!Files.exists(path)) return new LinkedHashMap<>();
        try {
            String content = stripLineComments(Files.readString(path));
            Map<String, Object> map = MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            return (map != null) ? map : new LinkedHashMap<>();
        } catch (Exception e) {
            LOG.warn("Failed to read existing MCP config at " + path + ", starting fresh", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * Strips single-line comments (// ...) from JSON content.
     * Some MCP config files (e.g., Copilot) use line comments that are not valid JSON.
     */
    private static String stripLineComments(@NotNull String content) {
        return content.replaceAll("(?m)^\\s*//.*$", "");
    }

    // ==================== JWT / URL Derivation (reused from McpSettingsInjector) ====================

    /**
     * Extracts the issuer claim from a JWT token's payload.
     */
    @Nullable
    static String tryExtractIssuer(@Nullable String rawToken) {
        if (rawToken == null) return null;
        String[] parts = rawToken.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Object iss = map.get("iss");
            return iss != null ? iss.toString() : null;
        } catch (Exception e) {
            LOG.warn("Failed to parse token payload for issuer", e);
            return null;
        }
    }

    /**
     * Derives the AST base URL from the JWT issuer.
     * Replaces "iam" with "ast" in the issuer hostname if it matches the Checkmarx pattern.
     */
    @NotNull
    static String deriveBaseUrl(@Nullable String issuer) {
        if (issuer == null || issuer.isBlank()) return FALLBACK_BASE;
        try {
            String host = URI.create(issuer).getHost();
            if (host != null && host.contains("iam.checkmarx")) {
                host = host.replace("iam", "ast");
                return "https://" + host;
            }
        } catch (Exception e) {
            LOG.warn("Could not derive AST base URL from issuer: " + issuer, e);
        }
        return FALLBACK_BASE;
    }
}

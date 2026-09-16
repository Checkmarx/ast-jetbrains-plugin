package com.checkmarx.intellij.devassist.configuration.mcp;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class McpSettingsInjector {
    private static final Logger LOG = Logger.getInstance(McpSettingsInjector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FALLBACK_BASE = "https://ast-master-components.dev.cxast.net";

    private McpSettingsInjector() {
    }

    /**
     * Adds or updates the Checkmarx MCP server entry. Returns true if file was updated.
     */
    public static boolean installForCopilot(String token) throws Exception {
        String issuer = tryExtractIssuer(token);
        String baseUrl = deriveBaseUrlFromIssuer(issuer);
        String mcpUrl = baseUrl + "/api/security-mcp/mcp";

        Path cfg = resolveCopilotMcpConfigPath();
        boolean changed = mergeCheckmarxServer(cfg, mcpUrl, token);
        if (changed) {
            LOG.info("Installed/updated Checkmarx MCP for Copilot at: " + cfg);
        } else {
            LOG.debug("MCP config unchanged at: " + cfg);
        }
        return changed;
    }

    /**
     * Removes the Checkmarx MCP server entry. Returns true if removal happened.
     */
    public static boolean uninstallFromCopilot() throws Exception {
        Path cfg = resolveCopilotMcpConfigPath();
        boolean removed = removeCheckmarxServer(cfg);
        if (removed) {
            LOG.info("Removed Checkmarx MCP from Copilot at: " + cfg);
        } else {
            LOG.debug("No Checkmarx MCP entry found to remove at: " + cfg);
        }
        return removed;
    }

    /**
     * Adds or updates the Checkmarx MCP server entry in JetBrains AI Assistant's MCP client
     * configuration. Returns true if the file was updated.
     * <p>
     * AI Assistant (bundled plugin {@code com.intellij.ml.llm}) ships a built-in MCP client
     * backed by the official MCP Kotlin SDK, configured via a {@code mcp.json} file using the
     * conventional {@code mcpServers} schema (the same shape used by Claude Desktop/Cursor),
     * as opposed to Copilot's bespoke {@code servers}/{@code requestInit} shape. The IDE-side
     * registry keys {@code llm.mcp.client.global.mcp.json.path} / {@code ...project.mcp.json.path}
     * both default to the relative path {@code .ai/mcp/mcp.json}; this resolves the global
     * variant against the user's home directory, matching the convention used by comparable
     * MCP-client tools. Verify under Settings | Tools | AI Assistant | Model Context Protocol (MCP)
     * after install, since this file layout is not part of AI Assistant's documented public API.
     */
    public static boolean installForAiAssistant(String token) throws Exception {
        String issuer = tryExtractIssuer(token);
        String baseUrl = deriveBaseUrlFromIssuer(issuer);
        String mcpUrl = baseUrl + "/api/security-mcp/mcp";

        Path cfg = resolveAiAssistantMcpConfigPath();
        boolean changed = mergeCheckmarxServerStandardSchema(cfg, mcpUrl, token);
        if (changed) {
            LOG.info("Installed/updated Checkmarx MCP for AI Assistant at: " + cfg);
        } else {
            LOG.debug("MCP config unchanged at: " + cfg);
        }
        return changed;
    }

    /**
     * Removes the Checkmarx MCP server entry from AI Assistant's MCP configuration.
     */
    public static boolean uninstallFromAiAssistant() throws Exception {
        Path cfg = resolveAiAssistantMcpConfigPath();
        boolean removed = removeCheckmarxServerStandardSchema(cfg);
        if (removed) {
            LOG.info("Removed Checkmarx MCP from AI Assistant at: " + cfg);
        } else {
            LOG.debug("No Checkmarx MCP entry found to remove at: " + cfg);
        }
        return removed;
    }

    /**
     * Public accessor used by UI components to locate AI Assistant's MCP configuration file.
     */
    public static Path getAiAssistantMcpJsonPath() {
        return resolveAiAssistantMcpConfigPath();
    }

    /* ---------- Path resolution ---------- */

    private static Path resolveCopilotMcpConfigPath() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isBlank()) {
                throw new IllegalStateException("%LOCALAPPDATA% is not set on Windows.");
            }
            return Path.of(localAppData, "github-copilot", "intellij", "mcp.json");
        }

        // For macOS and Linux/Unix, use XDG_CONFIG_HOME if set, otherwise fallback to ~/.config
        // This fallback logic resolves to ~/.config/github-copilot/intellij/mcp.json
        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        if (xdgConfig != null && !xdgConfig.isBlank()) {
            return Path.of(xdgConfig, "github-copilot", "intellij", "mcp.json");
        }
        // Fallback to ~/.config/github-copilot/intellij/mcp.json (common on macOS where XDG_CONFIG_HOME is not set)
        Path configBase = Path.of(home, ".config");
        return configBase.resolve(Path.of("github-copilot", "intellij", "mcp.json"));
    }

    /**
     * Resolves AI Assistant's global MCP config path: {@code ~/.ai/mcp/mcp.json}, matching the
     * IDE's default registry value for {@code llm.mcp.client.global.mcp.json.path}.
     */
    private static Path resolveAiAssistantMcpConfigPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".ai", "mcp", "mcp.json");
    }

    /* ---------- Helpers ---------- */

    private static String deriveBaseUrlFromIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) return FALLBACK_BASE;
        try {
            String host = URI.create(issuer).getHost();
            if (host != null && host.contains("iam.")) {
                host = host.replace("iam", "ast");
                return "https://" + host;
            } else if (host != null && !host.isBlank()) {
                return "https://" + host;
            }
        } catch (Exception e) {
            LOG.warn("Could not derive AST base URL from issuer: " + issuer, e);
        }
        return FALLBACK_BASE;
    }

    private static String tryExtractIssuer(String rawToken) {
        if (rawToken == null) return null;
        String[] parts = rawToken.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> map =
                    MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
                    });
            Object iss = map.get("iss");
            return iss != null ? iss.toString() : null;
        } catch (Exception e) {
            LOG.warn("Failed to parse token payload for issuer", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean mergeCheckmarxServer(Path configPath, String url, String token) throws Exception {
        Map<String, Object> root = readJson(configPath);
        Map<String, Object> servers = (Map<String, Object>) root
                .getOrDefault("servers", new LinkedHashMap<>());

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("cx-origin", Constants.JET_BRAINS_AGENT_NAME);
        headers.put("Authorization", token);

        Map<String, Object> serverEntry = new LinkedHashMap<>();
        serverEntry.put("url", url);
        Map<String, Object> requestInit = new LinkedHashMap<>();
        requestInit.put("headers", headers);
        serverEntry.put("requestInit", requestInit);

        String mcpServerKey = getMCPServerKey();

        Map<String, Object> existing = (Map<String, Object>) servers.get(mcpServerKey);
        boolean changed = !Objects.equals(existing, serverEntry);

        if (!changed) {
            return false;
        }

        servers.put(mcpServerKey, serverEntry);
        root.put("servers", servers);

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean removeCheckmarxServer(Path configPath) throws Exception {
        if (!Files.exists(configPath)) return false;

        Map<String, Object> root = readJson(configPath);
        Object serversObj = root.get("servers");
        if (!(serversObj instanceof Map)) return false;

        String mcpServerKey = getMCPServerKey();

        Map<String, Object> servers = (Map<String, Object>) serversObj;
        boolean removed = servers.remove(mcpServerKey) != null;
        if (!removed) {
            return false;
        }

        root.put("servers", servers);
        Files.writeString(configPath,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return true;
    }

    /**
     * Merges the Checkmarx server entry using the standard MCP client {@code mcpServers} schema
     * (flat {@code headers} map, no {@code requestInit} wrapper) used by AI Assistant's built-in
     * MCP client rather than Copilot's bespoke {@code servers} schema.
     */
    @SuppressWarnings("unchecked")
    private static boolean mergeCheckmarxServerStandardSchema(Path configPath, String url, String token) throws Exception {
        Map<String, Object> root = readJson(configPath, "mcpServers");
        Map<String, Object> servers = (Map<String, Object>) root
                .getOrDefault("mcpServers", new LinkedHashMap<>());

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("cx-origin", Constants.JET_BRAINS_AGENT_NAME);
        headers.put("Authorization", token);

        Map<String, Object> serverEntry = new LinkedHashMap<>();
        serverEntry.put("url", url);
        serverEntry.put("headers", headers);

        String mcpServerKey = getMCPServerKey();

        Map<String, Object> existing = (Map<String, Object>) servers.get(mcpServerKey);
        boolean changed = !Objects.equals(existing, serverEntry);

        if (!changed) {
            return false;
        }

        servers.put(mcpServerKey, serverEntry);
        root.put("mcpServers", servers);

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean removeCheckmarxServerStandardSchema(Path configPath) throws Exception {
        if (!Files.exists(configPath)) return false;

        Map<String, Object> root = readJson(configPath, "mcpServers");
        Object serversObj = root.get("mcpServers");
        if (!(serversObj instanceof Map)) return false;

        String mcpServerKey = getMCPServerKey();

        Map<String, Object> servers = (Map<String, Object>) serversObj;
        boolean removed = servers.remove(mcpServerKey) != null;
        if (!removed) {
            return false;
        }

        root.put("mcpServers", servers);
        Files.writeString(configPath,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return true;
    }

    private static Map<String, Object> readJson(Path path, String rootKey) {
        if (!Files.exists(path)) return new LinkedHashMap<>(Collections.singletonMap(rootKey, new LinkedHashMap<>()));
        try {
            String content = stripLineComments(Files.readString(path));
            Map<String, Object> map =
                    MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
                    });
            return (map == null || map.isEmpty())
                    ? new LinkedHashMap<>(Collections.singletonMap(rootKey, new LinkedHashMap<>()))
                    : map;
        } catch (Exception e) {
            LOG.warn("Failed to read existing MCP config, starting fresh", e);
            return new LinkedHashMap<>(Collections.singletonMap(rootKey, new LinkedHashMap<>()));
        }
    }

    private static Map<String, Object> readJson(Path path) {
        if (!Files.exists(path)) return emptyServersRoot();
        try {
            String content = stripLineComments(Files.readString(path));
            Map<String, Object> map =
                    MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
                    });
            return (map == null || map.isEmpty()) ? emptyServersRoot() : map;
        } catch (Exception e) {
            LOG.warn("Failed to read existing Copilot MCP config, starting fresh", e);
            return emptyServersRoot();
        }
    }

    private static Map<String, Object> emptyServersRoot() {
        return new LinkedHashMap<>(Collections.singletonMap("servers", new LinkedHashMap<>()));
    }

    private static String stripLineComments(String s) {
        return s.replaceAll("(?m)^\\s*//.*$", "");
    }

    /**
     * Public accessor used by UI components to locate the MCP configuration file.
     */
    public static Path getMcpJsonPath() {
        return resolveCopilotMcpConfigPath();
    }

    /**
     * Get mcp server key to register in mcp.json for connection details
     *
     * @return plugin-specific mcp server key
     */
    private static String getMCPServerKey() {
        return Utils.getPluginDisplayName();
    }
}


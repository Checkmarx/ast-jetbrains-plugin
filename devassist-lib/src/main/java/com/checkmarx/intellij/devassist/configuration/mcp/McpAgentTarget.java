package com.checkmarx.intellij.devassist.configuration.mcp;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A single AI agent's MCP client (Copilot, JetBrains AI Assistant, ...) that the Checkmarx MCP
 * server entry can be installed into.
 * <p>
 * Implementations are looked up via {@link com.checkmarx.intellij.devassist.remediation.AiAgent#mcpTarget()}
 * - adding a new agent means writing one implementation of this interface and wiring it into a
 * new {@code AiAgent} constant, with no changes required in {@link McpInstallService} or the
 * settings UI's "Install MCP" / "Edit in mcp.json" handlers.
 */
public interface McpAgentTarget {

    /**
     * Adds or updates the Checkmarx MCP server entry for this agent.
     *
     * @return true if the config file was modified, false if it was already up to date
     */
    boolean install(String credential) throws Exception;

    /**
     * Removes the Checkmarx MCP server entry for this agent.
     *
     * @return true if an entry was actually removed
     */
    boolean uninstall() throws Exception;

    /**
     * The MCP client config file this agent reads, for "open the raw file" UIs.
     */
    Path getConfigPath();

    /**
     * ID of this agent's own IDE Settings page for managing MCP servers, if it has one. When
     * present, UIs should navigate the Settings dialog there instead of opening
     * {@link #getConfigPath()} directly - the settings UI is the officially supported way to
     * inspect/manage MCP config for agents that provide one, whereas the raw file's location
     * and schema are typically undocumented internals. Empty for agents with no such page
     * (e.g. Copilot), in which case {@link #getConfigPath()} should be opened instead.
     */
    default Optional<String> getSettingsConfigurableId() {
        return Optional.empty();
    }
}

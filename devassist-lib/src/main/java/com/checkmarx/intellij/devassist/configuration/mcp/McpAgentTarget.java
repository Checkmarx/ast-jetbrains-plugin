package com.checkmarx.intellij.devassist.configuration.mcp;

import java.nio.file.Path;
import java.util.Optional;

/**
 * McpAgentTarget interface responsible to provide services for MCP configuration for different agents.
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
     * ID of this agent's own IDE Settings page for managing MCP servers, if it has one.
     */
    default Optional<String> getSettingsConfigurableId() {
        return Optional.empty();
    }
}

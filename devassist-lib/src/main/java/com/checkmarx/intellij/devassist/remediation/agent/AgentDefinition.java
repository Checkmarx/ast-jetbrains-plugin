package com.checkmarx.intellij.devassist.remediation.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Jackson-deserialized POJO representing a single AI agent definition from {@code agents-config.json}.
 * <p>
 * Each agent definition captures everything needed to interact with the agent's JetBrains plugin:
 * detection, chat opening, agent mode switching, input finding, message sending, MCP configuration,
 * and timing parameters.
 * <p>
 * All behavior of {@link GenericAgentConnector} is driven by these configuration values.
 * Adding a new AI agent requires only a new entry in the JSON file -- zero Java code changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AgentDefinition {

    @JsonProperty("id")
    private String id;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("priority")
    private int priority = Integer.MAX_VALUE;

    @JsonProperty("detection")
    private DetectionConfig detection;

    @JsonProperty("chat")
    private ChatConfig chat;

    @JsonProperty("agent_mode")
    private AgentModeConfig agentMode;

    @JsonProperty("input")
    private InputConfig input;

    @JsonProperty("send")
    private SendConfig send;

    @JsonProperty("timing")
    private TimingConfig timing;

    @JsonProperty("mcp")
    private McpConfig mcp;

    // ==================== Getters ====================

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }
    public DetectionConfig getDetection() { return detection; }
    public ChatConfig getChat() { return chat; }
    public AgentModeConfig getAgentMode() { return agentMode; }
    public InputConfig getInput() { return input; }
    public SendConfig getSend() { return send; }
    public TimingConfig getTiming() { return timing; }
    public McpConfig getMcp() { return mcp; }

    // ==================== Nested Config Classes ====================

    /**
     * How to detect if the agent's JetBrains plugin is installed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class DetectionConfig {

        @JsonProperty("tool_window_ids")
        private List<String> toolWindowIds = Collections.emptyList();

        @JsonProperty("action_ids")
        private List<String> actionIds = Collections.emptyList();

        public List<String> getToolWindowIds() { return toolWindowIds; }
        public List<String> getActionIds() { return actionIds; }
    }

    /**
     * How to open the agent's chat window.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ChatConfig {

        @JsonProperty("open_via_tool_window")
        private boolean openViaToolWindow = true;

        @JsonProperty("open_via_action")
        private boolean openViaAction = true;

        @JsonProperty("preferred_tool_window_ids")
        private List<String> preferredToolWindowIds = Collections.emptyList();

        @JsonProperty("preferred_action_ids")
        private List<String> preferredActionIds = Collections.emptyList();

        public boolean isOpenViaToolWindow() { return openViaToolWindow; }
        public boolean isOpenViaAction() { return openViaAction; }
        public List<String> getPreferredToolWindowIds() { return preferredToolWindowIds; }
        public List<String> getPreferredActionIds() { return preferredActionIds; }
    }

    /**
     * How to switch the agent's chat to "agent mode" (agentic mode).
     * <p>
     * Strategies:
     * <ul>
     *   <li>{@code "combo_box_popup"} - Find a JComboBox by class pattern, open popup, select mode item</li>
     *   <li>{@code "button_click"} - Find and click a button matching text patterns</li>
     *   <li>{@code "none"} - No mode switch needed (agent is always in agentic mode)</li>
     * </ul>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class AgentModeConfig {

        @JsonProperty("required")
        private boolean required = false;

        @JsonProperty("strategy")
        private String strategy = "none";

        @JsonProperty("combo_box_class_patterns")
        private List<String> comboBoxClassPatterns = Collections.emptyList();

        @JsonProperty("target_mode_name")
        private String targetModeName = "";

        @JsonProperty("target_mode_id_pattern")
        private String targetModeIdPattern = "";

        @JsonProperty("reflection_method_names")
        private List<String> reflectionMethodNames = Collections.emptyList();

        @JsonProperty("fallback_combo_index")
        private int fallbackComboIndex = -1;

        @JsonProperty("fallback_strategy")
        private String fallbackStrategy = "none";

        @JsonProperty("button_text_patterns")
        private List<String> buttonTextPatterns = Collections.emptyList();

        public boolean isRequired() { return required; }
        public String getStrategy() { return strategy; }
        public List<String> getComboBoxClassPatterns() { return comboBoxClassPatterns; }
        public String getTargetModeName() { return targetModeName; }
        public String getTargetModeIdPattern() { return targetModeIdPattern; }
        public List<String> getReflectionMethodNames() { return reflectionMethodNames; }
        public int getFallbackComboIndex() { return fallbackComboIndex; }
        public String getFallbackStrategy() { return fallbackStrategy; }
        public List<String> getButtonTextPatterns() { return buttonTextPatterns; }
    }

    /**
     * How to find the text input field in the agent's chat panel.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class InputConfig {

        @JsonProperty("component_type")
        private String componentType = "JTextComponent";

        @JsonProperty("search_strategy")
        private String searchStrategy = "recursive_swing_traversal";

        public String getComponentType() { return componentType; }
        public String getSearchStrategy() { return searchStrategy; }
    }

    /**
     * How to send a message after the prompt text has been set.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SendConfig {

        @JsonProperty("strategies")
        private List<String> strategies = Collections.emptyList();

        @JsonProperty("button_text_patterns")
        private List<String> buttonTextPatterns = Collections.emptyList();

        @JsonProperty("button_tooltip_patterns")
        private List<String> buttonTooltipPatterns = Collections.emptyList();

        @JsonProperty("button_class_patterns")
        private List<String> buttonClassPatterns = Collections.emptyList();

        public List<String> getStrategies() { return strategies; }
        public List<String> getButtonTextPatterns() { return buttonTextPatterns; }
        public List<String> getButtonTooltipPatterns() { return buttonTooltipPatterns; }
        public List<String> getButtonClassPatterns() { return buttonClassPatterns; }
    }

    /**
     * Timing delays for UI automation steps.
     * All delays can be overridden at runtime via system properties using the configured prefix.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class TimingConfig {

        @JsonProperty("open_delay_ms")
        private int openDelayMs = 1200;

        @JsonProperty("mode_switch_delay_ms")
        private int modeSwitchDelayMs = 800;

        @JsonProperty("popup_open_delay_ms")
        private int popupOpenDelayMs = 100;

        @JsonProperty("popup_select_delay_ms")
        private int popupSelectDelayMs = 100;

        @JsonProperty("popup_close_delay_ms")
        private int popupCloseDelayMs = 200;

        @JsonProperty("system_property_prefix")
        private String systemPropertyPrefix = "";

        public int getOpenDelayMs() { return openDelayMs; }
        public int getModeSwitchDelayMs() { return modeSwitchDelayMs; }
        public int getPopupOpenDelayMs() { return popupOpenDelayMs; }
        public int getPopupSelectDelayMs() { return popupSelectDelayMs; }
        public int getPopupCloseDelayMs() { return popupCloseDelayMs; }
        public String getSystemPropertyPrefix() { return systemPropertyPrefix; }
    }

    /**
     * MCP (Model Context Protocol) server configuration for this agent.
     * Defines where the agent stores its MCP config and how the Checkmarx server entry is structured.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class McpConfig {

        @JsonProperty("config_path")
        private McpConfigPath configPath;

        @JsonProperty("root_property")
        private String rootProperty = "servers";

        @JsonProperty("server_key")
        private String serverKey = "Checkmarx";

        @JsonProperty("entry_template")
        private Map<String, Object> entryTemplate = Collections.emptyMap();

        public McpConfigPath getConfigPath() { return configPath; }
        public String getRootProperty() { return rootProperty; }
        public String getServerKey() { return serverKey; }
        public Map<String, Object> getEntryTemplate() { return entryTemplate; }
    }

    /**
     * OS-specific paths to the agent's MCP configuration file.
     * Supports environment variable expansion (e.g., {@code ${LOCALAPPDATA}}) and
     * default-value syntax (e.g., {@code ${XDG_CONFIG_HOME:-~/.config}}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class McpConfigPath {

        @JsonProperty("windows")
        private String windows = "";

        @JsonProperty("macos")
        private String macos = "";

        @JsonProperty("linux")
        private String linux = "";

        public String getWindows() { return windows; }
        public String getMacos() { return macos; }
        public String getLinux() { return linux; }
    }
}

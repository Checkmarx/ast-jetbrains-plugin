package com.checkmarx.intellij.devassist.utils;

import com.checkmarx.intellij.devassist.inspection.CxOneAssistScanScheduler;
import com.intellij.openapi.util.Key;

import java.util.List;

/**
 * The DevAssistConstants class defines a collection of constant values
 * related to real-time scanning functionalities, including support for
 * different scanning engines and associated configurations.
 */
public final class DevAssistConstants {

    private DevAssistConstants() {
        throw new UnsupportedOperationException("Cannot instantiate DevAssistConstants class");
    }

    // Tab Name Constants
    public static final String DEVASSIST_TAB = "Checkmarx One Assist Findings";
    public static final String SCAN_RESULTS_TAB = "Scan Results";
    public static final String IGNORED_FINDINGS_TAB = "Ignored Findings";


    // OSS Scanner Constants
    public static final String ACTIVATE_OSS_REALTIME_SCANNER = "Activate OSS-Realtime";
    public static final String OSS_REALTIME_SCANNER = "Checkmarx Open Source Realtime Scanner (OSS-Realtime)";
    public static final String OSS_REALTIME_SCANNER_START = "Realtime OSS Scanner Engine started";
    public static final String OSS_REALTIME_SCANNER_DISABLED = "Realtime OSS Scanner Engine disabled";
    public static final String OSS_REALTIME_SCANNER_DIRECTORY = "Cx-oss-realtime-scanner";
    public static final String ERROR_OSS_REALTIME_SCANNER = "Failed to handle OSS Realtime scan";

    public static final String ACTIVATE_CONTAINER_REALTIME_SCANNER = "Activate Containers-Realtime";
    public static final String CONTAINER_REALTIME_SCANNER = "Checkmarx Containers Realtime Scanner (Containers-Realtime)";
    public static final String CONTAINER_REALTIME_SCANNER_START = "Realtime Containers Scanner Engine started";
    public static final String CONTAINER_REALTIME_SCANNER_DISABLED = "Realtime Containers Scanner Engine disabled";
    public static final String CONTAINER_REALTIME_SCANNER_DIRECTORY = "Cx-containers-realtime-scanner";
    public static final String ERROR_CONTAINER_REALTIME_SCANNER = "Failed to handle Containers Realtime scan";

    // Secrets Scanner Constants
    public static final String ACTIVATE_SECRETS_REALTIME_SCANNER = "Activate Secrets-Realtime";
    public static final String SECRETS_REALTIME_SCANNER = "Checkmarx Secrets Realtime Scanner (Secrets-Realtime)";
    public static final String SECRETS_REALTIME_SCANNER_START = "Realtime Secrets Scanner Engine started";
    public static final String SECRETS_REALTIME_SCANNER_DISABLED = "Realtime Secrets Scanner Engine disabled";
    public static final String SECRETS_REALTIME_SCANNER_DIRECTORY = "Cx-secrets-realtime-scanner";
    public static final String ERROR_SECRETS_REALTIME_SCANNER = "Failed to handle Secrets Realtime scan";


    // Iac Scanner Constants
    public static final String ACTIVATE_IAC_REALTIME_SCANNER = "Activate IAC-Realtime";
    public static final String IAC_REALTIME_SCANNER = "Checkmarx IAC Realtime Scanner (IAC-Realtime)";
    public static final String IAC_REALTIME_SCANNER_START = "Realtime IAC Scanner Engine started";
    public static final String IAC_REALTIME_SCANNER_DISABLED = "Realtime IAC Scanner Engine disabled";
    public static final String IAC_REALTIME_SCANNER_DIRECTORY = "Cx-iac-realtime-scanner";
    public static final String ERROR_IAC_REALTIME_SCANNER = "Failed to handle IAC Realtime scan";

    // ASCA Scanner Constants
    public static final String ACTIVATE_ASCA_REALTIME_SCANNER = "Activate ASCA-Realtime";
    public static final String ASCA_REALTIME_SCANNER = "Checkmarx Al Secure Coding Assistant (ASCA)";
    public static final String ASCA_REALTIME_SCANNER_START = "AI Secure Coding Assistant Engine started.";
    public static final String ASCA_REALTIME_SCANNER_DISABLED = "AI Secure Coding Assistant Engine disabled.";
    public static final String ERROR_ASCA_REALTIME_SCANNER = "Failed to handle ASCA Realtime scan";

    // ASCA Supported File Extensions (based on VSCode implementation)
    public static final List<String> ASCA_SUPPORTED_EXTENSIONS = List.of(
            "java",
            "cs",
            "go",
            "py",
            "js",
            "jsx"
    );

    //Dev Assist Fixes Constants
    public static final String FIX_WITH_CXONE_ASSIST = "Fix with Checkmarx One Assist";
    public static final String VIEW_DETAILS_FIX_NAME = "View details";
    public static final String IGNORE_THIS_VULNERABILITY_FIX_NAME = "Ignore this vulnerability";
    public static final String IGNORE_ALL_OF_THIS_TYPE_FIX_NAME = "Ignore all of this type";

    // Manifest file patterns constant
    public static final List<String> MANIFEST_FILE_PATTERNS = List.of(
            "**/Directory.Packages.props",
            "**/packages.config",
            "**/pom.xml",
            "**/package.json",
            "**/requirements.txt",
            "**/go.mod",
            "**/*.csproj"
    );

    public static final List<String> CONTAINERS_FILE_PATTERNS = List.of(
            "**/dockerfile",
            "**/dockerfile-*",
            "**/dockerfile.*",
            "**/docker-compose.yml",
            "**/docker-compose.yaml",
            "**/docker-compose-*.yml",
            "**/docker-compose-*.yaml"
    );

    public static final List<String> IAC_SUPPORTED_PATTERNS = List.of(
            "**/dockerfile",
            "**/*.auto.tfvars",
            "**/*.terraform.tfvars"
    );

    public static final List<String> IAC_FILE_EXTENSIONS = List.of(
            "tf",
            "yaml",
            "yml",
            "json",
            "proto",
            "dockerfile"
    );

    public static final String MULTIPLE_IAC_ISSUES = " IAC issues detected on this line";
    public static final String MULTIPLE_ASCA_ISSUES = " ASCA violations detected on this line";
    public static final List<String> CONTAINER_HELM_EXTENSION = List.of("yml",
            "yaml");

    public static final List<String> CONTAINER_HELM_EXCLUDED_FILES = List.of("chart.yml",
            "chart.yaml");

    // Container filetype constants

    public static final String DOCKERFILE = "dockerfile";
    public static final String DOCKER_COMPOSE = "docker-compose";
    public static final String HELM = "helm";
    public static final String UNKNOWN = "unknown";

    //Container Image description constants
    public static final String MALICIOUS_RISK_CONTAINER = "Malicious-risk container image";
    public static final String CRITICAL_RISK_CONTAINER = "Critical-risk container image";
    public static final String HIGH_RISK_CONTAINER = "High-risk container image";
    public static final String MEDIUM_RISK_CONTAINER = "Medium-risk container imagee";
    public static final String LOW_RISK_CONTAINER = "Low-risk container image";

    //Tooltip description constants
    public static final String RISK_PACKAGE = "risk package";
    public static final String SEVERITY_PACKAGE = "Severity Package";
    public static final String PACKAGE_DETECTED = "package detected";
    public static final String THEME = "THEME";
    // Dev Assist Remediation
    public static final String CX_AGENT_NAME = "Checkmarx One Assist";
    // Files generated by the agent (Copilot, AI Assistant)
    public static final List<String> AI_AGENT_FILES = List.of("/Dummy.txt", "/", "/AIAssistantInput");
    public static final String SEPERATOR = ":";
    public static final String QUICK_FIX = "QUICK_FIX";
    public static final String UNDO = "Undo";

    /**
     * The Keys class contains static final keys used for storing and retrieving
     * specific instances and configurations related to the Checkmarx One Assist
     * scanning functionalities within the IntelliJ platform.
     */
    public static final class Keys {

        private Keys() {
            throw new IllegalStateException("Can't instantiate Keys class");
        }

        public static final Key<CxOneAssistScanScheduler> SCHEDULER_INSTANCE_KEY = Key.create("CX_ONE_ASSIST_SCAN_SCHEDULER");
        public static final Key<Boolean> SCAN_SOURCE_KEY = Key.create("SCAN_SOURCE");
        public static final Key<Boolean> THEME_KEY = Key.create(DevAssistConstants.THEME);

    }
}
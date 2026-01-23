# Checkmarx One Assist Plugin

## Overview

**Checkmarx One Assist** is a JetBrains IDE plugin that provides real-time security scanning and AI-powered remediation for developers.

## Features

### Real-Time Security Scanners
- **OSS Scanner** - Identifies risks in open source packages
- **Secrets Scanner** - Detects exposed secrets and credentials
- **Container Scanner** - Scans container images for vulnerabilities
- **IAC Scanner** - Identifies infrastructure configuration risks
- **ASCA Scanner** - Detects insecure code patterns

### AI-Powered Remediation
- **MCP Integration** - Model Context Protocol for agentic AI
- **Quick Fixes** - AI-generated code fixes
- **Detailed Explanations** - Context-aware vulnerability descriptions
- **Ignore Management** - Manage ignored vulnerabilities

## Plugin ID

`com.checkmarx.checkmarx-one-assist-jetbrains-plugin`

## Dependencies

- `common-lib` - Shared library module
- JetBrains Platform SDK 2022.2.1+
- Lombok

## Building

Build the plugin:
```bash
./gradlew :plugin-devassist:buildPlugin
```

The distributable ZIP will be created in `plugin-devassist/build/distributions/`

## Running

Run the plugin in a sandboxed IDE:
```bash
./gradlew :plugin-devassist:runIde
```

## Testing

Run tests:
```bash
./gradlew :plugin-devassist:test
```

## Package Structure

```
com.checkmarx.intellij.devassist/
├── basescanner/          # Base scanner interfaces
├── common/               # DevAssist-specific utilities
├── configuration/        # Scanner configuration & lifecycle
│   └── mcp/             # MCP installation & settings
├── ignore/              # Ignore file management
├── inspection/          # CxOneAssist inspection
├── listeners/           # File change listeners
├── model/               # Data models
├── problems/            # Problem descriptors
├── registry/            # Scanner registry
├── remediation/         # AI remediation & quick fixes
│   └── prompts/        # AI prompt templates
├── scanners/            # Scanner implementations
│   ├── asca/           # ASCA scanner
│   ├── containers/     # Container scanner
│   ├── iac/            # IAC scanner
│   ├── oss/            # OSS scanner
│   └── secrets/        # Secrets scanner
├── telemetry/          # Telemetry service
├── ui/                 # UI components
│   ├── actions/        # Toolbar actions
│   ├── findings/       # Findings windows
│   └── layout/         # Custom layouts
└── utils/              # DevAssist utilities
```

## Key Components

### Scanners
- `OssScannerService` - OSS vulnerability scanning
- `SecretsScannerService` - Secret detection
- `ContainerScannerService` - Container image scanning
- `IacScannerService` - Infrastructure as Code scanning
- `AscaScannerService` - Application Security Code Analysis

### Configuration
- `GlobalScannerController` - Manages scanner lifecycle
- `ScannerLifeCycleManager` - Per-project scanner management
- `McpInstallService` - MCP installation and configuration

### UI
- `CxFindingsWindow` - Main findings display
- `CxIgnoredFindings` - Ignored vulnerabilities view
- `CxOneAssistInspection` - Real-time code inspection

### Remediation
- `CxOneAssistFix` - AI-powered quick fix
- `RemediationManager` - Manages remediation flow
- `CopilotIntegration` - Integration with AI assistants

## Publishing

Publish to JetBrains Marketplace:
```bash
./gradlew :plugin-devassist:publishPlugin
```

Requires `PUBLISH_TOKEN` environment variable.

## Configuration

The plugin can be configured in:
**Settings → Tools → Checkmarx One Assist**

## License Requirements

Requires either:
- Checkmarx One Assist license
- Checkmarx Dev Assist license

## Compatibility

- IntelliJ IDEA 2022.2+
- All JetBrains IDEs based on IntelliJ Platform

## Support

For issues and support, visit:
- [Documentation](https://docs.checkmarx.com)
- [Support Portal](https://support.checkmarx.com)


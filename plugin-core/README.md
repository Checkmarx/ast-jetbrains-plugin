# Checkmarx AST Plugin

## Overview

**Checkmarx AST** is a JetBrains IDE plugin that enables developers to run Checkmarx One scans and view results directly in their IDE.

## Features

### Scan Management
- **Run Scans** - Execute Checkmarx One scans from the IDE
- **Import Results** - View scan results from Checkmarx One platform
- **Project Selection** - Select and manage Checkmarx projects
- **Branch Selection** - Choose branches for scanning

### Results Viewing
- **Results Tree** - Hierarchical view of scan findings
- **Filtering** - Filter by severity, state, type
- **Grouping** - Group results by severity, state, file, package, etc.
- **Details Panel** - View detailed vulnerability information
- **Remediation Examples** - See code examples for fixing issues

### Integration
- **OAuth Authentication** - Secure authentication with Checkmarx One
- **API Key Support** - Alternative authentication method
- **Triage Integration** - Update vulnerability states
- **Comments** - Add comments to findings

## Plugin ID

`com.checkmarx.checkmarx-ast-jetbrains-plugin`

## Dependencies

- `common-lib` - Shared library module
- JetBrains Platform SDK 2022.2.1+
- Lombok

## Building

Build the plugin:
```bash
./gradlew :plugin-core:buildPlugin
```

The distributable ZIP will be created in `plugin-core/build/distributions/`

## Running

Run the plugin in a sandboxed IDE:
```bash
./gradlew :plugin-core:runIde
```

## Testing

Run unit tests:
```bash
./gradlew :plugin-core:test
```

Run UI tests:
```bash
./gradlew :plugin-core:runIdeForUiTests
```

## Package Structure

```
com.checkmarx.intellij/
├── components/          # UI components
│   ├── CxLinkLabel.java
│   ├── PaneUtils.java
│   └── TreeUtils.java
├── inspections/        # Code inspections (legacy)
│   ├── CxInspection.java
│   └── CxVisitor.java
├── project/            # Project management
│   ├── ProjectListener.java
│   └── ProjectResultsService.java
├── settings/           # Settings UI
│   └── global/
│       ├── GlobalSettingsComponent.java
│       └── GlobalSettingsConfigurable.java
└── tool/              # Tool window
    └── window/
        ├── CxToolWindowFactory.java
        ├── CxToolWindowPanel.java
        ├── actions/        # Toolbar actions
        └── results/        # Results tree
```

## Key Components

### Tool Window
- `CxToolWindowFactory` - Creates the Checkmarx tool window
- `CxToolWindowPanel` - Main panel for scan results
- `ResultsTreeFactory` - Builds the results tree

### Actions
- `StartScanAction` - Initiates a new scan
- `CancelScanAction` - Cancels running scan
- `OpenSettingsAction` - Opens settings dialog
- `FilterBaseAction` - Filters results by severity
- `GroupByActionGroup` - Groups results by various criteria

### Settings
- `GlobalSettingsConfigurable` - Settings UI
- `GlobalSettingsComponent` - Settings panel component

### Project Management
- `ProjectListener` - Listens to project lifecycle events
- `ProjectResultsService` - Manages scan results per project

## Publishing

Publish to JetBrains Marketplace:
```bash
./gradlew :plugin-core:publishPlugin
```

Requires `PUBLISH_TOKEN` environment variable.

## Configuration

The plugin can be configured in:
**Settings → Tools → Checkmarx One**

### Required Settings
- **Base URL** - Checkmarx One base URL
- **Tenant** - Checkmarx One tenant name
- **Authentication** - API Key or OAuth

### Optional Settings
- **Additional Parameters** - Custom CLI parameters

## Compatibility

- IntelliJ IDEA 2022.2+
- All JetBrains IDEs based on IntelliJ Platform

## Supported Scan Types

- **SAST** - Static Application Security Testing
- **SCA** - Software Composition Analysis
- **KICS** - Infrastructure as Code scanning
- **API Security** - API security scanning

## Support

For issues and support, visit:
- [Documentation](https://docs.checkmarx.com)
- [Support Portal](https://support.checkmarx.com)
- [GitHub Issues](https://github.com/checkmarx-ltd/ast-jetbrains-plugin/issues)


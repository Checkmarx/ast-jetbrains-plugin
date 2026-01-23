# Common Library Module

## Overview

This module contains shared code used by both the **Checkmarx AST** and **Checkmarx One Assist** plugins.

## Purpose

The `common-lib` module provides:
- Shared utilities and constants
- Authentication services (OAuth, API Key)
- AST CLI wrapper commands
- Settings state management
- Common UI components and icons
- HTTP client utilities

## Key Components

### Authentication & Settings
- `AuthService` - OAuth 2.0 authentication flow
- `GlobalSettingsState` - Persistent application settings
- `GlobalSettingsSensitiveState` - Secure credential storage
- `CxWrapperFactory` - Factory for creating AST CLI wrapper instances

### Commands (AST CLI Wrappers)
- `Authentication` - Authentication commands
- `Project` - Project management commands
- `Scan` - Scan execution commands
- `TenantSetting` - Tenant configuration
- `Triage` - Result triage operations
- `Results` - Result retrieval and processing

### Utilities
- `Bundle` - Internationalization support
- `Constants` - Application-wide constants
- `Resource` - Resource key enumeration
- `Utils` - Common utility methods
- `CxIcons` - Icon definitions
- `HttpClientUtils` - HTTP client utilities
- `InputValidator` - Input validation
- `SeverityLevel` - Severity level definitions

### Helper Classes
- `OAuthCallbackServer` - Local server for OAuth callback handling

## Dependencies

- **JetBrains Platform SDK** (provided at runtime)
- **Checkmarx AST CLI Java Wrapper** - Core scanning functionality
- **Jackson** - JSON processing
- **Lombok** - Code generation

## Usage

This module is not deployable on its own. It is consumed as a dependency by:
- `plugin-devassist` - Checkmarx One Assist plugin
- `plugin-core` - Checkmarx AST plugin

## Building

```bash
./gradlew :common-lib:build
```

## Testing

```bash
./gradlew :common-lib:test
```

## Package Structure

```
com.checkmarx.intellij/
├── Bundle.java
├── Constants.java
├── CxIcons.java
├── Resource.java
├── Utils.java
├── commands/
│   ├── Authentication.java
│   ├── Project.java
│   ├── Scan.java
│   ├── TenantSetting.java
│   ├── Triage.java
│   └── results/
│       ├── Results.java
│       └── obj/
├── helper/
│   └── OAuthCallbackServer.java
├── service/
│   ├── AuthService.java
│   └── StateService.java
├── settings/
│   ├── SettingsComponent.java (interface)
│   ├── SettingsListener.java
│   └── global/
│       ├── GlobalSettingsState.java
│       ├── GlobalSettingsSensitiveState.java
│       └── CxWrapperFactory.java
└── util/
    ├── HttpClientUtils.java
    ├── InputValidator.java
    └── SeverityLevel.java
```

## Notes

- This module uses `compileOnly` scope for IntelliJ Platform dependencies to avoid bundling them
- All shared constants should be added to this module to avoid duplication
- Authentication state is managed centrally in this module


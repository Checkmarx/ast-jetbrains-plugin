# Module Split Effort Plan - JetBrains Plugin

## Executive Summary

This document outlines the effort required to split the current monolithic JetBrains plugin into 3 separate modules:

1. **plugin-devassist** - Deployable plugin for CxOne Assist (DevAssist) functionality
2. **plugin-core** - Deployable plugin for Checkmarx AST core functionality  
3. **common-lib** - Shared library module containing common utilities and services

---

## Current Project Analysis

### Package Structure Overview

#### DevAssist Packages (`com.checkmarx.intellij.devassist`)
- `basescanner` - Base scanner interfaces and implementations
- `common` - DevAssist-specific common utilities (ScannerFactory, ScanManager, ScanResult)
- `configuration` - Scanner configuration and lifecycle management
  - `mcp` - MCP (Model Context Protocol) installation and settings
- `ignore` - Ignore file management for vulnerabilities
- `inspection` - CxOneAssist inspection and scan scheduling
- `listeners` - File change listeners for real-time scanning
- `model` - Data models (Location, ScanIssue, Vulnerability)
- `problems` - Problem descriptor builders and decorators
- `registry` - Scanner registry for managing multiple scanners
- `remediation` - AI-powered remediation and quick fixes
  - `prompts` - Prompt templates for AI remediation
- `scanners` - Scanner implementations
  - `asca` - ASCA (Application Security Code Analysis) scanner
  - `containers` - Container security scanner
  - `iac` - Infrastructure as Code scanner
  - `oss` - Open Source Software scanner
  - `secrets` - Secrets detection scanner
- `telemetry` - Telemetry service for DevAssist
- `ui` - UI components for DevAssist
  - `actions` - Toolbar actions and filters
  - `findings` - Findings window and ignored findings UI
  - `layout` - Custom layout managers
- `utils` - DevAssist-specific utilities and constants

**Total Classes**: ~80+ classes

#### Core Plugin Packages (Outside `devassist`)
- `com.checkmarx.intellij` - Root package
  - `Bundle.java`, `Constants.java`, `CxIcons.java`, `Resource.java`, `Utils.java`
- `commands` - AST CLI wrapper commands
  - `Authentication.java`, `Project.java`, `Scan.java`, `TenantSetting.java`, `Triage.java`
  - `results` - Result processing and objects
- `components` - UI components (CxLinkLabel, PaneUtils, TreeUtils)
- `helper` - OAuth callback server
- `inspections` - Legacy CxInspection (currently commented out)
- `project` - Project listener and results service
- `service` - AuthService, StateService
- `settings` - Settings management
  - `global` - Global settings components and configurables
- `tool` - Tool window implementation
  - `window` - Main tool window panels and factories
    - `actions` - Scan actions, filters, grouping
    - `results` - Results tree rendering
- `util` - HTTP client utils, input validators, severity levels

**Total Classes**: ~60+ classes

---

## Module Split Strategy

### Module 1: `common-lib` (Shared Library)

**Purpose**: Contains all shared code used by both plugins

**Packages to Include**:
- `com.checkmarx.intellij.Bundle`
- `com.checkmarx.intellij.Constants`
- `com.checkmarx.intellij.CxIcons`
- `com.checkmarx.intellij.Resource`
- `com.checkmarx.intellij.Utils`
- `com.checkmarx.intellij.commands.*` (All AST CLI wrapper commands)
- `com.checkmarx.intellij.helper.OAuthCallbackServer`
- `com.checkmarx.intellij.service.AuthService`
- `com.checkmarx.intellij.service.StateService`
- `com.checkmarx.intellij.settings.global.GlobalSettingsState`
- `com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState`
- `com.checkmarx.intellij.settings.global.CxWrapperFactory`
- `com.checkmarx.intellij.settings.SettingsComponent` (interface)
- `com.checkmarx.intellij.settings.SettingsListener`
- `com.checkmarx.intellij.util.*` (All utilities)

**Dependencies**:
- JetBrains Platform SDK
- Checkmarx AST CLI Java Wrapper
- Jackson (JSON processing)
- Lombok

**Estimated Classes**: ~25-30 classes

---

### Module 2: `plugin-devassist` (CxOne Assist Plugin)

**Purpose**: Deployable plugin for real-time security scanning and AI-powered remediation

**Packages to Include**:
- All packages under `com.checkmarx.intellij.devassist.*`

**Dependencies**:
- `common-lib` module
- JetBrains Platform SDK
- Lombok

**Plugin Configuration** (`plugin.xml`):
- Plugin ID: `com.checkmarx.checkmarx-one-assist-jetbrains-plugin`
- Plugin Name: `Checkmarx One Assist`
- Extensions:
  - `applicationConfigurable` for CxOne Assist settings
  - `localInspection` for CxOneAssistInspection
  - `codeInsight.linkHandler` for remediation links
  - `postStartupActivity` for MCP installation and scanner startup
- Actions:
  - `VulnerabilityToolbarGroup` with filter actions
- Listeners:
  - `McpUninstallHandler` for plugin lifecycle
- Tool Window:
  - "Checkmarx One Assist" with Findings and Ignored Findings tabs

**Estimated Classes**: ~80+ classes

---

### Module 3: `plugin-core` (Checkmarx AST Core Plugin)

**Purpose**: Deployable plugin for traditional Checkmarx AST scanning

**Packages to Include**:
- `com.checkmarx.intellij.components.*`
- `com.checkmarx.intellij.inspections.*` (if re-enabled)
- `com.checkmarx.intellij.project.*`
- `com.checkmarx.intellij.settings.global.GlobalSettingsComponent`
- `com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable`
- `com.checkmarx.intellij.tool.window.*` (All tool window components)

**Dependencies**:
- `common-lib` module
- JetBrains Platform SDK
- Lombok

**Plugin Configuration** (`plugin.xml`):
- Plugin ID: `com.checkmarx.checkmarx-ast-jetbrains-plugin`
- Plugin Name: `Checkmarx AST`
- Extensions:
  - `applicationConfigurable` for global settings
  - `applicationService` for GlobalSettingsState, GlobalSettingsSensitiveState
  - `projectService` for ProjectResultsService
  - `toolWindow` for "Checkmarx" tool window
  - `notificationGroup` for Checkmarx notifications
- Actions:
  - `Checkmarx.Toolbar` group with scan, filter, and grouping actions
- Listeners:
  - `ProjectListener` for project lifecycle events

**Estimated Classes**: ~60+ classes

---

## Shared Dependencies Analysis

### Classes Used by Both Modules

| Class/Package | Used by DevAssist | Used by Core | Location in Split |
|---------------|-------------------|--------------|-------------------|
| `GlobalSettingsState` | ✓ (scanner toggles, MCP status) | ✓ (credentials, scan config) | `common-lib` |
| `GlobalSettingsSensitiveState` | ✓ (API key/token for MCP) | ✓ (API key/token for scans) | `common-lib` |
| `CxWrapperFactory` | ✓ (ASCA, OSS scanning) | ✓ (AST scans) | `common-lib` |
| `AuthService` | ✓ (MCP authentication) | ✓ (OAuth flow) | `common-lib` |
| `Bundle` / `Resource` | ✓ (UI messages) | ✓ (UI messages) | `common-lib` |
| `Constants` | ✓ (DevAssist constants) | ✓ (Core constants) | `common-lib` |
| `CxIcons` | ✓ (DevAssist icons) | ✓ (Core icons) | `common-lib` |
| `Utils` | ✓ (notifications, threading) | ✓ (notifications, threading) | `common-lib` |
| `Commands.*` | ✓ (for scanner results) | ✓ (for AST scans) | `common-lib` |

### Cross-Module Dependencies

**DevAssist → Core**:
- `CxToolWindowFactory` creates DevAssist tabs (`CxFindingsWindow`, `CxIgnoredFindings`)
- **Resolution**: Move tool window factory to `common-lib` or create separate factories per plugin

**Core → DevAssist**:
- `CxToolWindowFactory` references `DevAssistConstants.IGNORED_FINDINGS_TAB`
- **Resolution**: Define tab names in `common-lib` Constants

---

## Effort Estimation

### Phase 1: Project Setup and Module Creation (2-3 days)

**Tasks**:
1. Create Gradle multi-module structure
   - Update `settings.gradle` to include 3 modules
   - Create `common-lib/build.gradle`
   - Create `plugin-devassist/build.gradle`
   - Create `plugin-core/build.gradle`
2. Configure module dependencies
3. Set up module-specific source directories
4. Configure IntelliJ Platform plugin for each deployable module

**Complexity**: Medium
**Risk**: Low
**Estimated Effort**: 2-3 days

---

### Phase 2: Common Library Extraction (3-4 days)

**Tasks**:
1. Move shared classes to `common-lib` module
   - Bundle, Constants, Resource, CxIcons, Utils
   - Commands package (Authentication, Project, Scan, etc.)
   - Service package (AuthService, StateService)
   - Settings state classes (GlobalSettingsState, GlobalSettingsSensitiveState, CxWrapperFactory)
   - Util package (HttpClientUtils, InputValidator, SeverityLevel)
   - Helper package (OAuthCallbackServer)
2. Update package imports across all modules
3. Resolve circular dependencies
4. Create common interfaces for cross-module communication
5. Test common-lib compilation

**Complexity**: High
**Risk**: Medium (import resolution, dependency conflicts)
**Estimated Effort**: 3-4 days

---

### Phase 3: DevAssist Plugin Module (4-5 days)

**Tasks**:
1. Move all `com.checkmarx.intellij.devassist.*` packages to `plugin-devassist`
2. Create new `plugin.xml` for DevAssist plugin
   - Define unique plugin ID and name
   - Register CxOneAssist inspection
   - Register MCP-related services and listeners
   - Register DevAssist actions and toolbar groups
3. Update imports to reference `common-lib`
4. Create DevAssist-specific tool window factory
5. Handle resource files (icons, messages, inspection descriptions)
6. Test DevAssist plugin compilation and runtime
7. Verify real-time scanning functionality
8. Test MCP integration

**Complexity**: High
**Risk**: Medium (plugin.xml configuration, service registration)
**Estimated Effort**: 4-5 days

---

### Phase 4: Core Plugin Module (4-5 days)

**Tasks**:
1. Move core packages to `plugin-core`
   - Components, inspections, project, tool.window, settings.global
2. Create new `plugin.xml` for Core plugin
   - Define unique plugin ID and name
   - Register global settings configurable
   - Register tool window for scan results
   - Register scan actions and toolbar
3. Update imports to reference `common-lib`
4. Resolve tool window factory dependencies
5. Handle resource files
6. Test Core plugin compilation and runtime
7. Verify AST scanning functionality
8. Test project/branch/scan selection

**Complexity**: High
**Risk**: Medium (tool window integration, settings UI)
**Estimated Effort**: 4-5 days

---

### Phase 5: Integration and Cross-Module Communication (3-4 days)

**Tasks**:
1. Resolve tool window integration
   - Option A: Separate tool windows per plugin
   - Option B: Shared tool window factory in common-lib
2. Handle shared settings UI
   - DevAssist settings under Core settings tree
   - Or separate settings for each plugin
3. Implement message bus for cross-plugin communication (if needed)
4. Test both plugins running simultaneously
5. Verify authentication flow works for both plugins
6. Test license checking for both plugins

**Complexity**: Very High
**Risk**: High (plugin interaction, shared state management)
**Estimated Effort**: 3-4 days

---

### Phase 6: Testing and Validation (5-7 days)

**Tasks**:
1. Unit test updates
   - Update test imports
   - Fix broken tests due to module split
   - Add new tests for module boundaries
2. Integration testing
   - Test DevAssist plugin standalone
   - Test Core plugin standalone
   - Test both plugins together
3. UI/UX testing
   - Verify all tool windows render correctly
   - Test settings panels
   - Verify notifications and messages
4. Functional testing
   - Real-time scanning (DevAssist)
   - AST scanning (Core)
   - Authentication and license validation
   - MCP installation and remediation
5. Regression testing
   - Ensure no functionality is lost
   - Verify backward compatibility

**Complexity**: Very High
**Risk**: High (comprehensive testing required)
**Estimated Effort**: 5-7 days

---

### Phase 7: Build and Deployment Configuration (2-3 days)

**Tasks**:
1. Configure Gradle build tasks
   - `:common-lib:build`
   - `:plugin-devassist:buildPlugin`
   - `:plugin-core:buildPlugin`
2. Set up distribution packaging
3. Configure plugin verifier for both plugins
4. Update CI/CD pipelines
5. Create deployment documentation
6. Test plugin installation from ZIP files

**Complexity**: Medium
**Risk**: Medium (build configuration)
**Estimated Effort**: 2-3 days

---

### Phase 8: Documentation and Cleanup (2-3 days)

**Tasks**:
1. Update README.md
2. Create module-specific documentation
3. Update developer setup guide
4. Document module dependencies
5. Clean up deprecated code
6. Update CODEOWNERS if needed
7. Create migration guide for developers

**Complexity**: Low
**Risk**: Low
**Estimated Effort**: 2-3 days

---

## Total Effort Estimation

| Phase | Estimated Days | Risk Level |
|-------|----------------|------------|
| Phase 1: Project Setup | 2-3 days | Low |
| Phase 2: Common Library | 3-4 days | Medium |
| Phase 3: DevAssist Plugin | 4-5 days | Medium |
| Phase 4: Core Plugin | 4-5 days | Medium |
| Phase 5: Integration | 3-4 days | High |
| Phase 6: Testing | 5-7 days | High |
| Phase 7: Build & Deployment | 2-3 days | Medium |
| Phase 8: Documentation | 2-3 days | Low |
| **TOTAL** | **25-34 days** | **Medium-High** |

**Recommended Timeline**: 6-7 weeks (with buffer for unforeseen issues)

**Team Size**: 2-3 developers recommended for parallel work on different modules

---

## Risk Assessment and Mitigation

### High-Risk Areas

1. **Tool Window Integration**
   - **Risk**: Both plugins need to contribute to the same tool window
   - **Mitigation**:
     - Option A: Create separate tool windows (simpler, cleaner separation)
     - Option B: Use extension points in common-lib (more complex, better UX)
   - **Recommendation**: Start with Option A for faster delivery

2. **Shared State Management**
   - **Risk**: GlobalSettingsState used by both plugins
   - **Mitigation**: Keep in common-lib, ensure thread-safe access
   - **Recommendation**: Add synchronization if needed

3. **Plugin Dependencies**
   - **Risk**: Users might install only one plugin
   - **Mitigation**:
     - Make plugins independent (each can work standalone)
     - Or make DevAssist depend on Core plugin
   - **Recommendation**: Independent plugins for maximum flexibility

4. **Authentication Flow**
   - **Risk**: Both plugins need authentication
   - **Mitigation**: Centralize in common-lib, share credentials
   - **Recommendation**: Test thoroughly with both plugins

5. **Resource Conflicts**
   - **Risk**: Icon/message bundle conflicts
   - **Mitigation**: Namespace resources per plugin
   - **Recommendation**: Use plugin-specific resource bundles where needed

---

## Key Decisions Required

### Decision 1: Tool Window Strategy
**Options**:
- A) Separate tool windows: "Checkmarx AST" and "Checkmarx One Assist"
- B) Shared tool window with tabs from both plugins
- C) Core plugin provides tool window, DevAssist extends it

**Recommendation**: Option A (separate tool windows) for cleaner separation

### Decision 2: Plugin Dependency Model
**Options**:
- A) Independent plugins (no dependency between them)
- B) DevAssist depends on Core
- C) Both depend only on common-lib (published separately)

**Recommendation**: Option C (common-lib as separate artifact) for maximum flexibility

### Decision 3: Settings UI Organization
**Options**:
- A) Separate settings: "Checkmarx AST" and "Checkmarx One Assist"
- B) Nested settings: "Checkmarx AST" → "One Assist" (current structure)
- C) Shared settings in common-lib

**Recommendation**: Option A for independent plugins, Option B if maintaining current UX

### Decision 4: Common-lib Distribution
**Options**:
- A) Bundled with each plugin (duplicated)
- B) Published as separate library JAR
- C) Gradle submodule (compile-time only)

**Recommendation**: Option A (bundled) for simpler deployment, Option B for smaller plugin sizes

---

## Success Criteria

1. ✅ Both plugins build successfully independently
2. ✅ Both plugins can be installed and run simultaneously
3. ✅ Both plugins can be installed and run independently
4. ✅ All existing functionality preserved
5. ✅ No regression in performance
6. ✅ All tests passing
7. ✅ Clean module boundaries (no circular dependencies)
8. ✅ Documentation updated
9. ✅ CI/CD pipelines working
10. ✅ Plugin verifier passes for both plugins

---

## Next Steps

1. **Review and approve this plan** with stakeholders
2. **Make key decisions** (tool window strategy, dependency model, etc.)
3. **Set up development environment** for multi-module work
4. **Create feature branch** for module split work
5. **Begin Phase 1** (Project Setup)
6. **Regular check-ins** to track progress and address blockers

---

## Appendix A: File Count by Module

### common-lib
- Java files: ~25-30
- Resource files: ~5-10
- Test files: ~10-15

### plugin-devassist
- Java files: ~80-85
- Resource files: ~15-20
- Test files: ~20-25

### plugin-core
- Java files: ~60-65
- Resource files: ~10-15
- Test files: ~15-20

**Total**: ~250-300 files to organize across 3 modules

---

## Appendix B: Gradle Module Structure

```
ast-jetbrains-plugin/
├── settings.gradle
├── build.gradle (root)
├── common-lib/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/checkmarx/intellij/
│       │   └── resources/
│       └── test/
├── plugin-devassist/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/checkmarx/intellij/devassist/
│       │   └── resources/
│       │       └── META-INF/plugin.xml
│       └── test/
└── plugin-core/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── java/com/checkmarx/intellij/
        │   └── resources/
        │       └── META-INF/plugin.xml
        └── test/
```

---

*Document Version: 1.0*
*Last Updated: 2026-01-21*
*Author: AI Assistant (Augment Agent)*


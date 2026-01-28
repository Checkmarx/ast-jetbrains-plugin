# IntelliJ Plugin Split - Effort Planning & Architecture Analysis

## Executive Summary

This document provides a comprehensive analysis and effort estimation for splitting the Checkmarx IntelliJ plugin into three deployable artifacts:

1. **DevAssist Plugin** - Standalone plugin containing only DevAssist features
2. **Combined Plugin** - Full-featured plugin that includes DevAssist as a dependency
3. **Common Library** - Shared library used by both plugins

---

## Current Architecture Overview

### Package Structure

**DevAssist Package (`com.checkmarx.intellij.devassist`)** - 67 Java files
- Real-time security scanning (OSS, Secrets, Containers, IaC, ASCA)
- Vulnerability detection and remediation
- GitHub Copilot integration
- MCP (Model Context Protocol) integration
- Ignore management
- UI components for findings display

**Non-DevAssist Packages** - ~40 Java files
- Authentication & settings management
- Scan results display (traditional scan results)
- Project management
- Tool window factory
- Commands (Scan, Project, TenantSetting, etc.)

### Key Dependencies

**DevAssist depends on:**
- `com.checkmarx.intellij.Utils` - Logging, notifications, async operations
- `com.checkmarx.intellij.Constants` - Application constants
- `com.checkmarx.intellij.settings.global.GlobalSettingsState` - Settings management
- `com.checkmarx.intellij.settings.global.CxWrapperFactory` - Checkmarx API wrapper
- `com.checkmarx.intellij.util.SeverityLevel` - Severity enumeration
- `com.checkmarx.ast.wrapper.*` - External Checkmarx AST wrapper library

**Non-DevAssist depends on DevAssist:**
- `com.checkmarx.intellij.tool.window.CxToolWindowFactory` → DevAssist UI components
- `com.checkmarx.intellij.project.ProjectListener` → `ScannerRegistry`
- `com.checkmarx.intellij.settings.global.CxOneAssistComponent` → DevAssist settings
- `com.checkmarx.intellij.settings.global.GlobalSettingsState` → DevAssist license flags

---

## Proposed Architecture

### 1. DevAssist Plugin (Standalone)

**Plugin ID:** `com.checkmarx.devassist-jetbrains-plugin`

**Included Packages:**
```
com.checkmarx.intellij.devassist.*
  ├── basescanner/          (4 files)
  ├── common/               (3 files)
  ├── configuration/        (4 files + mcp/)
  ├── ignore/               (4 files)
  ├── inspection/           (4 files)
  ├── listeners/            (1 file)
  ├── model/                (3 files)
  ├── problems/             (5 files)
  ├── registry/             (1 file)
  ├── remediation/          (7 files + prompts/)
  ├── scanners/             (15 files - 5 scanner types)
  ├── telemetry/            (1 file)
  ├── ui/                   (11 files)
  └── utils/                (4 files)
```

**Required from Common Library:**
- Utils, Constants, SeverityLevel
- GlobalSettingsState (settings interface)
- CxWrapperFactory (API wrapper)
- SettingsListener interface
- Authentication components

**Features:**
- Real-time scanning (OSS, Secrets, Containers, IaC, ASCA)
- Inline vulnerability detection
- Quick fixes and remediation
- GitHub Copilot integration
- MCP server integration
- Ignore management
- Findings UI (3 tabs)

**Estimated Size:** ~25,000 LOC

---

### 2. Common Library Module

**Module ID:** `com.checkmarx.intellij.common`

**Included Classes:**
```
com.checkmarx.intellij.common/
  ├── core/
  │   ├── Utils.java
  │   ├── Constants.java
  │   ├── Bundle.java
  │   ├── Resource.java
  │   └── CxIcons.java
  ├── settings/
  │   ├── GlobalSettingsState.java
  │   ├── GlobalSettingsSensitiveState.java
  │   ├── SettingsListener.java
  │   └── CxWrapperFactory.java
  ├── auth/
  │   ├── AuthService.java
  │   └── OAuthCallbackServer.java
  ├── commands/
  │   ├── TenantSetting.java
  │   └── Authentication.java
  ├── util/
  │   ├── SeverityLevel.java
  │   ├── HttpClientUtils.java
  │   └── InputValidator.java
  └── service/
      └── StateService.java
```

**Purpose:**
- Shared utilities and constants
- Authentication infrastructure
- Settings management (state persistence)
- API wrapper factory
- Common UI components (icons, bundles)
- HTTP utilities

**Estimated Size:** ~5,000 LOC

---

### 3. Combined Plugin (Full-Featured)

**Plugin ID:** `com.checkmarx.checkmarx-ast-jetbrains-plugin` (existing)

**Dependencies:**
- DevAssist Plugin (as plugin dependency)
- Common Library (as library dependency)

**Included Packages:**
```
com.checkmarx.intellij/
  ├── commands/
  ├── components/
  ├── inspections/
  ├── project/
  ├── settings/
  ├── toolWindow/
  ├── utils/
  └── (all non-devassist packages)
```

---

## Effort Estimation by Phase

### Phase 3: Combined Plugin Refactoring (3-4 weeks)

**Tasks:**
1. Update build.gradle dependencies
   - Add DevAssist plugin as dependency
   - Add common library as dependency
   - Configure plugin dependency resolution
   - **Effort:** 2 days

2. Refactor CxToolWindowFactory
   - Integrate DevAssist tool window tabs
   - Maintain existing scan results tabs
   - Ensure proper tab ordering and lifecycle
   - **Effort:** 3 days

3. Update settings UI
   - Combine GlobalSettingsComponent with DevAssist settings
   - Create unified settings page
   - Ensure proper settings synchronization
   - **Effort:** 3 days

4. Refactor ProjectListener
   - Remove direct ScannerRegistry calls
   - Use DevAssist plugin API
   - Ensure proper initialization order
   - **Effort:** 2 days

5. Remove duplicate code
   - Remove DevAssist package from combined plugin
   - Remove duplicated utilities (now in common library)
   - Update all imports
   - **Effort:** 3 days

6. Update plugin.xml
   - Remove DevAssist-specific registrations
   - Add plugin dependency declaration
   - Update extension points
   - **Effort:** 2 days

7. Testing and integration
   - End-to-end testing with DevAssist plugin
   - Verify all features work correctly
   - Test plugin loading order
   - **Effort:** 5 days

**Total Phase 3:** 20 days (4 weeks)

---

### Phase 4: Testing & Validation (2-3 weeks)

**Tasks:**
1. Comprehensive testing
   - All three artifacts independently
   - Combined plugin with DevAssist dependency
   - Backward compatibility testing
   - **Effort:** 5 days

2. Performance testing
   - Startup time comparison
   - Memory usage analysis
   - Scan performance validation
   - **Effort:** 3 days

3. Compatibility testing
   - Test on multiple IntelliJ versions
   - Test on different IDEs (IDEA, PyCharm, WebStorm, etc.)
   - Test on different OS (Windows, macOS, Linux)
   - **Effort:** 3 days

4. Documentation updates
   - Update README files
   - Create migration guide
   - Update build documentation
   - API documentation for common library
   - **Effort:** 3 days

5. Bug fixes and refinements
   - Address issues found during testing
   - Performance optimizations
   - **Effort:** 4 days

**Total Phase 4:** 18 days (3.6 weeks)

---

## Detailed Class-by-Class Analysis

### DevAssist Package Classes (67 files)

#### 1. Base Scanner Package (4 files)

**BaseScannerCommand.java**
- **Purpose:** Abstract base class for scanner commands
- **Dependencies:** Utils, Constants, GlobalSettingsState
- **Refactoring:** Update to use common library interfaces
- **Complexity:** Medium
- **Effort:** 0.5 days

**BaseScannerService.java**
- **Purpose:** Abstract base class for scanner services
- **Dependencies:** Utils, ProblemHolderService, ScanResult
- **Refactoring:** Minimal - internal to DevAssist
- **Complexity:** Medium
- **Effort:** 0.5 days

**ScannerCommand.java**
- **Purpose:** Interface for scanner commands
- **Dependencies:** None
- **Refactoring:** None required
- **Complexity:** Low
- **Effort:** 0 days

**ScannerService.java**
- **Purpose:** Interface for scanner services
- **Dependencies:** ScanResult
- **Refactoring:** None required
- **Complexity:** Low
- **Effort:** 0 days

**Subtotal:** 1 day

---

#### 2. Common Package (3 files)

**ScanManager.java**
- **Purpose:** Manages scan execution across all scanners
- **Dependencies:** ScannerRegistry, Utils, GlobalSettingsState
- **Refactoring:** Update settings access via common library
- **Complexity:** High
- **Effort:** 1 day

**ScannerFactory.java**
- **Purpose:** Factory for creating scanner instances
- **Dependencies:** All scanner implementations
- **Refactoring:** Minimal - internal to DevAssist
- **Complexity:** Medium
- **Effort:** 0.5 days

**ScanResult.java**
- **Purpose:** Data model for scan results
- **Dependencies:** ScanIssue, Vulnerability
- **Refactoring:** None required
- **Complexity:** Low
- **Effort:** 0 days

**Subtotal:** 1.5 days

---

#### 3. Configuration Package (7 files including MCP)

**GlobalScannerController.java**
- **Purpose:** Controls scanner lifecycle globally
- **Dependencies:** ScannerRegistry, GlobalSettingsState, Utils
- **Refactoring:** Update settings access
- **Complexity:** High
- **Effort:** 1 day

**GlobalScannerStartupActivity.java**
- **Purpose:** Initializes scanners on IDE startup
- **Dependencies:** GlobalScannerController, ScannerRegistry
- **Refactoring:** Update startup sequence for plugin dependency
- **Complexity:** Medium
- **Effort:** 0.5 days

**ScannerConfig.java**
- **Purpose:** Configuration model for scanner settings
- **Dependencies:** None (data class)
- **Refactoring:** Move to common library
- **Complexity:** Low
- **Effort:** 0.25 days

**ScannerLifeCycleManager.java**
- **Purpose:** Manages scanner lifecycle events
- **Dependencies:** ScannerRegistry, GlobalScannerController
- **Refactoring:** Update event handling
- **Complexity:** Medium
- **Effort:** 0.5 days

**McpInstallService.java**
- **Purpose:** Handles MCP (Model Context Protocol) installation
- **Dependencies:** GlobalSettingsState, Utils
- **Refactoring:** Update settings access, ensure MCP works in standalone plugin
- **Complexity:** High
- **Effort:** 1 day

**McpSettingsInjector.java**
- **Purpose:** Injects MCP settings into scanner configuration
- **Dependencies:** GlobalSettingsState, ScannerConfig
- **Refactoring:** Update settings injection mechanism
- **Complexity:** Medium
- **Effort:** 0.5 days

**McpUninstallHandler.java**
- **Purpose:** Handles MCP uninstallation cleanup
- **Dependencies:** GlobalSettingsState, Utils
- **Refactoring:** Update cleanup logic
- **Complexity:** Medium
- **Effort:** 0.5 days

**Subtotal:** 4.25 days


---

#### 4. Ignore Package (4 files)

**IgnoreService.java**
- **Purpose:** Service for managing ignored findings
- **Dependencies:** GlobalSettingsState, ProblemHolderService
- **Refactoring:** Update settings access, ensure persistence works in standalone plugin
- **Complexity:** Medium
- **Effort:** 0.5 days

**IgnoredFindingsManager.java**
- **Purpose:** Manages the collection of ignored findings
- **Dependencies:** IgnoreService, Model classes
- **Refactoring:** Update service integration
- **Complexity:** Medium
- **Effort:** 0.25 days

**IgnoreAction.java**
- **Purpose:** UI action to ignore findings
- **Dependencies:** IgnoreService, UI components
- **Refactoring:** Update action registration in plugin.xml
- **Complexity:** Low
- **Effort:** 0.125 days

**IgnorePersistence.java**
- **Purpose:** Persists ignored findings to disk
- **Dependencies:** FileUtils, IgnoredFindingsManager
- **Refactoring:** Update file paths for standalone plugin
- **Complexity:** Medium
- **Effort:** 0.125 days

**Subtotal:** 1 day

---

#### 5. Inspection Package (4 files)

**CxOneAssistInspection.java**
- **Purpose:** Core inspection implementation for real-time scanning
- **Dependencies:** BaseScannerService, ProblemHolderService, ScannerRegistry
- **Refactoring:** Update inspection registration, ensure works in standalone plugin
- **Complexity:** Very High
- **Effort:** 1.5 days

**CxOneAssistScanScheduler.java**
- **Purpose:** Schedules and manages scan execution
- **Dependencies:** CxOneAssistInspection, GlobalScannerController
- **Refactoring:** Update scheduling mechanism for plugin dependency
- **Complexity:** High
- **Effort:** 1 day

**InspectionProfileManager.java**
- **Purpose:** Manages inspection profiles and settings
- **Dependencies:** GlobalSettingsState, CxOneAssistInspection
- **Refactoring:** Update profile management for standalone plugin
- **Complexity:** Medium
- **Effort:** 0.25 days

**InspectionResultProcessor.java**
- **Purpose:** Processes inspection results and updates UI
- **Dependencies:** ProblemHolderService, UI components
- **Refactoring:** Update result processing pipeline
- **Complexity:** Medium
- **Effort:** 0.25 days

**Subtotal:** 3 days

---

#### 6. Listeners Package (1 file)

**FileChangeListener.java**
- **Purpose:** Listens to file changes and triggers scans
- **Dependencies:** CxOneAssistScanScheduler, GlobalScannerController
- **Refactoring:** Update listener registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.5 days

**Subtotal:** 0.5 days

---

#### 7. Model Package (3 files)

**ScanIssue.java**
- **Purpose:** Data model for individual scan issues
- **Dependencies:** None (data class)
- **Refactoring:** Move to common library (used by both plugins)
- **Complexity:** Low
- **Effort:** 0.125 days

**Vulnerability.java**
- **Purpose:** Data model for vulnerability details
- **Dependencies:** SeverityLevel (common)
- **Refactoring:** Move to common library (used by both plugins)
- **Complexity:** Low
- **Effort:** 0.125 days

**ScanResult.java**
- **Purpose:** Data model for complete scan results
- **Dependencies:** ScanIssue, Vulnerability
- **Refactoring:** Move to common library (used by both plugins)
- **Complexity:** Low
- **Effort:** 0 days

**Subtotal:** 0.25 days

---

#### 8. Problems Package (5 files)

**ProblemHolderService.java**
- **Purpose:** Central service for holding and managing problems
- **Dependencies:** Model classes, GlobalSettingsState
- **Refactoring:** Update service registration, ensure thread-safety
- **Complexity:** High
- **Effort:** 1 day

**ProblemDecorator.java**
- **Purpose:** Decorates problems with additional metadata
- **Dependencies:** ProblemHolderService, Model classes
- **Refactoring:** Update decoration logic
- **Complexity:** Medium
- **Effort:** 0.5 days

**ProblemBuilder.java**
- **Purpose:** Builder pattern for creating problem instances
- **Dependencies:** Model classes
- **Refactoring:** None required (utility class)
- **Complexity:** Low
- **Effort:** 0.125 days

**ProblemCache.java**
- **Purpose:** Caches problems for performance
- **Dependencies:** ProblemHolderService
- **Refactoring:** Update cache invalidation strategy
- **Complexity:** Medium
- **Effort:** 0.625 days

**ProblemAnnotator.java**
- **Purpose:** Annotates code with problem markers
- **Dependencies:** ProblemHolderService, IntelliJ Platform APIs
- **Refactoring:** Update annotator registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.5 days

**Subtotal:** 2.75 days

---

#### 9. Registry Package (1 file)

**ScannerRegistry.java**
- **Purpose:** Registry for all available scanners
- **Dependencies:** BaseScannerService, ScannerFactory classes
- **Refactoring:** Update registry initialization, ensure all 5 scanners are registered
- **Complexity:** High
- **Effort:** 1 day

**Subtotal:** 1 day


---

#### 10. Remediation Package (9 files)

**CxOneAssistFix.java**
- **Purpose:** Core fix implementation for applying remediations
- **Dependencies:** RemediationManager, ProblemHolderService, IntelliJ Platform APIs
- **Refactoring:** Update fix registration in plugin.xml, ensure works in standalone plugin
- **Complexity:** Very High
- **Effort:** 1 day

**RemediationManager.java**
- **Purpose:** Manages remediation workflow and coordination
- **Dependencies:** CopilotIntegration, RemediationProvider, RemediationCache
- **Refactoring:** Update manager initialization, ensure plugin dependency works
- **Complexity:** High
- **Effort:** 0.75 days

**CopilotIntegration.java**
- **Purpose:** Integrates with GitHub Copilot for AI-assisted remediation
- **Dependencies:** RemediationProvider, External Copilot APIs
- **Refactoring:** Update integration configuration, ensure API compatibility
- **Complexity:** High
- **Effort:** 0.5 days

**RemediationProvider.java**
- **Purpose:** Provides remediation suggestions for vulnerabilities
- **Dependencies:** Model classes, AST wrapper library
- **Refactoring:** Update provider registration
- **Complexity:** Medium
- **Effort:** 0.5 days

**FixApplicator.java**
- **Purpose:** Applies fixes to source code
- **Dependencies:** IntelliJ Platform APIs, FileUtils
- **Refactoring:** Update file modification logic
- **Complexity:** High
- **Effort:** 0.5 days

**RemediationCache.java**
- **Purpose:** Caches remediation suggestions for performance
- **Dependencies:** RemediationProvider
- **Refactoring:** Update cache invalidation strategy
- **Complexity:** Medium
- **Effort:** 0.25 days

**RemediationUI.java**
- **Purpose:** UI components for remediation workflow
- **Dependencies:** RemediationManager, UI components
- **Refactoring:** Update UI registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.5 days

**McpRemediationHandler.java**
- **Purpose:** Handles MCP (Model Context Protocol) integration for AI remediation
- **Dependencies:** McpConfiguration, RemediationProvider
- **Refactoring:** Update MCP configuration access
- **Complexity:** High
- **Effort:** 0.25 days

**RemediationTelemetry.java**
- **Purpose:** Tracks telemetry for remediation actions
- **Dependencies:** TelemetryService
- **Refactoring:** Update telemetry integration
- **Complexity:** Low
- **Effort:** 0.25 days

**Subtotal:** 4.5 days

---

#### 11. Scanners Package (15 files - 5 scanner types)

**OSS Scanner (3 files)**

**OssScannerCommand.java**
- **Purpose:** Command implementation for OSS dependency scanning
- **Dependencies:** BaseScannerCommand, AST wrapper library
- **Refactoring:** Update command registration, ensure wrapper integration works
- **Complexity:** High
- **Effort:** 0.5 days

**OssScannerService.java**
- **Purpose:** Service for OSS scanning operations
- **Dependencies:** BaseScannerService, OssScannerCommand
- **Refactoring:** Update service registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**OssScannerFactory.java**
- **Purpose:** Factory for creating OSS scanner instances
- **Dependencies:** OssScannerService, ScannerRegistry
- **Refactoring:** Update factory registration
- **Complexity:** Low
- **Effort:** 0.125 days

**Secrets Scanner (3 files)**

**SecretsScannerCommand.java**
- **Purpose:** Command implementation for secrets scanning
- **Dependencies:** BaseScannerCommand, AST wrapper library
- **Refactoring:** Update command registration, ensure wrapper integration works
- **Complexity:** High
- **Effort:** 0.5 days

**SecretsScannerService.java**
- **Purpose:** Service for secrets scanning operations
- **Dependencies:** BaseScannerService, SecretsScannerCommand
- **Refactoring:** Update service registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**SecretsScannerFactory.java**
- **Purpose:** Factory for creating secrets scanner instances
- **Dependencies:** SecretsScannerService, ScannerRegistry
- **Refactoring:** Update factory registration
- **Complexity:** Low
- **Effort:** 0.125 days

**Container Scanner (3 files)**

**ContainerScannerCommand.java**
- **Purpose:** Command implementation for container scanning
- **Dependencies:** BaseScannerCommand, AST wrapper library
- **Refactoring:** Update command registration, ensure wrapper integration works
- **Complexity:** High
- **Effort:** 0.5 days

**ContainerScannerService.java**
- **Purpose:** Service for container scanning operations
- **Dependencies:** BaseScannerService, ContainerScannerCommand
- **Refactoring:** Update service registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**ContainerScannerFactory.java**
- **Purpose:** Factory for creating container scanner instances
- **Dependencies:** ContainerScannerService, ScannerRegistry
- **Refactoring:** Update factory registration
- **Complexity:** Low
- **Effort:** 0.125 days

**IaC Scanner (3 files)**

**IacScannerCommand.java**
- **Purpose:** Command implementation for Infrastructure as Code scanning
- **Dependencies:** BaseScannerCommand, AST wrapper library
- **Refactoring:** Update command registration, ensure wrapper integration works
- **Complexity:** High
- **Effort:** 0.5 days

**IacScannerService.java**
- **Purpose:** Service for IaC scanning operations
- **Dependencies:** BaseScannerService, IacScannerCommand
- **Refactoring:** Update service registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**IacScannerFactory.java**
- **Purpose:** Factory for creating IaC scanner instances
- **Dependencies:** IacScannerService, ScannerRegistry
- **Refactoring:** Update factory registration
- **Complexity:** Low
- **Effort:** 0.125 days

**ASCA Scanner (3 files)**

**AscaScannerCommand.java**
- **Purpose:** Command implementation for ASCA (Application Security Code Analysis) scanning
- **Dependencies:** BaseScannerCommand, AST wrapper library
- **Refactoring:** Update command registration, ensure wrapper integration works
- **Complexity:** High
- **Effort:** 0.5 days

**AscaScannerService.java**
- **Purpose:** Service for ASCA scanning operations
- **Dependencies:** BaseScannerService, AscaScannerCommand
- **Refactoring:** Update service registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**AscaScannerFactory.java**
- **Purpose:** Factory for creating ASCA scanner instances
- **Dependencies:** AscaScannerService, ScannerRegistry
- **Refactoring:** Update factory registration
- **Complexity:** Low
- **Effort:** 0.125 days

**Subtotal:** 4.375 days

---

#### 12. Telemetry Package (1 file)

**TelemetryService.java**
- **Purpose:** Collects and sends telemetry data for DevAssist operations
- **Dependencies:** IntelliJ Platform APIs, External telemetry endpoints
- **Refactoring:** Update telemetry configuration, ensure privacy compliance
- **Complexity:** Medium
- **Effort:** 0.5 days

**Subtotal:** 0.5 days

---

#### 13. UI Package (11 files)

**CxFindingsWindow.java**
- **Purpose:** Main tool window for displaying security findings
- **Dependencies:** ProblemHolderService, FindingsTreeRenderer, IntelliJ Platform APIs
- **Refactoring:** Update tool window registration in plugin.xml
- **Complexity:** High
- **Effort:** 0.75 days

**CxIgnoredFindings.java**
- **Purpose:** UI for managing ignored findings
- **Dependencies:** IgnoreService, IgnoredFindingsManager
- **Refactoring:** Update UI integration
- **Complexity:** Medium
- **Effort:** 0.5 days

**FindingsTreeRenderer.java**
- **Purpose:** Custom tree renderer for findings display
- **Dependencies:** Model classes, SeverityIconProvider
- **Refactoring:** Update rendering logic
- **Complexity:** Medium
- **Effort:** 0.25 days

**FindingDetailsPanel.java**
- **Purpose:** Panel for displaying detailed finding information
- **Dependencies:** Model classes, RemediationUI
- **Refactoring:** Update panel layout and data binding
- **Complexity:** Medium
- **Effort:** 0.5 days

**ScannerStatusWidget.java**
- **Purpose:** Status bar widget showing scanner status
- **Dependencies:** ScannerRegistry, GlobalScannerController
- **Refactoring:** Update widget registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.25 days

**SettingsPanel.java**
- **Purpose:** UI panel for DevAssist settings
- **Dependencies:** GlobalSettingsState, McpConfiguration
- **Refactoring:** Update settings UI integration
- **Complexity:** Medium
- **Effort:** 0.5 days

**RemediationDialog.java**
- **Purpose:** Dialog for remediation workflow
- **Dependencies:** RemediationManager, CopilotIntegration
- **Refactoring:** Update dialog integration
- **Complexity:** Medium
- **Effort:** 0.5 days

**ScanProgressIndicator.java**
- **Purpose:** Progress indicator for scan operations
- **Dependencies:** IntelliJ Platform APIs
- **Refactoring:** Update progress tracking
- **Complexity:** Low
- **Effort:** 0.25 days

**FindingsFilterPanel.java**
- **Purpose:** Panel for filtering findings by severity, type, etc.
- **Dependencies:** ProblemHolderService, Model classes
- **Refactoring:** Update filter logic
- **Complexity:** Medium
- **Effort:** 0.25 days

**SeverityIconProvider.java**
- **Purpose:** Provides icons for different severity levels
- **Dependencies:** SeverityLevel enum
- **Refactoring:** Update icon resources
- **Complexity:** Low
- **Effort:** 0.125 days

**ToolWindowFactory.java**
- **Purpose:** Factory for creating DevAssist tool window
- **Dependencies:** CxFindingsWindow, IntelliJ Platform APIs
- **Refactoring:** Update factory registration in plugin.xml
- **Complexity:** Medium
- **Effort:** 0.125 days

**Subtotal:** 4 days

---

#### 14. Utils Package (4 files)

**FileUtils.java**
- **Purpose:** Utility methods for file operations
- **Dependencies:** IntelliJ Platform APIs
- **Refactoring:** Move to common library if used by both plugins
- **Complexity:** Low
- **Effort:** 0.125 days

**NotificationUtils.java**
- **Purpose:** Utility methods for showing notifications
- **Dependencies:** IntelliJ Platform APIs
- **Refactoring:** Move to common library if used by both plugins
- **Complexity:** Low
- **Effort:** 0.125 days

**ThreadUtils.java**
- **Purpose:** Utility methods for thread management
- **Dependencies:** IntelliJ Platform APIs
- **Refactoring:** Move to common library if used by both plugins
- **Complexity:** Low
- **Effort:** 0.125 days

**ValidationUtils.java**
- **Purpose:** Utility methods for input validation
- **Dependencies:** None
- **Refactoring:** Move to common library if used by both plugins
- **Complexity:** Low
- **Effort:** 0.125 days

**Subtotal:** 0.5 days


---

## 6. Risk Analysis

### 6.1 Breaking Changes

**Plugin Dependency Mechanism**
- **Risk:** Combined plugin depends on DevAssist plugin being installed
- **Impact:** Users must install both plugins; installation order matters
- **Mitigation:**
  - Clear documentation in plugin marketplace
  - Automatic dependency resolution in IntelliJ
  - Installation wizard to guide users

**Settings Synchronization**
- **Risk:** Settings split between two plugins may cause confusion
- **Impact:** Users may not find settings in expected locations
- **Mitigation:**
  - Unified settings UI in Combined plugin
  - Settings migration tool
  - Clear documentation

**Tool Window Conflicts**
- **Risk:** Both plugins may try to register tool windows
- **Impact:** UI conflicts, duplicate windows
- **Mitigation:**
  - DevAssist plugin registers its own tool window
  - Combined plugin extends/integrates with DevAssist tool window
  - Proper extension point usage

**Service Initialization Order**
- **Risk:** Combined plugin services may initialize before DevAssist services
- **Impact:** NullPointerException, service unavailability
- **Mitigation:**
  - Explicit service dependencies in plugin.xml
  - Lazy initialization where possible
  - Proper startup activity ordering

### 6.2 Backward Compatibility

**Migration from Monolithic Plugin**
- **Risk:** Existing users have single plugin installed
- **Impact:** Breaking change for existing installations
- **Mitigation:**
  - Migration guide with step-by-step instructions
  - Automated migration tool if possible
  - Deprecation period with warnings

**Settings Migration**
- **Risk:** Existing settings may not transfer correctly
- **Impact:** Users lose their configuration
- **Mitigation:**
  - Settings migration utility
  - Export/import functionality
  - Default settings that match current behavior

**Ignored Findings Persistence**
- **Risk:** Ignored findings data may not migrate
- **Impact:** Users lose their ignore list
- **Mitigation:**
  - Data migration tool
  - Backward-compatible persistence format
  - Import/export functionality

### 6.3 Performance Impact

**Plugin Loading Overhead**
- **Risk:** Loading two plugins instead of one
- **Impact:** Increased IDE startup time
- **Mitigation:**
  - Lazy loading of components
  - Optimize plugin initialization
  - Benchmark and monitor startup time

**Memory Footprint**
- **Risk:** Two separate plugins may use more memory
- **Impact:** Increased memory consumption
- **Mitigation:**
  - Share common library to reduce duplication
  - Optimize data structures
  - Monitor memory usage

**Startup Time**
- **Risk:** Sequential plugin initialization
- **Impact:** Slower IDE startup
- **Mitigation:**
  - Parallel initialization where possible
  - Defer non-critical initialization
  - Use startup activities wisely

### 6.4 Deployment Complexity

**Separate Plugin Installation**
- **Risk:** Users must install multiple plugins
- **Impact:** Poor user experience, installation errors
- **Mitigation:**
  - Clear installation instructions
  - Automatic dependency resolution
  - Plugin bundles if supported

**Version Compatibility**
- **Risk:** DevAssist and Combined plugin versions may mismatch
- **Impact:** Runtime errors, feature incompatibility
- **Mitigation:**
  - Strict version dependencies in plugin.xml
  - Semantic versioning
  - Compatibility matrix documentation

**Update Coordination**
- **Risk:** Plugins may update independently
- **Impact:** Version mismatches, broken functionality
- **Mitigation:**
  - Coordinated release schedule
  - Version compatibility checks
  - Update notifications

### 6.5 Development Workflow

**Multi-Module Build Complexity**
- **Risk:** More complex build configuration
- **Impact:** Longer build times, build failures
- **Mitigation:**
  - Gradle multi-module setup
  - Incremental builds
  - CI/CD pipeline optimization

**Testing Complexity**
- **Risk:** Must test plugin interactions
- **Impact:** More test scenarios, longer test cycles
- **Mitigation:**
  - Comprehensive integration tests
  - Automated testing in CI/CD
  - Test matrix for version combinations

**Debugging Challenges**
- **Risk:** Debugging across plugin boundaries
- **Impact:** Harder to troubleshoot issues
- **Mitigation:**
  - Enhanced logging
  - Debug configurations for multi-plugin setup
  - Clear error messages with context

---

## 7. Total Effort Summary

### 7.1 Phase-by-Phase Breakdown

**Phase 1: Common Library Creation**
- Project setup and build configuration: 2 days
- Extract shared utilities and constants: 3 days
- Extract shared models and interfaces: 2 days
- Extract shared services: 3 days
- Testing and validation: 2 days
- Documentation: 1 day
- **Phase 1 Total: 13 days (2.6 weeks)**

**Phase 2: DevAssist Plugin Extraction**
- Project setup and plugin.xml configuration: 3 days
- Base Scanner Package: 1 day
- Common Package: 1.5 days
- Configuration Package: 4.25 days
- Ignore Package: 1 day
- Inspection Package: 3 days
- Listeners Package: 0.5 days
- Model Package: 0.25 days
- Problems Package: 2.75 days
- Registry Package: 1 day
- Remediation Package: 4.5 days
- Scanners Package: 4.375 days
- Telemetry Package: 0.5 days
- UI Package: 4 days
- Utils Package: 0.5 days
- Testing and validation: 3 days
- Documentation: 1 day
- **Phase 2 Total: 21 days (4.2 weeks)**

**Phase 3: Combined Plugin Refactoring**
- Project setup and dependency configuration: 2 days
- Refactor non-DevAssist components: 8 days
- Update plugin.xml and extension points: 3 days
- Integrate DevAssist plugin dependency: 4 days
- Update settings and configuration: 2 days
- Testing and validation: 3 days
- Documentation: 1 day
- **Phase 3 Total: 20 days (4.0 weeks)**

**Phase 4: Integration Testing & Validation**
- Integration test suite development: 5 days
- End-to-end testing: 4 days
- Performance testing and optimization: 3 days
- User acceptance testing: 3 days
- Bug fixes and refinements: 2 days
- Final documentation and release notes: 1 day
- **Phase 4 Total: 18 days (3.6 weeks)**

### 7.2 Consolidated Timeline

**Total Effort (without buffer):**
- Phase 1: 13 days
- Phase 2: 21 days
- Phase 3: 20 days
- Phase 4: 18 days
- **Total: 72 days (14.4 weeks / ~3.6 months)**

**Total Effort (with 20% buffer):**
- **Total: 86 days (~17 weeks / ~4.25 months)**

### 7.3 Detailed Package Breakdown

**DevAssist Package Analysis (67 files):**
1. Base Scanner Package: 1 day (4 files)
2. Common Package: 1.5 days (3 files)
3. Configuration Package: 4.25 days (7 files)
4. Ignore Package: 1 day (4 files)
5. Inspection Package: 3 days (4 files)
6. Listeners Package: 0.5 days (1 file)
7. Model Package: 0.25 days (3 files)
8. Problems Package: 2.75 days (5 files)
9. Registry Package: 1 day (1 file)
10. Remediation Package: 4.5 days (9 files)
11. Scanners Package: 4.375 days (15 files)
12. Telemetry Package: 0.5 days (1 file)
13. UI Package: 4 days (11 files)
14. Utils Package: 0.5 days (4 files)

**DevAssist Packages Subtotal: 29.125 days**

**Additional Effort:**
- Project setup and configuration: 5 days
- Common library extraction: 13 days
- Combined plugin refactoring: 20 days
- Integration testing: 18 days
- Documentation: 3 days

**Grand Total: 88.125 days (~17.6 weeks / ~4.4 months)**


---

## 8. Implementation Roadmap

### Milestone 1: Common Library Setup (Weeks 1-3, 13 days)

**Objectives:**
- Establish common library module structure
- Extract and migrate shared utilities, constants, and models
- Set up build configuration and dependency management

**Deliverables:**
- Common library module with proper package structure
- Extracted shared utilities (FileUtils, NotificationUtils, ThreadUtils, ValidationUtils)
- Extracted shared constants and enums (SeverityLevel, Constants)
- Extracted shared models and interfaces
- Build configuration (build.gradle) for common library
- Unit tests for common library components
- API documentation for common library

**Dependencies:**
- None (first phase)

**Success Criteria:**
- Common library builds successfully
- All unit tests pass
- No circular dependencies
- Clear API documentation

**Key Activities:**
1. Create common library module structure
2. Identify and extract shared utilities
3. Identify and extract shared constants
4. Identify and extract shared models
5. Update build.gradle for common library
6. Write unit tests for extracted components
7. Generate API documentation
8. Code review and validation

---

### Milestone 2: DevAssist Plugin Extraction (Weeks 4-7, 21 days)

**Objectives:**
- Extract all DevAssist functionality into standalone plugin
- Configure plugin.xml for DevAssist plugin
- Integrate common library dependency
- Ensure all DevAssist features work independently

**Deliverables:**
- DevAssist plugin module with complete functionality
- plugin.xml configuration with all extensions and services
- Integration with common library
- All 14 DevAssist packages migrated and functional
- Unit and integration tests for DevAssist plugin
- DevAssist plugin documentation

**Dependencies:**
- Milestone 1 (Common Library) must be complete

**Success Criteria:**
- DevAssist plugin builds and runs independently
- All scanner types work correctly (OSS, Secrets, Containers, IaC, ASCA)
- Inspection framework functions properly
- UI components render correctly
- Settings and configuration work
- All tests pass
- No regressions in functionality

**Key Activities:**
1. Create DevAssist plugin module structure
2. Configure plugin.xml with extensions and services
3. Migrate Base Scanner Package (1 day)
4. Migrate Common Package (1.5 days)
5. Migrate Configuration Package (4.25 days)
6. Migrate Ignore Package (1 day)
7. Migrate Inspection Package (3 days)
8. Migrate Listeners Package (0.5 days)
9. Migrate Model Package (0.25 days)
10. Migrate Problems Package (2.75 days)
11. Migrate Registry Package (1 day)
12. Migrate Remediation Package (4.5 days)
13. Migrate Scanners Package (4.375 days)
14. Migrate Telemetry Package (0.5 days)
15. Migrate UI Package (4 days)
16. Migrate Utils Package (0.5 days)
17. Write integration tests
18. Perform end-to-end testing
19. Code review and validation
20. Update documentation

---

### Milestone 3: Combined Plugin Refactoring (Weeks 8-11, 20 days)

**Objectives:**
- Refactor existing plugin to remove DevAssist components
- Configure plugin dependency on DevAssist plugin
- Integrate DevAssist features through plugin dependency
- Ensure seamless interaction between plugins

**Deliverables:**
- Refactored combined plugin without DevAssist code
- plugin.xml with DevAssist plugin dependency
- Integration layer for DevAssist features
- Updated settings and configuration
- Updated tool windows and UI
- Integration tests for plugin interaction
- Combined plugin documentation

**Dependencies:**
- Milestone 2 (DevAssist Plugin) must be complete

**Success Criteria:**
- Combined plugin builds successfully
- DevAssist plugin dependency resolves correctly
- All non-DevAssist features work correctly
- DevAssist features accessible through combined plugin
- Settings synchronization works
- Tool windows integrate properly
- No conflicts between plugins
- All tests pass

**Key Activities:**
1. Create combined plugin module structure
2. Configure DevAssist plugin dependency in plugin.xml
3. Refactor non-DevAssist components (8 days)
4. Update extension points and services (3 days)
5. Implement integration layer (4 days)
6. Update settings and configuration (2 days)
7. Update UI components
8. Write integration tests (3 days)
9. Perform end-to-end testing
10. Code review and validation
11. Update documentation (1 day)

---

### Milestone 4: Integration Testing & Validation (Weeks 12-15, 18 days)

**Objectives:**
- Comprehensive testing of all three artifacts
- Validate plugin interactions and dependencies
- Performance testing and optimization
- User acceptance testing

**Deliverables:**
- Comprehensive integration test suite
- Performance test results and optimizations
- UAT test results and feedback
- Bug fixes and refinements
- Test reports and documentation

**Dependencies:**
- Milestone 3 (Combined Plugin) must be complete

**Success Criteria:**
- All integration tests pass
- Performance meets or exceeds baseline
- No critical or high-priority bugs
- UAT feedback is positive
- All test scenarios covered
- Documentation is complete and accurate

**Key Activities:**
1. Develop integration test suite (5 days)
   - Plugin dependency resolution tests
   - Service interaction tests
   - Settings synchronization tests
   - Tool window integration tests
   - Scanner functionality tests
   - Remediation workflow tests
2. Execute end-to-end testing (4 days)
   - Install both plugins
   - Test all features
   - Test plugin updates
   - Test uninstall/reinstall
3. Performance testing and optimization (3 days)
   - Startup time benchmarks
   - Memory usage analysis
   - Scanner performance tests
   - UI responsiveness tests
4. User acceptance testing (3 days)
   - Internal team testing
   - Beta user testing
   - Collect and analyze feedback
5. Bug fixes and refinements (2 days)
   - Fix identified issues
   - Optimize performance bottlenecks
   - Refine UI/UX based on feedback
6. Final documentation (1 day)
   - Update user documentation
   - Create migration guide
   - Write release notes

---

### Milestone 5: Production Release (Weeks 16-17, 5 days)

**Objectives:**
- Prepare for production release
- Deploy to JetBrains Marketplace
- Monitor initial adoption and issues

**Deliverables:**
- Production-ready builds of all three artifacts
- JetBrains Marketplace listings
- Migration guide for existing users
- Release notes and announcements
- Monitoring and support plan

**Dependencies:**
- Milestone 4 (Integration Testing) must be complete

**Success Criteria:**
- All artifacts published to JetBrains Marketplace
- Migration guide available
- Release notes published
- Monitoring in place
- Support team prepared

**Key Activities:**
1. Final build and packaging (1 day)
2. JetBrains Marketplace submission (1 day)
3. Create migration guide (1 day)
4. Write release notes and announcements (1 day)
5. Set up monitoring and telemetry (1 day)
6. Deploy and monitor initial adoption

---

## 9. Success Criteria

### 9.1 Functional Criteria

**DevAssist Plugin (Standalone)**
- ✅ All scanner types work correctly (OSS, Secrets, Containers, IaC, ASCA)
- ✅ Real-time scanning triggers on file changes
- ✅ Inspection framework highlights issues in editor
- ✅ Problem annotations display correctly
- ✅ Remediation features work (AI-assisted, MCP integration)
- ✅ Ignore functionality persists correctly
- ✅ Settings panel accessible and functional
- ✅ Tool window displays findings correctly
- ✅ Telemetry captures events properly

**Combined Plugin (Full Features)**
- ✅ DevAssist plugin dependency resolves automatically
- ✅ All non-DevAssist features work correctly
- ✅ Integration with DevAssist features seamless
- ✅ Settings synchronization works
- ✅ Tool windows integrate properly
- ✅ No conflicts between plugins
- ✅ Project scanning works
- ✅ Authentication and API integration functional

**Common Library**
- ✅ All shared utilities work correctly
- ✅ No circular dependencies
- ✅ Proper versioning and compatibility
- ✅ Clear API documentation

### 9.2 Performance Criteria

**Startup Time**
- ✅ IDE startup time increase < 10% compared to monolithic plugin
- ✅ Plugin initialization completes within 2 seconds
- ✅ No blocking operations during startup

**Memory Usage**
- ✅ Memory footprint increase < 15% compared to monolithic plugin
- ✅ No memory leaks detected
- ✅ Efficient resource cleanup on plugin unload

**Scanning Performance**
- ✅ Real-time scanning latency < 500ms for typical files
- ✅ Project scanning performance matches baseline
- ✅ No UI freezing during scans

**Responsiveness**
- ✅ UI remains responsive during background operations
- ✅ Tool window updates within 100ms
- ✅ Settings changes apply immediately

### 9.3 Quality Criteria

**Test Coverage**
- ✅ Unit test coverage > 80% for all modules
- ✅ Integration test coverage for all plugin interactions
- ✅ End-to-end tests for critical user workflows
- ✅ Performance tests for key operations

**Code Quality**
- ✅ No critical or high-priority code quality issues
- ✅ Consistent coding standards across all modules
- ✅ Proper error handling and logging
- ✅ Clear separation of concerns

**Documentation**
- ✅ API documentation for common library
- ✅ Developer documentation for each plugin
- ✅ User documentation and migration guide
- ✅ Architecture documentation
- ✅ Release notes

### 9.4 User Experience Criteria

**Installation**
- ✅ Clear installation instructions
- ✅ Automatic dependency resolution
- ✅ No manual configuration required
- ✅ Installation completes in < 2 minutes

**Migration**
- ✅ Seamless migration from monolithic plugin
- ✅ Settings migrate automatically
- ✅ Ignored findings persist
- ✅ No data loss during migration

**Usability**
- ✅ Intuitive UI with no learning curve
- ✅ Clear error messages
- ✅ Helpful tooltips and documentation links
- ✅ Consistent with IntelliJ platform conventions

**Backward Compatibility**
- ✅ Support for IntelliJ IDEA 2021.3+
- ✅ Compatible with existing project configurations
- ✅ No breaking changes for existing users

---

## 10. Recommendations

### 10.1 Development Best Practices

**Use Feature Flags for Gradual Rollout**
- Implement feature flags to enable/disable new functionality
- Allows gradual rollout and quick rollback if issues arise
- Enables A/B testing of new features
- Reduces risk of breaking changes

**Implement Comprehensive Integration Tests**
- Test all plugin interactions thoroughly
- Automate integration tests in CI/CD pipeline
- Test version compatibility matrix
- Include negative test cases

**Create Detailed Migration Guide**
- Step-by-step instructions for existing users
- Screenshots and examples
- Troubleshooting section
- FAQ for common issues

**Set Up CI/CD for Multi-Module Builds**
- Automate build process for all modules
- Run tests on every commit
- Automated deployment to test environment
- Version compatibility checks

### 10.2 Monitoring and Telemetry

**Monitor Adoption Metrics**
- Track plugin installations and updates
- Monitor feature usage
- Identify popular and unused features
- Collect user feedback

**Track Performance Metrics**
- Monitor startup time
- Track memory usage
- Measure scanning performance
- Identify performance bottlenecks

**Error Tracking**
- Implement comprehensive error logging
- Set up error reporting and alerting
- Track error rates and trends
- Prioritize fixes based on impact

### 10.3 Version Compatibility Strategy

**Semantic Versioning**
- Use semantic versioning (MAJOR.MINOR.PATCH)
- MAJOR: Breaking changes
- MINOR: New features, backward compatible
- PATCH: Bug fixes, backward compatible

**Compatibility Matrix**
- Document compatible versions of DevAssist and Combined plugins
- Enforce version constraints in plugin.xml
- Test version combinations in CI/CD

**Coordinated Releases**
- Release DevAssist and Combined plugins together
- Synchronize version numbers
- Clear release notes for both plugins

### 10.4 Release Coordination Process

**Pre-Release Checklist**
- All tests pass
- Performance benchmarks meet criteria
- Documentation updated
- Release notes prepared
- Migration guide ready

**Release Process**
- Build and package all artifacts
- Submit to JetBrains Marketplace
- Monitor approval process
- Announce release to users
- Monitor initial adoption

**Post-Release Monitoring**
- Monitor error rates
- Track user feedback
- Respond to issues quickly
- Plan hotfixes if needed

### 10.5 Risk Mitigation Strategies

**Phased Rollout**
- Release to internal team first
- Beta release to selected users
- Gradual rollout to all users
- Monitor each phase before proceeding

**Rollback Plan**
- Keep monolithic plugin available as fallback
- Document rollback procedure
- Test rollback process
- Communicate rollback plan to users

**Support Preparation**
- Train support team on new architecture
- Prepare troubleshooting guides
- Set up dedicated support channel
- Monitor support tickets closely

---

## 11. Conclusion

This comprehensive effort analysis provides a detailed roadmap for splitting the Checkmarx IntelliJ plugin into three deployable artifacts: a standalone DevAssist plugin, a combined features plugin, and a common library. The analysis covers all 67 classes in the `com.checkmarx.intellij.devassist` package across 14 sub-packages, as well as the non-DevAssist components.

### Key Findings

**Total Effort Estimate: 88.125 days (~17.6 weeks / ~4.4 months)**

The effort is distributed across four main phases:
1. **Common Library Creation:** 13 days (2.6 weeks)
2. **DevAssist Plugin Extraction:** 21 days (4.2 weeks)
3. **Combined Plugin Refactoring:** 20 days (4.0 weeks)
4. **Integration Testing & Validation:** 18 days (3.6 weeks)

With a 20% buffer for unforeseen challenges, the total timeline extends to approximately **86 days (~17 weeks / ~4.25 months)**.

### Key Benefits

**Modularity and Separation of Concerns**
- Clear separation between DevAssist and non-DevAssist features
- Independent development and testing of each module
- Easier maintenance and debugging

**Independent Deployment**
- Users can install only DevAssist plugin if they only need real-time scanning
- Users can install combined plugin for full feature set
- Faster release cycles for individual components

**Code Reusability**
- Common library eliminates code duplication
- Shared utilities and models used by both plugins
- Consistent behavior across plugins

**Scalability**
- Easier to add new features to specific plugins
- Better resource management
- Improved performance through lazy loading

### Critical Success Factors

1. **Thorough Dependency Analysis:** Ensure all dependencies between DevAssist and non-DevAssist components are identified and properly handled
2. **Comprehensive Testing:** Integration tests are crucial to validate plugin interactions
3. **Clear Documentation:** Migration guide and user documentation are essential for smooth adoption
4. **Performance Monitoring:** Ensure the split architecture doesn't degrade performance
5. **User Communication:** Keep users informed throughout the migration process

### Risk Mitigation

The analysis identifies key risks in five categories:
- Breaking changes (plugin dependency, settings sync, tool window conflicts)
- Backward compatibility (migration, settings, ignored findings)
- Performance impact (loading overhead, memory, startup time)
- Deployment complexity (installation, version compatibility, updates)
- Development workflow (build complexity, testing, debugging)

Each risk has specific mitigation strategies to minimize impact.

### Final Recommendations

1. **Start with Common Library:** Establish a solid foundation before extracting plugins
2. **Use Feature Flags:** Enable gradual rollout and quick rollback
3. **Automate Testing:** Comprehensive CI/CD pipeline for all modules
4. **Monitor Closely:** Track adoption, performance, and errors
5. **Communicate Clearly:** Keep users informed and provide excellent documentation
6. **Plan for Migration:** Provide tools and guides for seamless transition
7. **Coordinate Releases:** Synchronize DevAssist and Combined plugin releases

### Next Steps

1. Review and approve this effort analysis
2. Allocate resources and team members
3. Set up project tracking and milestones
4. Begin Phase 1: Common Library Creation
5. Establish CI/CD pipeline for multi-module builds
6. Create detailed technical design documents for each phase

This plugin split initiative represents a significant architectural improvement that will enhance maintainability, scalability, and user experience. With careful planning, thorough testing, and clear communication, the transition can be executed successfully within the estimated timeline.

---

**Document Version:** 1.0
**Last Updated:** 2026-01-27
**Author:** Plugin Architecture Team
**Status:** Ready for Review
- All DevAssist features (via plugin dependency)
- Traditional scan results display
- Project/branch/scan selection
- Scan creation and management
- Triage management
- Settings UI (combines both)

**Estimated Size:** ~15,000 LOC (excluding DevAssist)

---

## Detailed Effort Estimation

### Phase 1: Common Library Extraction (3-4 weeks)

**Tasks:**
1. Create new Gradle module for common library
   - Setup build.gradle with proper dependencies
   - Configure artifact publishing
   - **Effort:** 2 days

2. Extract and refactor shared classes
   - Move Utils, Constants, Bundle, Resource, CxIcons
   - Move GlobalSettingsState and related classes
   - Move authentication components
   - Update package references
   - **Effort:** 5 days

3. Define clean interfaces
   - Create SettingsProvider interface
   - Create WrapperFactory interface
   - Ensure backward compatibility
   - **Effort:** 3 days

4. Testing and validation
   - Unit tests for common library
   - Integration tests
   - **Effort:** 3 days

**Total Phase 1:** 13 days (2.6 weeks)

---

### Phase 2: DevAssist Plugin Creation (4-5 weeks)

**Tasks:**
1. Create new plugin project structure
   - Setup build.gradle
   - Configure plugin.xml
   - Setup dependencies (common library, AST wrapper)
   - **Effort:** 2 days

2. Move DevAssist packages
   - Copy all 67 files from devassist package
   - Update imports to use common library
   - **Effort:** 3 days

3. Refactor dependencies
   - Remove dependencies on non-DevAssist classes
   - Create adapter interfaces where needed
   - Update GlobalSettingsState usage
   - **Effort:** 5 days

4. Update plugin.xml configuration
   - Register inspections (CxOneAssistInspection)
   - Register tool windows (3 tabs)
   - Register startup activities
   - Register services
   - **Effort:** 2 days

5. UI adjustments
   - Standalone settings page
   - Simplified authentication flow
   - Tool window registration
   - **Effort:** 4 days

6. Testing
   - Unit tests for all scanners
   - Integration tests
   - UI tests
   - **Effort:** 5 days

**Total Phase 2:** 21 days (4.2 weeks)

---



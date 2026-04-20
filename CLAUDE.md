# CLAUDE.md — Checkmarx One JetBrains Plugin

> Standardized Cloud MD file for [ast-jetbrains-plugin](https://github.com/Checkmarx/ast-jetbrains-plugin)
> Following the Cloud MD standardization template defined in epic AST-146801.

---

## Project Overview

The **Checkmarx One JetBrains Plugin** integrates the full Checkmarx One security platform directly into JetBrains IDEs (IntelliJ IDEA, WebStorm, PyCharm, GoLand, etc.). It enables developers to discover and remediate vulnerabilities without leaving their editor — embodying the shift-left AppSec philosophy.

The project produces **two independent plugins** from a single codebase:

1. **Checkmarx** (`plugin-checkmarx-ast`) — Full-featured plugin with scan management, result viewing, triage, and DevAssist capabilities bundled in.
2. **Checkmarx Developer Assist** (`plugin-checkmarx-devassist`) — Lightweight standalone plugin focused on real-time security scanning and AI-guided remediation.

**Key capabilities:**
- Import scan results (SAST, SCA, IaC Security, Secrets) from Checkmarx One directly into the IDE
- Run new scans from the IDE before committing code
- Navigate from a vulnerability directly to the affected source line
- Triage results (adjust severity, state, add comments) without leaving the IDE
- Filter and group results by severity, state, query name, file, or direct dependency
- Real-time DevAssist scanning (OSS, Secrets, Containers, IaC, ASCA)
- MCP-based agentic AI remediation workflows
- AI-powered explanations of risk details
- Ignore file management for false positives

**Supported JetBrains IDEs:** IntelliJ IDEA, PyCharm, WebStorm, GoLand, and other JetBrains IDEs (build 222+)

---

## Architecture

The plugin follows a **multi-module Gradle architecture** with shared libraries and two independent plugin outputs.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        JetBrains IDE                                │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐     │
│  │  Checkmarx Tool Window   │  │  DevAssist Findings Window   │     │
│  │  (Scan Results, Triage)  │  │  (Real-time Vulnerabilities) │     │
│  └──────────┬───────────────┘  └──────────────┬───────────────┘     │
│             │                                  │                     │
│  ┌──────────┴───────────────┐  ┌──────────────┴───────────────┐     │
│  │   plugin-checkmarx-ast   │  │ plugin-checkmarx-devassist   │     │
│  │   (Full AST + DevAssist) │  │ (Standalone DevAssist)       │     │
│  └──────────┬───────────────┘  └──────────────┬───────────────┘     │
│             │                                  │                     │
│  ┌──────────┴──────────────────────────────────┴───────────────┐    │
│  │                    devassist-lib                              │    │
│  │  (Scanners, Inspections, Remediation, Telemetry, MCP)        │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
│  ┌──────────────────────────┴───────────────────────────────────┐    │
│  │                     common-lib                                │    │
│  │  (Auth, Settings, Icons, Wrapper, Shared UI Components)       │    │
│  └──────────────────────────┬───────────────────────────────────┘    │
└─────────────────────────────┼───────────────────────────────────────┘
                              │
                   ┌──────────┴──────────┐
                   │ ast-cli-java-wrapper │
                   │ (Checkmarx One API)  │
                   └─────────────────────┘
```

**Module dependency graph:**
```
common-lib                  (no internal dependencies)
    ↑
devassist-lib               (depends on common-lib)
    ↑
plugin-checkmarx-ast        (depends on common-lib + devassist-lib)
plugin-checkmarx-devassist  (depends on common-lib + devassist-lib)
```

**Key architectural decisions:**
- **Split plugin output:** Single codebase produces two independent plugin ZIPs — one full-featured (`checkmarx-ast-plugin.zip`) and one lightweight DevAssist-only (`checkmarx-developer-assist-plugin.zip`).
- **CLI wrapper:** All communication with the Checkmarx One platform is delegated to `ast-cli-java-wrapper`, which wraps the Checkmarx CLI binary. No direct REST calls from the plugin.
- **Shared libraries:** `common-lib` and `devassist-lib` are `java-library` modules that export functionality consumed by both plugin modules.
- **Inspection-driven DevAssist:** Real-time scanning is implemented as a JetBrains `LocalInspection`, allowing findings to appear as inline editor warnings.
- **MCP remediation:** AI-guided remediation uses MCP (Model Context Protocol) integration, auto-installed at IDE startup if authenticated.

---

## Repository Structure

```
ast-jetbrains-plugin/
├── common-lib/                                # Shared library (auth, settings, icons, wrapper)
│   └── src/main/java/com/checkmarx/intellij/common/
│       ├── auth/                              # AuthService, OAuthCallbackServer
│       ├── commands/                          # Authentication, TenantSetting CLI wrappers
│       ├── components/                        # CxLinkLabel, PaneUtils, TreeUtils
│       ├── context/                           # PluginContext
│       ├── resources/                         # CxIcons, Bundle (i18n messages), Resource
│       ├── settings/                          # GlobalSettingsState, GlobalSettingsSensitiveState
│       ├── startup/                           # LicenseFlagSyncStartupActivity
│       ├── ui/                                # CommonPanels, DevAssistPromotionalPanel
│       ├── utils/                             # Utility classes
│       ├── window/                            # Shared window actions (ExpandAll, CollapseAll)
│       └── wrapper/                           # AST CLI wrapper integration
├── devassist-lib/                             # DevAssist shared library (scanners, remediation)
│   └── src/main/java/com/checkmarx/intellij/devassist/
│       ├── basescanner/                       # BaseScannerCommand, ScannerService base
│       ├── common/                            # ScanManager, ScannerFactory, ScanResult
│       ├── configuration/                     # GlobalScannerController, ScannerConfig, MCP
│       ├── ignore/                            # IgnoreManager, IgnoreFileManager
│       ├── inspection/                        # DevAssistInspection, DevAssistInspectionMgr
│       ├── listeners/                         # DevAssistProjectListener
│       ├── model/                             # Data models
│       ├── problems/                          # Problem representation
│       ├── registry/                          # Scanner registry
│       ├── remediation/                       # RemediationLinkHandler (MCP-based)
│       ├── scanners/                          # ASCA, OSS, Secrets, IaC, Container scanners
│       ├── telemetry/                         # TelemetryService (analytics events)
│       └── ui/                                # DevAssist UI components and filter actions
├── plugin-checkmarx-ast/                      # Main Checkmarx plugin
│   ├── src/main/java/com/checkmarx/intellij/ast/
│   │   ├── commands/                          # Scan, Project, Results, Triage commands
│   │   ├── inspections/                       # CxInspection
│   │   ├── project/                           # ProjectResultsService, ProjectListener
│   │   ├── results/                           # Result data models
│   │   ├── service/                           # Services
│   │   ├── settings/                          # GlobalSettingsConfigurable
│   │   ├── ui/                                # UI components
│   │   └── window/                            # CxToolWindowFactory, toolbar actions
│   │       └── actions/                       # Filter, GroupBy, Scan, Selection actions
│   ├── src/main/resources/META-INF/plugin.xml # Plugin descriptor (Checkmarx)
│   └── src/test/java/                         # Unit, integration, UI tests
├── plugin-checkmarx-devassist/                # Standalone DevAssist plugin
│   ├── src/main/java/com/checkmarx/intellij/cxdevassist/
│   │   ├── settings/                          # CxDevAssistSettingsConfigurable
│   │   ├── ui/                                # UI components
│   │   ├── utils/                             # Utilities
│   │   └── window/                            # CxDevAssistToolWindowFactory
│   └── src/main/resources/META-INF/plugin.xml # Plugin descriptor (Developer Assist)
├── unit-coverage-report/                      # Aggregated JaCoCo coverage
├── docs/                                      # Contributing guidelines, code of conduct
├── .github/workflows/                         # CI/CD pipelines
├── gradle.properties                          # Centralized version management
├── settings.gradle                            # Module definitions
└── build.gradle                               # Root build configuration
```

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 11 |
| IDE Framework | IntelliJ Platform SDK | 2022.2.1 (sinceBuild: 222) |
| Build System | Gradle (with wrapper) | 8.x |
| Annotation Processing | Lombok (io.freefair.lombok) | 8.6 |
| Plugin Development | org.jetbrains.intellij | 1.17.4 |
| Platform API | ast-cli-java-wrapper | 2.4.23 (configurable) |
| Serialization | Jackson BOM | 2.21.1 |
| HTTP | OkHttp3 / Okio | 4.12.0 / 3.8.0 |
| Logging | Log4j | 2.23.1 |
| UI Layout | MigLayout | 11.3 |
| Testing | JUnit 5 | 5.10.1 / 5.10.2 |
| Mocking | Mockito | 5.0.0 / 5.15.2 |
| UI Testing | IntelliJ Remote Robot | 0.11.23 |
| Coverage | JaCoCo | 0.8.12 |

---

## Development Setup

### Prerequisites

1. **Java 11** (Temurin/Zulu recommended)
2. **IntelliJ IDEA** (Community or Ultimate)
3. **Gradle** (via included wrapper — no separate install needed)
4. **Checkmarx One account** with API key for integration testing

### Clone and Import

```bash
git clone https://github.com/Checkmarx/ast-jetbrains-plugin.git
cd ast-jetbrains-plugin
```

Import into IntelliJ:
- `File -> Open` and select the repo root
- Gradle will auto-import all four modules

### Build from CLI

```bash
# Build AST plugin ZIP
./gradlew :plugin-checkmarx-ast:buildPlugin

# Build DevAssist plugin ZIP
./gradlew :plugin-checkmarx-devassist:buildPlugin

# Build both plugins (skip tests)
./gradlew buildPlugin

# Clean build
./gradlew clean buildPlugin

# Build with library tests only
./gradlew buildWithLibTests
```

**Output locations:**
- `plugin-checkmarx-ast/build/distributions/checkmarx-ast-plugin.zip`
- `plugin-checkmarx-devassist/build/distributions/checkmarx-developer-assist-plugin.zip`

### Run in Development

```bash
# Launch IDE with AST plugin loaded
./gradlew :plugin-checkmarx-ast:runIde

# Launch IDE for UI test automation
./gradlew :plugin-checkmarx-ast:runIdeForUiTests
```

### Run Tests

```bash
# Unit tests (all modules)
./gradlew test

# Unit tests (specific module)
./gradlew :plugin-checkmarx-ast:test --tests "com.checkmarx.intellij.ast.test.unit*"
./gradlew :devassist-lib:test --tests "com.checkmarx.intellij.devassist.test*"

# Integration tests (requires CX_BASE_URI, CX_TENANT, CX_APIKEY env vars)
./gradlew :plugin-checkmarx-ast:test --tests "com.checkmarx.intellij.ast.test.integration.standard*"

# UI tests (requires runIdeForUiTests running separately)
./gradlew :plugin-checkmarx-ast:test -PuiWaitDuration=800 --tests "com.checkmarx.intellij.ast.test.ui*"

# Coverage report
./gradlew :plugin-checkmarx-ast:jacocoTestReport -PjacocoTask=unit

# Plugin compatibility verification
./gradlew :plugin-checkmarx-ast:runPluginVerifier
```

---

## Coding Standards

- **Java 11** language level — lambdas and streams are fine, but no records or newer constructs
- **Lombok:** Used for boilerplate reduction (`@Getter`, `@Setter`, `@Builder`, etc.). All modules apply the Lombok Gradle plugin.
- **Logging:** Use `Log4j` (2.23.1). Logger instances are typically created with `LogManager.getLogger()`.
- **UI thread safety:** All IntelliJ UI updates must happen on the EDT (Event Dispatch Thread). Use `ApplicationManager.getApplication().invokeLater()` for background-to-UI transitions.
- **Constants:** Add string literals used in UI or logic to appropriate constants classes. Never hardcode strings inline.
- **Null safety:** Check nullable returns from IntelliJ APIs before accessing. Use `@Nullable` / `@NotNull` annotations.
- **UTF-8 encoding:** Enforced for all JavaCompile tasks via Gradle configuration.

---

## Project Rules

- **All PRs target `main`** (or an integration branch when batching multiple fixes).
- **Branch naming:**
  - Bug fixes: `bug/AST-XXXXX` or `bug/<description>`
  - Features: `feature/AST-XXXXX` or `feature/<description>`
  - Documentation: `docs/AST-XXXXX`
  - Other: `other/AST-XXXXX` or `other/<description>`
- **Commit messages** should reference the Jira ticket when applicable: `Fix AST-XXXXX: <description>`
- **Never commit secrets.** Checkmarx credentials are injected via environment variables or IDE settings at runtime — never hardcoded.
- **Wrapper version** is managed in `gradle.properties` (`defaultJavaWrapperVersion`). Can be overridden with the `JAVA_WRAPPER_VERSION` environment variable.
- **PR size:** Keep PRs focused on a single ticket. Use an integration branch to batch multiple related fixes before merging to main.
- **Plugin IDs:**
  - AST: `com.checkmarx.checkmarx-ast-jetbrains-plugin`
  - DevAssist: `com.checkmarx.devassist-jetbrains-plugin`

---

## Testing Strategy

### Test Types

| Type | Location | Runner | Purpose |
|------|----------|--------|---------|
| Unit | `plugin-checkmarx-ast/src/test/.../test/unit/` | JUnit 5 | Test commands, components, settings, tool window actions in isolation |
| Unit (lib) | `devassist-lib/src/test/`, `common-lib/src/test/` | JUnit 5 | Test shared library logic |
| Integration | `plugin-checkmarx-ast/src/test/.../test/integration/standard/` | JUnit 5 | Test AST commands and server interactions against real tenant |
| UI | `plugin-checkmarx-ast/src/test/.../test/ui/` | Remote Robot + JUnit 5 | Test full plugin behavior inside a running IDE instance |

### CI Triggers

- **Unit tests:** Run on every PR via GitHub Actions (`ci.yml`)
- **Integration tests:** Run on PRs with Checkmarx One secrets (`CX_BASE_URI`, `CX_TENANT`, `CX_APIKEY`)
- **UI tests:** Run on PRs using Remote Robot with Xvfb (Linux), also runnable on macOS and Windows via manual dispatch
- **Plugin verifier:** Runs during release to check compatibility with IC-2023.1, IC-2023.2, IC-2023.3, IC-2024.1

### Coverage

- JaCoCo coverage reports generated per module (HTML, CSV, XML)
- Reports uploaded as GitHub Actions artifacts
- Coverage badge auto-generated via `cicirello/jacoco-badge-generator`
- Aggregated report in `unit-coverage-report/`

---

## External Integrations

| Integration | Purpose | How |
|-------------|---------|-----|
| **Checkmarx One Platform** | Fetch projects, branches, scans, results; submit triage | Via `ast-cli-java-wrapper` (wraps the Checkmarx CLI binary) |
| **MCP (Model Context Protocol)** | AI-powered agentic remediation | Auto-installed at startup via `McpInstallService` |
| **JetBrains Marketplace** | Plugin distribution and install | Published via `publishPlugin` Gradle task during release |

---

## Deployment

### Release Process

Releases are triggered via `.github/workflows/release.yml` (manual dispatch or called from nightly):

1. **Resolve:** Compute versions and tags from inputs
2. **Verify:** Run plugin verifier for IDE compatibility across multiple IDE versions
3. **Test Integration:** Optional integration test run (can be skipped)
4. **Delete Dev Releases:** Clean up previous development releases (dev release only)
5. **Build:** Both plugins are always built (both ZIPs included in every release)
6. **GitHub Release:** Create release with assets and changelog
7. **Publish:** Publish to JetBrains Marketplace (configurable: `checkmarx`, `devAssist`, or `both`)
8. **Notify:** Send release notification (production releases only)

### Versioning

- AST plugin: `2.x.x` (semantic versioning)
- DevAssist plugin: `1.x.x` (semantic versioning)
- Nightly builds: `2.0.{timestamp}` published to "nightly" channel

### Distribution

- **JetBrains Marketplace:** Published automatically on release
- **GitHub Releases:** ZIP artifacts attached to each release

### Install (End Users)

`Settings -> Plugins -> Marketplace -> Search "Checkmarx"`

---

## Telemetry

The plugin sends two types of telemetry events via `TelemetryService`:

### User Action Events (on click)

| Field | Values |
|-------|--------|
| `eventType` | `"click"` |
| `subType` | `"fixWithAIChat"`, `"viewDetails"`, `"ignorePackage"`, `"ignoreAll"` |
| `engine` | `"Oss"`, `"Secrets"`, `"IaC"`, `"Asca"`, `"Containers"` |
| `problemSeverity` | `"Critical"`, `"High"`, `"Medium"`, `"Low"`, `"Malicious"`, `"Unknown"` |
| `aiProvider` | `"Copilot"` |
| `agent` | `"Jetbrains <IDE name>"` |

### Scan Result Events (on scan completion)

| Field | Values |
|-------|--------|
| `scanType` | `"Oss"`, `"Secrets"`, `"IaC"`, `"Asca"`, `"Containers"` |
| `status` | `"Critical"`, `"High"`, `"Medium"`, `"Low"`, `"Malicious"`, `"Unknown"` |
| `totalCount` | Integer count of issues at that severity |

All telemetry calls are asynchronous via `CompletableFuture` and use `CxWrapperFactory.build().telemetryAIEvent()`.

---

## Security & Access

- **API Key authentication:** Users configure a Checkmarx One API key in `Settings -> Tools -> Checkmarx`. The key is stored via IntelliJ's `GlobalSettingsSensitiveState` (encrypted).
- **No credentials in code:** All secrets are injected at runtime via IDE settings or environment variables (CI). Never commit API keys or tokens.
- **TLS:** All communication with Checkmarx One is HTTPS, enforced by the CLI wrapper.
- **MCP auto-install:** MCP configuration is installed automatically on IDE startup if the user is already authenticated.

---

## Debugging Steps

### Plugin not loading

1. Check `Help -> Show Log in Explorer/Finder` for activation errors
2. Verify Java 11+ is set as the project SDK
3. Confirm the plugin is enabled: `Settings -> Plugins -> Installed`

### Authentication failures

1. Verify API key in `Settings -> Tools -> Checkmarx` — click **Test Connection**
2. Check IDE logs for authentication-related errors
3. Confirm the API key has appropriate roles on the Checkmarx One tenant

### No results / empty tree

1. Confirm project, branch, and scan ID are selected in the Checkmarx tool window
2. Check filter state — severity filters may be disabled (toolbar toggle buttons)
3. Check IDE logs for errors from result-fetching commands

### DevAssist not scanning

1. Verify authentication is configured and connected
2. Check if DevAssist inspections are enabled: `Settings -> Editor -> Inspections -> CxOneAssist`
3. Verify scanner configuration in assist settings page
4. Check IDE logs for scanner-related errors

### Running UI tests locally

```bash
# Start IDE for UI tests (keep running)
./gradlew :plugin-checkmarx-ast:runIdeForUiTests &

# Run UI tests
./gradlew :plugin-checkmarx-ast:test -PuiWaitDuration=800 --tests "com.checkmarx.intellij.ast.test.ui*"
```

---

## CI/CD Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | PR, manual | Unit, integration, UI tests with coverage reports |
| `release.yml` | Manual, workflow_call | Build, verify, release, publish both plugins |
| `nightly.yml` | Push to main, manual | Nightly builds with timestamp versions |
| `test-ui-ubuntu.yml` | Manual | Platform-specific UI tests (Ubuntu) |
| `test-ui-mac.yml` | Manual | Platform-specific UI tests (macOS) |
| `test-ui-windows.yml` | Manual | Platform-specific UI tests (Windows) |
| `checkmarx-one-scan.yml` | Daily schedule | Security scanning of the plugin codebase |
| `update-wrapper-version.yml` | Manual | Auto-update CLI wrapper version |
| `dependabot-auto-merge.yml` | Dependabot PR | Auto-merge dependency updates |

---

*Generated for AST-146800 - Checkmarx Integrations Team*

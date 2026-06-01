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
├── common-lib/              # Auth, settings, icons, CLI wrapper integration (CxWrapperFactory)
├── devassist-lib/           # Real-time scanners, inspections, remediation, telemetry, MCP
├── plugin-checkmarx-ast/    # Full Checkmarx plugin; src/test/java/ holds all test types
├── plugin-checkmarx-devassist/  # Standalone DevAssist plugin
├── unit-coverage-report/    # Aggregated JaCoCo coverage
├── .github/workflows/       # CI/CD pipelines
├── gradle.properties        # Centralized version management (defaultJavaWrapperVersion)
└── settings.gradle          # Module definitions
```

Key package roots under `src/main/java/`:
- `com.checkmarx.intellij.common` — shared auth, settings, wrapper, UI utilities
- `com.checkmarx.intellij.devassist` — scanners, inspection, ignore, remediation, telemetry
- `com.checkmarx.intellij.ast` — scan/project/results/triage commands, tool window, UI
- `com.checkmarx.intellij.cxdevassist` — DevAssist settings, tool window, utilities

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
- **UI thread safety:** All IntelliJ UI updates must happen on the EDT (Event Dispatch Thread). Use `ApplicationManager.getApplication().invokeLater()` for background-to-UI transitions.
- **Constants:** Add string literals used in UI or logic to appropriate constants classes. Never hardcode strings inline.
- **Null safety:** Check nullable returns from IntelliJ APIs before accessing. Use `@Nullable` / `@NotNull` annotations.
- **UTF-8 encoding:** Enforced for all JavaCompile tasks via Gradle configuration.

---

## Logging

Plugin source code uses the IntelliJ Platform's native logger (`com.intellij.openapi.diagnostic.Logger`), accessed through the shared utility method:

```java
private static final Logger LOGGER = Utils.getLogger(MyClass.class);
```

This routes all output to the IDE log file (`idea.log`), viewable at `Help -> Show Log in Explorer/Finder` (or `Help -> Show Log in Files` on Linux).

`ast-cli-java-wrapper` uses SLF4J (`org.slf4j.Logger` via `LoggerFactory.getLogger(...)`). When the wrapper runs inside the IDE process, its SLF4J output is bridged to `idea.log` by the IDE's logging framework.

**Log levels:**

| Level | When to use |
|-------|-------------|
| `LOGGER.info(...)` | Normal operation milestones — scan start/end, authentication events, settings changes |
| `LOGGER.warn(...)` | Recoverable issues — unsupported file type, missing PSI element, skipped scan |
| `LOGGER.error(...)` | Unrecoverable errors — unexpected exceptions that affect plugin functionality |
| `LOGGER.debug(...)` | Verbose diagnostics — use sparingly; off by default in production IDE builds |

**Conventions:**
- Real-time scanner log messages are prefixed with `"RTS: "` for easy filtering in log output.
- Never log sensitive values (API keys, refresh tokens, tenant credentials).
- Always pass the exception as the second argument to `warn`/`error` so the full stack trace is captured.
- Use parameterized SLF4J-style formatting (`{}`) in wrapper code; use `String.format()` in plugin code via IntelliJ's `Logger`.

---

## Performance Considerations

Plugin code runs inside the shared IDE process. Blocking the Event Dispatch Thread (EDT) or launching unthrottled background work degrades IDE responsiveness for all open projects.

**Event Dispatch Thread (EDT)**
- **Never block the EDT.** CLI wrapper calls (`CxWrapper.*`) are synchronous and can take several seconds. Always execute them on a background thread.
- Use `ApplicationManager.getApplication().executeOnPooledThread(...)` or `ProgressManager.getInstance().run(new Task.Backgroundable(...))` for any CLI invocation or I/O.
- Marshal UI updates back to the EDT with `ApplicationManager.getApplication().invokeLater(...)`.

**Real-time inspections (DevAssist)**
- `DevAssistInspection.checkFile()` is invoked by the IntelliJ inspection framework on every file edit. Keep this method fast — do not make synchronous CLI calls on the calling thread.
- Actual scanning is delegated to `DevAssistScanScheduler`, which debounces requests so that rapid successive edits result in a single scan rather than many.
- Use composite timestamp caching (PSI stamp ⊕ document stamp ⊕ VFS timestamp) to skip re-scanning unchanged files — this pattern is already established in `DevAssistInspection`.
- If adding a new scanner, plug into the existing `DevAssistScanScheduler` rather than creating a parallel scheduling path.

**CLI wrapper calls**
- Each `CxWrapper` method call spawns a subprocess (the Checkmarx CLI binary). Subprocess startup has non-trivial overhead — avoid calling the CLI in tight loops or on every keypress.
- Realtime scans write intermediate results to the system temp directory. Clean up temp files after use (see `BaseScannerService.deleteTempFolder()`).
- Wrapper calls may involve network I/O to Checkmarx One. Handle `InterruptedException` and `IOException` gracefully and always provide timeout protection.

**Telemetry**
- All `TelemetryService` calls are already executed asynchronously via `CompletableFuture`. Preserve this pattern — do not convert telemetry calls to synchronous invocations.

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

## API / Endpoints / Interfaces

The plugin has **no direct REST communication** with the Checkmarx One platform. All platform interactions — authentication, project listing, scan management, results retrieval, triage, real-time scanning, and telemetry — are routed exclusively through `ast-cli-java-wrapper`.

**Communication boundary:**
```
Plugin code  →  CxWrapperFactory.build()  →  CxWrapper  →  Checkmarx CLI binary  →  Checkmarx One Platform
```

**Entry point:** `CxWrapperFactory.build()` in `common-lib/wrapper/CxWrapperFactory.java` constructs a fully configured `CxWrapper` instance from `GlobalSettingsState` and `GlobalSettingsSensitiveState`. This is the only sanctioned way to obtain a wrapper instance from plugin code.

**Primary interface — `CxWrapper` methods (grouped by domain):**

| Domain | Methods |
|--------|---------|
| Authentication | `authValidate()` |
| Scans | `scanCreate()`, `scanList()`, `scanShow()`, `scanCancel()` |
| Results | `results()`, `resultsSummary()` |
| Projects | `projectList()`, `projectShow()`, `projectBranches()` |
| Triage | `triageShow()`, `triageUpdate()`, `triageScaShow()`, `triageScaUpdate()` |
| Real-time scanning | `ScanAsca()`, `ossRealtimeScan()`, `iacRealtimeScan()`, `secretsRealtimeScan()`, `containersRealtimeScan()`, `kicsRealtimeScan()` |
| Tenant / Feature flags | `tenantSettings()`, `ideScansEnabled()`, `devAssistEnabled()`, `oneAssistEnabled()` |
| Utilities | `codeBashingList()`, `learnMore()`, `telemetryAIEvent()` |

**Configuration — `CxConfig`:**
- Built by `CxWrapperFactory` from IDE-stored settings (`GlobalSettingsState` / `GlobalSettingsSensitiveState`).
- Key fields: `apiKey`, `clientId`, `clientSecret`, `baseUri`, `tenant`, `baseAuthUri`, `agentName`, `pathToExecutable`.
- Credentials are passed as CLI arguments at runtime — never embedded in source code.

**`CxThinWrapper`:**
A lower-level wrapper that executes raw CLI commands. Used by MCP-related services for operations that do not map to typed `CxWrapper` methods.

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

**Versioning:** AST `2.x.x`, DevAssist `1.x.x` (semantic). Nightly builds use `2.0.{timestamp}` on the "nightly" channel.

**Distribution:** Published to JetBrains Marketplace automatically on release; ZIP artifacts attached to each GitHub Release. End users install via `Settings -> Plugins -> Marketplace -> Search "Checkmarx"`.

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

## Do Not (AI Assistant Rules)

The following rules apply specifically when an AI assistant is making changes to this codebase. They protect plugin integrity, marketplace compatibility, and user security.

**Architecture & platform communication**
- **Do not bypass `ast-cli-java-wrapper`.** Never introduce direct HTTP/REST calls to Checkmarx One API endpoints. All platform communication must go through `CxWrapper` methods or `CxThinWrapper.run()`.
- **Do not add new HTTP client usages** (e.g., `OkHttp`, `HttpURLConnection`, `java.net.http.HttpClient`) for communicating with Checkmarx One or any external Checkmarx service.
- **Do not call Checkmarx One REST endpoints directly**, even if the endpoint URL and schema can be inferred from CLI wrapper source code or network traffic.
- **Do not instantiate `CxWrapper` directly** from plugin code — always go through `CxWrapperFactory.build()` so settings and credentials are resolved correctly.

**Plugin identity**
- **Do not modify plugin IDs.** The IDs `com.checkmarx.checkmarx-ast-jetbrains-plugin` and `com.checkmarx.devassist-jetbrains-plugin` are registered on the JetBrains Marketplace and in customer installations. Changing them is a breaking, irreversible change.
- **Do not modify `<id>`, `<version>`, or `<vendor>` tags in `plugin.xml`** unless explicitly instructed with a Jira ticket reference.

**Credentials & secrets**
- **Do not hardcode API keys, tokens, or tenant URLs** anywhere in source or test code. Use environment variables or IDE settings injection.
- **Do not log sensitive values.** API keys, refresh tokens, and user credentials must never appear in log output, even at `DEBUG` level.

**Thread safety & IDE conventions**
- **Do not perform synchronous CLI calls on the EDT.** All `CxWrapper` invocations must run on background threads (see Performance Considerations).
- **Do not update Swing/IntelliJ UI components from background threads** without marshalling to the EDT via `invokeLater`.
- **Do not disable or remove the debounce/throttle logic** in `DevAssistScanScheduler` — it protects IDE performance during real-time scanning.

**Build & CI**
- **Do not modify CI workflow files** (`.github/workflows/`) without explicit instruction — changes there can break automated releases and JetBrains Marketplace publishing.
- **Do not change `defaultJavaWrapperVersion` in `gradle.properties`** without confirming the target wrapper version is published and compatible.
- **Do not use `--no-verify`** or skip pre-commit hooks to work around build failures — investigate and fix the root cause instead.


# Fix: Open New Chat Session on Each "Fix With" Click

## Problem

Every time the user clicked "Fix with Checkmarx Developer Assist", the remediation prompt was pasted into the **same existing** Copilot Chat session. This caused previous conversation context to pollute new fix requests.

In the VS Code extension, each click opens a **new chat session** (see `openAIChatCommand.ts` — `"Always start a new conversation so previous context is not reused"`). The JetBrains plugin did not have this behavior.

## Root Cause

`CopilotIntegration.java` only called `toolWindow.show()` / `toolWindow.activate()` to bring the existing Copilot window to focus. It never requested a new conversation before pasting the prompt.

## Approach

Added a **"Phase 0"** step in the automation flow that always attempts to start a new chat session before switching to Agent mode and pasting the prompt. This uses a multi-strategy fallback approach to handle different Copilot plugin versions and UI layouts.

### Why Multiple Strategies?

GitHub Copilot for JetBrains does not expose a public API. The internal action IDs and UI components can change across versions. A layered fallback approach ensures reliability:

1. **Action-based** strategies are the most reliable (no UI timing issues)
2. **UI-based** strategies handle cases where Copilot doesn't register discoverable actions

### Initial Attempt (Failed)

The first implementation only searched inside `getContentManager().getContents()` (content panels) for `AbstractButton` components. This failed because:

- The "new chat" button is in the **tool window toolbar**, not inside content panels
- IntelliJ toolbar buttons are `ActionButton` (extends `JComponent`), **not** `AbstractButton`

### Final Solution — 4-Strategy Fallback

All changes are in a single file: `CopilotIntegration.java`

---

## Changes Made

### New Constants

| Name | Purpose |
|------|---------|
| `COPILOT_NEW_CHAT_ACTION_IDS` | Array of known Copilot action IDs for starting a new conversation |
| `COPILOT_ACTION_PREFIX` / `GITHUB_COPILOT_ACTION_PREFIX` | Prefixes used to identify Copilot actions when scanning the ActionManager |

### New Methods

#### Strategy Orchestration

| Method | Purpose |
|--------|---------|
| `tryStartNewChatSession(project)` | Orchestrates all 4 strategies in order, returns true on first success |

#### Strategy 1 — Known Action IDs

| Method | Purpose |
|--------|---------|
| `tryInvokeNewChatAction(project)` | Iterates through `COPILOT_NEW_CHAT_ACTION_IDS` and invokes the first one found in ActionManager |

#### Strategy 2 — Action Discovery

| Method | Purpose |
|--------|---------|
| `tryDiscoverAndInvokeNewChatAction(project)` | Scans ALL registered actions in ActionManager for any copilot-related action containing "new" + "chat"/"conversation"/"thread". Logs all available Copilot actions for debugging if none match |

#### Strategy 3 & 4 — UI Button Search

| Method | Purpose |
|--------|---------|
| `tryClickNewChatInToolWindow(toolWindow)` | Searches the full tool window component tree via `toolWindow.getComponent()` (includes toolbars), then falls back to content panels |
| `findNewChatComponentRecursively(component)` | Recursively traverses the Swing component hierarchy, checking both `ActionButton` (IntelliJ toolbar) and `AbstractButton` (standard Swing) for new-chat indicators |
| `getActionButtonText(component)` | Extracts presentation text from an IntelliJ `ActionButton` via reflection (`getAction()` -> `getTemplatePresentation()` -> `getText()`) |
| `clickComponent(component)` | Clicks a component handling both `AbstractButton.doClick()` and `ActionButton.click()` via reflection, with mouse event dispatch as last resort |

#### Helpers

| Method | Purpose |
|--------|---------|
| `isNewChatText(lowerText)` | Returns true if text contains "new" combined with "chat", "conversation", "session", or "thread" |
| `findMethod(clazz, name)` | Reflection helper to find a public no-arg method by name |
| `invokeAction(action, project, place)` | Reusable helper to invoke an `AnAction` on the EDT with project context |

### Modified Methods

| Method | Change |
|--------|--------|
| `tryComponentBasedAutomation(project, prompt)` | Added "Phase 0" at the start that calls `tryStartNewChatSession()` before proceeding to Agent mode switch and prompt entry |

## Debugging

If issues occur, check the IDE log (**Help -> Show Log in Explorer**) and search for `CxFix:`. The debug lines show:

- Which strategies were attempted and their results
- All available Copilot actions discovered in ActionManager
- Component hierarchy details when UI search is performed

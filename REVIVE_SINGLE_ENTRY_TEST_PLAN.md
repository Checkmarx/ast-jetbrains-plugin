# Test Plan: reviveSingleEntry Method

## Overview
This test plan covers the corrected `reviveSingleEntry` method that now properly handles user interaction with an "Undo" option.

## Test Scenarios

### 1. Basic Revive Flow (Happy Path)

**Preconditions:**
- At least one vulnerability is ignored in the `.checkmarxIgnored` file
- The ignored vulnerability has active file references

**Steps:**
1. Open the Ignored Findings window
2. Click the "Revive" button on an ignored entry
3. Observe the notification that appears

**Expected Results:**
- ✅ Notification appears with message: "'[package-name]' vulnerability has been revived in X file(s)"
- ✅ Notification has an "Undo" button
- ✅ The entry is immediately revived (files set to inactive in `.checkmarxIgnored`)
- ✅ Rescan is triggered for affected files
- ✅ Vulnerabilities reappear in the findings

---

### 2. Undo Revive Action

**Preconditions:**
- Same as Test 1

**Steps:**
1. Click "Revive" on an ignored entry
2. Immediately click "Undo" on the notification

**Expected Results:**
- ✅ All file references are restored to active (ignored) state
- ✅ `.checkmarxIgnored` file is updated with active=true
- ✅ Rescan is triggered
- ✅ Vulnerabilities are hidden again (ignored state restored)
- ✅ Notification disappears
- ✅ Log shows: "RTS-Ignore: Successfully undone revive for entry: [package-name]"

---

### 3. Dismiss Notification (No Undo)

**Preconditions:**
- Same as Test 1

**Steps:**
1. Click "Revive" on an ignored entry
2. Dismiss the notification (click X or wait for auto-dismiss)

**Expected Results:**
- ✅ Revive stays in effect
- ✅ Files remain inactive in `.checkmarxIgnored`
- ✅ Vulnerabilities remain visible in findings
- ✅ No undo is performed

---

### 4. Revive with Multiple Files

**Preconditions:**
- An ignored vulnerability exists in multiple files (e.g., OSS package used in 3 files)

**Steps:**
1. Click "Revive" on the entry

**Expected Results:**
- ✅ Notification shows correct file count: "revived in 3 files"
- ✅ All file references are set to inactive
- ✅ Rescan is triggered for all affected files
- ✅ If "Undo" is clicked, all files are restored to active

---

### 5. Revive Different Scanner Types

Test each scanner type separately:

#### 5a. OSS Package
**Entry Key Format:** `packageManager:packageName:packageVersion`
- ✅ Revive works correctly
- ✅ Undo restores ignored state
- ✅ Notification shows package name and version

#### 5b. Container Image
**Entry Key Format:** `imageName:imageTag`
- ✅ Revive works correctly
- ✅ Undo restores ignored state
- ✅ Notification shows image name and tag

#### 5c. Secret
**Entry Key Format:** `title:secretValue:filePath`
- ✅ Revive works correctly
- ✅ Undo restores ignored state
- ✅ Notification shows secret title

#### 5d. IaC Finding
**Entry Key Format:** `title:similarityId:filePath`
- ✅ Revive works correctly
- ✅ Undo restores ignored state
- ✅ Notification shows finding title

#### 5e. ASCA Rule
**Entry Key Format:** `title:ruleId:filePath`
- ✅ Revive works correctly
- ✅ Undo restores ignored state
- ✅ Notification shows rule name

---

### 6. Error Handling: Entry Not Found

**Preconditions:**
- Manually edit `.checkmarxIgnored` to create an inconsistent state

**Steps:**
1. Attempt to revive an entry that doesn't exist in the ignore file

**Expected Results:**
- ✅ Error notification appears with message from `Resource.REVIVE_FAILED`
- ✅ Log shows: "RTS-Ignore: Failed to revive entry: [package-name]"
- ✅ No crash or exception

---

### 7. Error Handling: Undo Key Not Found

**Preconditions:**
- Create a scenario where the entry key cannot be generated (edge case)

**Steps:**
1. Revive an entry
2. Click "Undo"

**Expected Results:**
- ✅ Log shows: "RTS-Ignore: Failed to find entry key for undo: [package-name]"
- ✅ Notification still expires
- ✅ No crash or exception

---

### 8. Concurrent Operations

**Steps:**
1. Revive entry A
2. Before clicking Undo, revive entry B
3. Click Undo on entry A's notification

**Expected Results:**
- ✅ Entry A is restored to ignored state
- ✅ Entry B remains revived
- ✅ No interference between operations

---

### 9. File System Verification

**Steps:**
1. Revive an entry
2. Check `.checkmarxIgnored` file
3. Click Undo
4. Check `.checkmarxIgnored` file again

**Expected Results:**
- ✅ After revive: All file references have `"active": false`
- ✅ After undo: All file references have `"active": true`
- ✅ `.checkmarxIgnoredTempList.json` is updated correctly in both cases

---

### 10. UI Refresh

**Steps:**
1. Have Ignored Findings window open
2. Revive an entry
3. Click Undo

**Expected Results:**
- ✅ After revive: Entry disappears from Ignored Findings (or shows as inactive)
- ✅ After undo: Entry reappears in Ignored Findings
- ✅ UI updates automatically without manual refresh

---

## Regression Tests

### Compare with VS Code Extension
- ✅ Behavior matches VS Code extension's `revivePackage` method
- ✅ User experience is consistent across platforms
- ✅ Notification timing and options are similar

---

## Performance Tests

### Large Ignore File
**Preconditions:**
- `.checkmarxIgnored` file with 100+ entries

**Steps:**
1. Revive an entry
2. Click Undo

**Expected Results:**
- ✅ Operations complete in < 1 second
- ✅ No UI freezing
- ✅ No performance degradation

---

## Logging Verification

Check logs for proper debug/info/warn messages:
- ✅ "RTS-Ignore: Reviving entry: [package-name]"
- ✅ "RTS-Ignore: Successfully revived entry: [package-name]"
- ✅ "RTS-Ignore: Undoing revive for entry: [package-name]"
- ✅ "RTS-Ignore: Successfully undone revive for entry: [package-name]"
- ✅ "RTS-Ignore: Failed to revive entry: [package-name]" (on error)


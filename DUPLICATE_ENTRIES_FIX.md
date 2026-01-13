# Fix for Duplicate Entries in CxFindingsWindow During Revive Flow

## Problem Description

When reviving an ignored vulnerability using the revive flow, duplicate entries were appearing in the `CxFindingsWindow`. This issue did NOT occur when using the normal `scanFileAndUpdateResults` method during the ignore flow.

## Root Cause Analysis

### The Issue

Both flows (`ignore` and `revive`) call `scanFileAndUpdateResults` with the exact engine and file, but the revive flow was creating duplicates. The problem was in the **`cacheScanResults`** method in `CxOneAssistScanScheduler.java`.

### The Problematic Code Pattern

```java
// OLD CODE - Had a race condition
private void cacheScanResults(..., ScanEngine scanEngine) {
    if (scanEngine == ScanEngine.ALL) {
        holderService.addScanIssues(filePath, scanIssues);
        holderService.addProblemDescriptors(filePath, problems);
    } else {
        // PROBLEM: These two operations are NOT atomic
        holderService.removeScanIssuesByFileAndScanner(scanEngine.name(), filePath);
        holderService.mergeScanIssues(filePath, scanIssues);
        
        holderService.removeProblemDescriptorsForFileByScanner(filePath, scanEngine);
        holderService.mergeProblemDescriptors(filePath, problems);
    }
}
```

### Why Duplicates Occurred

1. **Non-Atomic Operations**: The `remove` and `merge` operations were separate, creating a window for race conditions
2. **Asynchronous Execution**: `cacheScanResults` is called inside `ApplicationManager.getApplication().invokeLater()`, making it asynchronous
3. **Timing Issue**: If the scan results were updated before the removal completed, duplicates would appear

### Flow Comparison

**Normal Ignore Flow** (No Duplicates):
```
scanFileAndUpdateResults(issue) 
  → scheduleScan(filePath, problemHelper, issue.getScanEngine())
  → runScan() 
  → cacheScanResults() with specific scanEngine
  → Works correctly (most of the time)
```

**Revive Flow** (Duplicates):
```
reviveSingleEntry(entry)
  → triggerRescanForEntry(entry)
  → triggerRescanForFile(vFile, filePath, entry.getType())
  → scheduleScan(filePath, problemHelper, scanEngine)
  → runScan()
  → cacheScanResults() with specific scanEngine
  → Race condition more likely due to timing
```

## Solution

### Created Atomic Methods

Added two new atomic methods to `ProblemHolderService.java`:

1. **`replaceScanIssuesForScanner()`** - Atomically removes and adds scan issues
2. **`replaceProblemDescriptorsForScanner()`** - Atomically removes and adds problem descriptors

### Implementation

```java
// NEW METHOD in ProblemHolderService.java
public void replaceScanIssuesForScanner(String scannerType, String filePath, List<ScanIssue> newIssues) {
    fileToIssues.compute(filePath, (key, existingIssues) -> {
        List<ScanIssue> updatedList = (Objects.isNull(existingIssues) || existingIssues.isEmpty())
                ? new ArrayList<>()
                : new ArrayList<>(existingIssues);
        
        // Remove all issues for this scanner type
        updatedList.removeIf(scanIssue -> scannerType.equalsIgnoreCase(scanIssue.getScanEngine().name()));
        
        // Add new issues
        if (Objects.nonNull(newIssues) && !newIssues.isEmpty()) {
            updatedList.addAll(newIssues);
        }
        
        return updatedList;
    });
    syncWithCxOneFindings();
}
```

### Updated CxOneAssistScanScheduler

```java
// FIXED CODE - Atomic operations
private void cacheScanResults(..., ScanEngine scanEngine) {
    if (scanEngine == ScanEngine.ALL) {
        holderService.addScanIssues(filePath, scanIssues);
        holderService.addProblemDescriptors(filePath, problems);
    } else {
        // Use atomic replace operations to prevent duplicate entries
        holderService.replaceScanIssuesForScanner(scanEngine.name(), filePath, scanIssues);
        holderService.replaceProblemDescriptorsForScanner(scanEngine, filePath, problems);
    }
}
```

## Benefits

1. **Thread-Safe**: The `compute()` method ensures atomic operations
2. **No Race Conditions**: Remove and add happen in a single operation
3. **Consistent Behavior**: Both ignore and revive flows now work identically
4. **No Duplicates**: Prevents duplicate entries in CxFindingsWindow

## Files Modified

1. `src/main/java/com/checkmarx/intellij/devassist/problems/ProblemHolderService.java`
   - Added `replaceScanIssuesForScanner()` method
   - Added `replaceProblemDescriptorsForScanner()` method

2. `src/main/java/com/checkmarx/intellij/devassist/inspection/CxOneAssistScanScheduler.java`
   - Updated `cacheScanResults()` to use atomic methods

## Testing Recommendations

1. Test reviving a single OSS vulnerability
2. Test reviving multiple vulnerabilities
3. Test reviving vulnerabilities for different scanner types (OSS, ASCA, IAC, Containers, Secrets)
4. Verify no duplicates appear in CxFindingsWindow
5. Verify the normal ignore flow still works correctly


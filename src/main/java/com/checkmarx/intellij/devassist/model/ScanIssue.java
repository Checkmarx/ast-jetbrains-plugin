package com.checkmarx.intellij.devassist.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Common Model class for an issue found during a scan.
 * This class is used to represent both single- and multi-vulnerability issues.
 * @apiNote This class is not intended to be instantiated directly. This should be built from the respective scanner adapter classes.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanIssue {

    private String severity;      // e.g. "Critical", "High", "Medium", etc.
    private String title;         // Rule name or Package name
    private String description;   // Human-readable description of the issue
    private String remediationAdvise; // Fix suggestion, if available
    private String packageVersion; // May be null for rule-based issues.
    private String cve;            // If a single CVE (or null, if not applicable)
    private String scanEngine; // engine name, e.g., OSS, SECRET etc.
    private String filePath;

    // One- or multiple-vulnerable code ranges for this issue.
    private List<Location> locations = new ArrayList<>();

    /**
     * One- or multiple-vulnerabilities for this issue.
     * E.g., If the scan issue is a package, then the package contains vulnerability details.
     */
    private List<Vulnerability> vulnerabilities = new ArrayList<>();
}

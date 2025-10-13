package com.checkmarx.intellij.realtimeScanners.dto;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class CxProblems {

    private int line;
    private int column;
    private String severity;      // e.g. "Critical", "High", "Medium", etc.
    private String title;         // Rule name or Package name
    private String description;   // Human-readable description of the issue
    private String remediationAdvise; // Fix suggestion, if available
    private String packageVersion; // May be null for rule-based issues.
    private String cve;            // If a single CVE (or null, if not applicable)
    private String scannerType;


}

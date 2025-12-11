package com.checkmarx.intellij.devassist.model;

import com.checkmarx.intellij.devassist.utils.ScanEngine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a scan issue detected during a real-time scan engine.
 * This class captures detailed information about issues identified in a scanned project, file, or package.
 * <p>
 * Each scan issue can potentially have multiple locations and vulnerabilities linked to it
 * for providing comprehensive details about the issue's scope.
 * <p>
 * Attributes:
 * - severity: Severity level of the issue, such as "Critical", "High", "Medium", etc.
 * - title: Rule name or package name associated with the issue.
 * - description: Detailed explanation or context describing the issue.
 * - remediationAdvise: Suggestion or advice for addressing the issue, if available.
 * - packageVersion: Version of the package linked to the issue (may be null for rule-based issues).
 * - cve: Associated CVE identifier (if any) or null if not applicable.
 * - scanEngine: The name of the scanning engine responsible for detecting the issue (e.g., OSS, SECRET).
 * - filePath: The path to the file in which the issue is detected.
 * - locations: A list of location details highlighting vulnerable code ranges or other points of concern.
 * - vulnerabilities: A list of associated vulnerabilities providing additional insights into the issue.
 *
 * @apiNote This class is not intended to be instantiated directly. This should be built from the respective scanner adapter classes.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScanIssue {

    private String severity;
    private String title;
    private String description;
    private String remediationAdvise;
    private String packageVersion;
    private String packageManager;
    private String cve;
    private ScanEngine scanEngine;
    private String filePath;
    private String imageTag;
    private String fileType;
    private Integer problematicLineNumber;


    /**
     * A list of specific locations within the file that are related to the scan issue.
     * Each location typically identifies a vulnerable code segment or point of concern,
     * including its line number and character range within the line.
     */
    private List<Location> locations = new ArrayList<>();

    /**
     * A list of associated vulnerabilities providing additional insights into the scan issue.
     * Each vulnerability represents a specific security risk or flaw detected during the scan,
     * including attributes such as the CVE identifier, description, severity level, etc.
     * <p>
     * Vulnerabilities are linked to the scan issue to provide context and help users understand
     * the potential impact and required actions to address the identified risks.
     */
    private List<Vulnerability> vulnerabilities = new ArrayList<>();
}

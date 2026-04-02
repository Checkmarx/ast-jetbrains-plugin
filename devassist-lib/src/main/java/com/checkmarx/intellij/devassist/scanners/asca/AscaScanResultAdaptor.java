package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling ASCA scan results and converting them into a standardized format
 * using the {@link com.checkmarx.intellij.devassist.common.ScanResult} interface.
 * This class wraps a {@link ScanResult} instance and provides methods to process and extract
 * meaningful scan issues based on ASCA findings detected in the files.
 */
public class AscaScanResultAdaptor implements com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> {
    private static final Logger LOGGER = Utils.getLogger(AscaScanResultAdaptor.class);
    private final ScanResult ascaScanResult;
    private final String filePath;
    private final List<ScanIssue> scanIssues;
    private final Project project;


    /**
     * Constructs an instance of {@code AscaScanResultAdaptor} with the specified ASCA scan results.
     * This adapter allows conversion and processing of ASCA scan results into a standardized format.
     * Filtering of ignored vulnerabilities occurs during the scanning process.
     *
     * @param ascaScanResult the ASCA scan results to be wrapped by this adapter
     * @param filePath       the path of the file being scanned (needed for UI display)
     * @param project        the IntelliJ project instance (used for accessing ignore manager)
     */
    public AscaScanResultAdaptor(ScanResult ascaScanResult, String filePath, Project project) {
        this(ascaScanResult, filePath, project, true);
    }

    /**
     * Constructs an instance of {@code AscaScanResultAdaptor} with optional filtering control.
     *
     * @param ascaScanResult the ASCA scan results to be wrapped by this adapter
     * @param filePath       the path of the file being scanned (needed for UI display)
     * @param project        the IntelliJ project instance (used for accessing ignore manager)
     * @param filterIgnored   whether to filter out ignored vulnerabilities (true = filter, false = keep all)
     */
    public AscaScanResultAdaptor(ScanResult ascaScanResult, String filePath, Project project, boolean filterIgnored) {
        this.ascaScanResult = ascaScanResult;
        this.filePath = filePath;
        this.project = project;
        this.scanIssues = filterIgnored ? buildIssues() : buildIssuesUnfiltered();
    }

    /**
     * Retrieves the ASCA scan results wrapped by this adapter.
     *
     * @return the ASCA scan results instance containing the results of the ASCA scan
     */
    @Override
    public ScanResult getResults() {
        return ascaScanResult;
    }

    /**
     * Retrieves a list of scan issues discovered in the ASCA scan.
     * This method processes the scan details obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no scan details are found, an empty list is returned.
     *
     * @return a list of {@link ScanIssue} objects representing the ASCA findings, or an empty list if none are found
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Builds a list of ScanIssue objects from the ASCA scan results.
     * Groups multiple vulnerabilities on the same line and sorts them by severity.
     *
     * @return a list of ScanIssue objects
     */
    private List<ScanIssue> buildIssues() {
        if (ascaScanResult == null || ascaScanResult.getScanDetails() == null) {
            LOGGER.debug("ASCA adaptor: No scan results or scan details available");
            return Collections.emptyList();
        }

        List<ScanDetail> scanDetails = ascaScanResult.getScanDetails();
        if (scanDetails.isEmpty()) {
            return Collections.emptyList();
        }
        // Group scan details by line number, then sort by severity precedence
        Map<Integer, List<ScanDetail>> groupedIssues = scanDetails.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ScanDetail::getLine,
                        Collectors.collectingAndThen(Collectors.toList(), detailsList -> {
                            detailsList.sort(Comparator.comparingInt(detail ->
                                    SeverityLevel.fromValue(mapSeverity(detail.getSeverity())).getPrecedence()));
                            return detailsList;
                        })
                ));

        List<ScanIssue> issues = groupedIssues.values().stream()
                .map(this::createScanIssueForGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.debug("ASCA adaptor: Converted " + issues.size() + " grouped scan issues for file: " + filePath);
        return issues;
    }

    /**
     * Builds a list of ScanIssue objects from the ASCA scan results WITHOUT filtering ignored vulnerabilities.
     * Used for updating line numbers and ignore entries, where all vulnerabilities (including already-ignored ones)
     * are needed to properly track which vulnerabilities are still present in the code.
     *
     * @return a list of ScanIssue objects with all vulnerabilities unfiltered
     */
    private List<ScanIssue> buildIssuesUnfiltered() {
        if (ascaScanResult == null || ascaScanResult.getScanDetails() == null) {
            LOGGER.debug("ASCA adaptor (unfiltered): No scan results or scan details available");
            return Collections.emptyList();
        }

        List<ScanDetail> scanDetails = ascaScanResult.getScanDetails();
        if (scanDetails.isEmpty()) {
            return Collections.emptyList();
        }
        // Group scan details by line number, then sort by severity precedence
        Map<Integer, List<ScanDetail>> groupedIssues = scanDetails.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ScanDetail::getLine,
                        Collectors.collectingAndThen(Collectors.toList(), detailsList -> {
                            detailsList.sort(Comparator.comparingInt(detail ->
                                    SeverityLevel.fromValue(mapSeverity(detail.getSeverity())).getPrecedence()));
                            return detailsList;
                        })
                ));

        List<ScanIssue> issues = groupedIssues.values().stream()
                .map(this::createScanIssueForGroupUnfiltered)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.debug("ASCA adaptor (unfiltered): Converted " + issues.size() + " grouped scan issues for file: " + filePath);
        return issues;
    }

    /**
     * Creates a ScanIssue from a group of ASCA scan details that are on the same line.
     * Filters out ignored vulnerabilities during creation when filterIgnored=true:
     * - For single vulnerability: skip entirely if ignored
     * - For multiple vulnerabilities: add only non-ignored ones
     *
     * @param ascaScanDetails the list of ASCA scan details for the same line (already sorted by severity)
     * @return a {@link ScanIssue} representing the ASCA finding(s), or {@code null} if all ignored or conversion fails
     */
    private ScanIssue createScanIssueForGroup(List<ScanDetail> ascaScanDetails) {
        return createScanIssueForGroupInternal(ascaScanDetails, true);
    }

    /**
     * Creates a ScanIssue from a group of ASCA scan details without filtering.
     * All vulnerabilities are included, regardless of ignore status.
     *
     * @param ascaScanDetails the list of ASCA scan details for the same line (already sorted by severity)
     * @return a {@link ScanIssue} representing the ASCA finding(s)
     */
    private ScanIssue createScanIssueForGroupUnfiltered(List<ScanDetail> ascaScanDetails) {
        return createScanIssueForGroupInternal(ascaScanDetails, false);
    }

    /**
     * Internal method that creates a ScanIssue from a group of scan details.
     *
     * @param ascaScanDetails the list of ASCA scan details for the same line (already sorted by severity)
     * @param applyFilter     true to filter out ignored vulnerabilities, false to include all
     * @return a {@link ScanIssue} or null if all ignored (when filter=true) or conversion fails
     */
    private ScanIssue createScanIssueForGroupInternal(List<ScanDetail> ascaScanDetails, boolean applyFilter) {
        if (ascaScanDetails == null || ascaScanDetails.isEmpty()) {
            return null;
        }

        try {
            IgnoreManager ignoreManager = null;
            IgnoreFileManager ignoreFileManager = null;
            List<IgnoreEntry> ignoreEntries = null;
            String normalizedPath = null;

            if (applyFilter) {
                ignoreManager = new IgnoreManager(project);
                ignoreFileManager = IgnoreFileManager.getInstance(project);
                ignoreEntries = ignoreFileManager.getAllIgnoreEntries();
                normalizedPath = ignoreFileManager.normalizePath(filePath);

                // For single vulnerability with filtering: skip entirely if ignored
                if (ascaScanDetails.size() == 1) {
                    Vulnerability tempVuln = createVulnerability(ascaScanDetails.get(0), null);
                    if (ignoreManager.isVulnerabilityIgnored(tempVuln, ignoreEntries, normalizedPath)) {
                        LOGGER.debug("ASCA adaptor: Skipping single ignored vulnerability on line " +
                                   ascaScanDetails.get(0).getLine());
                        return null;
                    }
                }
            }

            // Create base ScanIssue
            ScanIssue scanIssue = getScanIssue(ascaScanDetails);

            // Add vulnerabilities (filtered or unfiltered based on applyFilter)
            for (int i = 0; i < ascaScanDetails.size(); i++) {
                ScanDetail detail = ascaScanDetails.get(i);
                String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
                Vulnerability vuln = createVulnerability(detail, vulnerabilityId);

                if (!applyFilter || !ignoreManager.isVulnerabilityIgnored(vuln, ignoreEntries, normalizedPath)) {
                    scanIssue.getVulnerabilities().add(vuln);
                }
            }

            // If filtering and all vulnerabilities were ignored, skip this ScanIssue
            if (applyFilter && scanIssue.getVulnerabilities().isEmpty()) {
                LOGGER.debug("ASCA adaptor: All vulnerabilities on line " +
                           ascaScanDetails.get(0).getLine() + " are ignored");
                return null;
            }

            // Update title based on actual number of vulnerabilities
            updateScanIssueTitleAndLocation(scanIssue, ascaScanDetails, applyFilter);

            String logMsg = applyFilter ? "active" : "total";
            LOGGER.debug("ASCA adaptor: Created ScanIssue with " + scanIssue.getVulnerabilities().size() +
                    " " + logMsg + " vulnerabilities on line " + scanIssue.getProblematicLineNumber());
            return scanIssue;
        } catch (Exception e) {
            LOGGER.warn("ASCA adaptor: Failed to convert scan details group to ScanIssue", e);
            return null;
        }
    }

    /**
     * Updates the ScanIssue title and location based on vulnerability count.
     */
    private void updateScanIssueTitleAndLocation(ScanIssue scanIssue, List<ScanDetail> ascaScanDetails, boolean applyFilter) {
        // Update title based on actual number of vulnerabilities
        if (scanIssue.getVulnerabilities().size() == 1) {
            scanIssue.setTitle(scanIssue.getVulnerabilities().get(0).getTitle());
        } else if (scanIssue.getVulnerabilities().size() > 1) {
            scanIssue.setTitle(scanIssue.getVulnerabilities().size() + DevAssistConstants.MULTIPLE_ASCA_ISSUES);
        }

        // Add location information
        Location location = new Location();
        location.setLine(ascaScanDetails.get(0).getLine());
        scanIssue.getLocations().add(location);
        scanIssue.setProblematicLineNumber(location.getLine());
    }

    /**
     * Creates a ScanIssue with appropriate title and basic properties from a group of ASCA scan details.
     *
     * @param ascaScanDetails the list of ASCA scan details (already sorted by severity)
     * @return a ScanIssue with basic properties set
     */
    private @NotNull ScanIssue getScanIssue(List<ScanDetail> ascaScanDetails) {
        ScanIssue scanIssue = new ScanIssue();
        ScanDetail firstDetail = ascaScanDetails.get(0); // Highest severity (already sorted)

        // Set title based on whether there are multiple issues on the same line
        if (ascaScanDetails.size() > 1) {
            scanIssue.setTitle(ascaScanDetails.size() + DevAssistConstants.MULTIPLE_ASCA_ISSUES);
        } else {
            scanIssue.setTitle(firstDetail.getRuleName());
        }

        // Use the first (highest severity) detail for primary issue properties
        scanIssue.setDescription(firstDetail.getDescription());
        scanIssue.setSeverity(mapSeverity(firstDetail.getSeverity()));
        scanIssue.setRemediationAdvise(firstDetail.getRemediationAdvise());
        scanIssue.setFilePath(this.filePath);
        scanIssue.setScanEngine(ScanEngine.ASCA);
        scanIssue.setRuleId(firstDetail.getRuleID());

        // Generate unique ID based on line, title, and description
        scanIssue.setScanIssueId(getUniqueId(firstDetail));

        return scanIssue;
    }

    /**
     * Creates a Vulnerability object from a ASCA scan detail.
     *
     * @param scanDetail the ASCA scan detail
     * @param overrideId optional vulnerability ID to use instead of generating one
     * @return a Vulnerability object
     */
    private Vulnerability createVulnerability(ScanDetail scanDetail, String overrideId) {
        Vulnerability vulnerability = new Vulnerability();

        // Generate or use provided vulnerability ID
        String vulnerabilityId = getUniqueId(scanDetail);
        if (overrideId != null && !overrideId.isBlank()) {
            vulnerabilityId = overrideId;
        }

        vulnerability.setVulnerabilityId(vulnerabilityId);
        vulnerability.setCve(scanDetail.getRuleName());
        vulnerability.setDescription(scanDetail.getDescription());
        vulnerability.setSeverity(mapSeverity(scanDetail.getSeverity()));
        vulnerability.setRemediationAdvise(scanDetail.getRemediationAdvise());
        vulnerability.setTitle(scanDetail.getRuleName());
        vulnerability.setProblematicLine(scanDetail.getProblematicLine());
        vulnerability.setRuleId(scanDetail.getRuleID());

        return vulnerability;
    }

    /**
     * Maps ASCA severity levels to standardized severity strings that match the icon system.
     *
     * @param ascaSeverity the ASCA severity level
     * @return standardized severity string (Capitalized format: "Critical", "High", "Medium", "Low")
     */
    private String mapSeverity(String ascaSeverity) {
        if (ascaSeverity == null) {
            return SeverityLevel.MEDIUM.getSeverity();
        } else if (ascaSeverity.equalsIgnoreCase("info")) {
            return SeverityLevel.LOW.getSeverity(); // Map info to Low for icon display
        }
        // retrieve the severity from enum to ensure valid severity levels
        SeverityLevel severityLevel = SeverityLevel.fromValue(ascaSeverity.toLowerCase()); // Validate severity
        return severityLevel == SeverityLevel.UNKNOWN
                ? SeverityLevel.MEDIUM.getSeverity()
                : severityLevel.getSeverity();
    }

    /**
     * Generates a unique ID for the given scan issue.
     */
    private String getUniqueId(ScanDetail scanIssue) {
        if (Objects.nonNull(scanIssue)) {
            return DevAssistUtils.generateUniqueId(scanIssue.getLine(),
                    scanIssue.getRuleID() + scanIssue.getRuleName(), scanIssue.getFileName());
        }
        return ScanEngine.ASCA.name();
    }

}

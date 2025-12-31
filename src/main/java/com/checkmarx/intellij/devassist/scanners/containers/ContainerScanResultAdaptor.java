package com.checkmarx.intellij.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeImage;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter class for handling container scan results and converting them into a standardized format
 * using the {@link ScanResult} interface.
 * This class wraps an {@code ContainersRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on vulnerabilities detected in the images.
 */

public class ContainerScanResultAdaptor implements ScanResult<ContainersRealtimeResults> {

    private final ContainersRealtimeResults containersRealtimeResults;
    private final String fileType;
    private final List<ScanIssue> scanIssues;

    /**
     * Constructs an instance of {@code ContainersRealtimeResults} with the specified container real-time results.
     * This adapter allows conversion and processing of OSS scan results into a standardized format.
     *
     * @param containersRealtimeResults the container real-time scan results to be wrapped by this adapter
     */

    public ContainerScanResultAdaptor(ContainersRealtimeResults containersRealtimeResults, String fileType) {
        this.containersRealtimeResults = containersRealtimeResults;
        this.fileType = fileType;
        this.scanIssues = buildIssues();
    }

    /**
     * Retrieves the container real-time scan results wrapped by this adapter.
     *
     * @return an {@code ContainersRealtimeResults} instance containing the results of the OSS scan
     */
    @Override
    public ContainersRealtimeResults getResults() {
        return containersRealtimeResults;
    }

    /**
     * Retrieves a list of scan issues discovered in the container real-time scan.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Retrieves a list of scan issues discovered in the container real-time scan.
     * This method processes the images obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no images are found, an empty list is returned.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     * or an empty list if no vulnerabilities are detected.
     */
    public List<ScanIssue> buildIssues() {
        List<ContainersRealtimeImage> images = Objects.nonNull(getResults()) ? getResults().getImages() : null;
        if (Objects.isNull(images) || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(this::createScanIssue)
                .collect(Collectors.toList());

    }

    /**
     * Creates a {@code ScanIssue} object based on the provided {@code ContainersRealtimeImage}.
     * The method processes the package details and converts them into a structured format to
     * represent a scan issue.
     *
     * @param containersImageObj the {@code ContainersRealtimeResults} containing information about the scanned images,
     *                           including its name, version, vulnerabilities, and locations.
     * @return a {@code ScanIssue} object encapsulating the details such as title, package version, scan engine,
     * severity, and vulnerability locations derived from the provided image.
     */

    private ScanIssue createScanIssue(ContainersRealtimeImage containersImageObj) {
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setScanEngine(ScanEngine.CONTAINERS);
        scanIssue.setTitle(containersImageObj.getImageName());
        scanIssue.setImageTag(containersImageObj.getImageTag());
        scanIssue.setSeverity(containersImageObj.getStatus());
        scanIssue.setFileType(this.fileType);
        scanIssue.setFilePath(containersImageObj.getFilePath());

        if (!Objects.isNull(containersImageObj.getLocations()) && !containersImageObj.getLocations().isEmpty()) {
            containersImageObj.getLocations().forEach(location -> scanIssue.getLocations().add(createLocation(location)));
        }

        if (!Objects.isNull(containersImageObj.getVulnerabilities()) && !containersImageObj.getVulnerabilities().isEmpty()) {
            containersImageObj.getVulnerabilities().forEach(vulnerability -> scanIssue.getVulnerabilities().add(createVulnerability(vulnerability)));
        }
        scanIssue.setScanIssueId(getUniqueId(scanIssue));
        return scanIssue;
    }

    /**
     * Creates a {@code Vulnerability} instance based on the provided {@code ContainerRealtimeVulnerability}.
     * This method extracts relevant information such as the ID, description, severity, and fix version
     * from the provided {@code ContainerRealtimeVulnerability} object and uses it to construct a new
     * {@code Vulnerability}.
     *
     * @param vulnerabilityObj the {@code ContainerRealtimeVulnerability} object containing details of the vulnerability
     *                         identified during the real-time scan, including cve, description, severity,
     *                         and fix version
     * @return a new {@code Vulnerability} instance encapsulating the details from the given {@code ContainerRealtimeVulnerability}
     */

    private Vulnerability createVulnerability(ContainersRealtimeVulnerability vulnerabilityObj) {
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setCve(vulnerabilityObj.getCve());
        vulnerability.setDescription(this.getDescription(vulnerabilityObj.getSeverity()));
        vulnerability.setSeverity(vulnerabilityObj.getSeverity());
        return vulnerability;
    }


    private String getDescription(String severity) {
        if (Objects.isNull(severity) || severity.isEmpty()) {
            return severity;
        }
        SeverityLevel severityLevel = SeverityLevel.fromValue(severity);
        switch (severityLevel) {
            case MALICIOUS:
                return DevAssistConstants.MALICIOUS_RISK_CONTAINER;

            case CRITICAL:
                return DevAssistConstants.CRITICAL_RISK_CONTAINER;

            case HIGH:
                return DevAssistConstants.HIGH_RISK_CONTAINER;

            case MEDIUM:
                return DevAssistConstants.MEDIUM_RISK_CONTAINER;

            case LOW:
                return DevAssistConstants.LOW_RISK_CONTAINER;
            default:
                return severity;
        }
    }

    /**
     * Creates a {@code Location} object based on the provided {@code RealtimeLocation}.
     * This method extracts the line, start index, and end index from the given
     * {@code RealtimeLocation} and constructs a new {@code Location} instance.
     *
     * @param location the {@code RealtimeLocation} containing details such as line,
     *                 start index, and end index for the location.
     * @return a new {@code Location} instance with the line incremented by one,
     * and start and end indices derived from the provided {@code RealtimeLocation}.
     */

    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }

    /**
     * Retrieves the line number from the given {@code RealtimeLocation} object, increments it by one, and returns the result.
     *
     * @param location the {@code RealtimeLocation} object containing the original line number
     * @return the incremented line number based on the {@code RealtimeLocation}'s line value
     * @apiNote - Current OSS scan result line numbers are zero-based, so this method adjusts them to be one-based.
     */
    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }

    /**
     * Generates a unique ID for the given scan issue.
     */
    private String getUniqueId(ScanIssue scanIssue) {
        int line = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
                ? scanIssue.getLocations().get(0).getLine() : 0;
        return DevAssistUtils.generateUniqueId(line, scanIssue.getTitle(), scanIssue.getImageTag());
    }
}

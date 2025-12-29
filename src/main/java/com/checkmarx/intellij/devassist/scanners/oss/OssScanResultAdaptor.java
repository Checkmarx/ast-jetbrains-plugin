package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter class for handling OSS scan results and converting them into a standardized format
 * using the {@link ScanResult} interface.
 * This class wraps an {@code OssRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on vulnerabilities detected in the packages.
 */
public class OssScanResultAdaptor implements ScanResult<OssRealtimeResults> {
    private final OssRealtimeResults ossRealtimeResults;
    private final List<ScanIssue> scanIssues;

    /**
     * Constructs an instance of {@code OssScanResultAdaptor} with the specified OSS real-time results.
     * This adapter allows conversion and processing of OSS scan results into a standardized format.
     *
     * @param ossRealtimeResults the OSS real-time scan results to be wrapped by this adapter
     */
    public OssScanResultAdaptor(OssRealtimeResults ossRealtimeResults) {
        this.ossRealtimeResults = ossRealtimeResults;
        this.scanIssues = buildIssues();
    }

    /**
     * Retrieves the OSS real-time scan results wrapped by this adapter.
     *
     * @return an {@code OssRealtimeResults} instance containing the results of the OSS scan
     */
    @Override
    public OssRealtimeResults getResults() {
        return ossRealtimeResults;
    }


    /**
     * Retrieves a list of scan issues discovered in the OSS real-time scan.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Retrieves a list of scan issues discovered in the OSS real-time scan.
     * This method processes the packages obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no packages are found, an empty list is returned.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     * or an empty list if no vulnerabilities are detected.
     */
    private List<ScanIssue> buildIssues() {
        List<OssRealtimeScanPackage> packages = Objects.nonNull(getResults()) ? getResults().getPackages() : null;
        if (Objects.isNull(packages) || packages.isEmpty()) {
            return Collections.emptyList();
        }
        return packages.stream()
                .map(this::createScanIssue)
                .collect(Collectors.toList());
    }

    /**
     * Creates a {@code ScanIssue} object based on the provided {@code OssRealtimeScanPackage}.
     * The method processes the package details and converts them into a structured format to
     * represent a scan issue.
     *
     * @param packageObj the {@code OssRealtimeScanPackage} containing information about the scanned package,
     *                   including its name, version, vulnerabilities, and locations.
     * @return a {@code ScanIssue} object encapsulating the details such as title, package version, scan engine,
     * severity, and vulnerability locations derived from the provided package.
     */
    private ScanIssue createScanIssue(OssRealtimeScanPackage packageObj) {
        ScanIssue scanIssue = new ScanIssue();

        scanIssue.setPackageManager(packageObj.getPackageManager());
        scanIssue.setTitle(packageObj.getPackageName());
        scanIssue.setPackageVersion(packageObj.getPackageVersion());
        scanIssue.setScanEngine(ScanEngine.OSS);
        scanIssue.setSeverity(packageObj.getStatus());
        scanIssue.setFilePath(packageObj.getFilePath());

        if (Objects.nonNull(packageObj.getLocations()) && !packageObj.getLocations().isEmpty()) {
            packageObj.getLocations().forEach(location ->
                    scanIssue.getLocations().add(createLocation(location)));
        }
        if (packageObj.getVulnerabilities() != null && !packageObj.getVulnerabilities().isEmpty()) {
            packageObj.getVulnerabilities().forEach(vulnerability ->
                    scanIssue.getVulnerabilities().add(createVulnerability(vulnerability)));
        }
        scanIssue.setScanIssueId(getUniqueId(scanIssue));
        return scanIssue;
    }

    /**
     * Creates a {@code Vulnerability} instance based on the provided {@code OssRealtimeVulnerability}.
     * This method extracts relevant information such as the ID, description, severity, and fix version
     * from the provided {@code OssRealtimeVulnerability} object and uses it to construct a new
     * {@code Vulnerability}.
     *
     * @param vulnerabilityObj the {@code OssRealtimeVulnerability} object containing details of the vulnerability
     *                         identified during the real-time scan, including ID, description, severity,
     *                         and fix version
     * @return a new {@code Vulnerability} instance encapsulating the details from the given {@code OssRealtimeVulnerability}
     */
    private Vulnerability createVulnerability(OssRealtimeVulnerability vulnerabilityObj) {
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setCve(vulnerabilityObj.getCve());
        vulnerability.setDescription(vulnerabilityObj.getDescription());
        vulnerability.setSeverity(vulnerabilityObj.getSeverity());
        vulnerability.setFixVersion(vulnerabilityObj.getFixVersion());
        return vulnerability;
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
    private String getUniqueId(ScanIssue scanIssue){
        int line = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
                ? scanIssue.getLocations().get(0).getLine() : 0;
        return DevAssistUtils.generateUniqueId(line, scanIssue.getPackageManager()+scanIssue.getTitle(),
                scanIssue.getPackageVersion());
    }
}

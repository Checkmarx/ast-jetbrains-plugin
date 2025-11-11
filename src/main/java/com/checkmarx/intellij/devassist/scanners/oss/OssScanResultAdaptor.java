package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;

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

    /**
     * Constructs an instance of {@code OssScanResultAdaptor} with the specified OSS real-time results.
     * This adapter allows conversion and processing of OSS scan results into a standardized format.
     *
     * @param ossRealtimeResults the OSS real-time scan results to be wrapped by this adapter
     */
    public OssScanResultAdaptor(OssRealtimeResults ossRealtimeResults) {
        this.ossRealtimeResults = ossRealtimeResults;
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
     * This method processes the packages obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no packages are found, an empty list is returned.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     * or an empty list if no vulnerabilities are detected.
     */
    @Override
    public List<ScanIssue> getIssues() {
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
        ScanIssue problem = new ScanIssue();

        List<RealtimeLocation> locations = packageObj.getLocations();
        if (Objects.nonNull(locations) && !locations.isEmpty()) {
            locations.forEach(location -> problem.getLocations().add(createLocation(location)));
        }
        problem.setTitle(packageObj.getPackageName());
        problem.setPackageVersion(packageObj.getPackageVersion());
        problem.setScanEngine(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME);
        problem.setSeverity(packageObj.getStatus());

        return problem;
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
        return new Location(location.getLine() + 1, location.getStartIndex(), location.getEndIndex());
    }
}

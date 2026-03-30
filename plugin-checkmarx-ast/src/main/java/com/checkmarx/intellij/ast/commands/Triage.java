package com.checkmarx.intellij.ast.commands;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import lombok.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handle triage related operations with the wrapper
 */
public class Triage {

    public static List<com.checkmarx.ast.predicate.Predicate> triageShow(@NonNull UUID projectId, String similarityId, String scanType)
            throws
            IOException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build().triageShow(projectId, similarityId, scanType);
    }

    public static void triageUpdate(@NonNull UUID projectId, String similarityId, String scanType, String state, String comment, String severity)
            throws
            IOException,
            InterruptedException,
            CxException {

        CxWrapperFactory.build().triageUpdate(projectId, similarityId, scanType, state, comment, severity);
    }

    public static List<com.checkmarx.ast.predicate.Predicate> triageScaShow(@NonNull UUID projectId, String vulnerabilities, String scanType)
            throws
            IOException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build().triageScaShow(projectId, vulnerabilities, scanType);
    }

    public static List<com.checkmarx.ast.predicate.Predicate> triageShowForResult(@NonNull UUID projectId, @NonNull Result result)
            throws
            IOException,
            InterruptedException,
            CxException {

        if (Constants.SCAN_TYPE_SCA.equalsIgnoreCase(result.getType())) {
            String vulnerabilities = buildScaVulnerabilityIdentifiers(result);
            if (vulnerabilities == null) {
                Utils.getLogger(Triage.class).debug("Missing SCA vulnerability identifiers, skipping triageScaShow");
                return java.util.Collections.emptyList();
            }
            try {
                return triageScaShow(projectId, vulnerabilities, Constants.SCAN_TYPE_SCA);
            } catch (CxException e) {
                // Suppress only the known case where backend has no predicate data for this SCA result.
                // All other failures are rethrown.
                if (e.getMessage() != null && e.getMessage().contains("Failed to get SCA predicate result")) {
                    Utils.getLogger(Triage.class).warn("No SCA triage history found for vulnerability, returning empty changes list: " + e.getMessage());
                    return java.util.Collections.emptyList();
                }
                throw e;
            }
        }
        return triageShow(projectId, result.getSimilarityId(), result.getType());
    }

    public static void triageScaUpdate(@NonNull UUID projectId, String state, String comment, String vulnerabilities, String scanType)
            throws
            IOException,
            InterruptedException,
            CxException {

        CxWrapperFactory.build().triageScaUpdate(projectId, state, comment, vulnerabilities, scanType);
    }

    public static void triageUpdateForResult(@NonNull UUID projectId, @NonNull Result result, String state, String comment, String severity)
            throws
            IOException,
            InterruptedException,
            CxException {

        if (Constants.SCAN_TYPE_SCA.equalsIgnoreCase(result.getType())) {
            String vulnerabilities = buildScaVulnerabilityIdentifiers(result);
            if (vulnerabilities == null) {
                Utils.getLogger(Triage.class).debug("Missing SCA vulnerability identifiers, skipping triageScaUpdate");
                return;
            }
            triageScaUpdate(projectId, state, comment, vulnerabilities, Constants.SCAN_TYPE_SCA);
            return;
        }
        triageUpdate(projectId, result.getSimilarityId(), result.getType(), state, comment, severity);
    }

    /**
     * Builds the --vulnerability-identifiers string required by the CLI for SCA triage operations.
     * Format: "packagename=<name>,packageversion=<version>,vulnerabilityId=<cveId>,packagemanager=<manager>"
     * PackageIdentifier format is: {Ecosystem}-{PackageName}-{Version} (e.g. "Npm-momnet-2.29.1")
     *
     * @param result the SCA result to extract package information from
     * @return formatted vulnerability identifiers string, or null if required data is missing
     */
    public static String buildScaVulnerabilityIdentifiers(Result result) {
        if (result.getData() == null
                || result.getData().getPackageIdentifier() == null) {
            Utils.getLogger(Triage.class).debug(
                    "SCA result is missing required fields for vulnerability identifiers: " + result.getId());
            return null;
        }
        String packageIdentifier = result.getData().getPackageIdentifier();
        int firstDash = packageIdentifier.indexOf('-');
        int lastDash = packageIdentifier.lastIndexOf('-');
        if (firstDash < 0 || lastDash <= firstDash) {
            Utils.getLogger(Triage.class).debug(
                    "SCA packageIdentifier has unexpected format: " + packageIdentifier);
            return null;
        }

        String packageManager = packageIdentifier.substring(0, firstDash).toLowerCase(Locale.ROOT);
        String packageName = packageIdentifier.substring(firstDash + 1, lastDash);
        String packageVersion = packageIdentifier.substring(lastDash + 1);
        String vulnerabilityId = result.getId();
        if (Utils.isBlank(vulnerabilityId) && result.getVulnerabilityDetails() != null) {
            vulnerabilityId = result.getVulnerabilityDetails().getCveName();
        }
        if (Utils.isBlank(vulnerabilityId)) {
            Utils.getLogger(Triage.class).debug(
                    "SCA result is missing vulnerability id for vulnerability identifiers: " + result.getId());
            return null;
        }
        return String.format(
                "packagename=%s,packageversion=%s,vulnerabilityId=%s,packagemanager=%s",
                packageName, packageVersion, vulnerabilityId, packageManager);
    }
}

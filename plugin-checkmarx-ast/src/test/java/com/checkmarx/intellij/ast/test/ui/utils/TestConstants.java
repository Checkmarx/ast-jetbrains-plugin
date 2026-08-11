package com.checkmarx.intellij.ast.test.ui.utils;

import java.util.List;
import java.util.Map;

public class TestConstants {

    public static final String TENANT = "tenantField";
    public static final String CX_BASE_URI = "baseUrlField";
    public static final String INVALID_BASE_URL_ERROR_MESSAGE =
            "Please check the server address of your Checkmarx One environment.";

    public static final String INVALID_TENANT_ERROR_MESSAGE =
            "Tenant \"invalid-tenant\" not found. Please check your tenant name.";

    public static final String CONFIRMED_TEXT = "Confirmed";
    public static final String CHECKMARX_TEXT = "Checkmarx";
    public static final String VULNERABILITIES_TEXT = "Missing User Instruction";
    public static final String SCAN_MY_LOCAL_BRANCH_TEXT = "scan my local branch";

    // TODO: replace placeholders with actual expected counts per Scan from the CxOne application for this scan/project.
    public static final Map<String, Integer> EXPECTED_SCAN_TYPE_COUNTS = Map.of(
            "sast", 0,
            "sca", 73,
            "secret detection", 0,
            "IaC Security", 62
    );

    // TODO: replace placeholders with actual expected counts per severity from the CxOne application for this scan/project.
    public static final Map<String, Integer> EXPECTED_SEVERITY_COUNTS = Map.of(
            "MALICIOUS", 0,
            "CRITICAL", 14,
            "HIGH", 29,
            "MEDIUM", 25,
            "LOW", 5,
            "INFO", 0
    );
}

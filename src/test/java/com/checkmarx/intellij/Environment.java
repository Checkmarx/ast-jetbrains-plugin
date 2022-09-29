package com.checkmarx.intellij;

public final class Environment {

    private Environment() {

    }

    public static final String BASE_URL = System.getenv("CX_BASE_URI");
    public static final String TENANT = System.getenv("CX_TENANT");
    public static final String API_KEY = System.getenv("CX_APIKEY");
    public static final String REPO = System.getenv("CX_TEST_REPO");
    public static final String PROJECT_NAME = System.getenv("CX_TEST_PROJECT");
    public static final String NOT_MATCH_PROJECT_NAME = System.getenv("CX_NOT_MATCH_TEST_PROJECT");
    public static final String BRANCH_NAME = System.getenv("CX_TEST_BRANCH");
    public static final String NOT_MATCH_BRANCH_NAME = System.getenv("CX_NOT_MATCH_TEST_BRANCH");
    public static final String SCAN_ID = System.getenv("CX_TEST_SCAN");
}

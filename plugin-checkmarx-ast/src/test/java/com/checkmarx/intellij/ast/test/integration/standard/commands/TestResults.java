package com.checkmarx.intellij.ast.test.integration.standard.commands;

import com.checkmarx.intellij.ast.commands.Scan;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.ast.test.integration.standard.BaseTest;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TestResults extends BaseTest {

    @Test
    public void testGetResults() {
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        String errorMsg = "Message: " + results.getMessage();
        Assertions.assertNotEquals(results.getMessage(), Bundle.message(Resource.LATEST_SCAN_ERROR), errorMsg);
        Assertions.assertNotEquals(results.getMessage(), Bundle.message(Resource.GETTING_RESULTS_ERROR), errorMsg);
        Assertions.assertTrue(Objects.equals(results.getMessage(),
                                             Bundle.message(Resource.NO_RESULTS, Environment.SCAN_ID))
                              || results.getResultOutput() != Results.emptyResults, errorMsg);
        Assertions.assertEquals(Environment.SCAN_ID, results.getScanIdFieldValue());
        Assertions.assertEquals(Environment.SCAN_ID, results.getScanId());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(results.getScanId()));
    }

    @Test
    public void testGetResults_LatestScan() {
        // Test with "" as scan ID which will be interpeted as latest scan
        CompletableFuture<ResultGetState> getFuture = Results.getResults("");
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        
        String errorMsg = "Message: " + results.getMessage();
        
        Assertions.assertNotEquals(results.getMessage(), Bundle.message(Resource.LATEST_SCAN_ERROR), errorMsg);
        
        Assertions.assertNotEquals(results.getMessage(), Bundle.message(Resource.GETTING_RESULTS_ERROR), errorMsg);

        Assertions.assertNotNull(results);
    }

    @Test
    public void testGetResults_NotExistingScanID_throwException() {
        CompletableFuture<ResultGetState> getFuture = Results.getResults("11111111-1111-1111-1111-111111111111");
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        assertTrue(results.getMessage().toLowerCase().contains("scan not found"));
        Assertions.assertNull(results.getScanId());
    }

    @Test
    public void testGetResults_WithInvalidCredentials_SetsErrorMessage() {
        GlobalSettingsSensitiveState.getInstance().setApiKey("invalid-api-key");

        // Use a fixed valid-format UUID to avoid IllegalArgumentException from UUID.fromString
        CompletableFuture<ResultGetState> getFuture = Results.getResults("11111111-1111-1111-1111-111111111111");
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);

        Assertions.assertNull(results.getScanId());
        Assertions.assertNotNull(results.getMessage());
        Assertions.assertFalse(results.getMessage().isBlank());
        Assertions.assertSame(Results.emptyResults, results.getResultOutput());
    }

    @Test
    public void testGetResults_WithZeroResultScan_SetsNoResultsMessage() {
        com.checkmarx.ast.scan.Scan newScan = Assertions.assertDoesNotThrow(
                () -> Scan.scanCreate(System.getProperty("user.dir"), Environment.PROJECT_NAME, Environment.BRANCH_NAME));
        String newScanId = newScan.getId();

        try {
            CompletableFuture<ResultGetState> getFuture = Results.getResults(newScanId);
            ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);

            // A freshly created scan that hasn't completed produces either a NO_RESULTS message
            // or an API error about scan status (e.g. "Running"/"Canceled"). Either way the
            // scan ID is cleared from state and the result output is still the empty sentinel.
            Assertions.assertNotNull(results.getMessage(), "Message should be set for a zero-result scan");
            Assertions.assertFalse(results.getMessage().isBlank());
            Assertions.assertSame(Results.emptyResults, results.getResultOutput());
        } finally {
            Assertions.assertDoesNotThrow(() -> Scan.scanCancel(newScanId));
        }
    }
}

package com.checkmarx.intellij.integration.standard.commands;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.integration.Environment;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.commands.results.obj.ResultGetState;
import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.integration.standard.BaseTest;
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
}

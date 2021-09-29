package com.checkmarx.intellij.commands.results;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.ast.results.structure.CxResultOutput;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.global.CxAuthFactory;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Handle results related operations with the CLI wrapper
 */
public class Results {

    private static final Logger LOGGER = Utils.getLogger(Results.class);

    public static final CxResultOutput emptyResults = new CxResultOutput(0, Collections.emptyList());

    /**
     * Get a {@link CompletableFuture} which, when completed, supplies a {@link ResultGetState}.
     * This will not update internal state or change the display!
     * The created state should be used in the context of the Swing EDT to update the internal state and the display.
     *
     * @param scanIdFieldValue current value in the scan id field
     * @return CompletableFuture that supplies state for drawing results
     */
    @NotNull
    public static CompletableFuture<ResultGetState> getResults(String scanIdFieldValue) {
        return CompletableFuture.supplyAsync(() -> {
            boolean getLatest = StringUtils.isBlank(scanIdFieldValue);
            ResultGetState newState = new ResultGetState();
            String scanId;

            if (getLatest) {
                try {
                    scanId = Scan.getLatestScanId();
                    newState.setLatest(true);
                } catch (IOException | URISyntaxException | InterruptedException | CxException e) {
                    newState.setMessage(Bundle.message(Resource.LATEST_SCAN_ERROR));
                    LOGGER.warn(newState.getMessage(), e);
                    newState.setScanId(null);
                    return newState;
                }
            } else {
                scanId = scanIdFieldValue;
            }
            newState.setScanId(scanId);
            newState.setScanIdFieldValue(scanIdFieldValue);

            CxResultOutput results;
            try {
                results = CxAuthFactory.build().cxGetResults(scanId);
            } catch (IOException | URISyntaxException | CxException e) {
                newState.setMessage(Bundle.message(Resource.GETTING_RESULTS_ERROR, scanId + Utils.formatLatest(getLatest)));
                LOGGER.warn(newState.getMessage(), e);
                newState.setScanId(null);
                newState.setScanIdFieldValue(null);
                return newState;
            }

            if (results.getTotalCount() > 0) {
                newState.setResultOutput(results);
            } else {
                newState.setMessage(Bundle.message(Resource.NO_RESULTS, scanId + Utils.formatLatest(getLatest)));
                LOGGER.info(newState.getMessage());
            }

            return newState;
        });
    }
}

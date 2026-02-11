package com.checkmarx.intellij.ast.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handle results related operations with the CLI wrapper
 */
public class Results {

    private static final Logger LOGGER = Utils.getLogger(Results.class);

    public static final com.checkmarx.ast.results.Results emptyResults = new com.checkmarx.ast.results.Results(0,
                                                                                                               Collections.emptyList(), "");

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
            boolean getLatest = Utils.isBlank(scanIdFieldValue);
            ResultGetState newState = new ResultGetState();
            String scanId;

            if (getLatest) {
                try {
                    scanId = Scan.getLatestScanId();
                    newState.setLatest(true);
                } catch (CxException e) {
                    newState.setMessage(e.getMessage());
                    LOGGER.warn(e);
                    return newState;
                } catch (Exception e) {
                    newState.setMessage(Bundle.message(Resource.LATEST_SCAN_ERROR));
                    LOGGER.warn(e);
                    return newState;
                }
            } else {
                scanId = scanIdFieldValue;
            }
            newState.setScanId(scanId);
            newState.setScanIdFieldValue(scanIdFieldValue);

            com.checkmarx.ast.results.Results results;
            try {
                results = CxWrapperFactory.build().results(UUID.fromString(scanId), Constants.JET_BRAINS_AGENT_NAME);
            } catch (IOException | CxException | InterruptedException e) {
                newState.setMessage(getMessageFromException(e.getMessage(), 2));
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

    private static String getMessageFromException(String text, int count) {
        return text == null || text.trim().isEmpty() ? "" :
                Arrays.stream(text.split("\\r?\\n"))
                        .skip(Math.max(0, text.split("\\r?\\n").length - count))
                        .findFirst().orElse("");
    }
}

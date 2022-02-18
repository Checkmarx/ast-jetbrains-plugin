package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxConstants;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.commands.results.ResultGetState;
import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.checkmarx.intellij.commands.Triage.triageShow;
import static com.checkmarx.intellij.commands.Triage.triageUpdate;

public class TestTriage extends BaseTest {

    @Test
    public void testShowPredicates() {
        Project project = getEnvProject();

        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().get(0);
        Assertions.assertDoesNotThrow(() -> triageShow(UUID.fromString(project.getId()), result.getSimilarityId(), result.getType()));
    }

    @Test
    public void testUpdatePredicates() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().stream().filter(res -> res.getType().equalsIgnoreCase(CxConstants.SAST)).findFirst().get();
        Assertions.assertDoesNotThrow(() -> triageUpdate(
                UUID.fromString(project.getId()), result.getSimilarityId(), result.getType(), result.getState().equals(Constants.SCAN_STATE_CONFIRMED) ? Constants.SCAN_STATE_TO_VERIFY : Constants.SCAN_STATE_CONFIRMED, "",
                result.getSeverity().equals(Constants.SCAN_SEVERITY_HIGH) ? Constants.SCAN_SEVERITY_LOW : Constants.SCAN_SEVERITY_HIGH));
    }

}

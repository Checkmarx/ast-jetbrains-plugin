package com.checkmarx.intellij.ast.test.integration.standard.commands;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.results.result.VulnerabilityDetails;
import com.checkmarx.ast.wrapper.CxConstants;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.ast.test.integration.standard.BaseTest;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.checkmarx.intellij.ast.commands.Triage.buildScaVulnerabilityIdentifiers;
import static com.checkmarx.intellij.ast.commands.Triage.triageScaShow;
import static com.checkmarx.intellij.ast.commands.Triage.triageScaUpdate;
import static com.checkmarx.intellij.ast.commands.Triage.triageShow;
import static com.checkmarx.intellij.ast.commands.Triage.triageShowForResult;
import static com.checkmarx.intellij.ast.commands.Triage.triageUpdate;
import static com.checkmarx.intellij.ast.commands.Triage.triageUpdateForResult;

public class TestTriage extends BaseTest {

    @Test
    public void testShowPredicates() {
        Project project = getEnvProject();

        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().stream()
                .filter(res -> !res.getType().equalsIgnoreCase(Constants.SCAN_TYPE_SCA))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No triage-supported results found (excluding SCA)"));
        Assertions.assertDoesNotThrow(() -> triageShow(UUID.fromString(project.getId()), result.getSimilarityId(), result.getType()));
    }

    @Test
    public void testUpdatePredicates() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().stream().filter(res -> res.getType().equalsIgnoreCase(CxConstants.SAST)).findFirst().get();
        Assertions.assertDoesNotThrow(() -> triageUpdate(
                UUID.fromString(project.getId()), result.getSimilarityId(), result.getType(), result.getState().equalsIgnoreCase("confirmed") ? "to_verify" : "confirmed", "",
                result.getSeverity().equalsIgnoreCase("high") ? "low" : "high"));
    }

    @Test
    public void testShowPredicatesSca() {
        Project project = getEnvProject();

        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);

        Result scaResult = results.getResultOutput().getResults().stream()
                .filter(res -> Constants.SCAN_TYPE_SCA.equalsIgnoreCase(res.getType()))
                .filter(res -> buildScaVulnerabilityIdentifiers(res) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SCA result found with valid vulnerability identifiers"));

        String vulnerabilities = buildScaVulnerabilityIdentifiers(scaResult);
        Assertions.assertNotNull(vulnerabilities);
        Assertions.assertDoesNotThrow(() -> triageScaShow(
                UUID.fromString(project.getId()),
                vulnerabilities,
                scaResult.getType()));
    }

    @Test
    public void testUpdatePredicatesSca() {
        Project project = getEnvProject();

        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);

        Result scaResult = results.getResultOutput().getResults().stream()
                .filter(res -> Constants.SCAN_TYPE_SCA.equalsIgnoreCase(res.getType()))
                .filter(res -> buildScaVulnerabilityIdentifiers(res) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SCA result found with valid vulnerability identifiers"));

        String vulnerabilities = buildScaVulnerabilityIdentifiers(scaResult);
        Assertions.assertNotNull(vulnerabilities);
        String newState = "confirmed".equalsIgnoreCase(scaResult.getState()) ? "to_verify" : "confirmed";

        Assertions.assertDoesNotThrow(() -> triageScaUpdate(
                UUID.fromString(project.getId()),
                newState,
                "integration test SCA triage update",
                vulnerabilities,
                scaResult.getType()));
    }

    @Test
    public void testGetStates() {
        try {
            int defaultStatesSize = 5;
            CxWrapper cxWrapper = CxWrapperFactory.build();
            List<CustomState> states = Assertions.assertDoesNotThrow(() -> cxWrapper.triageGetStates(false));
            Assertions.assertNotNull(states);
            Assertions.assertTrue(states.size() >= defaultStatesSize);
        } catch (Exception e) {
            Assertions.fail();
        }
    }

    @Test
    public void testShowForResult_WithNonScaResult_Success() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().stream()
                .filter(res -> !res.getType().equalsIgnoreCase(Constants.SCAN_TYPE_SCA))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No non-SCA result found (excluding SCA)"));
        List<Predicate> predicates = Assertions.assertDoesNotThrow(
                () -> triageShowForResult(UUID.fromString(project.getId()), result));
        Assertions.assertNotNull(predicates);
    }

    @Test
    public void testShowForResult_WithScaResult_Success() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result scaResult = results.getResultOutput().getResults().stream()
                .filter(res -> Constants.SCAN_TYPE_SCA.equalsIgnoreCase(res.getType()))
                .filter(res -> buildScaVulnerabilityIdentifiers(res) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SCA result found with valid vulnerability identifiers"));
        List<Predicate> predicates = Assertions.assertDoesNotThrow(
                () -> triageShowForResult(UUID.fromString(project.getId()), scaResult));
        Assertions.assertNotNull(predicates);
    }

    @Test
    public void testUpdateForResult_WithNonScaResult_Success() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result result = results.getResultOutput().getResults().stream()
                .filter(res -> res.getType().equalsIgnoreCase(CxConstants.SAST))
                .findFirst()
                .get();
        Assertions.assertDoesNotThrow(() -> triageUpdateForResult(
                UUID.fromString(project.getId()),
                result,
                result.getState().equalsIgnoreCase("confirmed") ? "to_verify" : "confirmed",
                "",
                result.getSeverity().equalsIgnoreCase("high") ? "low" : "high"));
    }

    @Test
    public void testUpdateForResult_WithScaResult_Success() {
        Project project = getEnvProject();
        CompletableFuture<ResultGetState> getFuture = Results.getResults(Environment.SCAN_ID);
        ResultGetState results = Assertions.assertDoesNotThrow((ThrowingSupplier<ResultGetState>) getFuture::get);
        Result scaResult = results.getResultOutput().getResults().stream()
                .filter(res -> Constants.SCAN_TYPE_SCA.equalsIgnoreCase(res.getType()))
                .filter(res -> buildScaVulnerabilityIdentifiers(res) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SCA result found with valid vulnerability identifiers"));
        String newState = "confirmed".equalsIgnoreCase(scaResult.getState()) ? "to_verify" : "confirmed";
        Assertions.assertDoesNotThrow(() -> triageUpdateForResult(
                UUID.fromString(project.getId()),
                scaResult,
                newState,
                "integration test ForResult SCA triage update",
                scaResult.getSeverity()));
    }

    @Test
    public void testShowForResult_WithScaResult_NullData_ReturnsEmpty() {
        UUID projectId = UUID.randomUUID();
        Result result = Mockito.mock(Result.class);
        Mockito.when(result.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        Mockito.when(result.getData()).thenReturn(null);
        Mockito.when(result.getId()).thenReturn("test-result-id");

        List<Predicate> predicates = Assertions.assertDoesNotThrow(
                () -> triageShowForResult(projectId, result));
        Assertions.assertNotNull(predicates);
        Assertions.assertTrue(predicates.isEmpty());
    }

    @Test
    public void testUpdateForResult_WithScaResult_NullData_SkipsUpdate() {
        UUID projectId = UUID.randomUUID();
        Result result = Mockito.mock(Result.class);
        Mockito.when(result.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        Mockito.when(result.getData()).thenReturn(null);
        Mockito.when(result.getId()).thenReturn("test-result-id");

        Assertions.assertDoesNotThrow(() -> triageUpdateForResult(projectId, result, "confirmed", "test", "high"));
    }

    @Test
    public void testBuildScaVulnerabilityIdentifiers_NullPackageIdentifier_ReturnsNull() {
        Result result = Mockito.mock(Result.class);
        Data data = Mockito.mock(Data.class);
        Mockito.when(result.getData()).thenReturn(data);
        Mockito.when(data.getPackageIdentifier()).thenReturn(null);

        String identifiers = buildScaVulnerabilityIdentifiers(result);
        Assertions.assertNull(identifiers);
    }

    @Test
    public void testBuildScaVulnerabilityIdentifiers_WithCveNameFallback_ReturnsString() {
        Result result = Mockito.mock(Result.class);
        Data data = Mockito.mock(Data.class);
        VulnerabilityDetails vulnDetails = Mockito.mock(VulnerabilityDetails.class);
        Mockito.when(result.getData()).thenReturn(data);
        Mockito.when(data.getPackageIdentifier()).thenReturn("Npm-moment-2.29.1");
        Mockito.when(result.getId()).thenReturn("");
        Mockito.when(result.getVulnerabilityDetails()).thenReturn(vulnDetails);
        Mockito.when(vulnDetails.getCveName()).thenReturn("CVE-2022-24785");

        String identifiers = buildScaVulnerabilityIdentifiers(result);
        Assertions.assertNotNull(identifiers);
        Assertions.assertEquals(
                "packagename=moment,packageversion=2.29.1,vulnerabilityId=CVE-2022-24785,packagemanager=npm",
                identifiers);
    }

    @Test
    public void testBuildScaVulnerabilityIdentifiers_BlankIdAndNoCveName_ReturnsNull() {
        Result result = Mockito.mock(Result.class);
        Data data = Mockito.mock(Data.class);
        Mockito.when(result.getData()).thenReturn(data);
        Mockito.when(data.getPackageIdentifier()).thenReturn("Npm-moment-2.29.1");
        Mockito.when(result.getId()).thenReturn("");
        Mockito.when(result.getVulnerabilityDetails()).thenReturn(null);

        String identifiers = buildScaVulnerabilityIdentifiers(result);
        Assertions.assertNull(identifiers);
    }

}

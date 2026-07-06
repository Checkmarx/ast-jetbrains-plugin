package com.checkmarx.intellij.ast.test.unit.commands;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.commands.Scan;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Results class.
 * Note: The async execution in Results.getResults() requires IntelliJ Platform context
 * for full execution, which is tested via integration tests (TestResults.java).
 * These unit tests cover the public API and static elements.
 *
 * Coverage achievable without IntelliJ Platform:
 * - emptyResults static field initialization
 * - getResults() method invocation (CompletableFuture creation)
 *
 * The async body inside getResults() requires Platform initialization and is tested in integration tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultsTest {

    @Mock
    private CxWrapper mockWrapper;

    @Test
    void emptyResults_HasCorrectInitialization() {
        // Assert all properties of emptyResults in one comprehensive test
        assertNotNull(Results.emptyResults, "emptyResults constant should not be null");
        assertEquals(0, Results.emptyResults.getTotalCount(), "emptyResults should have zero count");
        assertNotNull(Results.emptyResults.getResults(), "emptyResults results list should not be null");
        assertTrue(Results.emptyResults.getResults().isEmpty(), "emptyResults results list should be empty");
    }

    @Test
    void getResults_WithValidScanId_ReturnsCompletableFuture() {
        // Arrange
        String scanId = "12345678-1234-1234-1234-123456789012";

        // Act
        CompletableFuture<ResultGetState> future = Results.getResults(scanId);

        // Assert
        assertNotNull(future, "getResults should return a non-null CompletableFuture");
        assertInstanceOf(CompletableFuture.class, future, "Should return CompletableFuture type");
    }

    @Test
    void getResults_WithNullScanId_ReturnsCompletableFuture() {
        // Act - Null scan ID should trigger "get latest" path
        CompletableFuture<ResultGetState> future = Results.getResults(null);

        // Assert
        assertNotNull(future, "getResults with null should return a non-null CompletableFuture");
        assertInstanceOf(CompletableFuture.class, future);
    }

    @Test
    void getResults_WithEmptyScanId_ReturnsCompletableFuture() {
        // Act - Empty scan ID should trigger "get latest" path
        CompletableFuture<ResultGetState> future = Results.getResults("");

        // Assert
        assertNotNull(future, "getResults with empty string should return a non-null CompletableFuture");
        assertInstanceOf(CompletableFuture.class, future);
    }

    @Test
    void getResults_WithWhitespaceScanId_ReturnsCompletableFuture() {
        // Act - Whitespace scan ID should trigger "get latest" path
        CompletableFuture<ResultGetState> future = Results.getResults("   ");

        // Assert
        assertNotNull(future, "getResults with whitespace should return a non-null CompletableFuture");
        assertInstanceOf(CompletableFuture.class, future);
    }

    @Test
    void getResults_MultipleCallsReturnDifferentFutures() {
        // Arrange
        String scanId = "test-scan-id";

        // Act
        CompletableFuture<ResultGetState> future1 = Results.getResults(scanId);
        CompletableFuture<ResultGetState> future2 = Results.getResults(scanId);

        // Assert
        assertNotSame(future1, future2, "Each call to getResults should return a new CompletableFuture instance");
        assertNotNull(future1);
        assertNotNull(future2);
    }

    // ===== getMessageFromException (private static) =====

    private static String getMessageFromException(String text, int count) throws Exception {
        Method m = Results.class.getDeclaredMethod("getMessageFromException", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, text, count);
    }

    @Test
    void getMessageFromException_WithNullText_ReturnsEmpty() throws Exception {
        assertEquals("", getMessageFromException(null, 2));
    }

    @Test
    void getMessageFromException_WithBlankText_ReturnsEmpty() throws Exception {
        assertEquals("", getMessageFromException("   ", 2));
    }

    @Test
    void getMessageFromException_WithSingleLine_ReturnsThatLine() throws Exception {
        assertEquals("Only line", getMessageFromException("Only line", 2));
    }

    @Test
    void getMessageFromException_WithMultipleLines_ReturnsLastLine() throws Exception {
        String text = "line1\nline2\nline3";
        assertEquals("line3", getMessageFromException(text, 1));
    }

    @Test
    void getMessageFromException_WithMultipleLines_ReturnsSecondToLastForCount2() throws Exception {
        String text = "line1\nline2\nline3";
        assertEquals("line2", getMessageFromException(text, 2));
    }

    @Test
    void getMessageFromException_WithTwoLines_Count2_ReturnsFirst() throws Exception {
        String text = "line1\nline2";
        assertEquals("line1", getMessageFromException(text, 2));
    }

    @Test
    void getMessageFromException_WithEmptyString_ReturnsEmpty() throws Exception {
        assertEquals("", getMessageFromException("", 2));
    }

    // ===== getResults() lambda body — executed synchronously =====

    /** Runs the supplyAsync supplier synchronously and returns the ResultGetState. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private ResultGetState runGetResults(String scanId,
                                          MockedStatic<CompletableFuture> cfMock) {
        final ResultGetState[] captured = new ResultGetState[1];
        cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
            Supplier<ResultGetState> supplier = inv.getArgument(0);
            captured[0] = supplier.get();
            CompletableFuture<ResultGetState> future = mock(CompletableFuture.class);
            return future;
        });
        Results.getResults(scanId);
        return captured[0];
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithBlankId_LatestScanPath_CxExceptionSetsMessage() throws Exception {
        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<Scan> scanMock = mockStatic(Scan.class)) {

            CxException ex = mock(CxException.class);
            when(ex.getMessage()).thenReturn("auth error");
            scanMock.when(Scan::getLatestScanId).thenThrow(ex);

            ResultGetState state = runGetResults("", cfMock);

            assertNotNull(state);
            assertEquals("auth error", state.getMessage());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithBlankId_LatestScanPath_GenericExceptionSetsMessage() throws Exception {
        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<Scan> scanMock = mockStatic(Scan.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            scanMock.when(Scan::getLatestScanId).thenThrow(new RuntimeException("network timeout"));
            bundleMock.when(() -> Bundle.message(eq(Resource.LATEST_SCAN_ERROR))).thenReturn("latest scan error");

            ResultGetState state = runGetResults("   ", cfMock);

            assertNotNull(state);
            assertEquals("latest scan error", state.getMessage());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithValidScanId_ResultsFound_SetsResultOutput() throws Exception {
        String validScanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";

        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class)) {

            factoryMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            com.checkmarx.ast.results.Results mockResults = mock(com.checkmarx.ast.results.Results.class);
            when(mockResults.getTotalCount()).thenReturn(3);
            when(mockResults.getResults()).thenReturn(List.of(mock(Result.class)));

            when(mockWrapper.results(eq(UUID.fromString(validScanId)), anyString())).thenReturn(mockResults);

            ResultGetState state = runGetResults(validScanId, cfMock);

            assertNotNull(state);
            assertNotNull(state.getResultOutput());
            assertEquals(validScanId, state.getScanId());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithValidScanId_NoResults_SetsMessage() throws Exception {
        String validScanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";

        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            factoryMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            com.checkmarx.ast.results.Results mockResults = mock(com.checkmarx.ast.results.Results.class);
            when(mockResults.getTotalCount()).thenReturn(0);
            when(mockWrapper.results(eq(UUID.fromString(validScanId)), anyString())).thenReturn(mockResults);

            bundleMock.when(() -> Bundle.message(eq(Resource.NO_RESULTS), any())).thenReturn("no results");

            ResultGetState state = runGetResults(validScanId, cfMock);

            assertNotNull(state);
            assertEquals("no results", state.getMessage());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithValidScanId_WrapperThrowsIOException_SetsMessageAndClearsIds() throws Exception {
        String validScanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";

        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class)) {

            factoryMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.results(eq(UUID.fromString(validScanId)), anyString()))
                    .thenThrow(new java.io.IOException("connection refused"));

            ResultGetState state = runGetResults(validScanId, cfMock);

            assertNotNull(state);
            assertNotNull(state.getMessage());
            assertNull(state.getScanId());
            assertNull(state.getScanIdFieldValue());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getResults_WithBlankId_LatestScanFoundAndResultsPresent_SetsLatestAndResultOutput() throws Exception {
        String latestId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        try (MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<Scan> scanMock = mockStatic(Scan.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class)) {

            scanMock.when(Scan::getLatestScanId).thenReturn(latestId);
            factoryMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            com.checkmarx.ast.results.Results mockResults = mock(com.checkmarx.ast.results.Results.class);
            when(mockResults.getTotalCount()).thenReturn(1);
            when(mockResults.getResults()).thenReturn(List.of(mock(Result.class)));
            when(mockWrapper.results(eq(UUID.fromString(latestId)), anyString())).thenReturn(mockResults);

            ResultGetState state = runGetResults(null, cfMock);

            assertNotNull(state);
            assertTrue(state.isLatest());
            assertEquals(latestId, state.getScanId());
            assertNotNull(state.getResultOutput());
        }
    }

}


package com.checkmarx.intellij.ast.test.unit.commands;

import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

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
class ResultsTest {

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
}


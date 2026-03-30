package com.checkmarx.intellij.ast.test.unit.commands;

import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.results.result.VulnerabilityDetails;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.commands.Triage;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriageTest {

    @Mock
    private CxWrapper mockWrapper;

    @Mock
    private Predicate mockPredicate;

    @BeforeEach
    void setUp() {
        mockPredicate = mock(Predicate.class);
    }

    @Test
    void triageShow_Success() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";
        List<Predicate> expectedPredicates = Arrays.asList(mockPredicate);
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.triageShow(eq(projectId), eq(similarityId), eq(scanType))).thenReturn(expectedPredicates);

            // Act
            List<Predicate> result = Triage.triageShow(projectId, similarityId, scanType);

            // Assert
            assertNotNull(result);
            assertEquals(expectedPredicates, result);
            verify(mockWrapper).triageShow(projectId, similarityId, scanType);
        }
    }

    @Test
    void triageShow_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.triageShow(any(UUID.class), anyString(), anyString())).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                Triage.triageShow(projectId, similarityId, scanType)
            );
        }
    }

    @Test
    void triageUpdate_Success() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";
        String state = "test-state";
        String comment = "test-comment";
        String severity = "test-severity";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            doNothing().when(mockWrapper).triageUpdate(eq(projectId), eq(similarityId), eq(scanType), eq(state), eq(comment), eq(severity));

            // Act & Assert
            assertDoesNotThrow(() -> {
                Triage.triageUpdate(projectId, similarityId, scanType, state, comment, severity);
                verify(mockWrapper).triageUpdate(projectId, similarityId, scanType, state, comment, severity);
            });
        }
    }

    @Test
    void triageUpdate_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";
        String state = "test-state";
        String comment = "test-comment";
        String severity = "test-severity";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            doThrow(mock(CxException.class)).when(mockWrapper).triageUpdate(any(UUID.class), anyString(), anyString(), anyString(), anyString(), anyString());

            // Act & Assert
            assertThrows(CxException.class, () ->
                Triage.triageUpdate(projectId, similarityId, scanType, state, comment, severity)
            );
        }
    }

    @Test
    void triageShow_WithNullProjectId_ThrowsException() {
        // Arrange
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            Triage.triageShow(null, similarityId, scanType)
        );
    }

    @Test
    void triageUpdate_WithNullProjectId_ThrowsException() {
        // Arrange
        String similarityId = "test-similarity-id";
        String scanType = "test-scan-type";
        String state = "test-state";
        String comment = "test-comment";
        String severity = "test-severity";

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            Triage.triageUpdate(null, similarityId, scanType, state, comment, severity)
        );
    }

    @Test
    void triageShowForResult_NonSca_DelegatesToTriageShow() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();
        Result result = mock(Result.class);
        when(result.getType()).thenReturn("sast");
        when(result.getSimilarityId()).thenReturn("sim-id-123");

        List<Predicate> expectedPredicates = Arrays.asList(mockPredicate);
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.triageShow(projectId, "sim-id-123", "sast")).thenReturn(expectedPredicates);

            List<Predicate> actual = Triage.triageShowForResult(projectId, result);

            assertEquals(expectedPredicates, actual);
            verify(mockWrapper).triageShow(projectId, "sim-id-123", "sast");
            verify(mockWrapper, never()).triageScaShow(any(UUID.class), anyString(), anyString());
        }
    }

    @Test
    void triageShowForResult_Sca_InvalidThenValidIdentifiers() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();
        Result malformedScaResult = mock(Result.class);
        Data malformedData = mock(Data.class);
        when(malformedScaResult.getType()).thenReturn("sca");
        when(malformedScaResult.getData()).thenReturn(malformedData);
        when(malformedData.getPackageIdentifier()).thenReturn("NpmOnly");

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            List<Predicate> empty = Triage.triageShowForResult(projectId, malformedScaResult);
            assertTrue(empty.isEmpty());
            verify(mockWrapper, never()).triageScaShow(any(UUID.class), anyString(), anyString());

            Result validScaResult = mockScaResult("Cx4a52ebed-4106", "Npm-my-lib-core-2.1.0", "CVE-2024-1234");
            String expectedVulnerabilities = "packagename=my-lib-core,packageversion=2.1.0,vulnerabilityId=Cx4a52ebed-4106,packagemanager=npm";
            List<Predicate> expectedPredicates = Arrays.asList(mockPredicate);
            lenient().when(mockWrapper.triageScaShow(eq(projectId), eq(expectedVulnerabilities), anyString())).thenReturn(expectedPredicates);

            List<Predicate> actual = Triage.triageShowForResult(projectId, validScaResult);

            assertEquals(expectedPredicates, actual);
            verify(mockWrapper).triageScaShow(projectId, expectedVulnerabilities, "sca");
        }
    }

    @Test
    void triageUpdateForResult_NonSca_DelegatesToTriageUpdate() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();
        Result result = mock(Result.class);
        when(result.getType()).thenReturn("kics");
        when(result.getSimilarityId()).thenReturn("sim-id-456");

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            Triage.triageUpdateForResult(projectId, result, "confirmed", "test", "high");

            verify(mockWrapper).triageUpdate(projectId, "sim-id-456", "kics", "confirmed", "test", "high");
            verify(mockWrapper, never()).triageScaUpdate(any(UUID.class), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Test
    void triageUpdateForResult_Sca_MissingThenValidIdentifiers_AndBuilderGuards() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();

        Result missingDataResult = mock(Result.class);
        when(missingDataResult.getType()).thenReturn("sca");
        when(missingDataResult.getData()).thenReturn(null);
        when(missingDataResult.getId()).thenReturn("rid-missing");

        assertNull(Triage.buildScaVulnerabilityIdentifiers(missingDataResult));

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            Triage.triageUpdateForResult(projectId, missingDataResult, "to_verify", "comment", "medium");
            verify(mockWrapper, never()).triageScaUpdate(any(UUID.class), anyString(), anyString(), anyString(), anyString());

            Result validScaResult = mockScaResult("Cx4a52ebed-4106", "Npm-my-lib-core-2.1.0", "CVE-2024-1234");
            String expectedVulnerabilities = "packagename=my-lib-core,packageversion=2.1.0,vulnerabilityId=Cx4a52ebed-4106,packagemanager=npm";

            Triage.triageUpdateForResult(projectId, validScaResult, "confirmed", "note", "low");

            verify(mockWrapper).triageScaUpdate(projectId, "confirmed", "note", expectedVulnerabilities, "sca");
        }
    }

    @Test
    void triageShowForResult_Sca_KnownBackendFailure_ReturnsEmpty() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();
        Result result = mockScaResult("Cx4a52ebed-4106", "Npm-my-lib-core-2.1.0", "CVE-2024-1234");
        String expectedVulnerabilities = "packagename=my-lib-core,packageversion=2.1.0,vulnerabilityId=Cx4a52ebed-4106,packagemanager=npm";

        CxException knownException = mock(CxException.class);
        when(knownException.getMessage()).thenReturn("Failed showing the predicate: Failed to get SCA predicate result.");

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.triageScaShow(eq(projectId), eq(expectedVulnerabilities), anyString()))
                    .thenThrow(knownException);

            List<Predicate> actual = Triage.triageShowForResult(projectId, result);

            assertTrue(actual.isEmpty(), "Known SCA backend failure should return empty list silently");
        }
    }

    @Test
    void triageShowForResult_Sca_UnexpectedCxException_IsRethrown() throws IOException, CxException, InterruptedException {
        UUID projectId = UUID.randomUUID();
        Result result = mockScaResult("Cx4a52ebed-4106", "Npm-my-lib-core-2.1.0", "CVE-2024-1234");
        String expectedVulnerabilities = "packagename=my-lib-core,packageversion=2.1.0,vulnerabilityId=Cx4a52ebed-4106,packagemanager=npm";

        CxException authException = mock(CxException.class);
        when(authException.getMessage()).thenReturn("Authentication failed: invalid API key");

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.triageScaShow(eq(projectId), eq(expectedVulnerabilities), anyString()))
                    .thenThrow(authException);

            assertThrows(CxException.class, () -> Triage.triageShowForResult(projectId, result),
                    "Unexpected CxException should be rethrown, not silently swallowed");
        }
    }

    private Result mockScaResult(String resultId, String packageIdentifier, String cveName) {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        VulnerabilityDetails vulnerabilityDetails = mock(VulnerabilityDetails.class);

        when(result.getType()).thenReturn("sca");
        when(result.getId()).thenReturn(resultId);
        when(result.getData()).thenReturn(data);
        lenient().when(result.getVulnerabilityDetails()).thenReturn(vulnerabilityDetails);
        when(data.getPackageIdentifier()).thenReturn(packageIdentifier);
        lenient().when(vulnerabilityDetails.getCveName()).thenReturn(cveName);

        return result;
    }
} 
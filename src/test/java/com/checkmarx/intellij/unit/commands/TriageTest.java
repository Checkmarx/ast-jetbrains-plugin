package com.checkmarx.intellij.unit.commands;

import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Exceptions.InvalidCLIConfigException;
import com.checkmarx.intellij.commands.Triage;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
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
    void triageShow_Success() throws InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
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
    void triageShow_ThrowsException() throws InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
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
    void triageUpdate_Success() throws InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
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
    void triageUpdate_ThrowsException() throws InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
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
} 
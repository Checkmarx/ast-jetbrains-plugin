package com.checkmarx.intellij.unit.tool.window.results.tree.nodes;

import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.io.File;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultNodeTest {

    @Mock
    private Project mockProject;
    @Mock
    private Result mockResult;
    @Mock
    private Data mockResultData;
    @Mock
    private Node mockNode;
    @Mock
    private PackageData mockPackageData;

    private static final String SCAN_ID = "test-scan-id";
    private static final String QUERY_NAME = "Test Query";
    private static final String FILE_NAME = "test.java";
    private static final int LINE_NUMBER = 42;
    private static final String RESULT_ID = "TEST-001";

    private ResultNode resultNode;

    @BeforeEach
    void setUp() {
        when(mockResult.getData()).thenReturn(mockResultData);
    }

    @Test
    void constructor_WithQueryName_SetsLabelWithQueryName() {
        when(mockNode.getFileName()).thenReturn(FILE_NAME);
        when(mockNode.getLine()).thenReturn(LINE_NUMBER);
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(Collections.singletonList(mockNode));

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(QUERY_NAME + " (" + new File(FILE_NAME).getName() + ":" + LINE_NUMBER + ")", resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

//    @Test
//    void constructor_WithoutQueryName_SetsLabelWithResultId() {
//        when(mockNode.getFileName()).thenReturn(FILE_NAME);
//        when(mockNode.getLine()).thenReturn(LINE_NUMBER);
//        when(mockResult.getId()).thenReturn(RESULT_ID);
//
//        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);
//
//        assertEquals(RESULT_ID + " (" + new File(FILE_NAME).getName() + ":" + LINE_NUMBER + ")", resultNode.getLabel());
//        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
//    }

    @Test
    void constructor_WithoutNodes_SetsLabelWithoutFileInfo() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(Collections.emptyList());

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertEquals(QUERY_NAME, resultNode.getLabel());
        assertEquals(resultNode.getLabel(), resultNode.getUserObject());
    }

    @Test
    void constructor_WithNullNodes_SetsEmptyNodesList() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getNodes()).thenReturn(null);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertTrue(resultNode.getNodes().isEmpty());
    }

    @Test
    void constructor_WithNullPackageData_SetsEmptyPackageDataList() {
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        when(mockResultData.getPackageData()).thenReturn(null);

        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        assertTrue(resultNode.getPackageData().isEmpty());
    }

    @Test
    void getIcon_ReturnsSeverityIcon() {
        when(mockResult.getSeverity()).thenReturn(Severity.HIGH.name());
        when(mockResultData.getQueryName()).thenReturn(QUERY_NAME);
        resultNode = new ResultNode(mockResult, mockProject, SCAN_ID);

        Icon icon = resultNode.getIcon();
        assertNotNull(icon);
        assertEquals(Severity.HIGH.getIcon(), icon);
    }
}
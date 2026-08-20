package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.results.result.ScaPackageData;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.common.utils.Constants;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupByTest {

    // ===== getFunction() =====

    @Test
    void getFunction_Severity_ReturnsSeverityFromResult() {
        Result result = mock(Result.class);
        when(result.getSeverity()).thenReturn("HIGH");

        Function<Result, String> fn = GroupBy.SEVERITY.getFunction();
        assertEquals("HIGH", fn.apply(result));
    }

    @Test
    void getFunction_VulnerabilityTypeName_WhenSastType_ReturnsQueryName() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        when(result.getType()).thenReturn("sast");
        when(result.getData()).thenReturn(data);
        when(data.getQueryName()).thenReturn("SQL Injection");

        Function<Result, String> fn = GroupBy.VULNERABILITY_TYPE_NAME.getFunction();
        assertEquals("SQL Injection", fn.apply(result));
    }

    @Test
    void getFunction_VulnerabilityTypeName_WhenScsType_ReturnsResultId() {
        Result result = mock(Result.class);
        when(result.getType()).thenReturn(Constants.SCAN_TYPE_SCS);
        when(result.getId()).thenReturn("secret-123");

        Function<Result, String> fn = GroupBy.VULNERABILITY_TYPE_NAME.getFunction();
        assertEquals("secret-123", fn.apply(result));
    }

    @Test
    void getFunction_VulnerabilityTypeName_WhenScaType_ReturnsPackageIdentifier() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        when(result.getType()).thenReturn(Constants.SCAN_TYPE_SCA);
        when(result.getData()).thenReturn(data);
        when(data.getPackageIdentifier()).thenReturn("log4j:2.14");

        Function<Result, String> fn = GroupBy.VULNERABILITY_TYPE_NAME.getFunction();
        assertEquals("log4j:2.14", fn.apply(result));
    }

    @Test
    void getFunction_File_ReturnsFileName() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        when(result.getData()).thenReturn(data);
        when(data.getFileName()).thenReturn("src/main/App.java");

        Function<Result, String> fn = GroupBy.FILE.getFunction();
        assertEquals("src/main/App.java", fn.apply(result));
    }

    @Test
    void getFunction_State_ReturnsResultState() {
        Result result = mock(Result.class);
        when(result.getState()).thenReturn("TO_VERIFY");

        Function<Result, String> fn = GroupBy.STATE.getFunction();
        assertEquals("TO_VERIFY", fn.apply(result));
    }

    @Test
    void getFunction_Package_ReturnsPackageIdentifier() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        when(result.getData()).thenReturn(data);
        when(data.getPackageIdentifier()).thenReturn("commons-io:2.11");

        Function<Result, String> fn = GroupBy.PACKAGE.getFunction();
        assertEquals("commons-io:2.11", fn.apply(result));
    }

    @Test
    void getFunction_DirectDependency_WhenScaPackageDataNotNull_ReturnsTypeOfDependency() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        ScaPackageData scaData = mock(ScaPackageData.class);
        when(result.getData()).thenReturn(data);
        when(data.getScaPackageData()).thenReturn(scaData);
        when(scaData.getTypeOfDependency()).thenReturn("Direct");

        Function<Result, String> fn = GroupBy.DIRECT_DEPENDENCY.getFunction();
        assertEquals("Direct", fn.apply(result));
    }

    @Test
    void getFunction_DirectDependency_WhenScaPackageDataNull_ReturnsEmpty() {
        Result result = mock(Result.class);
        Data data = mock(Data.class);
        when(result.getData()).thenReturn(data);
        when(data.getScaPackageData()).thenReturn(null);

        Function<Result, String> fn = GroupBy.DIRECT_DEPENDENCY.getFunction();
        assertEquals("", fn.apply(result));
    }

    @Test
    void getFunction_ScaType_ReturnsScaType() {
        Result result = mock(Result.class);
        when(result.getScaType()).thenReturn("Vulnerable");

        Function<Result, String> fn = GroupBy.SCA_TYPE.getFunction();
        assertEquals("Vulnerable", fn.apply(result));
    }

    // ===== getComparator() =====

    @Test
    void getComparator_File_ReturnsStringComparator() {
        Comparator<String> cmp = GroupBy.FILE.getComparator();
        assertNotNull(cmp);
        assertEquals(0, cmp.compare("abc", "abc"));
        assertTrue(cmp.compare("abc", "xyz") < 0);
    }

    @Test
    void getComparator_Package_ReturnsStringComparator() {
        Comparator<String> cmp = GroupBy.PACKAGE.getComparator();
        assertNotNull(cmp);
        assertEquals(0, cmp.compare("log4j", "log4j"));
    }

    @Test
    void getComparator_DirectDependency_ReturnsStringComparator() {
        Comparator<String> cmp = GroupBy.DIRECT_DEPENDENCY.getComparator();
        assertNotNull(cmp);
        assertEquals(0, cmp.compare("Direct", "Direct"));
    }

    @Test
    void getComparator_ScaType_ReturnsStringComparator() {
        Comparator<String> cmp = GroupBy.SCA_TYPE.getComparator();
        assertNotNull(cmp);
        assertEquals(0, cmp.compare("Vulnerable", "Vulnerable"));
    }

    @Test
    void getComparator_VulnerabilityTypeName_ReturnsStringComparator() {
        Comparator<String> cmp = GroupBy.VULNERABILITY_TYPE_NAME.getComparator();
        assertNotNull(cmp);
        assertEquals(0, cmp.compare("XSS", "XSS"));
    }
}

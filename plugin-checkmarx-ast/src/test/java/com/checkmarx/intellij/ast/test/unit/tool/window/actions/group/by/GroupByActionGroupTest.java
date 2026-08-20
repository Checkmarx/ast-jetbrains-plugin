package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupByActionGroupTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private Presentation mockPresentation;

    private GroupByActionGroup actionGroup;

    @BeforeEach
    void setUp() {
        actionGroup = new GroupByActionGroup();
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);
    }

    @Test
    void update_EnablesPresentation() {
        actionGroup.update(mockEvent);
        verify(mockPresentation).setEnabled(true);
    }

    // ===== GroupBy.getComparator() — all enum values return non-null =====

    @Test
    void getComparator_ForAllEnumValues_ReturnsNonNull() {
        for (GroupBy groupBy : GroupBy.values()) {
            Comparator<String> comparator = groupBy.getComparator();
            assertNotNull(comparator, "getComparator() must not return null for " + groupBy);
        }
    }

    @Test
    void getComparator_ForSeverity_EqualStringsReturnZero() {
        Comparator<String> cmp = GroupBy.SEVERITY.getComparator();
        assertEquals(0, cmp.compare("HIGH", "HIGH"));
    }

    @Test
    void getComparator_ForVulnerabilityTypeName_IsStringComparator() {
        Comparator<String> cmp = GroupBy.VULNERABILITY_TYPE_NAME.getComparator();
        assertEquals(0, cmp.compare("same", "same"));
        assertTrue(cmp.compare("a", "b") < 0);
        assertTrue(cmp.compare("b", "a") > 0);
    }

    @Test
    void getComparator_ForFile_IsStringComparator() {
        Comparator<String> cmp = GroupBy.FILE.getComparator();
        assertEquals(0, cmp.compare("file.java", "file.java"));
        assertTrue(cmp.compare("a.java", "z.java") < 0);
    }

    @Test
    void getComparator_ForPackage_IsStringComparator() {
        Comparator<String> cmp = GroupBy.PACKAGE.getComparator();
        assertEquals(0, cmp.compare("pkg", "pkg"));
    }

    @Test
    void getComparator_ForDirectDependency_IsStringComparator() {
        Comparator<String> cmp = GroupBy.DIRECT_DEPENDENCY.getComparator();
        assertEquals(0, cmp.compare("direct", "direct"));
    }

    @Test
    void getComparator_ForScaType_IsStringComparator() {
        Comparator<String> cmp = GroupBy.SCA_TYPE.getComparator();
        assertEquals(0, cmp.compare("Library", "Library"));
    }

    // ===== GroupBy.getFunction() — non-null for all values =====

    @Test
    void getFunction_ForAllEnumValues_ReturnsNonNull() {
        for (GroupBy groupBy : GroupBy.values()) {
            assertNotNull(groupBy.getFunction(), "getFunction() must not return null for " + groupBy);
        }
    }

    // ===== DEFAULT_GROUP_BY and HIDDEN_GROUPS constants =====

    @Test
    void defaultGroupBy_ContainsSeverityAndVulnerabilityTypeName() {
        assertTrue(GroupBy.DEFAULT_GROUP_BY.contains(GroupBy.SEVERITY));
        assertTrue(GroupBy.DEFAULT_GROUP_BY.contains(GroupBy.VULNERABILITY_TYPE_NAME));
        assertEquals(2, GroupBy.DEFAULT_GROUP_BY.size());
    }

    @Test
    void hiddenGroups_ContainsScaTypeAndPackage() {
        assertTrue(GroupBy.HIDDEN_GROUPS.contains(GroupBy.SCA_TYPE));
        assertTrue(GroupBy.HIDDEN_GROUPS.contains(GroupBy.PACKAGE));
    }

    @Test
    void enumValues_AllSevenPresent() {
        assertEquals(7, GroupBy.values().length);
    }
}
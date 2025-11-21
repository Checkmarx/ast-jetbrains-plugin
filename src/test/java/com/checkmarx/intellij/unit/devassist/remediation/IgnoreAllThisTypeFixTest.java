package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.IgnoreAllThisTypeFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IgnoreAllThisTypeFixTest {

    private Project project;
    private ProblemDescriptor descriptor;
    private ScanIssue scanIssue;

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        descriptor = mock(ProblemDescriptor.class);
        scanIssue = new ScanIssue();
        scanIssue.setTitle("Sample Title");
    }

    @Test
    @DisplayName("Constructor creates instance without error")
    void testConstructor_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertNotNull(fix);
    }

    @Test
    @DisplayName("Constructor stores scanIssue reference (reflection check)")
    void testConstructor_storesScanIssue_functionality() throws Exception {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        Field f = IgnoreAllThisTypeFix.class.getDeclaredField("scanIssue");
        f.setAccessible(true);
        Object stored = f.get(fix);
        assertSame(scanIssue, stored);
    }

    @Test
    @DisplayName("getFamilyName returns expected constant string")
    void testGetFamilyName_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertEquals(Constants.RealTimeConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME, fix.getFamilyName());
    }

    @Test
    @DisplayName("getFamilyName is non-null")
    void testGetFamilyName_nonNull_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertNotNull(fix.getFamilyName());
    }

    @Test
    @DisplayName("getIcon returns STAR_ACTION icon for visibility flag")
    void testGetIcon_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        Icon icon = fix.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("getIcon returns same icon for combined read+visibility flags")
    void testGetIcon_withFlags_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        int flags = Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY;
        Icon icon = fix.getIcon(flags);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("applyFix executes without throwing when title present")
    void testApplyFix_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix executes without throwing when title is null")
    void testApplyFix_nullTitle_functionality() {
        scanIssue.setTitle(null); // simulate missing title
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix throws NullPointerException when scanIssue is null (edge case)")
    void testApplyFix_nullScanIssue_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(null); // allowed by constructor signature
        assertThrows(NullPointerException.class, () -> fix.applyFix(project, descriptor));
    }
}

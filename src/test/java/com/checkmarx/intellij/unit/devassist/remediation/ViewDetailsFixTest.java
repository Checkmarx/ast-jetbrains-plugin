package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.ViewDetailsFix;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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

public class ViewDetailsFixTest {

    private Project project;
    private ProblemDescriptor descriptor;
    private ScanIssue issue;

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        descriptor = mock(ProblemDescriptor.class);
        issue = new ScanIssue();
        issue.setTitle("Detail Title");
        issue.setScanEngine(ScanEngine.ASCA);
    }

    @Test
    @DisplayName("Constructor creates instance with non-null ScanIssue")
    void testConstructor_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        assertNotNull(fix);
    }

    @Test
    @DisplayName("Constructor stores scanIssue field (reflection identity)")
    void testConstructor_storesScanIssue_functionality() throws Exception {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        Field f = ViewDetailsFix.class.getDeclaredField("scanIssue");
        f.setAccessible(true);
        assertSame(issue, f.get(fix));
    }

    @Test
    @DisplayName("getFamilyName returns expected constant")
    void testGetFamilyName_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        assertEquals(Constants.RealTimeConstants.VIEW_DETAILS_FIX_NAME, fix.getFamilyName());
    }

    @Test
    @DisplayName("getFamilyName is non-null")
    void testGetFamilyName_nonNull_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        assertNotNull(fix.getFamilyName());
    }

    @Test
    @DisplayName("getIcon returns STAR_ACTION for visibility flag")
    void testGetIcon_visibilityFlag_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        Icon icon = fix.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("getIcon returns STAR_ACTION for combined flags")
    void testGetIcon_combinedFlags_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
        Icon icon = fix.getIcon(flags);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("applyFix completes without throwing when title present")
    void testApplyFix_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix completes without throwing when title is null")
    void testApplyFix_nullTitle_functionality() {
        issue.setTitle(null);
        ViewDetailsFix fix = new ViewDetailsFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix throws NullPointerException when scanIssue is null")
    void testApplyFix_nullScanIssueThrowsNpe_functionality() {
        ViewDetailsFix fix = new ViewDetailsFix(null); // dereferences scanIssue.getTitle internally
        assertThrows(NullPointerException.class, () -> fix.applyFix(project, descriptor));
    }
}

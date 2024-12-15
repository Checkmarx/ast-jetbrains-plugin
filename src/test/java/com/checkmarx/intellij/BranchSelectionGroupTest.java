package com.checkmarx.intellij;

import com.checkmarx.intellij.tool.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.tool.window.actions.selection.ScanSelectionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class BranchSelectionGroupTest {

    @Mock
    private Project project;

    @Mock
    private ScanSelectionGroup scanSelectionGroup;

    @Mock
    private PropertiesComponent propertiesComponent;

    private Method setDefaultBranchMethod;

    private BranchSelectionGroup branchSelectionGroup;

    @BeforeEach
    public void setUp() throws NoSuchMethodException {
        MockitoAnnotations.openMocks(this);
        when(project.getService(PropertiesComponent.class)).thenReturn(propertiesComponent);
        MessageBus messageBus = mock(MessageBus.class);
        MessageBusConnection messageBusConnection = mock(MessageBusConnection.class);
        when(project.getMessageBus()).thenReturn(messageBus);
        when(messageBus.connect()).thenReturn(messageBusConnection);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        branchSelectionGroup = spy(new BranchSelectionGroup(project, scanSelectionGroup));

        setDefaultBranchMethod = BranchSelectionGroup.class.getDeclaredMethod("setDefaultBranch");
        setDefaultBranchMethod.setAccessible(true);
    }

    private void setBranches(BranchSelectionGroup branchSelectionGroup, List<String> branches) throws NoSuchFieldException, IllegalAccessException {
        Field branchesField = BranchSelectionGroup.class.getDeclaredField("branches");
        branchesField.setAccessible(true);
        branchesField.set(branchSelectionGroup, branches);
    }

    @Test
    public void testSetDefaultBranch_WhenBranches_shouldReturnNone() throws Exception {
        setBranches(branchSelectionGroup, Collections.emptyList());
        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("none", result);
    }

    @Test
    public void testSetDefaultBranch_WhenActiveBranchInList_shouldReturnActiveBranch() throws Exception {
        List<String> branches = List.of("main", "develop");
        setBranches(branchSelectionGroup, branches);

        doReturn("main").when(branchSelectionGroup).getActiveBranch();

        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("main", result);
        verify(propertiesComponent).setValue("Checkmarx.SelectedBranch", "main");
    }

    @Test
    public void testSetDefaultBranch_WhenActiveBranchNotInList_shouldReturnLocalBranchTitle() throws Exception {
        List<String> branches = List.of("main", "develop");
        setBranches(branchSelectionGroup, branches);

        doReturn(Constants.USE_LOCAL_BRANCH).when(branchSelectionGroup).getActiveBranch();

        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("main", result);
        verify(propertiesComponent).setValue("Checkmarx.SelectedBranch", Constants.USE_LOCAL_BRANCH);
    }
}
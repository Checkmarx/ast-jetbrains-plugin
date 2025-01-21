package com.checkmarx.intellij.unit.tool.window.actions.filter;

import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.FilterBaseAction;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilterBaseActionTest {

    @Mock
    private AnActionEvent mockEvent;
    @Mock
    private Application mockApplication;
    @Mock
    private MessageBus mockMessageBus;
    @Mock
    private GlobalSettingsState mockGlobalSettings;
    @Mock
    private FilterBaseAction.FilterChanged mockFilterChanged;
    @Mock
    private MessageBusConnection mockConnection;

    private TestFilterAction filterAction;
    private MockedStatic<ApplicationManager> mockedApplicationManager;
    private MockedStatic<GlobalSettingsState> mockedGlobalSettingsState;

    @BeforeEach
    void setUp() {
        mockedApplicationManager = mockStatic(ApplicationManager.class);
        mockedGlobalSettingsState = mockStatic(GlobalSettingsState.class);
        
        when(ApplicationManager.getApplication()).thenReturn(mockApplication);
        when(mockApplication.getMessageBus()).thenReturn(mockMessageBus);
        when(GlobalSettingsState.getInstance()).thenReturn(mockGlobalSettings);

        filterAction = new TestFilterAction();
    }

    @AfterEach
    void tearDown() {
        mockedApplicationManager.close();
        mockedGlobalSettingsState.close();
    }

    @Test
    void isSelected_WhenFilterInGlobalSettings_ReturnsTrue() {
        when(mockGlobalSettings.getFilters()).thenReturn(new HashSet<>());
        when(mockGlobalSettings.getFilters()).thenReturn(new HashSet<>(java.util.Arrays.asList(Severity.HIGH)));
        assertTrue(filterAction.isSelected(mockEvent));
    }

    @Test
    void isSelected_WhenFilterNotInGlobalSettings_ReturnsFalse() {
        when(mockGlobalSettings.getFilters()).thenReturn(new HashSet<>());
        when(mockGlobalSettings.getFilters()).thenReturn(new HashSet<>());
        assertFalse(filterAction.isSelected(mockEvent));
    }

    @Test
    void setSelected_WhenTrue_AddsFilterToGlobalSettings() {
        when(mockMessageBus.syncPublisher(FilterBaseAction.FILTER_CHANGED)).thenReturn(mockFilterChanged);
        HashSet<Filterable> filters = new HashSet<>();
        when(mockGlobalSettings.getFilters()).thenReturn(filters);
        filterAction.setSelected(mockEvent, true);
        verify(mockFilterChanged).filterChanged();
        assertTrue(filters.contains(Severity.HIGH));
    }

    @Test
    void setSelected_WhenFalse_RemovesFilterFromGlobalSettings() {
        when(mockGlobalSettings.getFilters()).thenReturn(new HashSet<>());
        when(mockMessageBus.syncPublisher(FilterBaseAction.FILTER_CHANGED)).thenReturn(mockFilterChanged);
        HashSet<Filterable> filters = new HashSet<>();
        filters.add(Severity.HIGH);
        when(mockGlobalSettings.getFilters()).thenReturn(filters);
        filterAction.setSelected(mockEvent, false);
        verify(mockFilterChanged).filterChanged();
        assertFalse(filters.contains(Severity.HIGH));
    }

    @Test
    void constructor_SetsCorrectPresentation() {
        assertEquals("HIGH", filterAction.getTemplatePresentation().getText());
        assertNotNull(filterAction.getTemplatePresentation().getIcon());
    }

    @Test
    void getActionUpdateThread_ReturnsEDT() {
        assertEquals(ActionUpdateThread.EDT, filterAction.getActionUpdateThread());
    }

    private static class TestFilterAction extends FilterBaseAction {
        @Override
        protected Filterable getFilterable() {
            return Severity.HIGH;
        }
    }
}
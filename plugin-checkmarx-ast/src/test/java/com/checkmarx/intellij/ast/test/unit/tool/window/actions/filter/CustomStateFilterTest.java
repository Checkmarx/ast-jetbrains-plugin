package com.checkmarx.intellij.ast.test.unit.tool.window.actions.filter;

import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.window.actions.filter.CustomStateFilter;
import com.checkmarx.intellij.ast.window.actions.filter.FilterBaseAction;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomStateFilterTest {

    private CustomStateFilter newFilter(String label, MessageBus messageBus) {
        Application app = mock(Application.class);
        when(app.getMessageBus()).thenReturn(messageBus);

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class)) {
            appManager.when(ApplicationManager::getApplication).thenReturn(app);
            return new CustomStateFilter(new CustomResultState(label, label));
        }
    }

    @Test
    void getFilterable_ReturnsCustomResultStateWithExpectedLabel() {
        MessageBus messageBus = mock(MessageBus.class);
        CustomStateFilter filter = newFilter("TO_VERIFY", messageBus);

        Filterable filterable = filter.getFilterable();
        assertTrue(filterable instanceof CustomResultState);
        assertEquals("TO_VERIFY", ((CustomResultState) filterable).getLabel());
    }

    @Test
    void isSelected_ReturnsTrueWhenMatchingLabelExists() {
        MessageBus messageBus = mock(MessageBus.class);
        CustomStateFilter filter = newFilter("URGENT", messageBus);

        Set<Filterable> filters = new LinkedHashSet<>();
        filters.add(SeverityFilter.HIGH); // non-custom candidate -> isMatchingLabel false branch
        filters.add(new CustomResultState("URGENT", "Urgent"));

        GlobalSettingsState state = mock(GlobalSettingsState.class);
        when(state.getFilters()).thenReturn(filters);

        try (MockedStatic<GlobalSettingsState> mockedState = Mockito.mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(state);
            assertTrue(filter.isSelected(mock(AnActionEvent.class)));
        }
    }

    @Test
    void isSelected_ReturnsFalseWhenMatchingLabelDoesNotExist() {
        MessageBus messageBus = mock(MessageBus.class);
        CustomStateFilter filter = newFilter("TO_VERIFY", messageBus);

        Set<Filterable> filters = new LinkedHashSet<>();
        filters.add(new CustomResultState("CONFIRMED", "Confirmed"));
        filters.add(SeverityFilter.MEDIUM);

        GlobalSettingsState state = mock(GlobalSettingsState.class);
        when(state.getFilters()).thenReturn(filters);

        try (MockedStatic<GlobalSettingsState> mockedState = Mockito.mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(state);
            assertFalse(filter.isSelected(mock(AnActionEvent.class)));
        }
    }

    @Test
    void setSelected_WhenTrueAndNotPresent_AddsFilterAndPublishesEvent() {
        MessageBus messageBus = mock(MessageBus.class);
        FilterBaseAction.FilterChanged publisher = mock(FilterBaseAction.FilterChanged.class);
        when(messageBus.syncPublisher(FilterBaseAction.FILTER_CHANGED)).thenReturn(publisher);

        CustomStateFilter filter = newFilter("TO_VERIFY", messageBus);

        Set<Filterable> filters = new LinkedHashSet<>();
        filters.add(new CustomResultState("CONFIRMED", "Confirmed"));

        GlobalSettingsState state = mock(GlobalSettingsState.class);
        when(state.getFilters()).thenReturn(filters);

        try (MockedStatic<GlobalSettingsState> mockedState = Mockito.mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(state);

            filter.setSelected(mock(AnActionEvent.class), true);

            assertTrue(filters.stream().anyMatch(f -> f instanceof CustomResultState
                    && ((CustomResultState) f).getLabel().equals("TO_VERIFY")));
            verify(publisher).filterChanged();
        }
    }

    @Test
    void setSelected_WhenFalse_RemovesMatchingLabelOnlyAndPublishesEvent() {
        MessageBus messageBus = mock(MessageBus.class);
        FilterBaseAction.FilterChanged publisher = mock(FilterBaseAction.FilterChanged.class);
        when(messageBus.syncPublisher(FilterBaseAction.FILTER_CHANGED)).thenReturn(publisher);

        CustomStateFilter filter = newFilter("TO_VERIFY", messageBus);

        Set<Filterable> filters = new LinkedHashSet<>();
        filters.add(new CustomResultState("TO_VERIFY", "To Verify"));
        filters.add(new CustomResultState("CONFIRMED", "Confirmed"));
        filters.add(SeverityFilter.LOW); // non-custom candidate should remain

        GlobalSettingsState state = mock(GlobalSettingsState.class);
        when(state.getFilters()).thenReturn(filters);

        try (MockedStatic<GlobalSettingsState> mockedState = Mockito.mockStatic(GlobalSettingsState.class)) {
            mockedState.when(GlobalSettingsState::getInstance).thenReturn(state);

            filter.setSelected(mock(AnActionEvent.class), false);

            assertFalse(filters.stream().anyMatch(f -> f instanceof CustomResultState
                    && ((CustomResultState) f).getLabel().equals("TO_VERIFY")));
            assertTrue(filters.stream().anyMatch(f -> f instanceof CustomResultState
                    && ((CustomResultState) f).getLabel().equals("CONFIRMED")));
            assertTrue(filters.contains(SeverityFilter.LOW));
            verify(publisher).filterChanged();
        }
    }
}


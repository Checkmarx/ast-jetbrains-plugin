package com.checkmarx.intellij.ast.test.unit.tool.window.actions.filter;

import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.service.StateService;
import com.checkmarx.intellij.ast.window.actions.filter.CustomStateFilter;
import com.checkmarx.intellij.ast.window.actions.filter.DynamicFilterActionGroup;
import com.checkmarx.intellij.ast.window.actions.filter.FilterBaseAction;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicAndFilterBaseActionTest {

    @Test
    void severityFilters_ConstructAndResolveSelectedState() {
        Application app = mock(Application.class);
        MessageBus messageBus = mock(MessageBus.class);
        when(app.getMessageBus()).thenReturn(messageBus);

        GlobalSettingsState globalState = mock(GlobalSettingsState.class);
        Set<Filterable> filters = new HashSet<>();
        when(globalState.getFilters()).thenReturn(filters);

        AnActionEvent event = mock(AnActionEvent.class);

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<GlobalSettingsState> globalStateMock = Mockito.mockStatic(GlobalSettingsState.class)) {
            appManager.when(ApplicationManager::getApplication).thenReturn(app);
            globalStateMock.when(GlobalSettingsState::getInstance).thenReturn(globalState);

            FilterBaseAction.CriticalFilter critical = new FilterBaseAction.CriticalFilter();
            FilterBaseAction.HighFilter high = new FilterBaseAction.HighFilter();
            FilterBaseAction.MediumFilter medium = new FilterBaseAction.MediumFilter();
            FilterBaseAction.LowFilter low = new FilterBaseAction.LowFilter();
            FilterBaseAction.InfoFilter info = new FilterBaseAction.InfoFilter();

            assertEquals(ActionUpdateThread.EDT, high.getActionUpdateThread());

            filters.add(SeverityFilter.CRITICAL);
            assertTrue(critical.isSelected(event));
            filters.remove(SeverityFilter.CRITICAL);
            assertFalse(critical.isSelected(event));

            filters.add(SeverityFilter.HIGH);
            assertTrue(high.isSelected(event));
            filters.clear();

            filters.add(SeverityFilter.MEDIUM);
            assertTrue(medium.isSelected(event));
            filters.clear();

            filters.add(SeverityFilter.LOW);
            assertTrue(low.isSelected(event));
            filters.clear();

            filters.add(SeverityFilter.INFO);
            assertTrue(info.isSelected(event));
        }
    }

    @Test
    void filterBaseAction_SetSelected_AddsRemovesAndPublishes() {
        Application app = mock(Application.class);
        MessageBus messageBus = mock(MessageBus.class);
        when(app.getMessageBus()).thenReturn(messageBus);

        FilterBaseAction.FilterChanged publisher = mock(FilterBaseAction.FilterChanged.class);
        when(messageBus.syncPublisher(FilterBaseAction.FILTER_CHANGED)).thenReturn(publisher);

        GlobalSettingsState globalState = mock(GlobalSettingsState.class);
        Set<Filterable> filters = new HashSet<>();
        when(globalState.getFilters()).thenReturn(filters);

        AnActionEvent event = mock(AnActionEvent.class);

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<GlobalSettingsState> globalStateMock = Mockito.mockStatic(GlobalSettingsState.class)) {
            appManager.when(ApplicationManager::getApplication).thenReturn(app);
            globalStateMock.when(GlobalSettingsState::getInstance).thenReturn(globalState);

            FilterBaseAction.HighFilter high = new FilterBaseAction.HighFilter();

            high.setSelected(event, true);
            high.setSelected(event, false);

            ArgumentCaptor<Set<Filterable>> filtersCaptor = ArgumentCaptor.forClass(Set.class);
            verify(globalState, times(2)).setFilters(filtersCaptor.capture());
            List<Set<Filterable>> savedSets = filtersCaptor.getAllValues();

            assertTrue(savedSets.get(0).contains(SeverityFilter.HIGH));
            assertFalse(savedSets.get(1).contains(SeverityFilter.HIGH));

            verify(publisher, times(2)).filterChanged();
        }
    }

    @Test
    void customStateFilter_UsesCustomStateConstructorPathAndLabelMatching() {
        Application app = mock(Application.class);
        MessageBus messageBus = mock(MessageBus.class);
        when(app.getMessageBus()).thenReturn(messageBus);

        GlobalSettingsState globalState = mock(GlobalSettingsState.class);
        Set<Filterable> filters = new HashSet<>();
        when(globalState.getFilters()).thenReturn(filters);

        AnActionEvent event = mock(AnActionEvent.class);

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<GlobalSettingsState> globalStateMock = Mockito.mockStatic(GlobalSettingsState.class)) {
            appManager.when(ApplicationManager::getApplication).thenReturn(app);
            globalStateMock.when(GlobalSettingsState::getInstance).thenReturn(globalState);

            CustomStateFilter custom = new CustomStateFilter(new CustomResultState("TO_VERIFY", "To Verify"));
            assertEquals("To Verify", custom.getTemplatePresentation().getText());

            filters.add(new CustomResultState("TO_VERIFY", "Different Name"));
            assertTrue(custom.isSelected(event));

            filters.clear();
            filters.add(new CustomResultState("CONFIRMED", "Confirmed"));
            assertFalse(custom.isSelected(event));
        }
    }

    @Test
    void dynamicFilterActionGroup_ReturnsActionsFromStateService() {
        Application app = mock(Application.class);
        MessageBus messageBus = mock(MessageBus.class);
        when(app.getMessageBus()).thenReturn(messageBus);

        StateService stateService = mock(StateService.class);

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<StateService> stateServiceMock = Mockito.mockStatic(StateService.class)) {
            appManager.when(ApplicationManager::getApplication).thenReturn(app);
            stateServiceMock.when(StateService::getInstance).thenReturn(stateService);

            List<CustomStateFilter> list = new ArrayList<>();
            list.add(new CustomStateFilter(new CustomResultState("TO_VERIFY", "To Verify")));
            list.add(new CustomStateFilter(new CustomResultState("CONFIRMED", "Confirmed")));

            when(stateService.getCustomStateFilters()).thenReturn(list);

            DynamicFilterActionGroup group = new DynamicFilterActionGroup();
            AnAction[] children = group.getChildren(mock(AnActionEvent.class));

            assertEquals(2, children.length);
            assertTrue(children[0] instanceof CustomStateFilter);
            assertTrue(children[1] instanceof CustomStateFilter);

            when(stateService.getCustomStateFilters()).thenReturn(List.of());
            assertEquals(0, group.getChildren(mock(AnActionEvent.class)).length);
        }
    }
}


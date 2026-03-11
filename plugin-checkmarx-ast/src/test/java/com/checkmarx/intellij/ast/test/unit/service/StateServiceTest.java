package com.checkmarx.intellij.ast.test.unit.service;

import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.service.StateService;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.checkmarx.intellij.common.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StateService singleton class.
 * Tests cover singleton behavior, state initialization, and default filters.
 * Note: Methods that call CxWrapperFactory.build() (getCustomStateFilters, getStatesNameListForSastTriage,
 * refreshCustomStateFilters) require IntelliJ Platform and are tested in integration tests.
 */
@ExtendWith(MockitoExtension.class)
class StateServiceTest {

    @Test
    void getInstance_AndStateInitialization_WorksCorrectly() {
        // Test singleton pattern
        StateService instance1 = StateService.getInstance();
        StateService instance2 = StateService.getInstance();

        assertNotNull(instance1, "getInstance should return non-null instance");
        assertSame(instance1, instance2, "getInstance should return the same singleton instance");

        // Test state initialization
        Set<CustomResultState> states = instance1.getStates();
        Set<String> defaultLabels = instance1.getDefaultLabels();

        assertNotNull(states, "States should not be null");
        assertNotNull(defaultLabels, "Default labels should not be null");
        assertEquals(8, states.size(), "Should have 8 default states");
        assertEquals(8, defaultLabels.size(), "Should have 8 default labels");

        // Verify all expected states are present
        assertTrue(defaultLabels.contains(CONFIRMED), "Should contain CONFIRMED");
        assertTrue(defaultLabels.contains(IGNORE_LABEL), "Should contain IGNORE_LABEL");
        assertTrue(defaultLabels.contains(NOT_EXPLOITABLE_LABEL), "Should contain NOT_EXPLOITABLE_LABEL");
        assertTrue(defaultLabels.contains(NOT_IGNORE_LABEL), "Should contain NOT_IGNORE_LABEL");
        assertTrue(defaultLabels.contains(PROPOSED_NOT_EXPLOITABLE_LABEL), "Should contain PROPOSED_NOT_EXPLOITABLE_LABEL");
        assertTrue(defaultLabels.contains(SCA_HIDE_DEV_TEST_DEPENDENCIES), "Should contain SCA_HIDE_DEV_TEST_DEPENDENCIES");
        assertTrue(defaultLabels.contains(TO_VERIFY), "Should contain TO_VERIFY");
        assertTrue(defaultLabels.contains(URGENT), "Should contain URGENT");

        // Verify states are sorted alphabetically
        String[] labels = states.stream().map(CustomResultState::getLabel).toArray(String[]::new);
        for (int i = 0; i < labels.length - 1; i++) {
            assertTrue(labels[i].compareTo(labels[i + 1]) <= 0,
                    "States should be sorted: " + labels[i] + " <= " + labels[i + 1]);
        }
    }


    @Test
    void getDefaultFilters_IncludesAndExcludesCorrectFilters() {
        // Arrange
        StateService service = StateService.getInstance();

        // Act
        Set<Filterable> defaultFilters = service.getDefaultFilters();

        // Assert
        assertNotNull(defaultFilters, "Default filters should not be null");
        assertTrue(defaultFilters.size() >= SeverityFilter.DEFAULT_SEVERITIES.size(),
                "Should contain at least the default severity filters");

        // Verify all severity filters are included
        for (Filterable severity : SeverityFilter.DEFAULT_SEVERITIES) {
            assertTrue(defaultFilters.contains(severity),
                    "Should contain severity filter: " + severity);
        }

        // Verify INCLUDED state filters
        boolean hasConfirmed = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(CONFIRMED));
        assertTrue(hasConfirmed, "Should contain CONFIRMED in default filters");

        boolean hasToVerify = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(TO_VERIFY));
        assertTrue(hasToVerify, "Should contain TO_VERIFY in default filters");

        boolean hasUrgent = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(URGENT));
        assertTrue(hasUrgent, "Should contain URGENT in default filters");

        boolean hasIgnore = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(IGNORE_LABEL));
        assertTrue(hasIgnore, "Should contain IGNORE_LABEL in default filters");

        boolean hasNotIgnore = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(NOT_IGNORE_LABEL));
        assertTrue(hasNotIgnore, "Should contain NOT_IGNORE_LABEL in default filters");

        // Verify EXCLUDED state filters
        boolean hasNotExploitable = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(NOT_EXPLOITABLE_LABEL));
        assertFalse(hasNotExploitable, "Should not contain NOT_EXPLOITABLE_LABEL in default filters");

        boolean hasProposedNotExploitable = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(PROPOSED_NOT_EXPLOITABLE_LABEL));
        assertFalse(hasProposedNotExploitable, "Should not contain PROPOSED_NOT_EXPLOITABLE_LABEL in default filters");

        boolean hasScaHide = defaultFilters.stream()
                .anyMatch(f -> f instanceof CustomResultState &&
                        ((CustomResultState) f).getLabel().equals(SCA_HIDE_DEV_TEST_DEPENDENCIES));
        assertFalse(hasScaHide, "Should not contain SCA_HIDE_DEV_TEST_DEPENDENCIES in default filters");
    }

    /** Helper: mock the IntelliJ Application + CxWrapperFactory so buildCustomStateFilters() can run. */
    private void withMockedInfra(MockedStatic<ApplicationManager> mockedApp,
                                 MockedStatic<CxWrapperFactory> mockedFactory) {
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        mockedApp.when(ApplicationManager::getApplication).thenReturn(mockApp);
        mockedFactory.when(CxWrapperFactory::build).thenThrow(new RuntimeException("no connection"));
    }

    @Test
    void getStatesNameListForSastTriage_ExcludesIgnoreAndNotIgnoreAndScaHide() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            List<String> triageList = service.getStatesNameListForSastTriage();

            assertNotNull(triageList);
            assertFalse(triageList.contains(IGNORE_LABEL), "Should exclude IGNORE_LABEL");
            assertFalse(triageList.contains(NOT_IGNORE_LABEL), "Should exclude NOT_IGNORE_LABEL");
            assertFalse(triageList.contains(SCA_HIDE_DEV_TEST_DEPENDENCIES), "Should exclude SCA_HIDE_DEV_TEST_DEPENDENCIES");
        }
    }

    @Test
    void getStatesNameListForSastTriage_IncludesConfirmedAndToVerify() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            List<String> triageList = service.getStatesNameListForSastTriage();

            assertTrue(triageList.contains(CONFIRMED), "Should contain CONFIRMED");
            assertTrue(triageList.contains(TO_VERIFY), "Should contain TO_VERIFY");
        }
    }

    @Test
    void getCustomStateFilters_CalledTwice_ReturnsSameCachedList() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();

            var filters1 = service.getCustomStateFilters();
            var filters2 = service.getCustomStateFilters();
            assertSame(filters1, filters2, "Second call should return same cached list");
        }
    }

    @Test
    void refreshCustomStateFilters_RebuildsList() {
        StateService service = StateService.getInstance();
        try (MockedStatic<ApplicationManager> mockedApp = mockStatic(ApplicationManager.class);
             MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {

            withMockedInfra(mockedApp, mockedFactory);
            service.refreshCustomStateFilters();
            var filters1 = service.getCustomStateFilters();

            service.refreshCustomStateFilters();
            var filters2 = service.getCustomStateFilters();

            assertNotNull(filters2);
            assertEquals(filters1.size(), filters2.size(), "Refreshed list should have same number of default filters");
        }
    }
}

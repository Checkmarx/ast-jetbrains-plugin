package com.checkmarx.intellij.ast.test.unit.service;

import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.service.StateService;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static com.checkmarx.intellij.common.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

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
}


package com.checkmarx.intellij.ast.test.unit.results;

import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomResultState class.
 * Note: valueOf() method requires IntelliJ Platform context and is tested in integration tests.
 * These unit tests cover constructors, getters, and comparison logic.
 */
@ExtendWith(MockitoExtension.class)
class CustomResultStateTest {

    @Test
    void constructors_InitializeFieldsCorrectly() {
        // Test single parameter constructor
        String label1 = "TEST_LABEL";
        CustomResultState state1 = new CustomResultState(label1);
        assertEquals(label1, state1.getLabel(), "Label should be set correctly");
        assertEquals(label1, state1.getName(), "Name should be same as label when using single-param constructor");

        // Test two parameter constructor
        String label2 = "TO_VERIFY";
        String name2 = "To Verify";
        CustomResultState state2 = new CustomResultState(label2, name2);
        assertEquals(label2, state2.getLabel(), "Label should be set correctly");
        assertEquals(name2, state2.getName(), "Name should be set correctly");
    }

    @Test
    void tooltipSupplierAndGetFilterValue_ReturnLabelValue() {
        // Arrange
        String label = "CONFIRMED";
        CustomResultState state = new CustomResultState(label);

        // Act & Assert - Test both methods that return label
        assertEquals(label, state.tooltipSupplier().get(), "tooltipSupplier should return the label value");
        assertEquals(label, state.getFilterValue(), "getFilterValue should return the label value");
    }

    @Test
    void compareTo_WithVariousScenarios_WorksCorrectly() {
        // Test lexicographic ordering
        CustomResultState stateA = new CustomResultState("AAAAAA");
        CustomResultState stateB = new CustomResultState("BBBBBB");
        CustomResultState stateC = new CustomResultState("CCCCCC");

        assertTrue(stateA.compareTo(stateB) < 0, "AAAAAA should come before BBBBBB");
        assertTrue(stateB.compareTo(stateA) > 0, "BBBBBB should come after AAAAAA");
        assertEquals(0, stateA.compareTo(new CustomResultState("AAAAAA")), "Same labels should be equal");
        assertTrue(stateA.compareTo(stateC) < 0, "AAAAAA should come before CCCCCC");
        assertTrue(stateC.compareTo(stateB) > 0, "CCCCCC should come after BBBBBB");

        // Test real-world state values
        CustomResultState urgent = new CustomResultState("URGENT");
        CustomResultState proposed = new CustomResultState("PROPOSED_NOT_EXPLOITABLE");
        CustomResultState confirmed = new CustomResultState("CONFIRMED");
        CustomResultState notExploitable = new CustomResultState("NOT_EXPLOITABLE");

        assertTrue(confirmed.compareTo(urgent) < 0, "CONFIRMED comes before URGENT");
        assertTrue(notExploitable.compareTo(proposed) < 0, "NOT_EXPLOITABLE comes before PROPOSED_NOT_EXPLOITABLE");
        assertTrue(proposed.compareTo(urgent) < 0, "PROPOSED_NOT_EXPLOITABLE comes before URGENT");
    }

    @Test
    void compareTo_WithEdgeCases_HandlesCorrectly() {
        // Test with empty strings
        CustomResultState empty1 = new CustomResultState("");
        CustomResultState empty2 = new CustomResultState("");
        CustomResultState confirmed = new CustomResultState("CONFIRMED");

        assertEquals(0, empty1.compareTo(empty2), "Empty labels should be equal");
        assertTrue(empty1.compareTo(confirmed) < 0, "Empty string comes before any text");
        assertTrue(confirmed.compareTo(empty1) > 0, "Any text comes after empty string");

        // Test case sensitivity
        CustomResultState lowercase = new CustomResultState("aaa");
        CustomResultState uppercase = new CustomResultState("AAA");
        assertTrue(uppercase.compareTo(lowercase) < 0, "Uppercase comes before lowercase in lexicographic ordering");

        // Test with special characters
        CustomResultState withUnderscore = new CustomResultState("TEST_STATE");
        CustomResultState withoutUnderscore = new CustomResultState("TESTSTATE");
        assertNotEquals(0, withUnderscore.compareTo(withoutUnderscore), "Different strings should not be equal");
    }

    @Test
    void valueOf_WhenLabelFoundInFilters_ReturnsExistingInstance() {
        CustomResultState existing = new CustomResultState("CONFIRMED", "Confirmed");
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getFilters()).thenReturn(Set.of(existing));

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            CustomResultState result = CustomResultState.valueOf("CONFIRMED");
            assertSame(existing, result);
            assertEquals("Confirmed", result.getName());
        }
    }

    @Test
    void valueOf_WhenLabelNotFoundInFilters_ReturnsNewInstance() {
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getFilters()).thenReturn(Set.of());

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            CustomResultState result = CustomResultState.valueOf("URGENT");
            assertNotNull(result);
            assertEquals("URGENT", result.getLabel());
        }
    }

    @Test
    void valueOf_WhenFiltersContainNonCustomResultState_SkipsAndReturnsFallback() {
        Filterable nonCustom = mock(Filterable.class);
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getFilters()).thenReturn(Set.of(nonCustom));

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            CustomResultState result = CustomResultState.valueOf("ANY_LABEL");
            assertNotNull(result);
            assertEquals("ANY_LABEL", result.getLabel());
        }
    }
}


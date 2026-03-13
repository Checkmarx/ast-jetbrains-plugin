package com.checkmarx.intellij.ast.test.unit.settings.global;

import com.checkmarx.intellij.common.settings.FilterProviderRegistry;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.intellij.openapi.application.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsStateTest {

    @Mock
    private Application mockApplication;

    private GlobalSettingsState globalSettingsState;

    @BeforeEach
    void setUp() {
        FilterProviderRegistry.getInstance().registerProvider(null);
        globalSettingsState = new GlobalSettingsState();
    }

    @Test
    void getState_ReturnsSelf() {
        // Act
        GlobalSettingsState result = globalSettingsState.getState();

        // Assert
        assertSame(globalSettingsState, result);
    }

    @Test
    void loadState_CopiesStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);
        Set<Filterable> newFilters = new HashSet<>();
        newFilters.add(SeverityFilter.HIGH);
        newState.setFilters(newFilters);

        // Act
        globalSettingsState.loadState(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
        assertEquals(newFilters, globalSettingsState.getFilters());
    }

    @Test
    void apply_LoadsStateCorrectly() {
        // Arrange
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setAdditionalParameters("--test-param");
        newState.setAsca(true);

        // Act
        globalSettingsState.apply(newState);

        // Assert
        assertEquals("--test-param", globalSettingsState.getAdditionalParameters());
        assertTrue(globalSettingsState.isAsca());
    }


    @Test
    void constructor_InitializesDefaultValues() {
        assertEquals("", globalSettingsState.getAdditionalParameters());
        assertFalse(globalSettingsState.isAsca());

        // Verify filters contain only the expected severity filters
        // Note: SeverityFilter.DEFAULT_SEVERITIES contains [LOW, MEDIUM, CRITICAL, HIGH, MALICIOUS]
        Set<Filterable> filters = globalSettingsState.getFilters();
        assertNotNull(filters, "Filters should not be null");
        assertEquals(5, filters.size(), "Should have 5 default severity filters");
        assertTrue(filters.contains(SeverityFilter.LOW), "Should contain LOW");
        assertTrue(filters.contains(SeverityFilter.MEDIUM), "Should contain MEDIUM");
        assertTrue(filters.contains(SeverityFilter.CRITICAL), "Should contain CRITICAL");
        assertTrue(filters.contains(SeverityFilter.HIGH), "Should contain HIGH");
        assertTrue(filters.contains(SeverityFilter.MALICIOUS), "Should contain MALICIOUS");
    }
} 
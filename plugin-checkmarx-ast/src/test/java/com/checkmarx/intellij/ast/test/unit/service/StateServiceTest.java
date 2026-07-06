package com.checkmarx.intellij.ast.test.unit.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.service.StateService;
import com.checkmarx.intellij.ast.window.actions.filter.CustomStateFilter;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class StateServiceTest {

    @Test
    void getInstance_AlwaysReturnsSameInstance() {
        StateService instance1 = StateService.getInstance();
        StateService instance2 = StateService.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void getStates_ReturnsNonEmptySet() {
        Set<CustomResultState> states = StateService.getInstance().getStates();
        assertFalse(states.isEmpty());
        assertTrue(states.size() >= 8);
    }

    @Test
    void getCustomStateFilters_ReturnsNonEmptyList() {
        List<CustomStateFilter> filters = StateService.getInstance().getCustomStateFilters();
        assertNotNull(filters);
        assertFalse(filters.isEmpty());
    }

    @Test
    void resolveFilterByValue_WithSeverityValue_ReturnsSeverityFilter() {
        var resolved = StateService.getInstance().resolveFilterByValue("CRITICAL");
        assertTrue(resolved.isPresent());
        assertInstanceOf(SeverityFilter.class, resolved.get());
        assertEquals("CRITICAL", resolved.get().getFilterValue());
    }
}
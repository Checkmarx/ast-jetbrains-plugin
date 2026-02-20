package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.intellij.ast.window.actions.group.by.GroupByActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupByActionGroupTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private Presentation mockPresentation;

    private GroupByActionGroup actionGroup;

    @BeforeEach
    void setUp() {
        actionGroup = new GroupByActionGroup();
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);
    }

    @Test
    void update_EnablesPresentation() {
        // Act
        actionGroup.update(mockEvent);

        // Assert
        verify(mockPresentation).setEnabled(true);
    }
} 
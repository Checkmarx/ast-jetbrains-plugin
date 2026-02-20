package com.checkmarx.intellij.ast.test.unit.inspections;

import com.checkmarx.intellij.ast.inspections.CxInspection;
import com.checkmarx.intellij.ast.inspections.CxVisitor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CxInspectionTest {

    @Mock
    private ProblemsHolder mockHolder;

    private CxInspection cxInspection;

    @BeforeEach
    void setUp() {
        cxInspection = new CxInspection();
    }

    @Test
    void buildVisitor_WhenNotOnTheFly_ReturnsCxVisitor() {
        // Act
        PsiElementVisitor visitor = cxInspection.buildVisitor(mockHolder, false);

        // Assert
        assertTrue(visitor instanceof CxVisitor);
    }

    @Test
    void buildVisitor_WhenOnTheFlyAndCxDevTrue_ReturnsDummyVisitor() {
        // Arrange
        System.setProperty("CxDev", "true");

        try {
            // Act
            PsiElementVisitor visitor = cxInspection.buildVisitor(mockHolder, true);

            // Assert
            assertFalse(visitor instanceof CxVisitor);
        } finally {
            System.clearProperty("CxDev");
        }
    }

    @Test
    void buildVisitor_WhenOnTheFlyAndCxDevFalse_ReturnsCxVisitor() {
        // Arrange
        System.setProperty("CxDev", "false");

        try {
            // Act
            PsiElementVisitor visitor = cxInspection.buildVisitor(mockHolder, true);

            // Assert
            assertTrue(visitor instanceof CxVisitor);
        } finally {
            System.clearProperty("CxDev");
        }
    }
} 
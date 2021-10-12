package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestProject extends BaseTest {

    @Test
    public void testGetList() {
        Project project = getEnvProject();
        List<com.checkmarx.ast.project.Project> projects
                = Assertions.assertDoesNotThrow(com.checkmarx.intellij.commands.Project::getList);
        Assertions.assertTrue(projects.size() > 0);
        Assertions.assertEquals(1, projects.stream()
                                           .filter(p -> p.getName().equals(project.getName())).count());
    }
}

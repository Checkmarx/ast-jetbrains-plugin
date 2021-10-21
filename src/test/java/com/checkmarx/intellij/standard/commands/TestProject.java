package com.checkmarx.intellij.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.Environment;
import com.checkmarx.intellij.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

public class TestProject extends BaseTest {

    @Test
    public void testGetList() {
        Project project = getEnvProject();
        List<com.checkmarx.ast.project.Project> projects
                = Assertions.assertDoesNotThrow(com.checkmarx.intellij.commands.Project::getList);
        Assertions.assertTrue(projects.size() > 0);
        Assertions.assertTrue(projects.size() <= 10000);
        Assertions.assertEquals(1, projects.stream()
                                           .filter(p -> p.getName().equals(project.getName())).count());
    }

    @Test
    public void testGetBranches() {
        Project project = getEnvProject();
        List<String> branches = Assertions.assertDoesNotThrow(() -> com.checkmarx.intellij.commands.Project.getBranches(
                UUID.fromString(project.getID())));
        Assertions.assertTrue(branches.size() >= 1);
        Assertions.assertTrue(branches.contains(Environment.BRANCH_NAME));
    }
}

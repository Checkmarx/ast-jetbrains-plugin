package com.checkmarx.intellij.ast.test.integration.standard.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.ast.test.integration.Environment;
import com.checkmarx.intellij.ast.test.integration.standard.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

public class TestProject extends BaseTest {

    @Test
    public void testGetList() {
        Project project = getEnvProject();
        List<Project> projects
                = Assertions.assertDoesNotThrow(com.checkmarx.intellij.commands.Project::getList);
        Assertions.assertTrue(projects.size() > 0);
        Assertions.assertTrue(projects.size() <= 10000);
        Assertions.assertEquals(1, projects.stream()
                                           .filter(p -> p.getName().equals(project.getName())).count());
    }

    @Test
    public void testGetBranches_WithNotSCMProject_shouldIgnoreLocalBranchValue() {
        Project project = getEnvProject();
        List<String> branches = Assertions.assertDoesNotThrow(() -> com.checkmarx.intellij.commands.Project.getBranches(
                UUID.fromString(project.getId()), false));
        Assertions.assertTrue(branches.size() >= 1, branches.toString());
        Assertions.assertTrue(branches.contains(Environment.BRANCH_NAME),
                              String.format("Branch: %s Branch List: %s", Environment.BRANCH_NAME, branches));
    }

    @Test
    public void testGetBranches_WithSCMProject_shouldContainLocalBranchValue() {
        Project project = getEnvProject();
        List<String> branches = Assertions.assertDoesNotThrow(() -> com.checkmarx.intellij.commands.Project.getBranches(
                UUID.fromString(project.getId()), true));
        Assertions.assertTrue(branches.size() >= 2, branches.toString());
        Assertions.assertTrue(branches.get(0).equals(Constants.USE_LOCAL_BRANCH),
                String.format("Branch: %s Branch List: %s", Constants.USE_LOCAL_BRANCH, branches));
    }
}

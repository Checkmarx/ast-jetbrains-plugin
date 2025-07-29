package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SearchTextField;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ui.SearchTextField;
import com.intellij.openapi.actionSystem.Presentation;


import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Action group for selecting a project in the UI.
 */
public class ProjectSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(ProjectSelectionGroup.class);

    private final ResetSelectionAction resetSelectionAction;
    private final BranchSelectionGroup branchSelectionGroup;
    private final ScanSelectionGroup scanSelectionGroup;
    private final Map<String, com.checkmarx.ast.project.Project> byId = new HashMap<>();

    public ProjectSelectionGroup(@NotNull Project project,
                                 @NotNull BranchSelectionGroup branchSelectionGroup,
                                 @NotNull ScanSelectionGroup scanSelectionGroup,
                                 @Nullable ResetSelectionAction resetSelectionAction) {
        super(project);
        this.resetSelectionAction = resetSelectionAction;
        this.branchSelectionGroup = branchSelectionGroup;
        this.scanSelectionGroup = scanSelectionGroup;
        populate(true);
    }

    @Override
    protected void clear() {
        propertiesComponent.setValue(Constants.SELECTED_PROJECT_PROPERTY, null);
        removeAll();
    }

    @Override
    protected void override(Scan scan) {
        select(byId.get(scan.getProjectId()));
        branchSelectionGroup.override(scan);
    }

    /**
     * Remove all children and repopulate by getting a project list from the wrapper
     * Auto-selects the previously selected project or, if none is stored,
     * tries to inherit the project name from the IDE.
     */
    void refresh() {
        removeAll();
        byId.clear();
        populate(false);
    }

    private void populate(boolean inherit) {
        setEnabled(false);
        removeAll();
        byId.clear();

        // Add search action first
        add(new SearchAction());
        CompletableFuture.supplyAsync((Supplier<List<com.checkmarx.ast.project.Project>>) () -> {
            try {
                return com.checkmarx.intellij.commands.Project.getList();
            } catch (Exception e) {
                LOGGER.warn(e);
            }
            return Collections.emptyList();
        }).thenAccept((projectList) -> ApplicationManager.getApplication().invokeLater(() -> {
            String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
            for (com.checkmarx.ast.project.Project p : projectList) {
                byId.put(p.getId(), p);
            }

            refreshProjectListWithFilter(""); // Initially show all

            updateProjectListUI(projectList);

            for (com.checkmarx.ast.project.Project p : projectList) {
                if (inherit && storedProject == null && matchProject(p)) {
                    propertiesComponent.setValue(Constants.SELECTED_PROJECT_PROPERTY, p.getName());
                    refreshBranchGroup(p, true);
                } else if (p.getName().equals(storedProject)) {
                    refreshBranchGroup(p, false);
                }
            }
            setEnabled(true);
            if (resetSelectionAction != null) {
                resetSelectionAction.setEnabled(true);
            }
            if (!inherit) {
                branchSelectionGroup.setEnabled(true);
                scanSelectionGroup.setEnabled(true);
            }
            refreshPanel(project);
        }));
    }

    @Override
    @NotNull
    protected String getTitle() {
        if (getChildrenCount() == 0) {
            return Bundle.message(Resource.PROJECT_SELECT_PREFIX) + ": " + (isEnabled() ? NONE_SELECTED : "...");
        }
        String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
        return Bundle.message(Resource.PROJECT_SELECT_PREFIX)
                + ": "
                + (StringUtils.isBlank(storedProject) ? NONE_SELECTED : storedProject);
    }

    /**
     * Selects a project. This implies:
     * - Storing the selected project in the IDE state;
     * - Refreshing the scan selection for the selected project;
     * - Refreshing the tool window panel to resize the components for the selected project name
     *
     * @param p selected project
     */
    private void select(com.checkmarx.ast.project.Project p) {
        propertiesComponent.setValue(Constants.SELECTED_PROJECT_PROPERTY, p.getName());
        branchSelectionGroup.clear();
        refreshBranchGroup(p, false);
    }

    private boolean matchProject(com.checkmarx.ast.project.Project astProject) {
        return astProject.getName().equals(project.getName()) ||
                (Utils.getRootRepository(project) != null && Objects.requireNonNull(Utils.getRootRepository(project)).getPresentableUrl().endsWith(astProject.getName()));
    }

    /**
     * Repopulate the scan selection according to the given project
     *
     * @param p project
     */
    private void refreshBranchGroup(com.checkmarx.ast.project.Project p, boolean inherit) {
        branchSelectionGroup.refresh(p.getId(), inherit);
        refreshPanel(project);
    }

    /**
     * Action performed when a project is selected
     */
    private class Action extends AnAction implements DumbAware {

        private final com.checkmarx.ast.project.Project project;
        public Action(com.checkmarx.ast.project.Project project) {
            super(project.getName());
            getTemplatePresentation().setText(project::getName, false);
            this.project = project;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            select(project);
        }
    }

    private class SearchAction extends AnAction implements DumbAware {

        public SearchAction() {
            super("🔍 Search Projects");
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            String filterText = com.intellij.openapi.ui.Messages.showInputDialog(
                    project,
                    "Enter project name to search:",
                    "Search Projects",
                    null
            );
            if (filterText != null) {
                refreshProjectListWithFilter(filterText.trim().toLowerCase());
            }
        }
    }
    private void refreshProjectListWithFilter(String filterText) {
        // Keep search action on top
        removeAll();
        add(new SearchAction());
        String lowercaseFilter = filterText.toLowerCase();
        // Filter the map of projects
        List<com.checkmarx.ast.project.Project> filteredProjects = byId.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(lowercaseFilter))
                .collect(Collectors.toList());

        if (filteredProjects.isEmpty()) {
            // No match found, try fetching from server
            try {
                updateProjectListUI(com.checkmarx.intellij.commands.Project.getList(lowercaseFilter));
            } catch (Exception e) {
                LOGGER.warn(e);
            }
        } else {
            // Update UI actions
            updateProjectListUI(filteredProjects);
        }
        refreshPanel(project);
    }

    private void updateProjectListUI(List<com.checkmarx.ast.project.Project> projects) {

        for (com.checkmarx.ast.project.Project project : projects) {
            add(new Action(project));
        }

    }

}





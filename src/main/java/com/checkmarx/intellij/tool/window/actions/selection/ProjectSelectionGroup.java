package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Action group for selecting a project in the UI.
 */
public class ProjectSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(ProjectSelectionGroup.class);
    private final ScanSelectionGroup scanSelectionGroup;

    public ProjectSelectionGroup(@NotNull Project project,
                                 ScanSelectionGroup scanSelectionGroup) {
        super(project);
        this.scanSelectionGroup = scanSelectionGroup;
        refresh();
    }

    /**
     * Remove all children and repopulate by getting a project list from the wrapper
     * Auto-selects the previously selected project or, if none is stored,
     * tries to inherit the project name from the IDE.
     */
    private void refresh() {
        removeAll();
        CompletableFuture.supplyAsync((Supplier<List<com.checkmarx.ast.project.Project>>) () -> {
            try {
                return com.checkmarx.intellij.commands.Project.getList();
            } catch (Exception e) {
                LOGGER.warnInProduction(e);
            }
            return Collections.emptyList();
        }).thenAccept((projectList) -> {
            if (CollectionUtils.isNotEmpty(projectList)) {
                ApplicationManager.getApplication()
                                  .invokeLater(() -> {
                                      String storedProject
                                              = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
                                      for (com.checkmarx.ast.project.Project p : projectList) {
                                          add(buildProjectSelectionAction(p));
                                          if ((storedProject == null
                                               && p.getName().equals(project.getName())) || p.getName()
                                                                                             .equals(storedProject)) {
                                              select(p);
                                          }
                                      }
                                  });
            }
        });
    }

    @Override
    @NotNull
    protected String getTitle() {
        if (getChildrenCount() == 0) {
            return Bundle.message(Resource.PROJECT_SELECT_PREFIX) + ": ...";
        }
        String storedProject = propertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY);
        return Bundle.message(Resource.PROJECT_SELECT_PREFIX)
               + ": "
               + (StringUtils.isBlank(storedProject) ? Bundle.message(Resource.NONE_SELECTED) : storedProject);
    }

    /**
     * Builds a sub-action for selecting a project.
     *
     * @param p project
     * @return action for selecting the given project
     */
    @NotNull
    private AnAction buildProjectSelectionAction(com.checkmarx.ast.project.Project p) {
        return new AnAction(p.getName()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                select(p);
            }
        };
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
        scanSelectionGroup.refresh(p.getID());
        Optional.ofNullable(getCxToolWindowPanel(project)).ifPresent(CxToolWindowPanel::refreshPanel);
    }
}

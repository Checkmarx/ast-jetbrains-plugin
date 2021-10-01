package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ProjectSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(ProjectSelectionGroup.class);

    public ProjectSelectionGroup(@NotNull Project project,
                                 ScanSelectionGroup scanSelectionGroup) {
        super(project);
        CompletableFuture.supplyAsync((Supplier<List<com.checkmarx.ast.project.Project>>) () -> {
            try {
                return CxWrapperFactory.build().projectList("limit=10000");
            } catch (IOException | URISyntaxException | InterruptedException | CxConfig.InvalidCLIConfigException | CxException e) {
                LOGGER.warnInProduction(e);
                return Collections.emptyList();
            }
        }).thenAccept((List<com.checkmarx.ast.project.Project> projectList) -> ApplicationManager.getApplication().invokeLater(() -> {
            for (com.checkmarx.ast.project.Project p : projectList) {
                add(new BaseSelectionAction(p.getName(), getValueProperty()) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        super.actionPerformed(e);
                        scanSelectionGroup.refresh(p.getID());
                    }
                });
            }
        }));
    }

    @Override
    protected String defaultValue() {
        return project.getName();
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_PROJECT_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.PROJECT_SELECT_PREFIX;
    }
}

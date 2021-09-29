package com.checkmarx.intellij.tool.window.actions.selection;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectSelectionGroup extends BaseSelectionGroup {

    private final PropertiesComponent propertiesComponent;

    public ProjectSelectionGroup(@NotNull Project project) {
        super(project);
        propertiesComponent = PropertiesComponent.getInstance(project);
        String selectedProject = propertiesComponent.getValue("Checkmarx.SelectedProject");
        if (selectedProject == null) {
            selectedProject = project.getName();
            propertiesComponent.setValue("Checkmarx.SelectedProject", selectedProject);
        }
        getTemplatePresentation().setText(() -> getPrefix()
                                                + propertiesComponent.getValue("Checkmarx.SelectedProject"));
        addAll(makeChild(project.getName()),
               makeChild("xs"),
               makeChild("BIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIG"),
               makeChild("NormalLength"));
    }

    @Override
    protected String getPrefix() {
        return "Project: ";
    }

    @NotNull
    private ProjectSelectionAction makeChild(String name) {
        return new ProjectSelectionAction(name);
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return false;
    }
}

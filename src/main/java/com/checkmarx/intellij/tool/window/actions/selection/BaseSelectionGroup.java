package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for the selection groups, performing basic and common configurations
 */
public abstract class BaseSelectionGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    protected final Project project;
    protected final PropertiesComponent propertiesComponent;

    public BaseSelectionGroup(Project project) {
        super();
        this.project = project;
        this.propertiesComponent = PropertiesComponent.getInstance(project);
        getTemplatePresentation().setText(this::getTitle);
        setPopup(true);
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return false;
    }

    /**
     * This method is called wrapped in a {@link java.util.function.Supplier} at each drawing tick.
     * Should return the title for the current state, as a state machine.
     *
     * @return title for the selection group
     */
    @NotNull
    protected abstract String getTitle();
}

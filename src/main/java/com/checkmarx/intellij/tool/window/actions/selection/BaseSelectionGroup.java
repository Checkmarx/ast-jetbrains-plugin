package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class BaseSelectionGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    protected final Project project;
    private final PropertiesComponent propertiesComponent;

    public BaseSelectionGroup(Project project) {
        super();
        this.project = project;
        this.propertiesComponent = PropertiesComponent.getInstance(project);
        if (propertiesComponent.getValue(getValueProperty()) == null) {
            propertiesComponent.setValue(getValueProperty(), defaultValue());
        }
        setPopup(true);
        getTemplatePresentation().setText(() -> getPrefix() + propertiesComponent
                .getValue(getValueProperty()));
    }

    protected abstract Resource getPrefixResource();

    protected abstract String getValueProperty();

    protected String defaultValue() {
        return Bundle.message(Resource.NONE_SELECTED);
    }

    protected final String getPrefix() {
        return Bundle.message(getPrefixResource()) + ": ";
    }

    protected final void addChild(@NotNull String name) {
        add(new SelectionAction(name, getValueProperty()));
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

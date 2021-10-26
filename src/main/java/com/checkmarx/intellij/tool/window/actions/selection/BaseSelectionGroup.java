package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.EmptyIcon;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for the selection groups, performing basic and common configurations
 */
public abstract class BaseSelectionGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    protected static final String NONE_SELECTED = Bundle.message(Resource.NONE_SELECTED);

    protected final Project project;
    protected final PropertiesComponent propertiesComponent;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PACKAGE)
    private boolean enabled = true;

    public BaseSelectionGroup(Project project) {
        super();
        this.project = project;
        this.propertiesComponent = PropertiesComponent.getInstance(project);
        getTemplatePresentation().setText(this::getTitle);
        getTemplatePresentation().setIcon(EmptyIcon.ICON_16);
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
     * {@inheritDoc}
     * <p>
     * Disables the selection if get results is in progress.
     * Sets icon according to refresh if the selection is disabled and a get operation is in progress.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation()
         .setDisabledIcon(enabled || getChildrenCount() > 0 ? EmptyIcon.ICON_16 : AllIcons.General.InlineRefresh);
        e.getPresentation().setEnabled(enabled);
    }

    /**
     * This method is called wrapped in a {@link java.util.function.Supplier} at each drawing tick.
     * Should return the title for the current state, as a state machine.
     *
     * @return title for the selection group
     */
    @NotNull
    protected abstract String getTitle();

    /**
     * Method for clearing the state of the selection group.
     */
    protected abstract void clear();

    /**
     * Override the selection with a scan set in the scan id field
     *
     * @param scan overriding scan
     */
    abstract void override(Scan scan);

    @Nullable
    protected final Repository getRootRepository() {
        List<Repository> repositories = VcsRepositoryManager.getInstance(project)
                                                            .getRepositories()
                                                            .stream()
                                                            .sorted(Comparator.comparing(r -> r.getRoot()
                                                                                               .toNioPath()))
                                                            .collect(Collectors.toUnmodifiableList());
        Repository repository = null;
        if (CollectionUtils.isNotEmpty(repositories)) {
            repository = repositories.get(0);
            for (int i = 1; i < repositories.size(); i++) {
                if (!repositories.get(i).getRoot().toNioPath().startsWith(repository.getRoot().toNioPath())) {
                    repository = null;
                    break;
                }
            }
        }
        return repository;
    }
}

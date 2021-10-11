package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Action group for selecting a scan in the UI.
 */
public class ScanSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(ScanSelectionGroup.class);

    private static final DateTimeFormatter sourceFormat = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter prettyFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final String scanFormat = "%s    %s";

    public ScanSelectionGroup(@NotNull Project project) {
        super(project);
        String storedValue = propertiesComponent.getValue(Constants.SELECTED_SCAN_PROPERTY);
        if (StringUtils.isNotBlank(storedValue)) {
            ApplicationManager.getApplication().invokeLater(() -> Optional.ofNullable(getCxToolWindowPanel(project))
                                                                          .ifPresent(cxToolWindowPanel -> cxToolWindowPanel.selectScan(
                                                                                  unFormatScan(storedValue))));
        }
    }

    @Override
    protected void clear() {
        propertiesComponent.setValue(Constants.SELECTED_SCAN_PROPERTY, null);
        removeAll();
    }

    @Override
    void override(com.checkmarx.ast.scan.Scan scan) {
        propertiesComponent.setValue(Constants.SELECTED_SCAN_PROPERTY, formatScan(scan));
    }

    /**
     * Remove all children and repopulate by getting a project's list of scans.
     *
     * @param projectId selected project
     */
    void refresh(String projectId) {
        setEnabled(false);
        removeAll();
        CompletableFuture.supplyAsync((Supplier<List<com.checkmarx.ast.scan.Scan>>) () -> {
            try {
                return StringUtils.isBlank(projectId)
                       ? Scan.getList()
                       : Scan.getList(projectId);
            } catch (IOException | URISyntaxException | InterruptedException | CxConfig.InvalidCLIConfigException | CxException e) {
                LOGGER.warnInProduction(e);
                return Collections.emptyList();
            }
        }).thenAccept((List<com.checkmarx.ast.scan.Scan> scans) -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (CollectionUtils.isNotEmpty(scans)) {
                    for (com.checkmarx.ast.scan.Scan scan : scans) {
                        add(new Action(scan.getID(), formatScan(scan)));
                    }
                }
                setEnabled(true);
                refreshPanel(project);
            });
        });
    }

    @Override
    @NotNull
    protected String getTitle() {
        if (getChildrenCount() == 0) {
            return Bundle.message(Resource.SCAN_SELECT_PREFIX) + ": " + (isEnabled() ? NONE_SELECTED : "...");
        }
        String storedScan = propertiesComponent.getValue(Constants.SELECTED_SCAN_PROPERTY);
        return Bundle.message(Resource.SCAN_SELECT_PREFIX) + ": " + (StringUtils.isBlank(storedScan)
                                                                     ? NONE_SELECTED
                                                                     : storedScan);
    }

    /**
     * Selects a scan. This implies:
     * - Storing the selected scan in the IDE state;
     * - Triggering redrawing of the panel with the new scan id
     *
     * @param scanId        selected scan's id
     * @param formattedScan scan formatted for showing in the label
     */
    private void select(String scanId, String formattedScan) {
        propertiesComponent.setValue(Constants.SELECTED_SCAN_PROPERTY, formattedScan);
        Optional<CxToolWindowPanel> toolWindowPanel = Optional.ofNullable(getCxToolWindowPanel(project));
        toolWindowPanel.ifPresent(cxToolWindowPanel -> cxToolWindowPanel.selectScan(scanId));
        refreshPanel(project);
    }

    /**
     * Formats a scan for displaying in the list
     *
     * @param scan scan to format
     * @return formatted string for scan, see {@link ScanSelectionGroup#scanFormat}
     */
    @NotNull
    private String formatScan(com.checkmarx.ast.scan.Scan scan) {
        return String.format(scanFormat,
                             LocalDateTime.parse(scan.getCreatedAt(), sourceFormat).format(prettyFormat),
                             scan.getID());
    }

    /**
     * Gets the scan id from a formatted string, reversing {@link ScanSelectionGroup#formatScan(com.checkmarx.ast.scan.Scan)}.
     *
     * @param formattedScan formatted scan string
     * @return scan id
     */
    @NotNull
    private String unFormatScan(@NotNull String formattedScan) {
        String[] split = formattedScan.split(" ");
        return split[split.length - 1];
    }

    /**
     * Action performed when a scan is selected
     */
    private class Action extends AnAction implements DumbAware {

        private final String name;
        private final String scanId;

        public Action(String scanId, String name) {
            super(name);
            this.name = name;
            this.scanId = scanId;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            select(scanId, name);
        }
    }
}

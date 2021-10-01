package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class ScanSelectionGroup extends BaseSelectionGroup {

    private static final Logger LOGGER = Utils.getLogger(ScanSelectionGroup.class);

    public ScanSelectionGroup(@NotNull Project project) {
        super(project);
        refresh(project.getName());
    }

    public void refresh(String p) {
        removeAll();
        CompletableFuture.supplyAsync((Supplier<List<String>>) () -> {
            try {
                return StringUtils.isBlank(p) ? Scan.getList() : Scan.getList(p);
            } catch (IOException | URISyntaxException | InterruptedException | CxConfig.InvalidCLIConfigException | CxException e) {
                LOGGER.warnInProduction(e);
                return Collections.emptyList();
            }
        }).thenAccept((List<String> scanList) -> ApplicationManager.getApplication().invokeLater(() -> {
            for (String scanId : scanList) {
                addChild(scanId);
            }
        }));
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_SCAN_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.SCAN_SELECT_PREFIX;
    }
}

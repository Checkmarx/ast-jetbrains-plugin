package com.checkmarx.intellij.tool.window.results.tree.nodes;

import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultDataNode;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.components.PaneUtils;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Results tree node.
 * Title of the node in the tree is set in super call.
 * buildDetailsPanel builds a panel with the result's details to show in the details panel.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ResultNode extends DefaultMutableTreeNode {

    private final String label;
    private final CxResult result;
    private final Project project;

    private final List<CxResultDataNode> nodes;
    private final List<String> packageData;

    /**
     * Set node title and store the associated result
     *
     * @param result  result for this node
     * @param project context project
     */
    public ResultNode(CxResult result, Project project) {
        super();
        this.result = result;
        this.project = project;
        this.nodes = Optional.ofNullable(this.result.getData().getNodes()).orElse(Collections.emptyList());
        this.packageData = Collections.singletonList("Issue");

        String labelBuilder = (result.getData().getQueryName() != null
                               ? result.getData().getQueryName()
                               : result.getId());
        int nodeCount = nodes.size();
        if (nodeCount > 0) {
            labelBuilder += " ("
                            + new File(result.getData()
                                             .getNodes()
                                             .get(nodeCount - 1)
                                             .getFileName()).getName()
                            + ")";
        }
        this.label = labelBuilder;

        setUserObject(this.label);
    }

    public Icon getIcon() {
        switch (getResult().getSeverity()) {
            case "INFO":
                return AllIcons.General.Note;
            case "LOW":
                return AllIcons.General.Information;
            case "MEDIUM":
                return AllIcons.General.Warning;
            case "HIGH":
                return AllIcons.General.Error;
            default:
                return EmptyIcon.ICON_0;
        }
    }

    /**
     * @return panel with result details
     */
    public JPanel buildResultPanel() {
        JPanel details = buildDetailsPanel(result);
        JPanel secondPanel = JBUI.Panels.simplePanel();

        if (nodes.size() > 0) {
            secondPanel = buildAttackVectorPanel(project, nodes);
        } else if (packageData.size() > 0) {
            secondPanel = buildPackageDataPanel(packageData);
        }

        OnePixelSplitter splitter = new OnePixelSplitter();
        splitter.setFirstComponent(PaneUtils.inVerticalScrollPane(details));
        splitter.setSecondComponent(PaneUtils.inVerticalScrollPane(secondPanel));

        return JBUI.Panels.simplePanel(splitter);
    }

    @NotNull
    private static JPanel buildDetailsPanel(CxResult result) {
        JPanel details = new JPanel(new MigLayout("fillx"));

        details.add(boldLabel(Bundle.message(Resource.TYPE)), "span, wrap");
        details.add(new JBLabel(result.getType().toUpperCase()), "span, wrap, gapbottom 5");

        details.add(boldLabel(Bundle.message(Resource.SEVERITY)), "span, wrap");
        details.add(new JBLabel(result.getSeverity()), "span, wrap, gapbottom 5");

        details.add(boldLabel(Bundle.message(Resource.STATE)), "span, wrap");
        details.add(new JBLabel(result.getState()), "span, wrap, gapbottom 5");

        details.add(boldLabel(Bundle.message(Resource.STATUS)), "span, wrap");
        details.add(new JBLabel(result.getStatus()), "span, wrap, gapbottom 5");

        details.add(boldLabel(Bundle.message(Resource.DESCRIPTION)), "span, wrap");
        String description = result.getData().getDescription();
        if (StringUtils.isBlank(description)) {
            description = "Description placeholder";
        }
        // wrapping the description in html tags auto wraps the text when it reaches the parent component size
        details.add(new JBLabel(String.format("<html>%s</html>", description)), "wrap, gapbottom 5");
        return details;
    }

    @NotNull
    private static JPanel buildAttackVectorPanel(Project project, List<CxResultDataNode> nodes) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(boldLabel(Bundle.message(Resource.NODES)), "span, wrap");
        for (int i = 0; i < nodes.size(); i++) {
            CxResultDataNode node = nodes.get(i);
            String label = String.format(Constants.NODE_FORMAT,
                                         i + 1,
                                         node.getFileName(),
                                         node.getLine(),
                                         node.getName());
            panel.add(new CxLinkLabel(label, mouseEvent -> navigate(project, node)), "span, wrap");
        }
        return panel;
    }

    private static JPanel buildPackageDataPanel(List<String> packageData) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(boldLabel(Bundle.message(Resource.PACKAGE_DATA)), "span, wrap");
        for (String pkg : packageData) {
            panel.add(new JBLabel(String.format("[%s]: ", pkg)), "split 2, span");
            panel.add(CxLinkLabel.buildDocLinkLabel("https://checkmarx.com", "https://checkmarx.com"), "growx, wrap");
        }
        return panel;
    }

    private static JBLabel boldLabel(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        Font font = label.getFont();
        Font bold = new Font(font.getFontName(), Font.BOLD, FontSize.MEDIUM.getSize());
        label.setFont(bold);
        return label;
    }

    private static void navigate(Project project, CxResultDataNode node) {
        String fileName = node.getFileName();
        Utils.runAsyncReadAction(() -> {
            VirtualFile file = FilenameIndex.getVirtualFilesByName(FilenameUtils.getName(fileName),
                                                                   GlobalSearchScope.projectScope(project))
                                            .stream()
                                            .filter(f -> f.getPath().contains(fileName))
                                            .findFirst()
                                            .orElse(null);
            if (file != null) {
                OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project,
                                                                               file,
                                                                               node.getLine() - 1,
                                                                               node.getColumn() - 1);
                ApplicationManager.getApplication().invokeLater(() -> openFileDescriptor.navigate(true));
            } else {
                new Notification(Constants.NOTIFICATION_GROUP_ID,
                                 Bundle.message(Resource.MISSING_FILE, fileName),
                                 NotificationType.WARNING).notify(project);
            }
        });
    }
}

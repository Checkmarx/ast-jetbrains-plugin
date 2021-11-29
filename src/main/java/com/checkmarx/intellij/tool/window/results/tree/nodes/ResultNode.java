package com.checkmarx.intellij.tool.window.results.tree.nodes;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.wrapper.CxConstants;
import com.checkmarx.intellij.*;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.components.PaneUtils;
import com.checkmarx.intellij.tool.window.FileNode;
import com.checkmarx.intellij.tool.window.Severity;
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
import java.util.stream.Collectors;

import static com.checkmarx.intellij.Constants.DEFAULT_COLUMN;

/**
 * Results tree node.
 * Title of the node in the tree is set in super call.
 * buildDetailsPanel builds a panel with the result's details to show in the details panel.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ResultNode extends DefaultMutableTreeNode {

    private final String label;
    private final Result result;
    private final Project project;

    private final List<Node> nodes;
    private final List<PackageData> packageData;

    /**
     * Set node title and store the associated result
     *
     * @param result  result for this node
     * @param project context project
     */
    public ResultNode(@NotNull Result result, @NotNull Project project) {
        super();
        this.result = result;
        this.project = project;
        this.nodes = Optional.ofNullable(this.result.getData().getNodes()).orElse(Collections.emptyList());
        this.packageData = Optional.ofNullable(this.result.getData().getPackageData()).orElse(Collections.emptyList());

        String labelBuilder = (result.getData().getQueryName() != null
                               ? result.getData().getQueryName()
                               : result.getId());
        int nodeCount = nodes.size();
        if (nodeCount > 0) {
            Node node = result.getData()
                              .getNodes()
                              .get(0);
            labelBuilder += String.format(" (%s:%d)", new File(node.getFileName()).getName(), node.getLine());
        }
        this.label = labelBuilder;

        setUserObject(this.label);
        setAllowsChildren(false);
    }

    @NotNull
    public Icon getIcon() {
        return Severity.valueOf(getResult().getSeverity()).getIcon();
    }

    /**
     * @return panel with result details
     */
    @NotNull
    public JPanel buildResultPanel() {
        JPanel details = buildDetailsPanel(result);
        JPanel secondPanel = JBUI.Panels.simplePanel();

        if (nodes.size() > 0) {
            secondPanel = buildAttackVectorPanel(project, nodes);
        } else if (packageData.size() > 0) {
            secondPanel = buildPackageDataPanel(packageData);
        } else if (StringUtils.isNotBlank(result.getData().getFileName())) {
            secondPanel = buildVulnerabilityLocation(project,
                    result.getData().getFileName(), result.getData().getLine(), DEFAULT_COLUMN);
        }

        OnePixelSplitter splitter = new OnePixelSplitter();
        splitter.setFirstComponent(PaneUtils.inVerticalScrollPane(details));
        splitter.setSecondComponent(PaneUtils.inVerticalScrollPane(secondPanel));

        return JBUI.Panels.simplePanel(splitter);
    }

    @NotNull
    private JPanel buildDetailsPanel(@NotNull Result result) {
        JPanel details = new JPanel(new MigLayout("fillx"));

        JLabel title = boldLabel(this.label);
        title.setIcon(getIcon());
        details.add(title, "growx, wrap");

        details.add(new JSeparator(), "span, growx, wrap");

        details.add(boldLabel(Bundle.message(Resource.SUMMARY)), "span, wrap");
        String detailsSummary = String.format(Constants.SUMMARY_FORMAT,
                                              result.getType(),
                                              result.getSeverity(),
                                              result.getState(),
                                              result.getStatus());
        details.add(new JBLabel(detailsSummary), "span, wrap, gapbottom 5");

        String description = result.getData().getDescription();
        if (StringUtils.isNotBlank(description)) {
            details.add(boldLabel(Bundle.message(Resource.DESCRIPTION)), "span, wrap");
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            details.add(new JBLabel(String.format("<html>%s</html>", description)), "wrap, gapbottom 5");
        }
        return details;
    }

    @NotNull
    private static JPanel buildAttackVectorPanel(@NotNull Project project, @NotNull List<Node> nodes) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(boldLabel(Bundle.message(Resource.NODES)), "span, wrap");
        panel.add(new JSeparator(), "span, growx, wrap");
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            String label = String.format(Constants.NODE_FORMAT,
                                         i + 1,
                                         node.getFileName(),
                                         node.getLine(),
                                         node.getName());
            FileNode fileNode = FileNode
                    .builder()
                    .fileName(node.getFileName())
                    .line(node.getLine())
                    .column(node.getColumn())
                    .build();

            panel.add(new CxLinkLabel(label, mouseEvent -> navigate(project, fileNode)), "span, wrap");
        }
        return panel;
    }

    @NotNull
    private static JPanel buildVulnerabilityLocation(@NotNull Project project, @NotNull String fileName, int line, int column) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(boldLabel(Bundle.message(Resource.LOCATION)), "span, wrap");
        panel.add(new JSeparator(), "span, growx, wrap");
        String label = String.format(Constants.FILE_FORMAT,
                fileName,
                line,
                column);

        FileNode fileNode = FileNode
                .builder()
                .fileName(fileName)
                .line(line)
                .column(column)
                .build();

        panel.add(new CxLinkLabel(label, mouseEvent -> navigate(project, fileNode)), "span, wrap");
        return panel;
    }

    @NotNull
    private static JPanel buildPackageDataPanel(@NotNull List<PackageData> packageData) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(boldLabel(Bundle.message(Resource.PACKAGE_DATA)), "span, wrap");
        panel.add(new JSeparator(), "span, growx, wrap");
        for (PackageData pkg : packageData) {
            panel.add(new JBLabel(pkg.getType()), "split 2, span, wrap");
            panel.add(CxLinkLabel.buildDocLinkLabel(pkg.getUrl(), pkg.getUrl()), "growx, wrap");
        }
        return panel;
    }

    @NotNull
    private static JBLabel boldLabel(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        Font font = label.getFont();
        Font bold = new Font(font.getFontName(), Font.BOLD, FontSize.MEDIUM.getSize());
        label.setFont(bold);
        return label;
    }

    private static void navigate(@NotNull Project project, @NotNull FileNode fileNode) {
        String fileName = fileNode.getFileName();
        Utils.runAsyncReadAction(() -> {
            List<VirtualFile> files = FilenameIndex.getVirtualFilesByName(FilenameUtils.getName(fileName),
                                                                          GlobalSearchScope.projectScope(project))
                                                   .stream()
                                                   .filter(f -> f.getPath().contains(fileName))
                                                   .collect(Collectors.toList());
            if (files.isEmpty()) {
                new Notification(Constants.NOTIFICATION_GROUP_ID,
                                 Bundle.message(Resource.MISSING_FILE, fileName),
                                 NotificationType.WARNING).notify(project);
            } else {
                if (files.size() > 1) {
                    new Notification(Constants.NOTIFICATION_GROUP_ID,
                                     "Multiples files found for " + fileNode.getFileName(),
                                     NotificationType.WARNING).notify(project);
                }
                for (VirtualFile file : files) {

                    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project,
                                                                                   file,
                                                                                   fileNode.getLine() - 1,
                                                                                   fileNode.getColumn() - 1);
                    ApplicationManager.getApplication().invokeLater(() -> openFileDescriptor.navigate(true));
                }
            }
        });
    }
}

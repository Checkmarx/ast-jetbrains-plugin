package com.checkmarx.intellij.tool.window.results.tree.nodes;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.components.PaneUtils;
import com.checkmarx.intellij.tool.window.FileNode;
import com.checkmarx.intellij.tool.window.Severity;
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
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
                                                     result.getData().getFileName(),
                                                     result.getData().getLine(),
                                                     DEFAULT_COLUMN);
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

        String description = result.getDescription();
        if (StringUtils.isNotBlank(description)) {
            details.add(boldLabel(Bundle.message(Resource.DESCRIPTION)), "span, wrap");
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            details.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, description)), "wrap, gapbottom 5");
        }
        if (StringUtils.isNotBlank(result.getData().getValue()) && StringUtils.isNotBlank(result.getData()
                                                                                                .getExpectedValue())) {

            details.add(new JBLabel(String.format(Constants.VALUE_FORMAT,
                                                  Bundle.message(Resource.ACTUAL_VALUE),
                                                  result.getData().getValue())), "span, growx, wrap");
            details.add(new JBLabel(String.format(Constants.VALUE_FORMAT,
                                                  Bundle.message(Resource.EXPECTED_VALUE),
                                                  result.getData().getExpectedValue())), "span, growx, wrap");
        }
        return details;
    }

    @NotNull
    private static JPanel buildAttackVectorPanel(@NotNull Project project, @NotNull List<Node> nodes) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addLocationHeader(panel, Resource.NODES);
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            FileNode fileNode = FileNode
                    .builder()
                    .fileName(node.getFileName())
                    .line(node.getLine())
                    .column(node.getColumn())
                    .build();

            String label = String.format(Constants.NODE_FORMAT,
                                         i + 1,
                                         node.getName());
            panel.add(new BoldLabel(label), "gapbottom 5");
            panel.add(new CxLinkLabel(capToLen(node.getFileName()),
                                      mouseEvent -> navigate(project, fileNode)), "span, wrap, gapbottom 5");
        }
        return panel;
    }

    @NotNull
    private static JPanel buildVulnerabilityLocation(@NotNull Project project,
                                                     @NotNull String fileName,
                                                     int line,
                                                     int column) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addLocationHeader(panel, Resource.LOCATION);

        FileNode fileNode = FileNode
                .builder()
                .fileName(fileName)
                .line(line)
                .column(column)
                .build();

        String label = String.format(Constants.NODE_FORMAT,
                                     Bundle.message(Resource.LOCATION),
                                     "");
        panel.add(new BoldLabel(label), "split 2, span, gapbottom 5");
        panel.add(new CxLinkLabel(label, mouseEvent -> navigate(project, fileNode)), "growx, wrap, gapbottom 5");
        return panel;
    }

    @NotNull
    private static JPanel buildPackageDataPanel(@NotNull List<PackageData> packageData) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addLocationHeader(panel, Resource.PACKAGE_DATA);
        for (PackageData pkg : packageData) {
            String label = String.format(Constants.NODE_FORMAT,
                                         pkg.getType(),
                                         "");
            panel.add(new BoldLabel(label), "split 2, span, gapbottom 5");
            panel.add(CxLinkLabel.buildDocLinkLabel(pkg.getUrl(), pkg.getUrl()), "growx, wrap, gapbottom 5");
        }
        return panel;
    }

    private static void addLocationHeader(@NotNull JPanel panel, @Nls Resource packageData) {
        panel.add(boldLabel(Bundle.message(packageData)), "span, wrap");
        panel.add(new JSeparator(), "span, growx, wrap");
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

    @NotNull
    private static String capToLen(String fileName) {
        return fileName.length() > Constants.FILE_PATH_MAX_LEN
               ? Constants.COLLAPSE_CRUMB + fileName.substring(fileName.length() - Constants.FILE_PATH_MAX_LEN
                                                               + Constants.COLLAPSE_CRUMB.length())
               : fileName;
    }

    /*JBTable table = new AttackVectorTable(project, nodes);
    DefaultTableModel model = new NoEditTableModel(2);
    for (int i = 0; i < nodes.size(); i++) {
        Node node = nodes.get(i);
        model.addRow(new Object[]{String.format(Constants.NODE_FORMAT, i + 1, node.getName()), node.getFileName()});
    }
    table.setModel(model);
    table.setRowHeight(table.getRowHeight() + 10);
    table.setRowMargin(10);
    table.getColumnModel().setColumnMargin(10);
    panel.add(table, "");*/

    private static class NoSelectionTableCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table,
                                                value,
                                                isSelected,
                                                hasFocus,
                                                row,
                                                column);
            setBorder(noFocusBorder);
            return this;
        }
    }

    private static class NoEditTableModel extends DefaultTableModel {
        public NoEditTableModel(int columnCount) {
            super(0, columnCount);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }


    }

    private static class AutoAdjustedTable extends JBTable {
        @Override
        public @NotNull Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            int rendererWidth = component.getPreferredSize().width;
            TableColumn tableColumn = getColumnModel().getColumn(column);
            tableColumn.setPreferredWidth(Math.max(rendererWidth + getIntercellSpacing().width,
                                                   row == 0 ? 0 : tableColumn.getPreferredWidth()));
            return component;
        }
    }

    private static final class AttackVectorTableCellRenderer extends NoSelectionTableCellRenderer {

        private final List<Node> nodes;

        public AttackVectorTableCellRenderer(List<Node> nodes) {
            super();
            this.nodes = nodes;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table,
                                                value,
                                                isSelected,
                                                hasFocus,
                                                row,
                                                column);
            if (column == 0) {
                setFont(new Font(getFont().getFontName(), Font.BOLD, getFont().getSize()));
            }
            if (column == 1) {
                return new JLabel(String.format(Constants.HELP_HTML, nodes.get(row).getFileName()));
            }
            return this;
        }
    }

    private static final class AttackVectorTable extends AutoAdjustedTable {
        public AttackVectorTable(@NotNull Project project,
                                 @NotNull List<Node> nodes) {
            super();
            setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            getTableHeader().setReorderingAllowed(false);
            setShowGrid(false);
            setRowSelectionAllowed(false);
            setExpandableItemsEnabled(true);
            setDefaultRenderer(Object.class, new AttackVectorTableCellRenderer(nodes));
            //noinspection UnstableApiUsage
            TableHoverListener.DEFAULT.addTo(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = rowAtPoint(new Point(e.getX(), e.getY()));
                    int col = columnAtPoint(new Point(e.getX(), e.getY()));
                    if (col == 1) {
                        Node node = nodes.get(row);
                        FileNode fileNode = FileNode
                                .builder()
                                .fileName(node.getFileName())
                                .line(node.getLine())
                                .column(node.getColumn())
                                .build();
                        navigate(project, fileNode);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (columnAtPoint(new Point(e.getX(), e.getY())) == 1) {
                        setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (columnAtPoint(new Point(e.getX(), e.getY())) != 1) {
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                }
            });
        }

        @NotNull
        @Override
        public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
            return super.getCellRect(row, column, true);
        }
    }
}

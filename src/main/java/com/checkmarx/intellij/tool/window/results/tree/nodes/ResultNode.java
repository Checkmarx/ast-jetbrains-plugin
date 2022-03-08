package com.checkmarx.intellij.tool.window.results.tree.nodes;

import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.PackageData;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.*;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.components.PaneUtils;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.tool.window.FileNode;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.util.ui.JBUI;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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
    private final String scanId;

    private final List<Node> nodes;
    private final List<PackageData> packageData;

    public enum StateEnum {
        TO_VERIFY,
        NOT_EXPLOITABLE,
        PROPOSED_NOT_EXPLOITABLE,
        CONFIRMED,
        URGENT
    }
    
    /**
     * Set node title and store the associated result
     *
     * @param result  result for this node
     * @param project context project
     */
    public ResultNode(@NotNull Result result, @NotNull Project project, String scanId) {
        super();
        this.result = result;
        this.project = project;
        this.scanId = scanId;
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
    public JPanel buildResultPanel(Runnable runnableDraw, Runnable runnableUpdater) throws CxException, CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, InterruptedException {
        JPanel details = buildDetailsPanel(runnableDraw, runnableUpdater);
        JPanel secondPanel = JBUI.Panels.simplePanel();

        if (nodes.size() > 0) {
            getBFL();
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
    private JPanel buildDetailsPanel(@NotNull Runnable runnableDraw, Runnable runnableUpdater) {
        JPanel details = new JPanel(new MigLayout("fillx"));
        JLabel title = boldLabel(this.label);
        title.setIcon(getIcon());

        details.add(title, "growx, wrap");
        details.add(new JSeparator(), "span, growx, wrap");

        //Panel with triage form
        JPanel triageForm = new JPanel(new MigLayout("fillx"));
        JButton updateButton = new JButton();
        updateButton.setText("Update");

        //Constructing selection of State combobox
        final ComboBox<StateEnum> stateComboBox = new ComboBox<>(StateEnum.values());
        stateComboBox.setEditable(true);
        stateComboBox.setSelectedItem(result.getState());

        //Constructing selection of Severity combobox
        final ComboBox<Severity> severityComboBox = new ComboBox<>(Severity.values());
        severityComboBox.setEditable(true);
        severityComboBox.setSelectedItem(result.getSeverity());

        //Constructing Comment textField
        JTextField commentText;
        commentText = new JTextField(Bundle.message(Resource.COMMENT_PLACEHOLDER));
        commentText.setForeground(JBColor.GRAY);
        commentText.addFocusListener(new FocusListener() {
            private boolean userEdited = false;
            @Override
            public void focusGained(FocusEvent e) {
                if (commentText.getText().equals(Bundle.message(Resource.COMMENT_PLACEHOLDER)) && !userEdited) {
                    userEdited = true;
                    commentText.setText("");
                    commentText.setForeground(JBColor.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (commentText.getText().isEmpty()) {
                    userEdited = false;
                    commentText.setForeground(JBColor.GRAY);
                    commentText.setText(Bundle.message(Resource.COMMENT_PLACEHOLDER));
                }
            }
        });
        //Button action
        updateButton.addActionListener(e -> {
            updateButton.setEnabled(false);
            Object selectedState = stateComboBox.getSelectedItem();
            Object selectedSeverity = severityComboBox.getSelectedItem();
            if (selectedState == null || selectedSeverity == null) {
                Utils.getLogger(ResultNode.class)
                     .info("found null value when triaging, aborting. state "
                           + selectedState
                           + " severity "
                           + selectedSeverity);
                return;
            }
            String newState = selectedState.toString();
            String newSeverity = selectedSeverity.toString();

            result.setState(newState);
            result.setSeverity(newSeverity);

            CompletableFuture.runAsync(() -> {
                try {
                    CxWrapperFactory.build().triageUpdate(
                            UUID.fromString(getProjectId()), result.getSimilarityId(), result.getType(), newState, commentText.getText() ,newSeverity);
                    runnableDraw.run();
                } catch (Throwable error) {
                    Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
                } finally {
                    //UI thread stuff
                    ApplicationManager.getApplication().invokeLater(() -> updateButton.setEnabled(true));
                }
            });
        });

        triageForm.add(severityComboBox);
        triageForm.add(stateComboBox);
        triageForm.add(updateButton);
        details.add(triageForm, "span, wrap");
        details.add(commentText, "growx, gapleft 6, gapright 5, wrap");

        //Construction of the tabs
        JBTabbedPane tabbedPane = new JBTabbedPane();

        JPanel descriptionPanel = new JPanel();
        descriptionPanel.setLayout(new MigLayout("fillx"));

        String description = result.getDescription();
        if (StringUtils.isNotBlank(description)) {
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            descriptionPanel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, description)), "wrap, gapbottom 5");
        }
        if (StringUtils.isNotBlank(result.getData().getValue()) && StringUtils.isNotBlank(result.getData()
                                                                                                .getExpectedValue())) {

            descriptionPanel.add(new JBLabel(String.format(Constants.VALUE_FORMAT,
                                                  Bundle.message(Resource.ACTUAL_VALUE),
                                                  result.getData().getValue())), "span, growx, wrap");
            descriptionPanel.add(new JBLabel(String.format(Constants.VALUE_FORMAT,
                                                  Bundle.message(Resource.EXPECTED_VALUE),
                                                  result.getData().getExpectedValue())), "span, growx, wrap");
        }
        tabbedPane.add(Bundle.message(Resource.DESCRIPTION), descriptionPanel);


        //Triage Changes Panel
        JPanel triageChanges = new JPanel();
        triageChanges.setLayout(new MigLayout("fillx"));

        CompletableFuture.supplyAsync((Supplier<List<Predicate>>) () -> {
            try {
                return CxWrapperFactory.build().triageShow(
                        UUID.fromString(getProjectId()),
                        result.getSimilarityId(),
                        result.getType());
            } catch (Throwable error) {
                Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
            }
            return Collections.emptyList();
        }).thenAccept(triageChangesList -> ApplicationManager.getApplication().invokeLater(() -> {
            for (Predicate predicate : triageChangesList) {

                createChangesPanels(triageChanges, predicate);
            }
            runnableUpdater.run();
        }));

        tabbedPane.add(Bundle.message(Resource.CHANGES), triageChanges);
        details.add(tabbedPane, "growx");

        return details;
    }

    private void createChangesPanels(JPanel triageChanges, Predicate predicate) {
        JLabel firstLabel = new JLabel(String.format("<html><b>%s</b> | %s</html>", boldLabel(predicate.getCreatedBy()).getText(), Utils.dateParser(predicate.getCreatedAt())));
        triageChanges.add(firstLabel, "span, wrap");

        JLabel severityLabel = new JLabel(String.format("<html>%s</html>", predicate.getSeverity()));
        severityLabel.setIcon(Severity.valueOf(predicate.getSeverity()).getIcon());
        triageChanges.add(severityLabel, "span, wrap");

        JLabel stateLabel = new JLabel(String.format("<html>%s</html>", predicate.getState()));
        stateLabel.setIcon(CxIcons.STATE);
        triageChanges.add(stateLabel, "span, wrap");

        if(!predicate.getComment().equals("")){
            JLabel commentLabel = new JLabel(String.format("<html>%s</html>", predicate.getComment()));
            commentLabel.setIcon(CxIcons.COMMENT);
            triageChanges.add(commentLabel, "span, wrap");
        }
        triageChanges.add(new JSeparator(), "span, wrap ,growx");
    }

    @NotNull
    private static JPanel buildAttackVectorPanel(@NotNull Project project, @NotNull List<Node> nodes) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addHeader(panel, Resource.NODES);
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            FileNode fileNode = FileNode
                    .builder()
                    .fileName(node.getFileName())
                    .line(node.getLine())
                    .column(node.getColumn())
                    .build();

            String labelContent = String.format(Constants.NODE_FORMAT, i + 1, node.getName());

            BoldLabel label = new BoldLabel(labelContent);
            label.setOpaque(true);

            CxLinkLabel link = new CxLinkLabel(capToLen(node.getFileName()),
                                               mouseEvent -> navigate(project, fileNode));

            addToPanel(panel, label, link);
        }

        return panel;
    }

    @NotNull
    private static JPanel buildVulnerabilityLocation(@NotNull Project project,
                                                     @NotNull String fileName,
                                                     int line,
                                                     int column) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addHeader(panel, Resource.LOCATION);

        FileNode fileNode = FileNode
                .builder()
                .fileName(fileName)
                .line(line)
                .column(column)
                .build();

        String labelContent = String.format(Constants.NODE_FORMAT, "File", "");
        BoldLabel label = new BoldLabel(labelContent);
        label.setOpaque(true);
        CxLinkLabel link = new CxLinkLabel(fileName, mouseEvent -> navigate(project, fileNode));

        addToPanel(panel, label, link);
        return panel;
    }

    @NotNull
    private static JPanel buildPackageDataPanel(@NotNull List<PackageData> packageData) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        addHeader(panel, Resource.PACKAGE_DATA);
        for (PackageData pkg : packageData) {
            String labelContent = String.format(Constants.NODE_FORMAT,
                                                pkg.getType(),
                                                "");
            BoldLabel label = new BoldLabel(labelContent);
            label.setOpaque(true);
            JComponent link = CxLinkLabel.buildDocLinkLabel(pkg.getUrl(), pkg.getUrl());
            addToPanel(panel, label, link);
        }
        return panel;
    }

    private static void addToPanel(JPanel panel, BoldLabel label, JComponent link) {
        JPanel rowPanel = new JPanel(new MigLayout("fillx"));
        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                toggleHover(label, true);
                toggleHover(rowPanel, true);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                toggleHover(label, false);
                toggleHover(rowPanel, false);
            }
        };
        label.addMouseListener(hoverListener);
        link.addMouseListener(hoverListener);
        rowPanel.addMouseListener(hoverListener);
        rowPanel.add(label, "split 2, span");
        rowPanel.add(link, "span");
        panel.add(rowPanel, "growx, wrap");
    }

    private static void addHeader(@NotNull JPanel panel, @Nls Resource resource) {
        panel.add(boldLabel(Bundle.message(resource)), "span, wrap");
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


    private String getProjectId() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        Scan scan = CxWrapperFactory.build().scanShow(UUID.fromString(scanId));
        return scan.getProjectId();
    }

    private void getBFL() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        System.out.println("ScanId: " + scanId);
        System.out.println("QueryId: " + result.getData().getQueryId());
        System.out.println("Nodes size: " + getNodes().size());
        int bflIndex = CxWrapperFactory.build().getResultsBfl(UUID.fromString(scanId), result.getData().getQueryId(), getNodes());
        System.out.println("Here bfl: " + bflIndex );
    }

    @NotNull
    private static String capToLen(String fileName) {
        return fileName.length() > Constants.FILE_PATH_MAX_LEN
               ? Constants.COLLAPSE_CRUMB + fileName.substring(fileName.length() - Constants.FILE_PATH_MAX_LEN
                                                               + Constants.COLLAPSE_CRUMB.length())
               : fileName;
    }

    private static void toggleHover(JComponent component, boolean hover) {
        component.setBackground(hover
                                ? JBUI.CurrentTheme.List.Hover.background(true)
                                : JBUI.CurrentTheme.List.BACKGROUND);
        component.repaint();
    }
}

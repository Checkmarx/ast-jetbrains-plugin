package com.checkmarx.intellij.tool.window.results.tree.nodes;

import com.checkmarx.ast.codebashing.CodeBashing;
import com.checkmarx.ast.learnMore.LearnMore;
import com.checkmarx.ast.learnMore.Sample;
import com.checkmarx.ast.predicate.Predicate;
import com.checkmarx.ast.results.result.*;
import com.checkmarx.ast.scan.Scan;
import com.checkmarx.ast.wrapper.CxConstants;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.*;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.components.PaneUtils;
import com.checkmarx.intellij.service.StateService;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.tool.window.FileNode;
import com.checkmarx.intellij.tool.window.Severity;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.util.ui.JBUI;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
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
    private static final Logger LOGGER = Utils.getLogger(ResultNode.class);

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

    public static final String UPGRADE_TO_VERSION_LABEL = "Upgrade to version: ";

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
    public JPanel buildResultPanel(Runnable runnableDraw, Runnable runnableUpdater) {
        JPanel details = buildDetailsPanel(runnableDraw, runnableUpdater);
        JPanel secondPanel = JBUI.Panels.simplePanel();

        if (nodes.size() > 0) {
            secondPanel = buildAttackVectorPanel(runnableUpdater, project, nodes);
        } else if (packageData.size() > 0) {
            secondPanel = buildPackageDataPanel(packageData);
        } else if (Utils.isNotBlank(result.getData().getFileName())) {
            secondPanel = buildVulnerabilityLocation(project,
                    result.getData().getFileName(),
                    result.getData().getLine(),
                    DEFAULT_COLUMN);
        }

        if (!result.getType().equals(Constants.SCAN_TYPE_SCA)) {
            OnePixelSplitter splitter = new OnePixelSplitter();
            splitter.setFirstComponent(PaneUtils.inVerticalScrollPane(details));
            splitter.setSecondComponent(PaneUtils.inVerticalScrollPane(secondPanel));

            return JBUI.Panels.simplePanel(splitter);
        } else {
            return buildScaPanel(result, runnableDraw, runnableUpdater);
        }
    }

    @NotNull
    private JPanel buildScaPanel(Result result, @NotNull Runnable runnableDraw, Runnable runnableUpdater) {
        String type = "Vulnerability";
        String cveName = result.getVulnerabilityDetails().getCveName() != null ? result.getVulnerabilityDetails().getCveName() : result.getVulnerabilityDetails().getCweId();
        String score = Double.toString(result.getVulnerabilityDetails().getCvssScore());
        String severity = result.getSeverity();

        JPanel details = new JPanel(new MigLayout("fillx"));
        JPanel header = new JPanel(new MigLayout("fillx"));
        JLabel title = boldLabel(result.getData().getPackageIdentifier() != null ? result.getData().getPackageIdentifier() : result.getId());
        title.setIcon(getIcon());
        header.add(title);

        details.add(header, "growx, wrap, span");

        JPanel scaBody = new JPanel();
        scaBody.setLayout(new BoxLayout(scaBody, BoxLayout.Y_AXIS));

        // Summary
        JLabel resultResume = new JLabel(String.format(Constants.SUMMARY_FORMAT, type, cveName, score, severity));
        resultResume.setBorder(JBUI.Borders.emptyBottom(20));
        scaBody.add(resultResume);

        // Description
        drawSCADescription(scaBody);

        // Remediation
        drawSCARemediation(result, scaBody);

        //Additional knowledge
        drawSCAAboutVulnerability(result, scaBody);

        //Vulnerability Path
        drawSCAVulnerabilityPath(result, scaBody);
        ;

        //References
        drawSCAReferences(result, scaBody);

        JBScrollPane scrollPane = new JBScrollPane(scaBody);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        details.add(scrollPane, "span, growx, wrap, gapleft 6");

        return details;
    }

    /**
     * Draw SCA description section
     *
     * @param scaBody - body panel
     */
    private void drawSCADescription(JPanel scaBody) {
        String description = result.getDescriptionHTML();
        if (Utils.isNotBlank(description)) {
            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet styleSheet = kit.getStyleSheet();
            styleSheet.addRule("p {margin: 0}");

            JEditorPane descriptionEditorPane = new JEditorPane();
            descriptionEditorPane.setEditorKit(kit);
            descriptionEditorPane.setContentType("text/html");
            descriptionEditorPane.setText(description);
            descriptionEditorPane.setLayout(new BoxLayout(descriptionEditorPane, BoxLayout.Y_AXIS));
            descriptionEditorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            descriptionEditorPane.setEditable(false);
            descriptionEditorPane.setOpaque(false);
            descriptionEditorPane.setBorder(JBUI.Borders.emptyBottom(20));
            descriptionEditorPane.addHyperlinkListener(hle -> {
                if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
                    try {
                        Desktop.getDesktop().browse(new URI(hle.getURL().toString()));
                    } catch (IOException | URISyntaxException e) {
                        LOGGER.error(e);
                    }
                }
            });

            scaBody.add(descriptionEditorPane);
        }
    }

    /**
     * Draw SCA remediation section
     *
     * @param scaBody - body panel
     */
    private void drawSCARemediation(Result result, JPanel scaBody) {
        addSectionTitle(scaBody, Resource.REMEDIATION);

        int r = JBColor.ORANGE.getRed();
        int g = JBColor.ORANGE.getGreen();
        int b = JBColor.ORANGE.getBlue();
        String hex = String.format("#%02x%02x%02x", r, g, b);

        JLabel remediation = new JLabel();
        remediation.setBorder(JBUI.Borders.empty(0, 10, 20, 0));
        if (Utils.isNotBlank(result.getData().getRecommendedVersion())) {
            remediation.setIcon(AllIcons.Diff.MagicResolve);
            remediation.setToolTipText(Bundle.message(Resource.AUTO_REMEDIATION_TOOLTIP));
            remediation.setText(String.format(Constants.HTML_FONT_YELLOW_FORMAT, hex, UPGRADE_TO_VERSION_LABEL + result.getData().getRecommendedVersion()));
            remediation.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if (result.getData().getScaPackageData() != null && result.getData().getScaPackageData().isSupportsQuickFix()) {
                remediation.addMouseListener(new MouseAdapter() {
                    @SneakyThrows
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        handleGeneralRemediationClick(remediation, result);
                    }
                });
            } else {
                remediation.setEnabled(false);
            }
        } else {
            remediation.setText(Bundle.message(Resource.NO_INFORMATION));
        }

        scaBody.add(remediation);
    }

    /**
     * Draw SCA about vulnerability section
     *
     * @param scaBody - body panel
     */
    private void drawSCAAboutVulnerability(Result result, JPanel scaBody) {
        addSectionTitle(scaBody, Resource.ADDITIONAL_KNOWLEDGE);

        JLabel aboutVulnerability = new JLabel(Bundle.message(Resource.ABOUT_VULNERABILITY));
        aboutVulnerability.setBorder(JBUI.Borders.empty(0, 10, 20, 0));
        if (result.getData().getScaPackageData() != null && Utils.isNotBlank(result.getData().getScaPackageData().getFixLink())) {
            aboutVulnerability.setIcon(CxIcons.ABOUT);
            aboutVulnerability.setCursor(new Cursor(Cursor.HAND_CURSOR));
            aboutVulnerability.addMouseListener(new MouseAdapter() {
                @SneakyThrows
                @Override
                public void mouseClicked(MouseEvent e) {
                    Desktop.getDesktop().browse(new URI(result.getData().getScaPackageData().getFixLink()));
                }
            });
        } else {
            aboutVulnerability.setIcon(CxIcons.ABOUT);
            aboutVulnerability.setText(Bundle.message(Resource.ABOUT_VULNERABILITY));
            aboutVulnerability.setEnabled(false);
        }

        scaBody.add(aboutVulnerability);
    }

    /**
     * Draw SCA vulnerability path section
     *
     * @param scaBody - body panel
     */
    private void drawSCAVulnerabilityPath(Result result, JPanel scaBody) {
        addSectionTitle(scaBody, Resource.PATH);

        List<List<DependencyPath>> dependencyPaths = result.getData().getScaPackageData() != null ? result.getData().getScaPackageData().getDependencyPaths() : null;
        JPanel vulnerabilitiesPanel = new JPanel();
        vulnerabilitiesPanel.setBorder(JBUI.Borders.empty(0, 10, 20, 0));
        vulnerabilitiesPanel.setLayout(new BoxLayout(vulnerabilitiesPanel, BoxLayout.Y_AXIS));

        if (dependencyPaths != null) {
            for (int i = 0; i < dependencyPaths.size(); i++) {
                StringBuilder vulnerabilities = new StringBuilder();
                for (int j = 0; j < dependencyPaths.get(i).size(); j++) {
                    vulnerabilities.append(dependencyPaths.get(i).get(j).getName());
                    if (j != dependencyPaths.get(i).size() - 1) {
                        vulnerabilities.append(" -> ");
                    }
                }
                JPanel locs = new JPanel(new MigLayout("fillx"));

                if (dependencyPaths.get(i).get(0).getLocations() != null && dependencyPaths.get(i).get(0).getLocations().size() != 0) {
                    for (int r = 0; r < dependencyPaths.get(i).get(0).getLocations().size(); r++) {
                        FileNode fileNode = FileNode.builder().fileName(dependencyPaths.get(i).get(0).getLocations().get(r)).line(0).column(0).build();

                        CxLinkLabel locations = new CxLinkLabel(dependencyPaths.get(i).get(0).getLocations().get(r), mouseEvent -> navigate(project, fileNode));
                        addToPanelNoLabel(locs, locations, result, i, r);
                    }
                } else {
                    JLabel locations = new JLabel();
                    locations.setText(Bundle.message(Resource.NO_INFORMATION));
                    locs.add(locations);
                }

                vulnerabilitiesPanel.add(new JLabel(vulnerabilities.toString()));
                vulnerabilitiesPanel.add(new JLabel(String.format("Package %s is present in: ", result.getData().getScaPackageData().getDependencyPaths().get(i).get(0).getName())));
                vulnerabilitiesPanel.add(locs);
            }
        } else {
            vulnerabilitiesPanel.add(new JLabel(Bundle.message(Resource.NO_INFORMATION)));
        }

        scaBody.add(vulnerabilitiesPanel);
    }

    /**
     * Draw SCA references section
     *
     * @param scaBody - body panel
     */
    private void drawSCAReferences(Result result, JPanel scaBody) {
        addSectionTitle(scaBody, Resource.REFERENCES);

        JPanel linksPanel = new JPanel();
        linksPanel.setBorder(JBUI.Borders.empty(-5, 6, 20, 0));
        linksPanel.setLayout(new MigLayout());

        int r = JBColor.BLUE.getRed();
        int g = JBColor.BLUE.getGreen();
        int b = JBColor.BLUE.getBlue();
        String hex = String.format("#%02x%02x%02x", r, g, b);
        if (result.getData().getScaPackageData() != null && result.getData().getPackageData() != null) {
            for (int i = 0; i < result.getData().getPackageData().size(); i++) {
                PackageData packageData = result.getData().getPackageData().get(i);
                JLabel packageName;
                if (Utils.isNotBlank(packageData.getUrl())) {
                    packageName = new JLabel(String.format(Constants.HTML_FONT_BLUE_FORMAT, hex, result.getData().getPackageData().get(i).getType()));
                    packageName.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    packageName.addMouseListener(new MouseAdapter() {
                        @SneakyThrows
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            Desktop.getDesktop().browse(new URI(packageData.getUrl()));
                        }
                    });
                } else {
                    packageName = new JLabel(result.getData().getPackageData().get(i).getType());
                    packageName.setEnabled(false);
                }
                linksPanel.add(packageName);
            }
            scaBody.add(linksPanel);
        } else {
            JLabel noInformation = new JLabel(Bundle.message(Resource.NO_INFORMATION));
            noInformation.setBorder(JBUI.Borders.empty(0, 6, 20, 0));
            scaBody.add(noInformation);
        }
    }

    private void handleGeneralRemediationClick(JLabel remediation, Result result) {
        remediation.setEnabled(false);
        CompletableFuture.runAsync(() -> {
            if (!result.getData().getScaPackageData().isSupportsQuickFix()) {
                return;
            }
            VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
            if (projectDir == null) {
                return;
            }
            String baseDir = projectDir.getCanonicalPath();
            if (Utils.isBlank(baseDir)) {
                return;
            }
            List<List<DependencyPath>> dependencyPaths = result.getData()
                    .getScaPackageData()
                    .getDependencyPaths();
            boolean error = false;
            for (List<DependencyPath> dependencyPath : dependencyPaths) {
                DependencyPath head = dependencyPath.get(0);
                if (head.isSupportsQuickFix()) {
                    StringBuilder locationFullPaths = new StringBuilder();
                    head.getLocations()
                            .forEach(l -> locationFullPaths.append(Paths.get(baseDir, l)).append(","));
                    locationFullPaths.deleteCharAt(locationFullPaths.length() - 1);
                    try {
                        CxWrapperFactory.build()
                                .scaRemediation(locationFullPaths.toString(),
                                        head.getName(),
                                        result.getData().getRecommendedVersion());
                    } catch (CxException | InterruptedException | IOException ex) {
                        error = true;
                        Utils.notify(project,
                                Bundle.message(Resource.AUTO_REMEDIATION_FAIL,
                                        result.getData().getPackageIdentifier(),
                                        locationFullPaths),
                                NotificationType.WARNING);
                        LOGGER.warn(ex);
                    }
                }
            }
            if (!error) {
                Utils.notify(project,
                        Bundle.message(Resource.AUTO_REMEDIATION_OK,
                                result.getData().getPackageIdentifier(),
                                result.getData().getRecommendedVersion()),
                        NotificationType.INFORMATION);
            }
        }).thenAcceptAsync((reply) -> ApplicationManager.getApplication().invokeLater(() -> {
            remediation.setEnabled(true);
        }));
    }

    /**
     * Create a section title in the panel
     *
     * @param scaBody  - body panel
     * @param resource - message recourse
     */
    private void addSectionTitle(JPanel scaBody, Resource resource) {
        JLabel sectionTitle = new JLabel(String.format(Constants.HTML_BOLD_FORMAT, boldLabel(Bundle.message(resource)).getText()));
        sectionTitle.setBorder(JBUI.Borders.emptyBottom(10));
        scaBody.add(sectionTitle);
    }

    @NotNull
    private JPanel buildDetailsPanel(@NotNull Runnable runnableDraw, Runnable runnableUpdater) {
        //Creating title label
        JPanel details = new JPanel(new MigLayout("fillx"));
        JPanel header = new JPanel(new MigLayout("fillx"));
        JLabel title = boldLabel(this.label);
        title.setIcon(getIcon());
        header.add(title);

        if (result.getType().equals(CxConstants.SAST)) {
            //Creating codebashing label
            JLabel codebashing = new JLabel("<html>Learn more at <font color='#F36A22'><b>>_</b></font>codebashing</html>");
            codebashing.setCursor(new Cursor(Cursor.HAND_CURSOR));
            codebashing.setToolTipText(String.format("Learn more about %s using Checkmarx's eLearning platform ", result.getData().getQueryName()));
            codebashing.addMouseListener(new MouseAdapter() {
                @SneakyThrows
                @Override
                public void mouseClicked(MouseEvent e) {
                    openCodebashingLink();
                }
            });

            header.add(codebashing, "span, align r");
        }

        details.add(header, "span, growx, wrap");
        details.add(new JSeparator(), "span, growx, wrap");

        boolean triageEnabled = !result.getType().equals(Constants.SCAN_TYPE_SCA);
        //Panel with triage form, not available to sca type
        JPanel triageForm = new JPanel(new MigLayout("fillx"));
        JButton updateButton = new JButton();
        updateButton.setText("Update");
        StateService stateService = StateService.getInstance();
        final ComboBox<String> stateComboBox = (result.getType().equals(CxConstants.SAST)) ? new ComboBox<>(stateService.getStatesNameListForSastTriage().toArray(new String[0]))
                : new ComboBox<>(Arrays.stream(StateEnum.values()).map(Enum::name).toArray(String[]::new));;

        stateComboBox.setEditable(true);
        stateComboBox.setSelectedItem(result.getState());
        stateComboBox.setEnabled(triageEnabled);

        //Constructing selection of Severity combobox
        final ComboBox<Severity> severityComboBox = new ComboBox<>(Severity.values());
        severityComboBox.setEditable(true);
        severityComboBox.setSelectedItem(result.getSeverity());
        severityComboBox.setEnabled(triageEnabled);

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

            CompletableFuture.runAsync(() -> {
                try {
                    CxWrapperFactory.build().triageUpdate(
                            UUID.fromString(getProjectId()),
                            result.getSimilarityId(),
                            result.getType(),
                            newState,
                            commentText.getText(),
                            newSeverity);
                    runnableDraw.run();
                    result.setState(newState);
                    result.setSeverity(newSeverity);
                } catch (Throwable error) {
                    Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
                    // Get log final line with error message
                    String[] lines = error.getMessage().split("\n");
                    String lastLine = lines[lines.length - 1];
                    Utils.notify(project,
                            lastLine,
                            NotificationType.ERROR);
                } finally {
                    //UI thread stuff
                    ApplicationManager.getApplication().invokeLater(() -> updateButton.setEnabled(true));
                }
            });
        });

        triageForm.add(severityComboBox, "growx");
        triageForm.add(stateComboBox, "growx");
        if (triageEnabled) {
            triageForm.add(updateButton, "growx, wrap");
            triageForm.add(commentText, "span, growx");
        }
        details.add(triageForm, "span, growx, wrap");
        //Construction of the tabs
        JBTabbedPane tabbedPane = new JBTabbedPane();

        JPanel descriptionPanel = new JPanel();
        descriptionPanel.setLayout(new MigLayout("fillx"));

        String description = result.getDescription();
        if (Utils.isNotBlank(description)) {
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            descriptionPanel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, description)),
                    "wrap, gapbottom 5");
        }
        if (Utils.isNotBlank(result.getData().getValue()) && Utils.isNotBlank(result.getData()
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
        JLabel firstLabel = new JLabel(String.format("<html><b>%s</b> | %s</html>",
                boldLabel(predicate.getCreatedBy()).getText(),
                Utils.dateParser(predicate.getCreatedAt())));
        triageChanges.add(firstLabel, "span, wrap");

        JLabel severityLabel = new JLabel(String.format("<html>%s</html>", predicate.getSeverity()));
        severityLabel.setIcon(Severity.valueOf(predicate.getSeverity()).getIcon());
        triageChanges.add(severityLabel, "span, wrap");

        JLabel stateLabel = new JLabel(String.format("<html>%s</html>", predicate.getState()));
        stateLabel.setIcon(CxIcons.STATE);
        triageChanges.add(stateLabel, "span, wrap");

        if (!predicate.getComment().equals("")) {
            JLabel commentLabel = new JLabel(String.format("<html>%s</html>", predicate.getComment()));
            commentLabel.setIcon(CxIcons.COMMENT);
            triageChanges.add(commentLabel, "span, wrap");
        }
        triageChanges.add(new JSeparator(), "span, wrap ,growx");
    }

    @NotNull
    private JPanel buildAttackVectorPanel(Runnable runnableUpdater, @NotNull Project project, @NotNull List<Node> nodes) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        JPanel attackPanel = new JPanel(new MigLayout("fillx"));
        JPanel learnMorePanel = new JPanel(new MigLayout("fillx"));
        JPanel codeSamplesPanel = new JPanel(new MigLayout("fillx"));
        //addHeader(panel, Resource.NODES);
        JBTabbedPane tabbedPane = new JBTabbedPane();

        //JLabel bflHint = new JLabel(Bundle.message(Resource.LOADING_BFL));
        //panel.add(bflHint, "span, growx, wrap");
        generateAttackVectorNodes(project, nodes, attackPanel, -1);
//        JLabel bflHint = new JLabel(Bundle.message(Resource.LOADING_BFL));
//        panel.add(bflHint, "span, growx, wrap");
//        CompletableFuture.supplyAsync(() -> {
//            try {
//                return getBFL();
//            } catch (Throwable error) {
//                Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
//            }
//            return -1;
//        }).thenAccept(bfl -> ApplicationManager.getApplication().invokeLater(() -> {
//            updateAttackVectorPanel(runnableUpdater, project, nodes, panel, bflHint, bfl);
//        }));

        // Adding inner tabs
        tabbedPane.add(Bundle.message(Resource.ATTACK_VECTOR), attackPanel);
        CompletableFuture.supplyAsync((Supplier<List<LearnMore>>) () -> {
            try {
                return CxWrapperFactory.build().learnMore(result.getData().getQueryId());
            } catch (Throwable error) {
                Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
            }
            return Collections.emptyList();
        }).thenAccept(learnMoreList -> ApplicationManager.getApplication().invokeLater(() -> {
            for (LearnMore learnMore : learnMoreList) {
                generateLearnMore(learnMore, learnMorePanel);
                generateCodeSamples(learnMore, codeSamplesPanel);
            }
            runnableUpdater.run();
        }));
        tabbedPane.add(Bundle.message(Resource.LEARN_MORE), learnMorePanel);
        tabbedPane.add(Bundle.message(Resource.REMEDIATION_EXAMPLES), codeSamplesPanel);
        panel.add(tabbedPane, "growx");
        return panel;
    }

    private void generateAttackVectorNodes(@NotNull Project project, @NotNull List<Node> nodes, JPanel panel, Integer bfl) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            FileNode fileNode = FileNode
                    .builder()
                    .fileName(node.getFileName())
                    .line(node.getLine())
                    .column(node.getColumn())
                    .build();

            String labelContent = String.format(Constants.NODE_FORMAT, i + 1, node.getName());

            BoldLabel label = new BoldLabel(labelContent, SwingConstants.LEFT);
            label.setOpaque(true);

            CxLinkLabel link = new CxLinkLabel(capToLen(node.getFileName()),
                    mouseEvent -> navigate(project, fileNode));

            addToPanel(panel, label, link);
        }
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

    private void addToPanelNoLabel(JPanel panel, JComponent link, Result result, int i, int r) {
        JPanel rowPanel = new JPanel(new MigLayout("fillx"));
        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                toggleHover(rowPanel, true);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                toggleHover(rowPanel, false);
            }
        };
        link.addMouseListener(hoverListener);
        rowPanel.addMouseListener(hoverListener);
        DependencyPath dependencyPath = result.getData().getScaPackageData().getDependencyPaths().get(i).get(0);
        if (dependencyPath.isSupportsQuickFix()) {
            JPanel buttonPanel = new JPanel(new MigLayout("fillx"));
            JBLabel icon = new JBLabel(AllIcons.Diff.MagicResolveToolbar);
            MouseAdapter hoverListenerButton = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    toggleHover(buttonPanel, true);
                    buttonPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseEntered(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    toggleHover(buttonPanel, false);
                    buttonPanel.setCursor(null);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    handleFileRemediationClick(buttonPanel,
                            icon,
                            dependencyPath.getLocations().get(r),
                            result,
                            result.getData()
                                    .getScaPackageData()
                                    .getDependencyPaths()
                                    .get(i)
                                    .get(0)
                                    .getName());
                }
            };
            buttonPanel.add(icon);
            icon.addMouseListener(hoverListenerButton);
            icon.setToolTipText(Bundle.message(Resource.AUTO_REMEDIATION_TOOLTIP));
            buttonPanel.addMouseListener(hoverListenerButton);
            panel.add(buttonPanel, "split 2, gapright 0");
        }
        rowPanel.add(link, "span");
        panel.add(rowPanel, "span, growx, wrap, gapleft 0");
    }

    private void handleFileRemediationClick(JPanel buttonPanel,
                                            JBLabel icon,
                                            String location,
                                            Result result,
                                            String name) {
        buttonPanel.setEnabled(false);
        icon.setEnabled(false);
        CompletableFuture.runAsync(() -> {
            VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
            if (projectDir == null) {
                return;
            }
            String baseDir = projectDir.getCanonicalPath();
            if (Utils.isBlank(baseDir)) {
                return;
            }
            try {
                CxWrapperFactory.build()
                        .scaRemediation(Paths.get(baseDir, location).toString(),
                                name,
                                result.getData().getRecommendedVersion());
                Utils.notify(project,
                        Bundle.message(Resource.AUTO_REMEDIATION_OK,
                                result.getData().getPackageIdentifier(),
                                result.getData().getRecommendedVersion()),
                        NotificationType.INFORMATION);
            } catch (CxException | IOException | InterruptedException ex) {
                Utils.notify(project,
                        Bundle.message(Resource.AUTO_REMEDIATION_FAIL,
                                result.getData().getPackageIdentifier(),
                                location),
                        NotificationType.WARNING);
                LOGGER.warn(ex);
            }
        }).thenAcceptAsync((reply) -> ApplicationManager.getApplication().invokeLater(() -> {
            buttonPanel.setEnabled(true);
            icon.setEnabled(true);
        }));
    }

    private static void addHeader(@NotNull JPanel panel, @Nls Resource resource) {
        panel.add(boldLabel(Bundle.message(resource)), "span, wrap");
        panel.add(new JSeparator(), "span, growx, wrap");
    }

    @NotNull
    private static JBLabel boldLabel(@NotNull String text) {
        JBLabel label = new JBLabel(String.format("<html>%s</html>", text));
        Font font = label.getFont();
        Font bold = new Font(font.getFontName(), Font.BOLD, FontSize.MEDIUM.getSize());
        label.setFont(bold);
        return label;
    }

    private static void navigate(@NotNull Project project, @NotNull FileNode fileNode) {
        String fileName = fileNode.getFileName();
        Utils.runAsyncReadAction(() -> {
            List<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                            FilenameUtils.getName(fileName),
                            true, // case-sensitive search
                            GlobalSearchScope.projectScope(project)
                    )
                    .stream()
                    .filter(f -> f.getPath().contains(fileName))
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                Utils.notify(project,
                        Bundle.message(Resource.MISSING_FILE, fileName),
                        NotificationType.WARNING);
            } else {
                if (files.size() > 1) {
                    Utils.notify(project,
                            Bundle.message(Resource.MULTIPLE_FILES, fileName),
                            NotificationType.WARNING);
                }
                for (VirtualFile file : files) {
                    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(
                            project,
                            file,
                            fileNode.getLine() - 1,
                            fileNode.getColumn() - 1
                    );
                    ApplicationManager.getApplication().invokeLater(() -> openFileDescriptor.navigate(true));
                }
            }
        });
    }


    private String getProjectId() throws
            IOException,
            CxException,
            InterruptedException {
        Scan scan = CxWrapperFactory.build().scanShow(UUID.fromString(scanId));
        return scan.getProjectId();
    }

    private int getBFL() throws
            IOException,
            CxException,
            InterruptedException {

        return CxWrapperFactory.build()
                .getResultsBfl(UUID.fromString(scanId), result.getData().getQueryId(), getNodes());
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

    public void openCodebashingLink() throws IOException, URISyntaxException {
        try {
            CodeBashing response = CxWrapperFactory.build().codeBashingList(
                    result.getVulnerabilityDetails().getCweId(),
                    result.getData().getLanguageName(),
                    result.getData().getQueryName()).get(0);

            Desktop.getDesktop().browse(new URI(response.getPath()));

        } catch (CxException error) {
            if (error.getExitCode() == Constants.LICENSE_NOT_FOUND_EXIT_CODE) {
                Utils.notify(project,
                        String.format("<html>%s <a href=%s>%s</a> </html>",
                                Bundle.message(Resource.CODEBASHING_NO_LICENSE),
                                Bundle.message(Resource.CODEBASHING_LINK),
                                Bundle.message(Resource.CODEBASHING_LINK)),
                        NotificationType.WARNING
                );
            } else if (error.getExitCode() == Constants.LESSON_NOT_FOUND_EXIT_CODE) {
                Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
                Utils.notify(project,
                        Bundle.message(Resource.CODEBASHING_NO_LESSON),
                        NotificationType.WARNING
                );
            } else {
                Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
            }

        } catch (InterruptedException error) {
            Utils.getLogger(ResultNode.class).error(error.getMessage(), error);
        }
    }

    public void generateLearnMore(@NotNull LearnMore learnMore, JPanel panel) {
        JLabel riskTitle = new JLabel(String.format(Constants.HTML_BOLD_FORMAT, boldLabel(Bundle.message(Resource.RISK)).getText()));
        panel.add(riskTitle, "span, growx");

        String risk = learnMore.getRisk();
        if (Utils.isNotBlank(risk)) {
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            panel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, risk.replaceAll("\n", "<br/>"))),
                    "wrap, gapbottom 3, gapleft 0");
        }

        JLabel causeTitle = new JLabel(String.format(Constants.HTML_BOLD_FORMAT, boldLabel(Bundle.message(Resource.CAUSE)).getText()));
        panel.add(causeTitle, "span, growx");

        String cause = learnMore.getCause();
        if (Utils.isNotBlank(cause)) {
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            panel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, cause.replaceAll("\n", "<br/>"))),
                    "wrap, gapbottom 3, gapleft 0");
        }

        JLabel recommendationsTitle = new JLabel(String.format(Constants.HTML_BOLD_FORMAT, boldLabel(Bundle.message(Resource.GENERAL_RECOMMENDATIONS)).getText()));
        panel.add(recommendationsTitle, "span, growx");

        String recommendations = learnMore.getGeneralRecommendations();
        if (Utils.isNotBlank(cause)) {
            // wrapping the description in html tags auto wraps the text when it reaches the parent component size
            panel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, recommendations.replaceAll("\n", "<br/>"))),
                    "wrap, gapbottom 3, gapleft 0");
        }

        //[AST-96753] Add CWE link to the learn more section
        JBLabel cweLinkTitle = new JBLabel(String.format(Constants.HTML_BOLD_FORMAT, boldLabel("CWE Link").getText()));
        panel.add(cweLinkTitle, "span, growx");

        VulnerabilityDetails vulnerabilityDetails = result.getVulnerabilityDetails();
        if (vulnerabilityDetails != null && Utils.isNotBlank(vulnerabilityDetails.getCweId())) {
            String cweId = vulnerabilityDetails.getCweId();
            String linkUrl = "https://cwe.mitre.org/data/definitions/" + cweId + ".html";
            String linkText = "CWE-" + cweId;
            JBLabel cweLinkLabel = new JBLabel(linkText);
            cweLinkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            cweLinkLabel.setForeground(JBColor.BLUE);

            cweLinkLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(linkUrl));
                        cweLinkLabel.setForeground(JBColor.GRAY); // Mark as visited
                    } catch (IOException | URISyntaxException ex) {
                        LOGGER.error("Failed to open CWE link", ex);
                    }
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    cweLinkLabel.setForeground(JBColor.ORANGE); // Hover color
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    cweLinkLabel.setForeground(JBColor.BLUE); // Default color
                }
            });
            panel.add(cweLinkLabel, "wrap, gapbottom 3, gapleft 0");
        }
    }

    public void generateCodeSamples(@NotNull LearnMore learnMore, JPanel panel) {
        List<Sample> samples = learnMore.getSamples();
        if (samples.size() > 0) {
            for (Sample sample : samples) {
                String title = sample.getTitle();
                panel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, title + Constants.REMEDIATION_EXAMPLES_USING + sample.getProgLanguage())),
                        "wrap, gapbottom 3, gapleft 0");
                JEditorPane editor = new JEditorPane();
                editor.setEditable(false);
                editor.setMinimumSize(new Dimension(418, 30));
                editor.setText(sample.getCode());
                editor.setMargin(new Insets(10, 10, 10, 10));
                panel.add(editor, "wrap, gapbottom 3, gapleft 0");
            }
        } else {
            panel.add(new JBLabel(String.format(Constants.HTML_WRAPPER_FORMAT, Resource.NO_REMEDIATION_EXAMPLES)),
                    "wrap, gapbottom 3, gapleft 0");
        }
    }
}

package com.checkmarx.intellij.project;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service for indexing results of a scan in a given project
 */
public class ProjectResultsService {

    private static final Logger LOGGER = Utils.getLogger(ProjectResultsService.class);

    private final Project project;

    // index by file and by line number
    private Map<String, Map<Integer, List<Node>>> nodesByFile = new HashMap<>();
    // index each node by the result
    private Map<Node, Result> resultByNode = new HashMap<>();

    public ProjectResultsService(Project project) {
        this.project = project;
    }

    /**
     * Index results for the project
     *
     * @param project project calling the service instance for validation
     * @param results results to index
     */
    public void indexResults(Project project, com.checkmarx.ast.results.Results results) {
        if (!Utils.validThread()) {
            return;
        }

        LOGGER.info(String.format("Indexing %d results", results.getTotalCount()));

        validateProject(project);

        this.nodesByFile = new HashMap<>();
        this.resultByNode = new HashMap<>();

        CompletableFuture.runAsync(() -> {
            Map<String, Map<Integer, List<Node>>> nodesByFile = new HashMap<>();
            Map<Node, Result> resultByNode = new HashMap<>();
            for (Result result : results.getResults()) {
                for (Node node : Optional.ofNullable(result.getData().getNodes())
                                         .orElse(Collections.emptyList())) {
                    Map<Integer, List<Node>> nodesByLine
                            = nodesByFile.computeIfAbsent(Paths.get(node.getFileName())
                                                               .toString()
                                                               .substring(1),
                                                          k -> new HashMap<>());
                    List<Node> nodesForLine = nodesByLine.computeIfAbsent(node.getLine(),
                                                                          k -> new ArrayList<>());
                    nodesForLine.add(node);
                    resultByNode.put(node, result);
                }
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                this.nodesByFile = nodesByFile;
                this.resultByNode = resultByNode;
                LOGGER.info("Indexed results are live");
            });
        });
    }

    /**
     * Get results for a given file and line number.
     *
     * @param project    project calling the service instance for validation
     * @param file       file to get results for
     * @param lineNumber line number in the file
     * @return results for file and line
     */
    public List<Node> getResultsForFileAndLine(Project project,
                                               String file,
                                               int lineNumber) {
        validateProject(project);

        List<Node> nodes = Collections.emptyList();

        if (this.project.getBasePath() != null) {
            try {
                Path projectRoot = Paths.get(this.project.getBasePath());
                Path absolute = Paths.get(file);
                String relativePath = projectRoot.relativize(absolute).toString();
                Map<Integer, List<Node>> nodesByLine = nodesByFile.get(relativePath);
                if (nodesByLine != null) {
                    List<Node> nodesForLine = nodesByLine.get(lineNumber);
                    if (nodesForLine != null) {
                        nodes = nodesForLine;
                    }
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Failed to relativize path: " + file, e);
            }
        }

        var pass = "23w2nsj823e!!";

        return nodes;
    }

    /**
     * Get a node's parent result.
     *
     * @param node node
     * @return result
     */
    public Result getResultForNode(Node node) {
        return resultByNode.get(node);
    }

    /**
     * Validate if the caller is of the correct project.
     *
     * @param project the project
     */
    private void validateProject(Project project) {
        if (!Objects.equals(this.project, project)) {
            throw new IllegalArgumentException("invalid project for service");
        }
    }
}

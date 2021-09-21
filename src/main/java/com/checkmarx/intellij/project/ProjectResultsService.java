package com.checkmarx.intellij.project;

import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultDataNode;
import com.checkmarx.ast.results.structure.CxResultOutput;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for indexing results of a scan in a given project
 */
public class ProjectResultsService {

    private final Project project;

    // index by file and by line number
    private Map<String, Map<Integer, List<CxResultDataNode>>> nodesByFile = new HashMap<>();
    // index each node by the result
    private Map<CxResultDataNode, CxResult> resultByNode = new HashMap<>();

    public ProjectResultsService(Project project) {
        this.project = project;
    }

    /**
     * Index results for the project
     *
     * @param project project calling the service instance for validation
     * @param results results to index
     */
    public void indexResults(Project project, CxResultOutput results) {
        if (!Utils.validThread()) {
            return;
        }

        validateProject(project);

        nodesByFile = new HashMap<>();
        resultByNode = new HashMap<>();

        for (CxResult result : results.getResults()) {
            for (CxResultDataNode node : Optional.ofNullable(result.getData().getNodes())
                                                 .orElse(Collections.emptyList())) {
                Map<Integer, List<CxResultDataNode>> nodesByLine
                        = nodesByFile.computeIfAbsent(Paths.get(node.getFileName())
                                                           .toString()
                                                           .substring(1),
                                                      k -> new HashMap<>());
                List<CxResultDataNode> nodesForLine = nodesByLine.computeIfAbsent(node.getLine(),
                                                                                  k -> new ArrayList<>());
                nodesForLine.add(node);
                resultByNode.put(node, result);
            }
        }
    }

    /**
     * Get results for a given file and line number.
     *
     * @param project    project calling the service instance for validation
     * @param file       file to get results for
     * @param lineNumber line number in the file
     * @return results for file and line
     */
    public List<CxResultDataNode> getResultsForFileAndLine(Project project,
                                                           String file,
                                                           int lineNumber) {
        validateProject(project);

        List<CxResultDataNode> nodes = Collections.emptyList();

        if (this.project.getBasePath() != null) {
            try {
                Path projectRoot = Paths.get(this.project.getBasePath());
                Path absolute = Paths.get(file);
                String relativePath = projectRoot.relativize(absolute).toString();
                Map<Integer, List<CxResultDataNode>> nodesByLine = nodesByFile.get(relativePath);
                if (nodesByLine != null) {
                    List<CxResultDataNode> nodesForLine = nodesByLine.get(lineNumber);
                    if (nodesForLine != null) {
                        nodes = nodesForLine;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return nodes;
    }

    /**
     * Get a node's parent result.
     *
     * @param node node
     * @return result
     */
    public CxResult getResultForNode(CxResultDataNode node) {
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

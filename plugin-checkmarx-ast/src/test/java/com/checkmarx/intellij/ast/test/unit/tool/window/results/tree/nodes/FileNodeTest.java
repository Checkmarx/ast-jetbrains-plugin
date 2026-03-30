package com.checkmarx.intellij.ast.test.unit.tool.window.results.tree.nodes;

import com.checkmarx.intellij.ast.window.results.tree.nodes.FileNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileNode (Lombok @Data @Builder class).
 * No IntelliJ platform dependencies — pure Java data class.
 */
class FileNodeTest {

    @Test
    void builder_WithAllFields_CreatesCorrectInstance() {
        FileNode node = FileNode.builder()
                .fileName("MyFile.java")
                .line(42)
                .column(10)
                .build();

        assertEquals("MyFile.java", node.getFileName());
        assertEquals(42, node.getLine());
        assertEquals(10, node.getColumn());
    }

    @Test
    void setterRoundTrips_UpdateFields() {
        String[][] samples = {
                {"updated.java", "99", "20"},
                {"another.java", "1", "1"},
                {"empty.java", "0", "0"}
        };

        for (String[] sample : samples) {
            FileNode node = FileNode.builder().build();
            node.setFileName(sample[0]);
            node.setLine(Integer.parseInt(sample[1]));
            node.setColumn(Integer.parseInt(sample[2]));
            assertEquals(sample[0], node.getFileName());
            assertEquals(Integer.parseInt(sample[1]), node.getLine());
            assertEquals(Integer.parseInt(sample[2]), node.getColumn());
        }
    }

    @Test
    void equals_AndHashCode_AreConsistent() {
        FileNode node1 = FileNode.builder().fileName("Test.java").line(10).column(5).build();
        FileNode node2 = FileNode.builder().fileName("Test.java").line(10).column(5).build();
        FileNode node3 = FileNode.builder().fileName("Test.java").line(11).column(5).build();

        assertEquals(node1, node2, "Identical nodes should be equal");
        assertEquals(node1.hashCode(), node2.hashCode(), "Equal nodes must have same hashCode");
        assertNotEquals(node1, node3, "Nodes with different line should not be equal");
    }

    @Test
    void toString_ContainsAllFields() {
        FileNode node = FileNode.builder().fileName("MyFile.java").line(42).column(10).build();
        String str = node.toString();
        assertTrue(str.contains("MyFile.java"));
        assertTrue(str.contains("42"));
        assertTrue(str.contains("10"));
    }
}

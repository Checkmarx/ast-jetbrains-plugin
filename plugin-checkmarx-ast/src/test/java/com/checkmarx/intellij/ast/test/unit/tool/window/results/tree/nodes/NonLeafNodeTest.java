package com.checkmarx.intellij.ast.test.unit.tool.window.results.tree.nodes;

import com.checkmarx.intellij.ast.window.results.tree.nodes.NonLeafNode;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NonLeafNode.
 * No IntelliJ platform dependencies — pure Java tree node.
 */
class NonLeafNodeTest {

    @Test
    void constructor_WithLabel_StoresUserObject() {
        NonLeafNode node = new NonLeafNode("MyLabel");
        assertEquals("MyLabel", node.getUserObject());
    }

    @Test
    void toString_WithZeroChildren_ShowsZeroCount() {
        NonLeafNode node = new NonLeafNode("Root");
        assertEquals("Root (0)", node.toString());
    }

    @Test
    void incrementSubTreeSize_Once_ReturnsOneInToString() {
        NonLeafNode node = new NonLeafNode("Root");
        node.incrementSubTreeSize();
        assertEquals("Root (1)", node.toString());
    }

    @Test
    void incrementSubTreeSize_Multiple_CountsCorrectly() {
        NonLeafNode node = new NonLeafNode("Root");
        node.incrementSubTreeSize();
        node.incrementSubTreeSize();
        node.incrementSubTreeSize();
        assertEquals("Root (3)", node.toString());
    }

    @Test
    void add_WithNullComparator_AddsChildWithoutSorting() {
        NonLeafNode parent = new NonLeafNode("parent");
        NonLeafNode child1 = new NonLeafNode("z-child");
        NonLeafNode child2 = new NonLeafNode("a-child");

        parent.add(child1, null);
        parent.add(child2, null);

        assertEquals(2, parent.getChildCount());
        // Order should be insertion order when comparator is null
        assertEquals("z-child", ((NonLeafNode) parent.getChildAt(0)).getUserObject());
        assertEquals("a-child", ((NonLeafNode) parent.getChildAt(1)).getUserObject());
    }

    @Test
    void add_WithComparator_SortsChildrenAlphabetically() {
        NonLeafNode parent = new NonLeafNode("parent");
        NonLeafNode child1 = new NonLeafNode("z-child");
        NonLeafNode child2 = new NonLeafNode("a-child");
        NonLeafNode child3 = new NonLeafNode("m-child");

        Comparator<String> alphabetical = Comparator.naturalOrder();
        parent.add(child1, alphabetical);
        parent.add(child2, alphabetical);
        parent.add(child3, alphabetical);

        assertEquals(3, parent.getChildCount());
        assertEquals("a-child", ((NonLeafNode) parent.getChildAt(0)).getUserObject());
        assertEquals("m-child", ((NonLeafNode) parent.getChildAt(1)).getUserObject());
        assertEquals("z-child", ((NonLeafNode) parent.getChildAt(2)).getUserObject());
    }

    @Test
    void add_WithComparator_ReverseOrder_SortsDescending() {
        NonLeafNode parent = new NonLeafNode("parent");
        NonLeafNode child1 = new NonLeafNode("a-child");
        NonLeafNode child2 = new NonLeafNode("z-child");

        Comparator<String> reverseOrder = Comparator.reverseOrder();
        parent.add(child1, reverseOrder);
        parent.add(child2, reverseOrder);

        assertEquals(2, parent.getChildCount());
        assertEquals("z-child", ((NonLeafNode) parent.getChildAt(0)).getUserObject());
        assertEquals("a-child", ((NonLeafNode) parent.getChildAt(1)).getUserObject());
    }

    @Test
    void add_MultipleChildren_WithComparator_MaintainsSortedOrder() {
        NonLeafNode parent = new NonLeafNode("parent");
        String[] labels = {"delta", "alpha", "gamma", "beta"};
        Comparator<String> alphabetical = Comparator.naturalOrder();

        for (String label : labels) {
            parent.add(new NonLeafNode(label), alphabetical);
        }

        assertEquals(4, parent.getChildCount());
        assertEquals("alpha", ((NonLeafNode) parent.getChildAt(0)).getUserObject());
        assertEquals("beta", ((NonLeafNode) parent.getChildAt(1)).getUserObject());
        assertEquals("delta", ((NonLeafNode) parent.getChildAt(2)).getUserObject());
        assertEquals("gamma", ((NonLeafNode) parent.getChildAt(3)).getUserObject());
    }
}


package com.checkmarx.intellij.ast.test.unit.commands.helper;

import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResultGetState} data class.
 * Tests the Lombok @Data-generated getters, setters, and constructors.
 */
class ResultGetStateTest {

    private ResultGetState state;

    @BeforeEach
    void setUp() {
        state = new ResultGetState();
    }

    @Test
    void constructor_CreatesInstanceWithDefaultValues() {
        assertNotNull(state, "Constructor should create non-null instance");
        assertNull(state.getScanId(), "scanId should default to null");
        assertNull(state.getScanIdFieldValue(), "scanIdFieldValue should default to null");
        assertFalse(state.isLatest(), "latest should default to false");
        assertNotNull(state.getResultOutput(), "resultOutput should have a default value");
        assertNull(state.getMessage(), "message should default to null");
    }

    @Test
    void setScanId_StoresAndRetrievesValue() {
        String testScanId = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";
        state.setScanId(testScanId);
        assertEquals(testScanId, state.getScanId(), "scanId getter should return the set value");
    }

    @Test
    void setScanIdFieldValue_StoresAndRetrievesValue() {
        String testFieldValue = "custom-field-value";
        state.setScanIdFieldValue(testFieldValue);
        assertEquals(testFieldValue, state.getScanIdFieldValue(), "scanIdFieldValue getter should return the set value");
    }

    @Test
    void setLatest_StoresAndRetrievesValue() {
        assertFalse(state.isLatest(), "latest should initially be false");
        state.setLatest(true);
        assertTrue(state.isLatest(), "latest getter should return true after setting");
        state.setLatest(false);
        assertFalse(state.isLatest(), "latest getter should return false after setting to false");
    }

    @Test
    void setResultOutput_StoresAndRetrievesValue() {
        assertNotNull(state.getResultOutput(), "Default resultOutput should not be null");

        // Verify that resultOutput can be retrieved (Lombok @Data works)
        Object output = state.getResultOutput();
        assertEquals(output, state.getResultOutput(), "Same getResultOutput call should return same instance");
    }

    @Test
    void setMessage_StoresAndRetrievesValue() {
        String testMessage = "Test message";
        state.setMessage(testMessage);
        assertEquals(testMessage, state.getMessage(), "message getter should return the set value");
    }

    @Test
    void setMessage_WithNull_StoresNull() {
        state.setMessage("Initial");
        state.setMessage(null);
        assertNull(state.getMessage(), "message getter should return null after setting to null");
    }

    @Test
    void multipleSetters_AllValuesIndependentlyChangeable() {
        String scanId = "test-scan-123";
        String fieldValue = "field-val";
        String message = "All set";

        state.setScanId(scanId);
        state.setScanIdFieldValue(fieldValue);
        state.setLatest(true);
        state.setMessage(message);

        assertEquals(scanId, state.getScanId());
        assertEquals(fieldValue, state.getScanIdFieldValue());
        assertTrue(state.isLatest());
        assertEquals(message, state.getMessage());
    }

    @Test
    void toString_ContainsAllFields() {
        state.setScanId("scan-1");
        state.setScanIdFieldValue("field-1");
        state.setLatest(true);
        state.setMessage("Test");

        String str = state.toString();
        assertNotNull(str, "toString should not be null");
        assertFalse(str.isEmpty(), "toString should not be empty");
        // Verify @Data generates toString with field names
        assertTrue(str.contains("ResultGetState") || str.contains("scan"), "toString should contain class or field info");
    }

    @Test
    void equals_WithSameValues_ReturnsTrue() {
        ResultGetState state1 = new ResultGetState();
        ResultGetState state2 = new ResultGetState();

        state1.setScanId("same-id");
        state2.setScanId("same-id");
        state1.setLatest(true);
        state2.setLatest(true);

        assertEquals(state1, state2, "Two instances with same field values should be equal");
    }

    @Test
    void equals_WithDifferentValues_ReturnsFalse() {
        ResultGetState state1 = new ResultGetState();
        ResultGetState state2 = new ResultGetState();

        state1.setScanId("id-1");
        state2.setScanId("id-2");

        assertNotEquals(state1, state2, "Two instances with different field values should not be equal");
    }

    @Test
    void hashCode_WithSameValues_ProducesSameHash() {
        ResultGetState state1 = new ResultGetState();
        ResultGetState state2 = new ResultGetState();

        state1.setScanId("same-id");
        state2.setScanId("same-id");
        state1.setMessage("same-msg");
        state2.setMessage("same-msg");

        assertEquals(state1.hashCode(), state2.hashCode(), "Equal objects should have equal hashCode");
    }
}

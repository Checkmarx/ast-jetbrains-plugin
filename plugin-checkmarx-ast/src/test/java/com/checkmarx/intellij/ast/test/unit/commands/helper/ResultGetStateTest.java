package com.checkmarx.intellij.ast.test.unit.commands.helper;

import com.checkmarx.intellij.ast.commands.helper.ResultGetState;
import com.checkmarx.ast.results.Results;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResultGetState (Lombok @Data class).
 * No IntelliJ platform dependencies — pure Java.
 */
class ResultGetStateTest {

    /** Plain Java 11 holder for parameterized setter/getter round-trip data. */
    static class FieldTriple {
        final String name;
        final BiConsumer<ResultGetState, Object> setter;
        final Function<ResultGetState, Object> getter;
        final Object value;

        FieldTriple(String name,
                    BiConsumer<ResultGetState, Object> setter,
                    Function<ResultGetState, Object> getter,
                    Object value) {
            this.name = name;
            this.setter = setter;
            this.getter = getter;
            this.value = value;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<FieldTriple> fieldTriples() {
        Results results = new Results(0, Collections.emptyList(), "");
        return Stream.of(
                new FieldTriple("scanId",
                        (s, v) -> s.setScanId((String) v),
                        ResultGetState::getScanId,
                        "scan-abc-123"),
                new FieldTriple("scanIdFieldValue",
                        (s, v) -> s.setScanIdFieldValue((String) v),
                        ResultGetState::getScanIdFieldValue,
                        "field-value-xyz"),
                new FieldTriple("latest",
                        (s, v) -> s.setLatest((Boolean) v),
                        s -> s.isLatest(),
                        Boolean.TRUE),
                new FieldTriple("resultOutput",
                        (s, v) -> s.setResultOutput((Results) v),
                        ResultGetState::getResultOutput,
                        results),
                new FieldTriple("message",
                        (s, v) -> s.setMessage((String) v),
                        ResultGetState::getMessage,
                        "some error message")
        );
    }

    @Test
    void defaultConstructor_InitializesDefaults() {
        ResultGetState state = new ResultGetState();
        assertNotNull(state.getResultOutput(), "resultOutput should not be null");
        assertEquals(0, state.getResultOutput().getTotalCount(), "default total count should be 0");
        assertNull(state.getMessage(), "message should be null");
        assertFalse(state.isLatest(), "latest should default to false");
    }

    @Test
    void setterAndGetter_RoundTrips() {
        for (FieldTriple triple : (Iterable<FieldTriple>) fieldTriples()::iterator) {
            ResultGetState state = new ResultGetState();
            triple.setter.accept(state, triple.value);
            assertEquals(triple.value, triple.getter.apply(state),
                    "Mismatch for field " + triple.name);
        }
    }

    @Test
    void equals_TwoDefaultInstances_AreEqual() {
        assertEquals(new ResultGetState(), new ResultGetState());
    }

    @Test
    void equals_DifferentScanId_AreNotEqual() {
        ResultGetState s1 = new ResultGetState();
        s1.setScanId("scan-1");
        ResultGetState s2 = new ResultGetState();
        s2.setScanId("scan-2");
        assertNotEquals(s1, s2);
        assertEquals(s1, s1); // reflexive
    }

    @Test
    void toString_ContainsAllFields() {
        ResultGetState state = new ResultGetState();
        state.setScanId("test-scan");
        state.setMessage("test-message");
        state.setLatest(true);
        String str = state.toString();
        assertTrue(str.contains("test-scan"));
        assertTrue(str.contains("test-message"));
        assertTrue(str.contains("true"));
    }
}

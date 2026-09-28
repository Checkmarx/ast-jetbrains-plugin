package com.checkmarx.intellij.ast.test.ui.PageMethods;

import com.intellij.remoterobot.fixtures.JButtonFixture;
import com.intellij.remoterobot.fixtures.JTextFieldFixture;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.UUID;

import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.selectFirstIacOrContainerVulnerability;
import static com.checkmarx.intellij.ast.test.ui.PageMethods.ScanResultsPannelPage.verifyChangeSaved;
import static com.checkmarx.intellij.ast.test.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ast.test.ui.utils.TestConstants.*;
import static com.checkmarx.intellij.ast.test.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ast.test.ui.utils.Xpath.*;

/**
 * Page object for the vulnerability triage side panel (Severity/State dropdowns, comment, Update button).
 */
public class TriagePage {

    public static final List<String> SEVERITY_OPTIONS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static final List<String> STATE_OPTIONS = List.of("TO_VERIFY", "CONFIRMED", "URGENT");

    private static String pendingSeverity;
    private static String pendingState;
    private static String pendingNote;

    private static String originalSeverityBeforeEdit;
    private static String originalStateBeforeEdit;
    private static String originalNoteBeforeEdit;

    /**
     * Reads the severity value currently shown in the triage side panel's Severity dropdown.
     *
     * @return one of {@link #SEVERITY_OPTIONS}.
     */
    public static String getCurrentSeverity() {
        return SEVERITY_OPTIONS.stream()
                .filter(severity -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, severity)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not determine current severity from triage side panel."));
    }

    /**
     * Changes the Severity dropdown in the triage side panel from one value to another.
     *
     * @param from The severity value currently shown in the dropdown (e.g. "MEDIUM").
     * @param to   The severity value to select (e.g. "HIGH").
     */
    public static void changeSeverity(String from, String to) {
        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, from)));

        boolean changed = selectDropDownValue(SEVERITY_COMBOBOX_ARROW, to);
        Assertions.assertTrue(changed, "Failed to select severity '" + to + "' from the dropdown.");

        pendingSeverity = to;
        log("Severity dropdown changed from " + from + " to " + to);
    }

    /**
     * Clicks the Update button in the triage side panel to persist the pending severity change.
     */
    public static void clickUpdate() {
        JButtonFixture update = find(JButtonFixture.class, UPDATE_BTN);

        waitFor(() -> {
            update.click();
            return !update.isEnabled();
        });
        waitFor(update::isEnabled);

        log("Clicked Update button to save triage change.");
    }

    /**
     * Verifies that the Severity dropdown reflects the value selected in the last {@link #changeSeverity} call.
     */
    public static void verifySeverityUpdated() {
        Assertions.assertNotNull(pendingSeverity, "changeSeverity(...) must be called before verifySeverityUpdated().");

        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, pendingSeverity)));
        log("Verified severity dropdown now shows: " + pendingSeverity);
    }

    /**
     * Verifies that a severity change made via {@link #changeSeverity} in a prior scan is still
     * in effect after a new scan has been triggered and its results reloaded. Predicates (triage
     * changes) are finding-scoped and persisted server-side, so they must survive a scan-and-reload
     * cycle, not just remain visible within the same session.
     */
    public static void verifyPredicatePersisted() {
        Assertions.assertNotNull(pendingSeverity, "changeSeverity(...) must be called before verifyPredicatePersisted().");

        selectFirstIacOrContainerVulnerability();
        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, pendingSeverity)));
        log("Verified predicate persisted after new scan: severity still shows " + pendingSeverity);
    }

    /**
     * Verifies that a severity change made via {@link #changeSeverity} is still shown correctly
     * after the same scan (not a new one) has been re-selected, forcing its results to reload.
     * The UI must reflect the latest server-side triage state rather than a stale cached value
     * left over from before the refresh.
     */
    public static void verifyPredicateValuesMatch() {
        Assertions.assertNotNull(pendingSeverity, "changeSeverity(...) must be called before verifyPredicateValuesMatch().");

        selectFirstIacOrContainerVulnerability();
        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, pendingSeverity)));
        log("Verified UI shows correct severity after refreshing the same scan: " + pendingSeverity);
    }

    /**
     * Reads the state value currently shown in the triage side panel's State dropdown.
     *
     * @return one of {@link #STATE_OPTIONS}.
     */
    public static String getCurrentState() {
        return STATE_OPTIONS.stream()
                .filter(state -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, state)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Could not determine current state from triage side panel."));
    }

    /**
     * Changes the State dropdown in the triage side panel to the given value.
     *
     * @param newState The state value to select (e.g. "CONFIRMED"), one of {@link #STATE_OPTIONS}.
     */
    public static void changeState(String newState) {
        String currentState = getCurrentState();
        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, currentState)));

        boolean changed = selectDropDownValue(STATE_COMBOBOX_ARROW, newState);
        Assertions.assertTrue(changed, "Failed to select state '" + newState + "' from the dropdown.");

        pendingState = newState;
        log("State dropdown changed from " + currentState + " to " + newState);
    }

    /**
     * Verifies that the State dropdown reflects the value selected in the last {@link #changeState} call.
     */
    public static void verifyStateUpdated() {
        Assertions.assertNotNull(pendingState, "changeState(...) must be called before verifyStateUpdated().");

        waitFor(() -> hasAnyComponent(String.format(TRIAGE_STATE_COMBOBOX, pendingState)));
        log("Verified state dropdown now shows: " + pendingState);
    }

    /**
     * Enters free-text into the Note field of the triage side panel.
     *
     * @param note The note text to enter.
     */
    public static void enterNote(String note) {
        JTextFieldFixture noteField = find(JTextFieldFixture.class, TRIAGE_NOTE);
        noteField.setText(note);

        pendingNote = note;
        log("Entered note in triage side panel: " + note);
    }

    /**
     * Verifies that the note entered via the last {@link #enterNote} call was saved
     * and is visible in the Changes tab.
     */
    public static void verifyNoteVisible() {
        Assertions.assertNotNull(pendingNote, "enterNote(...) must be called before verifyNoteVisible().");

        verifyChangeSaved(pendingNote);
        log("Verified note is visible in the Changes tab: " + pendingNote);
    }

    /**
     * Edits the Severity, State, and Note fields in the triage side panel without clicking Update,
     * recording the original values so {@link #verifyNoChangesPersisted()} can confirm they were discarded.
     */
    public static void editAllFieldsWithoutUpdate() {
        originalSeverityBeforeEdit = getCurrentSeverity();
        originalStateBeforeEdit = getCurrentState();
        originalNoteBeforeEdit = find(JTextFieldFixture.class, TRIAGE_NOTE).getText();

        String targetSeverity = SEVERITY_OPTIONS.stream()
                .filter(severity -> !severity.equals(originalSeverityBeforeEdit))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate severity value available to select."));
        String targetState = STATE_OPTIONS.stream()
                .filter(state -> !state.equals(originalStateBeforeEdit))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No alternate state value available to select."));

        changeSeverity(originalSeverityBeforeEdit, targetSeverity);
        changeState(targetState);
        enterNote(UUID.randomUUID().toString());

        log("Edited Severity, State, and Note in the triage side panel without clicking Update.");
    }

    /**
     * Verifies that the edits made in {@link #editAllFieldsWithoutUpdate()} were discarded because
     * Update was never clicked: after navigating away, re-opening the same vulnerability must show
     * the original Severity/State/Note values, not the pending edited ones.
     */
    public static void verifyNoChangesPersisted() {
        Assertions.assertNotNull(originalSeverityBeforeEdit, "editAllFieldsWithoutUpdate() must be called before verifyNoChangesPersisted().");
        Assertions.assertNotNull(originalStateBeforeEdit, "editAllFieldsWithoutUpdate() must be called before verifyNoChangesPersisted().");

        selectFirstIacOrContainerVulnerability();

        Assertions.assertEquals(originalSeverityBeforeEdit, getCurrentSeverity(),
                "Severity edit should have been discarded since Update was never clicked.");
        Assertions.assertEquals(originalStateBeforeEdit, getCurrentState(),
                "State edit should have been discarded since Update was never clicked.");

        String noteAfterReopen = find(JTextFieldFixture.class, TRIAGE_NOTE).getText();
        Assertions.assertEquals(originalNoteBeforeEdit, noteAfterReopen,
                "Note edit should have been discarded since Update was never clicked.");

        log("Verified Severity/State/Note edits were discarded after navigating away without clicking Update.");
    }
}

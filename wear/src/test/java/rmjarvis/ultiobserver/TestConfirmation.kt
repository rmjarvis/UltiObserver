package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.*

/** Confirmation timing and the phone-provided alternatives for pull violations. */
class TestConfirmation {
    /** Apply the configured guidance mode without resubmitting a pending confirmation. */
    @Test
    fun guidance() {
        val visible = timeoutConfirmation().prompt
        assertNull(visible.confirmationDelayMillis(false))

        // Timed guidance waits for its configured interval; None confirms immediately.
        val timed = visible.copy(
            presentation = WearGuidancePresentation.VISIBLE_TIMED, autoAcceptDelayMillis = 1_000L,
        )
        val none = visible.copy(presentation = WearGuidancePresentation.HIDDEN_AUTO_ACCEPT)
        assertEquals(1_000L, timed.confirmationDelayMillis(false))
        assertEquals(0L, none.confirmationDelayMillis(false))

        // Once submitted, neither automatic mode schedules another submission.
        assertNull(timed.confirmationDelayMillis(true))
        assertNull(none.confirmationDelayMillis(true))
    }

    /** Switch between mixed pull violations while retaining the phone's confirmation details. */
    @Test
    fun pullAlternatives() {
        val offsides = timeoutConfirmation().prompt.copy(title = "Offsides")
        val majority = offsides.copy(title = "Majority pull violation")
        val confirmation = WearActionConfirmation.PullViolation(
            "playing", TeamId.TEAM_ONE, 1_000L, PullViolationType.OFFSIDES,
            listOf(
                WearPullViolationOption(PullViolationType.OFFSIDES, offsides),
                WearPullViolationOption(PullViolationType.MAJORITY_PULL, majority),
            ), offsides,
        )

        // Each switch changes only the selected violation and its corresponding prompt.
        val alternative = confirmation.alternativeConfirmation()!!
        assertEquals(confirmation.copy(
            selectedViolation = PullViolationType.MAJORITY_PULL, prompt = majority,
        ), alternative)
        assertEquals("→ Majority pull viol.", alternative.selectedViolation.alternativeActionLabel())
        assertEquals(confirmation, alternative.alternativeConfirmation())
        assertEquals("→ Offsides", confirmation.selectedViolation.alternativeActionLabel())

        // Ordinary offsides and unrelated actions have no switch to offer.
        assertNull(confirmation.copy(options = confirmation.options.take(1)).alternativeConfirmation())
        assertNull(timeoutConfirmation().alternativeConfirmation())
    }

    /** Reject the unsupported use of False start as an alternative pull-violation button. */
    @Test
    fun unsupportedAlternative() {
        assertThrows(IllegalStateException::class.java) {
            PullViolationType.FALSE_START.alternativeActionLabel()
        }
    }
}

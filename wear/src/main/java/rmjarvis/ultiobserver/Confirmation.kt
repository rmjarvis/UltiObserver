package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearPromptSnapshot

/** Select automatic confirmation timing without starting a UI coroutine. */
internal fun WearPromptSnapshot.confirmationDelayMillis(commandPending: Boolean): Long? {
    if (commandPending) return null
    return when (presentation) {
        WearGuidancePresentation.VISIBLE -> null
        WearGuidancePresentation.VISIBLE_TIMED -> autoAcceptDelayMillis!!
        WearGuidancePresentation.HIDDEN_AUTO_ACCEPT -> 0L
    }
}

/** Return the other supported pull-violation confirmation, preserving its phone-owned copy. */
internal fun WearActionConfirmation.alternativeConfirmation(): WearActionConfirmation.PullViolation? {
    if (this !is WearActionConfirmation.PullViolation) return null
    val alternative = options.firstOrNull { it.violation != selectedViolation } ?: return null
    return copy(selectedViolation = alternative.violation, prompt = alternative.prompt)
}

/** Format the compact action that switches a mixed pull violation on the watch. */
internal fun PullViolationType.alternativeActionLabel(): String {
    return when (this) {
        PullViolationType.OFFSIDES -> "→ Offsides"
        PullViolationType.MAJORITY_PULL -> "→ Majority pull viol."
        PullViolationType.FALSE_START -> error("False start has no alternative pull violation.")
    }
}

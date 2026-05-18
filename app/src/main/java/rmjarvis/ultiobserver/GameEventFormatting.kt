package rmjarvis.ultiobserver

/// Format UI-facing text for a model event in the current Android app.
fun GameEvent.formatMessage(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatMessage()
        is GameEvent.TimeoutUnavailable -> this.formatMessage()
        is GameEvent.TeamOutOfTimeouts -> this.formatMessage()
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullInfractionRecorded -> this.formatMessage()
        is GameEvent.TimeViolationRecorded -> this.formatMessage()
    }
}

/// Format the title for an event-driven popup.
fun GameEvent.formatPopupTitle(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatPopupTitle()
        is GameEvent.TimeoutUnavailable -> this.formatPopupTitle()
        is GameEvent.TeamOutOfTimeouts -> this.formatPopupTitle()
        is GameEvent.TeamCardsChanged -> this.formatPopupTitle()
        is GameEvent.TechnicalFoulsChanged -> this.formatPopupTitle()
        is GameEvent.PullInfractionRecorded -> this.formatPopupTitle()
        is GameEvent.TimeViolationRecorded -> this.formatPopupTitle()
    }
}

/**
 * Return a singular or plural noun for a count.
 *
 * @param count The count controlling pluralization.
 * @param singular The singular noun form.
 */
internal fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}

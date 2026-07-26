package rmjarvis.ultiobserver

/**
 * One line of observer guidance with its semantic emphasis explicitly specified.
 *
 * @param text Text rendered on this line.
 * @param bold Whether this line should be rendered in bold by the UI.
 */
internal data class RuleGuidanceLine(
    val text: String,
    val bold: Boolean = false,
)

/**
 * Structured observer guidance whose emphasis does not need to be inferred from finished prose.
 *
 * @param lines Lines in display order, including empty lines used between paragraphs.
 */
internal data class RuleGuidanceMessage(
    val lines: List<RuleGuidanceLine>,
) {
    /// Return the same message without presentation metadata.
    val plainText: String
        get() = lines.joinToString("\n") { it.text }
}

/// Model events that report user-visible results from game actions.
sealed interface GameEvent {
    /**
     * Event confirming a timeout charged to a team.
     *
     * @param state The live state after charging the timeout.
     * @param team The team charged with the timeout.
     */
    data class TimeoutCharged(
        val state: GameState,
        val team: TeamId,
    ) : GameEvent

    /**
     * Event reporting that timeout rules do not allow a timeout in the current state.
     *
     * @param state The live state that rejected the timeout.
     */
    data class TimeoutUnavailable(
        val state: GameState,
    ) : GameEvent

    /**
     * Event reporting that a team has no remaining timeouts in the current half.
     *
     * @param state The live state that rejected the timeout.
     * @param team The team with no remaining timeouts.
     */
    data class TeamOutOfTimeouts(
        val state: GameState,
        val team: TeamId,
    ) : GameEvent

    /**
     * Event reporting a changed team-card total, with optional player-card context.
     *
     * @param state The live state after the card action.
     * @param team The team whose card total changed.
     * @param teamCardTotal The team-card point total after the action.
     * @param playerCardType The player-card event type, or null for team-only card changes.
     * @param playerCardJerseyNumber The player jersey number when playerCardType is present.
     * @param playerCardName The player name when entered for this player-card event.
     */
    data class TeamCardsChanged(
        val state: GameState,
        val team: TeamId,
        val teamCardTotal: Int,
        val playerCardType: PlayerCardEventType? = null,
        val playerCardJerseyNumber: String? = null,
        val playerCardName: String? = null,
    ) : GameEvent {
        init {
            require((playerCardType == null) == (playerCardJerseyNumber == null))
        }
    }

    /**
     * Event reporting a technical-foul total after a technical-foul action.
     *
     * @param state The live state after recording the technical foul.
     * @param team The team receiving the technical foul.
     * @param technicalFoulTotal The team's technical-foul total after the action.
     */
    data class TechnicalFoulsChanged(
        val state: GameState,
        val team: TeamId,
        val technicalFoulTotal: Int,
    ) : GameEvent

    /**
     * Event reporting a pull violation and its rule consequence.
     *
     * @param state The live state after recording the violation.
     * @param team The team that committed the violation.
     * @param violation The type of pull violation recorded.
     * @param totalPullViolations The team's combined pull-violation total after the action.
     */
    data class PullViolationRecorded(
        val state: GameState,
        val team: TeamId,
        val violation: PullViolationType,
        val totalPullViolations: Int,
    ) : GameEvent

    /**
     * Event reporting a pull time violation and its rule outcome.
     *
     * @param state The live state after assessing the violation.
     * @param team The team that committed the time violation.
     * @param outcome The warning, timeout, or no-timeout outcome.
     */
    data class TimeViolationRecorded(
        val state: GameState,
        val team: TeamId,
        val outcome: TimeViolationOutcome,
    ) : GameEvent
}

/// Report whether None mode must still surface this event briefly.
internal fun GameEvent.requiresGuidanceInNone(): Boolean {
    return when (this) {
        is GameEvent.TimeoutUnavailable,
        is GameEvent.TeamOutOfTimeouts -> true
        is GameEvent.TeamCardsChanged -> hasSuspensionNotice()
        is GameEvent.PullViolationRecorded -> pullViolationAlternative() != null
        else -> false
    }
}

/// Return the structured full or concise guidance selected for a game event.
internal fun GameEvent.guidanceMessage(mode: RuleGuidanceMode): RuleGuidanceMessage {
    return if (mode.usesBriefGuidance()) formatBriefMessage() else formatMessage()
}

// Keep these as extensions instead of GameEvent members. If they become members, Kotlin
// resolves same-named subtype extension helpers to the member and recursively calls this dispatcher.
/// Format full UI-facing guidance for this model event.
internal fun GameEvent.formatMessage(): RuleGuidanceMessage {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatMessage()
        is GameEvent.TimeoutUnavailable -> this.formatMessage()
        is GameEvent.TeamOutOfTimeouts -> this.formatMessage()
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullViolationRecorded -> this.formatMessage()
        is GameEvent.TimeViolationRecorded -> this.formatMessage()
    }
}

/// Format concise operational guidance for a live-game popup.
internal fun GameEvent.formatBriefMessage(): RuleGuidanceMessage {
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatBriefMessage()
        is GameEvent.TimeoutUnavailable -> this.formatMessage()
        is GameEvent.TeamOutOfTimeouts -> this.formatMessage()
        is GameEvent.TeamCardsChanged -> this.formatBriefMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatBriefMessage()
        is GameEvent.PullViolationRecorded -> this.formatBriefMessage()
        is GameEvent.TimeViolationRecorded -> this.formatBriefMessage()
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
        is GameEvent.PullViolationRecorded -> this.formatPopupTitle()
        is GameEvent.TimeViolationRecorded -> this.formatPopupTitle()
    }
}

/// Model prompts that require an observer decision or acknowledgement.
sealed interface GamePrompt {
    /**
     * Prompt asking whether to apply a due cap now.
     *
     * @param state The live state with a pending cap offer.
     * @param capType The cap being offered.
     */
    data class ApplyCap(
        val state: GameState,
        val capType: CapType,
    ) : GamePrompt

    /**
     * Prompt asking whether live-point misconduct was against the offense or defense.
     *
     * @param event The card or technical-foul event that triggered the misconduct prompt.
     */
    data class LivePointMisconduct(
        val event: GameEvent,
    ) : GamePrompt

    /**
     * Prompt notifying the observer that halftime has started.
     *
     * @param state The live state after entering halftime.
     */
    data class HalftimeStarted(
        val state: GameState,
    ) : GamePrompt

    /**
     * Prompt notifying the observer that the game has ended.
     *
     * @param state The completed live state.
     */
    data class GameOver(
        val state: GameState,
    ) : GamePrompt
}

/// Report whether None mode must still surface this prompt briefly.
internal fun GamePrompt.requiresGuidanceInNone(): Boolean {
    return this is GamePrompt.ApplyCap
}

/// Format title text for prompts that need a dialog title in the current Android app.
fun GamePrompt.formatTitle(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatTitle()
        is GamePrompt.LivePointMisconduct -> this.formatTitle()
        is GamePrompt.HalftimeStarted -> this.formatTitle()
        is GamePrompt.GameOver -> this.formatTitle()
    }
}

/// Format the main text shown to the observer for a prompt.
internal fun GamePrompt.formatMessage(): RuleGuidanceMessage {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatMessage()
        is GamePrompt.LivePointMisconduct -> this.formatMessage()
        is GamePrompt.HalftimeStarted -> this.formatMessage()
        is GamePrompt.GameOver -> this.formatMessage()
    }
}

/// Format the title for a halftime-started prompt.
private fun GamePrompt.HalftimeStarted.formatTitle(): String = "Halftime"

/// Format the halftime-started prompt body.
private fun GamePrompt.HalftimeStarted.formatMessage(): RuleGuidanceMessage {
    return RuleGuidanceMessage(
        listOf(RuleGuidanceLine("Announce halftime."))
    )
}

/// Format the title for a game-over prompt.
private fun GamePrompt.GameOver.formatTitle(): String {
    return if (state.rules.heatLevel == HeatLevel.LEVEL_3) {
        "Game suspended"
    } else {
        "Game over"
    }
}

/// Format the game-over prompt body with the winner first.
private fun GamePrompt.GameOver.formatMessage(): RuleGuidanceMessage {
    val orderedTeams = state.winnerFirstTeams()
    return RuleGuidanceMessage(
        listOf(
            RuleGuidanceLine("${orderedTeams[0].name} ${orderedTeams[0].score}"),
            RuleGuidanceLine("${orderedTeams[1].name} ${orderedTeams[1].score}"),
        )
    )
}

package rmjarvis.ultiobserver

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
     * Event reporting a pull infraction and its rule consequence.
     *
     * @param state The live state after recording the infraction.
     * @param team The team that committed the infraction.
     * @param infraction The type of pull infraction recorded.
     * @param totalPullViolations The team's combined pull-violation total after the action.
     */
    data class PullInfractionRecorded(
        val state: GameState,
        val team: TeamId,
        val infraction: PullInfractionType,
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

// Keep these as extensions instead of GameEvent members. If they become members, Kotlin
// resolves same-named subtype extension helpers to the member and recursively calls this dispatcher.
/// Format UI-facing text for this model event in the current Android app.
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
fun GamePrompt.formatMessage(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatMessage()
        is GamePrompt.LivePointMisconduct -> this.formatMessage()
        is GamePrompt.HalftimeStarted -> "Announce halftime."
        is GamePrompt.GameOver -> this.formatMessage()
    }
}

/// Format the title for a halftime-started prompt.
private fun GamePrompt.HalftimeStarted.formatTitle(): String = "Halftime"

/// Format the title for a game-over prompt.
private fun GamePrompt.GameOver.formatTitle(): String = "Game over"

/// Format the game-over prompt body with the winner first.
private fun GamePrompt.GameOver.formatMessage(): String {
    val orderedTeams = state.winnerFirstTeams()
    return buildString {
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

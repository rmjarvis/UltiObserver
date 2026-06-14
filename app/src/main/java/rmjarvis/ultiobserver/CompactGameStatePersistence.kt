@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Compact persisted form of the current/setup game bucket.
 *
 * @param liveState The live game stored with compact undo/redo chains.
 */
@Serializable
internal data class PersistedCurrentGameSnapshot(
    val versionName: String,
    val versionCode: Int,
    val setupState: GameSetupState,
    val liveState: PersistedGameState?,
    val setupMode: SetupMode,
    val hasSetupDraft: Boolean,
) {
    /// Convert this storage shape back to the app-facing current-game bucket.
    fun toCurrentGameSnapshot(): CurrentGameSnapshot {
        return CurrentGameSnapshot(
            versionName = versionName,
            versionCode = versionCode,
            setupState = setupState,
            liveState = liveState?.restore(),
            setupMode = setupMode,
            hasSetupDraft = hasSetupDraft,
        )
    }

    companion object {
        /**
         * Build compact storage from the app-facing current-game bucket.
         *
         * @param state The current-game bucket to persist.
         */
        fun fromCurrentGameSnapshot(state: CurrentGameSnapshot): PersistedCurrentGameSnapshot {
            return PersistedCurrentGameSnapshot(
                versionName = state.versionName,
                versionCode = state.versionCode,
                setupState = state.setupState,
                liveState = state.liveState?.toPersistedGameState(),
                setupMode = state.setupMode,
                hasSetupDraft = state.hasSetupDraft,
            )
        }
    }
}

/**
 * Compact persisted form of one game state and its undo/redo history.
 *
 * @param state The current state without undo or redo links.
 * @param undoEntry Patch chain that reconstructs the previous states from `state`.
 * @param redoEntry Optional redo state, also stored compactly.
 */
@Serializable
internal data class PersistedGameState(
    val state: GameState,
    val undoEntry: PersistedUndoEntry?,
    val redoEntry: PersistedGameState?,
) {
    /// Restore the full app-facing game state.
    fun restore(): GameState {
        val restoredState = state.withoutUndoRedo()
        return restoredState.copy(
            undoEntry = undoEntry?.restoreUndoEntry(restoredState),
            redoEntry = redoEntry?.restore(),
        )
    }
}

/**
 * Compact persisted undo entry.
 *
 * @param label The user-facing undo label.
 * @param patchToPrevious Fields that turn the later state into the previous state.
 * @param previousUndoEntry The previous state's own compact undo entry, if any.
 */
@Serializable
internal data class PersistedUndoEntry(
    val label: String,
    val patchToPrevious: GameStatePatch,
    val previousUndoEntry: PersistedUndoEntry?,
) {
    /**
     * Restore an app-facing undo entry from the later state that owns it.
     *
     * @param later The state from which this undo entry can restore the previous state.
     */
    fun restoreUndoEntry(later: GameState): UndoEntry {
        val previousBase = patchToPrevious.applyTo(later.withoutUndoRedo())
        val previous = previousBase.copy(
            undoEntry = previousUndoEntry?.restoreUndoEntry(previousBase),
        )
        return UndoEntry(label = label, previous = previous)
    }
}

/**
 * Field patch that recreates a previous `GameState` from a later state.
 *
 * Nullable game fields use `NullablePatchValue` so "unchanged" and "changed to null" stay distinct.
 */
@Serializable
internal data class GameStatePatch(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = LocalDateAsStringSerializer::class)
    val startDate: LocalDate? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @Serializable(with = ZoneIdAsStringSerializer::class)
    val timeZone: ZoneId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val startEpoch: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endEpoch: NullablePatchValue<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tournamentName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val division: NullablePatchValue<GameDivision>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val gameContext: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rules: GameRules? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamOne: TeamLiveStatePatch? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamTwo: TeamLiveStatePatch? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val priorCards: ListPatch<PlayerCardRecord>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamOnePlayerCards: ListPatch<InGamePlayerCardRecord>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamTwoPlayerCards: ListPatch<InGamePlayerCardRecord>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val eventLog: ListPatch<EventLogEntry>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nearAttackingTeam: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullingTeam: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullingFromEnd: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val openingPullingTeam: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val openingPullingFromEnd: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val phase: GamePhase? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val countdown: NullablePatchValue<CountdownState>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullCountdownExpired: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullSequenceOffsidesRecorded: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullSequenceFalseStartRecorded: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullSkippedForCurrentPoint: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pendingMisconductCountdown: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val halftimeTaken: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val halftimeTargetScore: NullablePatchValue<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val winningScore: NullablePatchValue<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val halfCapApplied: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val softCapApplied: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val hardCapApplied: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pendingCapOffer: NullablePatchValue<CapType>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lastEvent: String? = null,
) {
    /**
     * Apply this patch to a later state, returning the previous state without undo/redo links.
     *
     * @param later The later state in the undo chain.
     */
    fun applyTo(later: GameState): GameState {
        return later.copy(
            startDate = startDate ?: later.startDate,
            startTime = startTime ?: later.startTime,
            timeZone = timeZone ?: later.timeZone,
            startEpoch = startEpoch ?: later.startEpoch,
            endEpoch = if (endEpoch != null) endEpoch.value else later.endEpoch,
            tournamentName = tournamentName ?: later.tournamentName,
            division = if (division != null) division.value else later.division,
            gameContext = gameContext ?: later.gameContext,
            rules = rules ?: later.rules,
            teamOne = teamOne?.applyTo(later.teamOne) ?: later.teamOne,
            teamTwo = teamTwo?.applyTo(later.teamTwo) ?: later.teamTwo,
            priorCards = priorCards?.applyTo(later.priorCards) ?: later.priorCards,
            teamOnePlayerCards = teamOnePlayerCards?.applyTo(later.teamOnePlayerCards) ?: later.teamOnePlayerCards,
            teamTwoPlayerCards = teamTwoPlayerCards?.applyTo(later.teamTwoPlayerCards) ?: later.teamTwoPlayerCards,
            eventLog = eventLog?.applyTo(later.eventLog) ?: later.eventLog,
            nearAttackingTeam = nearAttackingTeam ?: later.nearAttackingTeam,
            pullingTeam = pullingTeam ?: later.pullingTeam,
            pullingFromEnd = pullingFromEnd ?: later.pullingFromEnd,
            openingPullingTeam = openingPullingTeam ?: later.openingPullingTeam,
            openingPullingFromEnd = openingPullingFromEnd ?: later.openingPullingFromEnd,
            phase = phase ?: later.phase,
            countdown = if (countdown != null) countdown.value else later.countdown,
            pullCountdownExpired = pullCountdownExpired ?: later.pullCountdownExpired,
            pullSequenceOffsidesRecorded = pullSequenceOffsidesRecorded ?: later.pullSequenceOffsidesRecorded,
            pullSequenceFalseStartRecorded = pullSequenceFalseStartRecorded ?: later.pullSequenceFalseStartRecorded,
            pullSkippedForCurrentPoint = pullSkippedForCurrentPoint ?: later.pullSkippedForCurrentPoint,
            pendingMisconductCountdown = pendingMisconductCountdown ?: later.pendingMisconductCountdown,
            halftimeTaken = halftimeTaken ?: later.halftimeTaken,
            halftimeTargetScore = if (halftimeTargetScore != null) halftimeTargetScore.value else later.halftimeTargetScore,
            winningScore = if (winningScore != null) winningScore.value else later.winningScore,
            halfCapApplied = halfCapApplied ?: later.halfCapApplied,
            softCapApplied = softCapApplied ?: later.softCapApplied,
            hardCapApplied = hardCapApplied ?: later.hardCapApplied,
            pendingCapOffer = if (pendingCapOffer != null) pendingCapOffer.value else later.pendingCapOffer,
            undoEntry = null,
            redoEntry = null,
            lastEvent = lastEvent ?: later.lastEvent,
        )
    }

    companion object {
        /**
         * Build a patch that turns a later state into its previous state.
         *
         * @param later The later state in the undo chain, without undo/redo links.
         * @param previous The state restored by undoing the later state, without undo/redo links.
         */
        fun fromLaterAndPrevious(later: GameState, previous: GameState): GameStatePatch {
            return GameStatePatch(
                startDate = previous.startDate.takeIfChangedFrom(later.startDate),
                startTime = previous.startTime.takeIfChangedFrom(later.startTime),
                timeZone = previous.timeZone.takeIfChangedFrom(later.timeZone),
                startEpoch = previous.startEpoch.takeIfChangedFrom(later.startEpoch),
                endEpoch = nullablePatch(later.endEpoch, previous.endEpoch),
                tournamentName = previous.tournamentName.takeIfChangedFrom(later.tournamentName),
                division = nullablePatch(later.division, previous.division),
                gameContext = previous.gameContext.takeIfChangedFrom(later.gameContext),
                rules = previous.rules.takeIfChangedFrom(later.rules),
                teamOne = TeamLiveStatePatch.fromLaterAndPrevious(later.teamOne, previous.teamOne),
                teamTwo = TeamLiveStatePatch.fromLaterAndPrevious(later.teamTwo, previous.teamTwo),
                priorCards = ListPatch.fromLaterAndPrevious(later.priorCards, previous.priorCards),
                teamOnePlayerCards = ListPatch.fromLaterAndPrevious(
                    later.teamOnePlayerCards,
                    previous.teamOnePlayerCards,
                ),
                teamTwoPlayerCards = ListPatch.fromLaterAndPrevious(
                    later.teamTwoPlayerCards,
                    previous.teamTwoPlayerCards,
                ),
                eventLog = ListPatch.fromLaterAndPrevious(later.eventLog, previous.eventLog),
                nearAttackingTeam = previous.nearAttackingTeam.takeIfChangedFrom(later.nearAttackingTeam),
                pullingTeam = previous.pullingTeam.takeIfChangedFrom(later.pullingTeam),
                pullingFromEnd = previous.pullingFromEnd.takeIfChangedFrom(later.pullingFromEnd),
                openingPullingTeam = previous.openingPullingTeam.takeIfChangedFrom(later.openingPullingTeam),
                openingPullingFromEnd = previous.openingPullingFromEnd.takeIfChangedFrom(later.openingPullingFromEnd),
                phase = previous.phase.takeIfChangedFrom(later.phase),
                countdown = nullablePatch(later.countdown, previous.countdown),
                pullCountdownExpired = previous.pullCountdownExpired.takeIfChangedFrom(later.pullCountdownExpired),
                pullSequenceOffsidesRecorded = previous.pullSequenceOffsidesRecorded.takeIfChangedFrom(
                    later.pullSequenceOffsidesRecorded,
                ),
                pullSequenceFalseStartRecorded = previous.pullSequenceFalseStartRecorded.takeIfChangedFrom(
                    later.pullSequenceFalseStartRecorded,
                ),
                pullSkippedForCurrentPoint = previous.pullSkippedForCurrentPoint.takeIfChangedFrom(
                    later.pullSkippedForCurrentPoint,
                ),
                pendingMisconductCountdown = previous.pendingMisconductCountdown.takeIfChangedFrom(
                    later.pendingMisconductCountdown,
                ),
                halftimeTaken = previous.halftimeTaken.takeIfChangedFrom(later.halftimeTaken),
                halftimeTargetScore = nullablePatch(later.halftimeTargetScore, previous.halftimeTargetScore),
                winningScore = nullablePatch(later.winningScore, previous.winningScore),
                halfCapApplied = previous.halfCapApplied.takeIfChangedFrom(later.halfCapApplied),
                softCapApplied = previous.softCapApplied.takeIfChangedFrom(later.softCapApplied),
                hardCapApplied = previous.hardCapApplied.takeIfChangedFrom(later.hardCapApplied),
                pendingCapOffer = nullablePatch(later.pendingCapOffer, previous.pendingCapOffer),
                lastEvent = previous.lastEvent.takeIfChangedFrom(later.lastEvent),
            )
        }
    }
}

/**
 * Patch for a nullable value.
 *
 * @param value The previous value, including null when the previous state had no value.
 */
@Serializable
internal data class NullablePatchValue<T>(
    val value: T?,
)

/**
 * Patch for a list field.
 *
 * @param previousSize Size to truncate the later list to when the previous list is a prefix.
 * @param replacement Full previous list when a prefix patch is not possible.
 */
@Serializable
internal data class ListPatch<T>(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previousSize: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val replacement: List<T>? = null,
) {
    init {
        require((previousSize == null) != (replacement == null)) {
            "A list patch must use exactly one representation."
        }
    }

    /**
     * Apply this patch to a later list.
     *
     * @param later The later list to patch back to a previous value.
     */
    fun applyTo(later: List<T>): List<T> {
        return previousSize?.let { later.take(it) } ?: replacement!!
    }

    companion object {
        /**
         * Build a list patch when a previous list differs from a later list.
         *
         * @param later The later list in the undo chain.
         * @param previous The previous list restored by undo.
         */
        fun <T> fromLaterAndPrevious(later: List<T>, previous: List<T>): ListPatch<T>? {
            if (previous == later) {
                return null
            }
            return if (previous.size <= later.size && later.subList(0, previous.size) == previous) {
                ListPatch(previousSize = previous.size)
            } else {
                ListPatch(replacement = previous)
            }
        }
    }
}

/**
 * Patch for one team's live counters and display identity.
 */
@Serializable
internal data class TeamLiveStatePatch(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val color: TeamColorChoice? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val customColorArgb: NullablePatchValue<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val coaches: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fieldCaptains: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val spiritCaptains: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val score: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val timeoutsUsedThisHalf: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val firstHalfTimeoutsUsed: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val offsides: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val falseStarts: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val timeViolationWarningIssued: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val technicalFouls: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val blueCards: Int? = null,
) {
    /**
     * Apply this patch to a later team state.
     *
     * @param later The later team state.
     */
    fun applyTo(later: TeamLiveState): TeamLiveState {
        return later.copy(
            name = name ?: later.name,
            color = color ?: later.color,
            customColorArgb = if (customColorArgb != null) customColorArgb.value else later.customColorArgb,
            coaches = coaches ?: later.coaches,
            fieldCaptains = fieldCaptains ?: later.fieldCaptains,
            spiritCaptains = spiritCaptains ?: later.spiritCaptains,
            score = score ?: later.score,
            timeoutsUsedThisHalf = timeoutsUsedThisHalf ?: later.timeoutsUsedThisHalf,
            firstHalfTimeoutsUsed = firstHalfTimeoutsUsed ?: later.firstHalfTimeoutsUsed,
            offsides = offsides ?: later.offsides,
            falseStarts = falseStarts ?: later.falseStarts,
            timeViolationWarningIssued = timeViolationWarningIssued ?: later.timeViolationWarningIssued,
            technicalFouls = technicalFouls ?: later.technicalFouls,
            blueCards = blueCards ?: later.blueCards,
        )
    }

    companion object {
        /**
         * Build a team-state patch when previous team fields differ from later team fields.
         *
         * @param later The later team state in the undo chain.
         * @param previous The previous team state restored by undo.
         */
        fun fromLaterAndPrevious(later: TeamLiveState, previous: TeamLiveState): TeamLiveStatePatch? {
            if (previous == later) {
                return null
            }
            return TeamLiveStatePatch(
                name = previous.name.takeIfChangedFrom(later.name),
                color = previous.color.takeIfChangedFrom(later.color),
                customColorArgb = nullablePatch(later.customColorArgb, previous.customColorArgb),
                coaches = previous.coaches.takeIfChangedFrom(later.coaches),
                fieldCaptains = previous.fieldCaptains.takeIfChangedFrom(later.fieldCaptains),
                spiritCaptains = previous.spiritCaptains.takeIfChangedFrom(later.spiritCaptains),
                score = previous.score.takeIfChangedFrom(later.score),
                timeoutsUsedThisHalf = previous.timeoutsUsedThisHalf.takeIfChangedFrom(later.timeoutsUsedThisHalf),
                firstHalfTimeoutsUsed = previous.firstHalfTimeoutsUsed.takeIfChangedFrom(later.firstHalfTimeoutsUsed),
                offsides = previous.offsides.takeIfChangedFrom(later.offsides),
                falseStarts = previous.falseStarts.takeIfChangedFrom(later.falseStarts),
                timeViolationWarningIssued = previous.timeViolationWarningIssued.takeIfChangedFrom(
                    later.timeViolationWarningIssued,
                ),
                technicalFouls = previous.technicalFouls.takeIfChangedFrom(later.technicalFouls),
                blueCards = previous.blueCards.takeIfChangedFrom(later.blueCards),
            )
        }
    }
}

/// Convert a game state and its history to compact persistence.
private fun GameState.toPersistedGameState(): PersistedGameState {
    return PersistedGameState(
        state = withoutUndoRedo(),
        undoEntry = undoEntry?.toPersistedUndoEntry(later = this),
        redoEntry = redoEntry?.toPersistedGameState(),
    )
}

/**
 * Convert an undo entry to compact persistence.
 *
 * @param later The later state that owns this undo entry.
 */
private fun UndoEntry.toPersistedUndoEntry(later: GameState): PersistedUndoEntry {
    return PersistedUndoEntry(
        label = label,
        patchToPrevious = GameStatePatch.fromLaterAndPrevious(
            later = later.withoutUndoRedo(),
            previous = previous.withoutUndoRedo(),
        ),
        previousUndoEntry = previous.undoEntry?.toPersistedUndoEntry(later = previous),
    )
}

/// Return this game state with undo and redo links removed but all live fields preserved.
private fun GameState.withoutUndoRedo(): GameState {
    return copy(undoEntry = null, redoEntry = null)
}

private fun <T> T.takeIfChangedFrom(later: T): T? {
    return takeIf { this != later }
}

private fun <T> nullablePatch(
    later: T?,
    previous: T?,
): NullablePatchValue<T>? {
    return if (previous == later) null else NullablePatchValue(previous)
}

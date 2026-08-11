@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Serialized form of one game state and its undo/redo history.
 *
 * This stores the current state directly and represents history with the patch-chain
 * serialization described in `SerializedUndoEntry`.
 *
 * The root state's official-clock offset applies to the entire serialized history. Historical
 * states are normalized to that offset before serialization because clock synchronization is not
 * an undoable game action.
 *
 * @param state The current state without undo or redo links.
 * @param undoEntry Patch chain that reconstructs the previous states from `state`.
 * @param redoEntry Optional redo state, also stored as serialized state.
 */
@Serializable
internal data class SerializedGameState(
    val state: GameState,
    val undoEntry: SerializedUndoEntry?,
    val redoEntry: SerializedGameState?,
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
 * Serialized undo entry.
 *
 * A normal serialized `UndoEntry` would contain a full previous `GameState`. Since most
 * undo entries differ from the current state in only a few fields, that natural shape is
 * dominated by repeated fields whose values are null, default, or unchanged. That is
 * especially expensive for persistence because the current-game bucket is rewritten often
 * and can contain a long undo/redo chain.
 *
 * Instead, each undo entry stores a `GameStatePatch` from the later state to the previous
 * state. The patch contains only fields that differ, with `NullablePatchValue` used where
 * "unchanged" and "changed to null" must stay distinct. Restoring walks the chain from the
 * current state backward: apply this patch to the later state, then attach the restored
 * previous undo entry, if there is one.
 *
 * @param label The user-facing undo label.
 * @param patchToPrevious Fields that turn the later state into the previous state.
 * @param previousUndoEntry The previous state's own serialized undo entry, if any.
 */
@Serializable
internal data class SerializedUndoEntry(
    val label: String,
    val patchToPrevious: GameStatePatch,
    val previousUndoEntry: SerializedUndoEntry?,
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
    val endEpoch: NullablePatchValue<Long>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tournamentName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val division: NullablePatchValue<GameDivision>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val level: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val gameContext: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val observerNames: ListPatch<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fieldName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nearEndName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val farEndName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rules: GameRules? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamOne: TeamStatePatch? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamTwo: TeamStatePatch? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamOnePlayers: ListPatch<PlayerRecord>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamTwoPlayers: ListPatch<PlayerRecord>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val eventLog: ListPatch<EventLogEntry>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullingTeam: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullingFromEnd: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topDisplayedEnd: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pullPromptTarget: PullPromptTarget? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val initialGenderRatio: GenderRatio? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val firstHalfGenZone: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val openingPullingTeam: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val openingPullingFromEnd: FieldEnd? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val phase: GamePhase? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val countdown: NullablePatchValue<CountdownState>? = null,
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
    val halftimeHighScore: NullablePatchValue<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pendingWaterBreakOffer: Boolean? = null,
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
    val pendingScoreTransition: NullablePatchValue<PendingScoreTransition>? = null,
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
            endEpoch = if (endEpoch != null) endEpoch.value else later.endEpoch,
            tournamentName = tournamentName ?: later.tournamentName,
            division = if (division != null) division.value else later.division,
            level = level ?: later.level,
            gameContext = gameContext ?: later.gameContext,
            observerNames = observerNames?.applyTo(later.observerNames) ?: later.observerNames,
            fieldName = fieldName ?: later.fieldName,
            nearEndName = nearEndName ?: later.nearEndName,
            farEndName = farEndName ?: later.farEndName,
            rules = rules ?: later.rules,
            teamOne = teamOne?.applyTo(later.teamOne) ?: later.teamOne,
            teamTwo = teamTwo?.applyTo(later.teamTwo) ?: later.teamTwo,
            teamOnePlayers = teamOnePlayers?.applyTo(later.teamOnePlayers) ?: later.teamOnePlayers,
            teamTwoPlayers = teamTwoPlayers?.applyTo(later.teamTwoPlayers) ?: later.teamTwoPlayers,
            eventLog = eventLog?.applyTo(later.eventLog) ?: later.eventLog,
            pullingTeam = pullingTeam ?: later.pullingTeam,
            pullingFromEnd = pullingFromEnd ?: later.pullingFromEnd,
            topDisplayedEnd = topDisplayedEnd ?: later.topDisplayedEnd,
            pullPromptTarget = pullPromptTarget ?: later.pullPromptTarget,
            initialGenderRatio = initialGenderRatio ?: later.initialGenderRatio,
            firstHalfGenZone = firstHalfGenZone ?: later.firstHalfGenZone,
            openingPullingTeam = openingPullingTeam ?: later.openingPullingTeam,
            openingPullingFromEnd = openingPullingFromEnd ?: later.openingPullingFromEnd,
            phase = phase ?: later.phase,
            countdown = if (countdown != null) countdown.value else later.countdown,
            pullSequenceOffsidesRecorded = pullSequenceOffsidesRecorded ?: later.pullSequenceOffsidesRecorded,
            pullSequenceFalseStartRecorded = pullSequenceFalseStartRecorded ?: later.pullSequenceFalseStartRecorded,
            pullSkippedForCurrentPoint = pullSkippedForCurrentPoint ?: later.pullSkippedForCurrentPoint,
            pendingMisconductCountdown = pendingMisconductCountdown ?: later.pendingMisconductCountdown,
            halftimeTaken = halftimeTaken ?: later.halftimeTaken,
            halftimeTargetScore = if (halftimeTargetScore != null) halftimeTargetScore.value else later.halftimeTargetScore,
            halftimeHighScore = if (halftimeHighScore != null) halftimeHighScore.value else later.halftimeHighScore,
            pendingWaterBreakOffer = pendingWaterBreakOffer ?: later.pendingWaterBreakOffer,
            winningScore = if (winningScore != null) winningScore.value else later.winningScore,
            halfCapApplied = halfCapApplied ?: later.halfCapApplied,
            softCapApplied = softCapApplied ?: later.softCapApplied,
            hardCapApplied = hardCapApplied ?: later.hardCapApplied,
            pendingCapOffer = if (pendingCapOffer != null) pendingCapOffer.value else later.pendingCapOffer,
            pendingScoreTransition = if (pendingScoreTransition != null) {
                pendingScoreTransition.value
            } else {
                later.pendingScoreTransition
            },
            undoEntry = null,
            redoEntry = null,
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
                endEpoch = nullablePatch(later.endEpoch, previous.endEpoch),
                tournamentName = previous.tournamentName.takeIfChangedFrom(later.tournamentName),
                division = nullablePatch(later.division, previous.division),
                level = previous.level.takeIfChangedFrom(later.level),
                gameContext = previous.gameContext.takeIfChangedFrom(later.gameContext),
                observerNames = ListPatch.fromLaterAndPrevious(
                    later.observerNames,
                    previous.observerNames,
                ),
                fieldName = previous.fieldName.takeIfChangedFrom(later.fieldName),
                nearEndName = previous.nearEndName.takeIfChangedFrom(later.nearEndName),
                farEndName = previous.farEndName.takeIfChangedFrom(later.farEndName),
                rules = previous.rules.takeIfChangedFrom(later.rules),
                teamOne = TeamStatePatch.fromLaterAndPrevious(later.teamOne, previous.teamOne),
                teamTwo = TeamStatePatch.fromLaterAndPrevious(later.teamTwo, previous.teamTwo),
                teamOnePlayers = ListPatch.fromLaterAndPrevious(
                    later.teamOnePlayers,
                    previous.teamOnePlayers,
                ),
                teamTwoPlayers = ListPatch.fromLaterAndPrevious(
                    later.teamTwoPlayers,
                    previous.teamTwoPlayers,
                ),
                eventLog = ListPatch.fromLaterAndPrevious(later.eventLog, previous.eventLog),
                pullingTeam = previous.pullingTeam.takeIfChangedFrom(later.pullingTeam),
                pullingFromEnd = previous.pullingFromEnd.takeIfChangedFrom(later.pullingFromEnd),
                topDisplayedEnd = previous.topDisplayedEnd.takeIfChangedFrom(later.topDisplayedEnd),
                pullPromptTarget = previous.pullPromptTarget.takeIfChangedFrom(later.pullPromptTarget),
                initialGenderRatio = previous.initialGenderRatio.takeIfChangedFrom(later.initialGenderRatio),
                firstHalfGenZone = previous.firstHalfGenZone.takeIfChangedFrom(later.firstHalfGenZone),
                openingPullingTeam = previous.openingPullingTeam.takeIfChangedFrom(later.openingPullingTeam),
                openingPullingFromEnd = previous.openingPullingFromEnd.takeIfChangedFrom(later.openingPullingFromEnd),
                phase = previous.phase.takeIfChangedFrom(later.phase),
                countdown = nullablePatch(later.countdown, previous.countdown),
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
                halftimeHighScore = nullablePatch(later.halftimeHighScore, previous.halftimeHighScore),
                pendingWaterBreakOffer = previous.pendingWaterBreakOffer
                    .takeIfChangedFrom(later.pendingWaterBreakOffer),
                winningScore = nullablePatch(later.winningScore, previous.winningScore),
                halfCapApplied = previous.halfCapApplied.takeIfChangedFrom(later.halfCapApplied),
                softCapApplied = previous.softCapApplied.takeIfChangedFrom(later.softCapApplied),
                hardCapApplied = previous.hardCapApplied.takeIfChangedFrom(later.hardCapApplied),
                pendingCapOffer = nullablePatch(later.pendingCapOffer, previous.pendingCapOffer),
                pendingScoreTransition = nullablePatch(
                    later.pendingScoreTransition,
                    previous.pendingScoreTransition,
                ),
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
 * Patch for one team's setup fields and game counters.
 */
@Serializable
internal data class TeamStatePatch(
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
    val majorityPullViolations: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val timeViolations: Int? = null,
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
    fun applyTo(later: TeamState): TeamState {
        return later.copy(
            name = name ?: later.name,
            color = color ?: later.color,
            customColorArgb = if (customColorArgb != null) {
                customColorArgb.value
            } else {
                later.customColorArgb
            },
            coaches = coaches ?: later.coaches,
            fieldCaptains = fieldCaptains ?: later.fieldCaptains,
            spiritCaptains = spiritCaptains ?: later.spiritCaptains,
            score = score ?: later.score,
            timeoutsUsedThisHalf = timeoutsUsedThisHalf ?: later.timeoutsUsedThisHalf,
            firstHalfTimeoutsUsed = firstHalfTimeoutsUsed ?: later.firstHalfTimeoutsUsed,
            offsides = offsides ?: later.offsides,
            falseStarts = falseStarts ?: later.falseStarts,
            majorityPullViolations = majorityPullViolations ?: later.majorityPullViolations,
            timeViolations = timeViolations ?: later.timeViolations,
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
        fun fromLaterAndPrevious(later: TeamState, previous: TeamState): TeamStatePatch? {
            if (previous == later) {
                return null
            }
            return TeamStatePatch(
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
                majorityPullViolations = previous.majorityPullViolations.takeIfChangedFrom(
                    later.majorityPullViolations,
                ),
                timeViolations = previous.timeViolations.takeIfChangedFrom(later.timeViolations),
                technicalFouls = previous.technicalFouls.takeIfChangedFrom(later.technicalFouls),
                blueCards = previous.blueCards.takeIfChangedFrom(later.blueCards),
            )
        }
    }
}

/// Convert a game state and its history to serialized state.
internal fun GameState.toSerializedGameState(): SerializedGameState {
    return toSerializedGameState(officialClockOffsetMillis)
}

/** Convert this state and its history using one clock mapping throughout the serialized graph. */
private fun GameState.toSerializedGameState(currentOffsetMillis: Long): SerializedGameState {
    val normalized = withOfficialClockOffset(currentOffsetMillis)
    return SerializedGameState(
        state = normalized.withoutUndoRedo(),
        undoEntry = normalized.undoEntry?.toSerializedUndoEntry(
            later = normalized,
            currentOffsetMillis = currentOffsetMillis,
        ),
        redoEntry = normalized.redoEntry?.toSerializedGameState(currentOffsetMillis),
    )
}

/**
 * Convert an undo entry to serialized state.
 *
 * @param later The later state that owns this undo entry.
 * @param currentOffsetMillis The root state's clock offset to apply throughout its history.
 */
private fun UndoEntry.toSerializedUndoEntry(
    later: GameState,
    currentOffsetMillis: Long,
): SerializedUndoEntry {
    val normalizedPrevious = previous.withOfficialClockOffset(currentOffsetMillis)
    return SerializedUndoEntry(
        label = label,
        patchToPrevious = GameStatePatch.fromLaterAndPrevious(
            later = later.withoutUndoRedo(),
            previous = normalizedPrevious.withoutUndoRedo(),
        ),
        previousUndoEntry = normalizedPrevious.undoEntry?.toSerializedUndoEntry(
            later = normalizedPrevious,
            currentOffsetMillis = currentOffsetMillis,
        ),
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

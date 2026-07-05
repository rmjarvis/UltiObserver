package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Entered player-card details from the yellow/red card dialog.
 *
 * @param jerseyNumber The player's jersey number, or blank for name-only.
 * @param playerName The player's name, or blank when unknown.
 * @param reason Optional observer-entered card reason.
 */
private data class PlayerCardEntry(
    val jerseyNumber: String,
    val playerName: String = "",
    val reason: CardReason = CardReason(),
)

/**
 * Known carded player offered for quick selection.
 *
 * @param jerseyNumber The player's jersey number, or blank for name-only.
 * @param playerName The player's name, or blank when unknown.
 * @param detail Compact card-count detail for this player.
 */
private data class PlayerCardCandidate(
    val jerseyNumber: String,
    val playerName: String,
    val detail: String,
)

/**
 * Live player-card entry with a same-number, different-name conflict awaiting confirmation.
 *
 * @param team The team receiving the card.
 * @param cardType The card being recorded.
 * @param entry Entered player-card details to record if confirmed.
 * @param conflict The conflicting known player identity.
 */
private data class PendingSameNumberPlayerCardConfirmation(
    val team: TeamId,
    val cardType: CardType,
    val entry: PlayerCardEntry,
    val conflict: SameNumberPlayerIdentityConflict,
)

/**
 * Manual card adjustment entry being added.
 *
 * @param team The team receiving the card.
 * @param cardType The card being added.
 * @param initialEntry The entry values to restore when returning to this add dialog.
 */
private data class PendingManualCardAdd(
    val team: TeamId,
    val cardType: CardType,
    val initialEntry: PlayerCardEntry,
)

/**
 * Manual card adjustment entry being edited.
 *
 * @param team The team whose card is being edited.
 * @param card The editable card row.
 */
private data class PendingManualCardEdit(
    val team: TeamId,
    val card: EditablePlayerCard,
)

/**
 * Manual card adjustment entry being removed.
 *
 * @param team The team whose card is being removed.
 * @param card The editable card row.
 */
private data class PendingManualCardRemove(
    val team: TeamId,
    val card: EditablePlayerCard,
)

/// Previous Card-dialog step to restore when dismissing a live-point misconduct choice.
private sealed interface PendingMisconductReturn {
    data class YellowEntry(val team: TeamId, val entry: PlayerCardEntry) : PendingMisconductReturn
    data class RedEntry(val team: TeamId, val entry: PlayerCardEntry) : PendingMisconductReturn
    data class BlueCard(val team: TeamId) : PendingMisconductReturn
}

/**
 * Live-point misconduct assessment waiting for the observer to choose offense or defense.
 *
 * @param result The assessed card or technical-foul result before the side choice is applied.
 * @param returnTo The previous UI step to reopen if the observer dismisses the side-choice prompt.
 */
private data class PendingMisconductChoice(
    val result: CardAssessmentResult,
    val returnTo: PendingMisconductReturn,
)

/**
 * Live-point misconduct consequence waiting for final confirmation.
 *
 * @param choice The offense/defense choice this consequence resolves.
 * @param againstOffense Whether the misconduct was against the offense.
 */
private data class PendingMisconductResolution(
    val choice: PendingMisconductChoice,
    val againstOffense: Boolean,
)

/**
 * One active step in the manual card/tech correction dialog.
 *
 * This mirrors the live Card dialog flow: nested card editors, confirmations, and notices are
 * represented as one value so only the visible dialog step is mounted.
 */
private sealed interface AdjustCardsDialogStep {
    data object CardCounts : AdjustCardsDialogStep
    data class ExistingCards(val team: TeamId) : AdjustCardsDialogStep
    data class CardAdd(val pending: PendingManualCardAdd) : AdjustCardsDialogStep
    data class CardEdit(
        val pending: PendingManualCardEdit,
        val initialEntry: PlayerCardEntry,
    ) : AdjustCardsDialogStep
    data class CardRemove(val pending: PendingManualCardRemove) : AdjustCardsDialogStep
    data class SameNumberConfirmation(
        val confirmation: PendingSameNumberPlayerCardConfirmation
    ) : AdjustCardsDialogStep
    data class InvalidAssignment(
        val message: String,
        val returnTo: AdjustCardsDialogStep,
    ) : AdjustCardsDialogStep
    data class SuspensionNotice(
        val message: String,
        val returnTo: AdjustCardsDialogStep,
    ) : AdjustCardsDialogStep
}

// Manual card/techs correction dialog, including per-player card edits.
@Composable
internal fun AdjustCardsDialog(
    state: GameState,
    now: Long,
    onDismiss: () -> Unit,
    onConfirm: (GameState) -> Unit,
    onStateUpdate: (GameState) -> Unit,
) {
    var teamOneB by remember {
        mutableStateOf(state.teamOne.blueCards)
    }
    var teamOneTf by remember {
        mutableStateOf(state.teamOne.technicalFouls)
    }
    var teamTwoB by remember {
        mutableStateOf(state.teamTwo.blueCards)
    }
    var teamTwoTf by remember {
        mutableStateOf(state.teamTwo.technicalFouls)
    }
    var workingTeamOnePlayerCards by remember {
        mutableStateOf(state.teamOnePlayers)
    }
    var workingTeamTwoPlayerCards by remember {
        mutableStateOf(state.teamTwoPlayers)
    }
    var step by remember {
        mutableStateOf<AdjustCardsDialogStep>(AdjustCardsDialogStep.CardCounts)
    }

    fun recordsFor(team: TeamId): List<PlayerRecord> {
        return if (team == TeamId.TEAM_ONE) workingTeamOnePlayerCards else workingTeamTwoPlayerCards
    }

    fun setRecordsFor(team: TeamId, records: List<PlayerRecord>) {
        if (team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards = records
        } else {
            workingTeamTwoPlayerCards = records
        }
    }

    fun nextManualCardIndex(): Int {
        return state.copy(
            teamOnePlayers = workingTeamOnePlayerCards,
            teamTwoPlayers = workingTeamTwoPlayerCards,
        ).getNextAssessmentIndex()
    }

    fun suspensionNoticeMessage(
        team: TeamId,
        records: List<PlayerRecord>,
        identity: PlayerIdentity
    ): String? {
        val status = playerSuspensionStatus(records, identity) ?: return null
        return "${state.teamFor(team).name} ${identity.displayText(compact = true)} " +
            status.noticeText
    }

    fun GameState.withPlayerCards(
        team: TeamId,
        records: List<PlayerRecord>,
        undoLabel: String,
    ): GameState {
        return adjustCardsAndTf(
            teamOneBlues = teamOne.blueCards,
            teamOneTechnicalFouls = teamOne.technicalFouls,
            teamTwoBlues = teamTwo.blueCards,
            teamTwoTechnicalFouls = teamTwo.technicalFouls,
            teamOnePlayers = if (team == TeamId.TEAM_ONE) records else teamOnePlayers,
            teamTwoPlayers = if (team == TeamId.TEAM_TWO) records else teamTwoPlayers,
            now = now,
            undoLabel = undoLabel,
        )
    }

    fun finalizeAdjustment() {
        onConfirm(
            state.adjustBlueCardsAndTechs(
                teamOneBlues = teamOneB,
                teamOneTechnicalFouls = teamOneTf,
                teamTwoBlues = teamTwoB,
                teamTwoTechnicalFouls = teamTwoTf,
                now = now,
            )
        )
    }

    fun applyManualCardAdd(
        pending: PendingManualCardAdd,
        entry: PlayerCardEntry,
        skipSameNumberWarning: Boolean = false,
    ) {
        val team = pending.team
        val cardType = pending.cardType
        val returnTo = AdjustCardsDialogStep.CardAdd(
            pending.copy(initialEntry = entry)
        )
        if (entry.jerseyNumber.isBlank() && entry.playerName.isBlank()) {
            step = AdjustCardsDialogStep.InvalidAssignment(
                "Enter a player number or name before recording this card.",
                returnTo,
            )
            return
        }
        val identity = PlayerIdentity(entry.jerseyNumber, entry.playerName)
        val records = recordsFor(team)
        if (!skipSameNumberWarning) {
            val conflict = records.sameNumberPlayerIdentityConflict(
                identity.jerseyNumber,
                identity.playerName
            )
            if (conflict != null) {
                step = AdjustCardsDialogStep.SameNumberConfirmation(
                    PendingSameNumberPlayerCardConfirmation(
                        team = team,
                        cardType = cardType,
                        entry = entry.copy(
                            jerseyNumber = identity.jerseyNumber,
                            playerName = identity.playerName
                        ),
                        conflict = conflict,
                    ),
                )
                return
            }
        }
        val status = playerSuspensionStatus(records, identity)
        if (status != null) {
            step = AdjustCardsDialogStep.InvalidAssignment(
                "${state.teamFor(team).name} ${identity.displayText(compact = true)} " +
                    status.rejectionText,
                returnTo,
            )
            return
        }
        val updatedRecords = addPlayerCardAssignment(
            records = records,
            jerseyNumber = identity.jerseyNumber,
            cardType = cardType,
            index = nextManualCardIndex(),
            playerName = identity.playerName,
            reason = entry.reason,
        )
        setRecordsFor(team, updatedRecords)
        val noticeMessage = suspensionNoticeMessage(team, updatedRecords, identity)
        onStateUpdate(
            state.withPlayerCards(
                team = team,
                records = updatedRecords,
                undoLabel = state.playerCardAddUndoLabel(team, cardType, identity),
            )
        )
        step = if (noticeMessage != null) {
            AdjustCardsDialogStep.SuspensionNotice(
                noticeMessage,
                AdjustCardsDialogStep.CardCounts,
            )
        } else {
            AdjustCardsDialogStep.CardCounts
        }
    }

    fun applyManualCardEdit(
        pending: PendingManualCardEdit,
        entry: PlayerCardEntry
    ) {
        val team = pending.team
        val originalCard = pending.card
        val returnTo = AdjustCardsDialogStep.CardEdit(pending, entry)
        if (entry.jerseyNumber.isBlank() && entry.playerName.isBlank()) {
            step = AdjustCardsDialogStep.InvalidAssignment(
                "Enter a player number or name before recording this card.",
                returnTo,
            )
            return
        }
        val identity = PlayerIdentity(entry.jerseyNumber, entry.playerName)
        val recordsAfterRemoval = removeEditablePlayerCard(recordsFor(team), originalCard)
        val status = playerSuspensionStatus(recordsAfterRemoval, identity)
        if (status != null) {
            step = AdjustCardsDialogStep.InvalidAssignment(
                "${state.teamFor(team).name} ${identity.displayText(compact = true)} " +
                    status.rejectionText,
                returnTo,
            )
            return
        }
        val updatedRecords = replaceEditablePlayerCard(
            records = recordsFor(team),
            editableCard = originalCard,
            jerseyNumber = identity.jerseyNumber,
            cardType = originalCard.cardType,
            playerName = identity.playerName,
            reason = entry.reason,
        )
        setRecordsFor(team, updatedRecords)
        val noticeMessage = if (!identity.matches(originalCard.identity())) {
            suspensionNoticeMessage(team, updatedRecords, identity)
        } else {
            null
        }
        onStateUpdate(
            state.withPlayerCards(
                team = team,
                records = updatedRecords,
                undoLabel = state.playerCardEditUndoLabel(team, originalCard.cardType, identity),
            )
        )
        val returnToList = AdjustCardsDialogStep.ExistingCards(team)
        step = if (noticeMessage != null) {
            AdjustCardsDialogStep.SuspensionNotice(noticeMessage, returnToList)
        } else {
            returnToList
        }
    }

    // No else branch: every AdjustCardsDialogStep value is handled.
    when (val activeStep = step) {
        AdjustCardsDialogStep.CardCounts -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Adjust cards / techs") },
                text = {
                    ScrollableDialogRegion(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TeamCorrectionSection(state.teamOne.name) {
                            CardCountRow(
                                label = "Blue",
                                value = teamOneB,
                                testTagPrefix = "cards-adjust-team-one-blue",
                                onIncrement = { teamOneB += 1 },
                                onDecrement = { teamOneB = maxOf(0, teamOneB - 1) },
                            )
                            CardCountRow(
                                label = "Tech",
                                value = teamOneTf,
                                testTagPrefix = "cards-adjust-team-one-tech",
                                onIncrement = { teamOneTf += 1 },
                                onDecrement = { teamOneTf = maxOf(0, teamOneTf - 1) },
                            )
                            PlayerCardAdjustmentActions(
                                hasEditableCards = editablePlayerCards(
                                    workingTeamOnePlayerCards
                                ).isNotEmpty(),
                                yellowCount = workingTeamOnePlayerCards.inGameCardCount(
                                    CardType.YELLOW
                                ),
                                redCount = workingTeamOnePlayerCards.inGameCardCount(
                                    CardType.RED
                                ),
                                onEditExisting = {
                                    step = AdjustCardsDialogStep.ExistingCards(TeamId.TEAM_ONE)
                                },
                                onAddYellow = {
                                    step = AdjustCardsDialogStep.CardAdd(
                                        PendingManualCardAdd(
                                            TeamId.TEAM_ONE,
                                            CardType.YELLOW,
                                            PlayerCardEntry(""),
                                        )
                                    )
                                },
                                onAddRed = {
                                    step = AdjustCardsDialogStep.CardAdd(
                                        PendingManualCardAdd(
                                            TeamId.TEAM_ONE,
                                            CardType.RED,
                                            PlayerCardEntry(""),
                                        )
                                    )
                                },
                                addYellowTestTag = "cards-adjust-team-one-add-yellow",
                                addRedTestTag = "cards-adjust-team-one-add-red",
                                editExistingTestTag = "cards-adjust-team-one-edit-existing",
                            )
                        }
                        TeamCorrectionSection(state.teamTwo.name) {
                            CardCountRow(
                                label = "Blue",
                                value = teamTwoB,
                                testTagPrefix = "cards-adjust-team-two-blue",
                                onIncrement = { teamTwoB += 1 },
                                onDecrement = { teamTwoB = maxOf(0, teamTwoB - 1) },
                            )
                            CardCountRow(
                                label = "Tech",
                                value = teamTwoTf,
                                testTagPrefix = "cards-adjust-team-two-tech",
                                onIncrement = { teamTwoTf += 1 },
                                onDecrement = { teamTwoTf = maxOf(0, teamTwoTf - 1) },
                            )
                            PlayerCardAdjustmentActions(
                                hasEditableCards = editablePlayerCards(
                                    workingTeamTwoPlayerCards
                                ).isNotEmpty(),
                                yellowCount = workingTeamTwoPlayerCards.inGameCardCount(
                                    CardType.YELLOW
                                ),
                                redCount = workingTeamTwoPlayerCards.inGameCardCount(
                                    CardType.RED
                                ),
                                onEditExisting = {
                                    step = AdjustCardsDialogStep.ExistingCards(TeamId.TEAM_TWO)
                                },
                                onAddYellow = {
                                    step = AdjustCardsDialogStep.CardAdd(
                                        PendingManualCardAdd(
                                            TeamId.TEAM_TWO,
                                            CardType.YELLOW,
                                            PlayerCardEntry(""),
                                        )
                                    )
                                },
                                onAddRed = {
                                    step = AdjustCardsDialogStep.CardAdd(
                                        PendingManualCardAdd(
                                            TeamId.TEAM_TWO,
                                            CardType.RED,
                                            PlayerCardEntry(""),
                                        )
                                    )
                                },
                                addYellowTestTag = "cards-adjust-team-two-add-yellow",
                                addRedTestTag = "cards-adjust-team-two-add-red",
                                editExistingTestTag = "cards-adjust-team-two-edit-existing",
                            )
                        }
                    }
                },
                confirmButton = {
                    TextActionButton(label = "Done", onClick = { finalizeAdjustment() })
                },
                dismissButton = {
                    TextActionButton(label = "Cancel", onClick = onDismiss)
                },
            )
        }

        is AdjustCardsDialogStep.ExistingCards -> {
            EditablePlayerCardsDialog(
                teamName = state.teamFor(activeStep.team).name,
                cards = editablePlayerCards(recordsFor(activeStep.team)),
                onDismiss = { step = AdjustCardsDialogStep.CardCounts },
                onEdit = { card ->
                    step = AdjustCardsDialogStep.CardEdit(
                        PendingManualCardEdit(activeStep.team, card),
                        PlayerCardEntry(
                            jerseyNumber = card.jerseyNumber,
                            playerName = card.playerName,
                            reason = card.reason,
                        ),
                    )
                },
                onRemove = { card ->
                    step = AdjustCardsDialogStep.CardRemove(
                        PendingManualCardRemove(activeStep.team, card)
                    )
                },
            )
        }

        is AdjustCardsDialogStep.CardAdd -> {
            val pending = activeStep.pending
            PlayerCardEntryDialog(
                title = "Add ${pending.cardType.label.lowercase()} card",
                teamName = state.teamFor(pending.team).name,
                initialEntry = pending.initialEntry,
                candidates = recordsFor(pending.team).playerCardCandidates(),
                cardType = pending.cardType,
                onDismiss = { step = AdjustCardsDialogStep.CardCounts },
                onConfirm = { entry ->
                    applyManualCardAdd(pending, entry)
                },
            )
        }

        is AdjustCardsDialogStep.CardEdit -> {
            val pending = activeStep.pending
            PlayerCardEntryDialog(
                title = "Edit ${pending.card.cardType.label.lowercase()} card",
                teamName = state.teamFor(pending.team).name,
                initialEntry = activeStep.initialEntry,
                candidates = emptyList(),
                cardType = pending.card.cardType,
                onDismiss = { step = AdjustCardsDialogStep.ExistingCards(pending.team) },
                onConfirm = { entry ->
                    applyManualCardEdit(pending, entry)
                },
            )
        }

        is AdjustCardsDialogStep.CardRemove -> {
            val pending = activeStep.pending
            RemoveEditablePlayerCardDialog(
                card = pending.card,
                onDismiss = { step = AdjustCardsDialogStep.ExistingCards(pending.team) },
                onConfirm = {
                    val updatedRecords = removeEditablePlayerCard(
                        recordsFor(pending.team),
                        pending.card,
                    )
                    setRecordsFor(pending.team, updatedRecords)
                    onStateUpdate(
                        state.withPlayerCards(
                            team = pending.team,
                            records = updatedRecords,
                            undoLabel = state.playerCardRemoveUndoLabel(
                                team = pending.team,
                                cardType = pending.card.cardType,
                                identity = pending.card.identity(),
                            ),
                        )
                    )
                    step = AdjustCardsDialogStep.ExistingCards(pending.team)
                }
            )
        }

        is AdjustCardsDialogStep.SameNumberConfirmation -> {
            val confirmation = activeStep.confirmation

            fun restoreManualCardAdd() {
                step = AdjustCardsDialogStep.CardAdd(
                    PendingManualCardAdd(
                        team = confirmation.team,
                        cardType = confirmation.cardType,
                        initialEntry = confirmation.entry,
                    )
                )
            }

            AlertDialog(
                onDismissRequest = {
                    restoreManualCardAdd()
                },
                title = { Text("Same number, different names") },
                text = {
                    Text(
                        "${PlayerIdentity(
                            confirmation.conflict.existingJerseyNumber,
                            confirmation.conflict.existingPlayerName,
                        ).displayText(compact = false)} is already listed. Record ${PlayerIdentity(
                            confirmation.conflict.proposedJerseyNumber,
                            confirmation.conflict.proposedPlayerName,
                        ).displayText(compact = false)} as a different player with the same number?"
                    )
                },
                confirmButton = {
                    TextActionButton(
                        label = "Record",
                        onClick = {
                            applyManualCardAdd(
                                pending = PendingManualCardAdd(
                                    team = confirmation.team,
                                    cardType = confirmation.cardType,
                                    initialEntry = confirmation.entry,
                                ),
                                entry = confirmation.entry,
                                skipSameNumberWarning = true,
                            )
                        }
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Cancel",
                        tag = "same-number-warning-cancel",
                        onClick = {
                            restoreManualCardAdd()
                        },
                    )
                }
            )
        }

        is AdjustCardsDialogStep.InvalidAssignment -> {
            AlertDialog(
                onDismissRequest = { step = activeStep.returnTo },
                title = { Text("Invalid card assignment") },
                text = { Text(activeStep.message) },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = { step = activeStep.returnTo },
                    )
                },
            )
        }

        is AdjustCardsDialogStep.SuspensionNotice -> {
            AlertDialog(
                onDismissRequest = { step = activeStep.returnTo },
                title = { Text("Card suspension") },
                text = { Text(activeStep.message) },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = { step = activeStep.returnTo },
                    )
                },
            )
        }
    }
}

/**
 * One active step in the live Card dialog flow.
 *
 * Keeping the flow as a single value makes dialog ownership explicit: platform Back and
 * outside-tap dismissal always apply to the visible step, instead of relying on several nullable
 * dialog flags to compose into the right window ordering.
 */
private sealed interface TeamCardDialogStep {
    data object InitialCardChoice : TeamCardDialogStep
    data class ExistingCards(val team: TeamId) : TeamCardDialogStep
    data class ExistingCardEdit(
        val pending: PendingManualCardEdit,
        val initialEntry: PlayerCardEntry,
    ) : TeamCardDialogStep
    data class CardedPlayerEntry(
        val team: TeamId,
        val cardType: CardType,
        val initialEntry: PlayerCardEntry,
    ) : TeamCardDialogStep
    data class SameNumberConfirmation(
        val confirmation: PendingSameNumberPlayerCardConfirmation
    ) : TeamCardDialogStep
    data class BlueCardConfirmation(val team: TeamId) : TeamCardDialogStep
    data class OffenseDefenseChoice(val pending: PendingMisconductChoice) : TeamCardDialogStep
    data class MisconductResolution(val pending: PendingMisconductResolution) : TeamCardDialogStep
    data class InvalidAssignment(
        val message: String,
        val returnTo: TeamCardDialogStep,
    ) : TeamCardDialogStep
    data class SuspensionNotice(
        val message: String,
        val returnTo: TeamCardDialogStep,
    ) : TeamCardDialogStep
}

/**
 * Render the dialog flow for recording a card for one team.
 *
 * @param state The current live game state used for team names, card summaries, and assessments.
 * @param team The team receiving the card.
 * @param now The current epoch millis for event logging.
 * @param onDismiss Callback closing the card dialog without recording.
 * @param onAssessment Callback receiving the completed state plus popup text after a card.
 * @param onStateOnly Callback receiving the completed state when the confirmation dialog already showed the result.
 * @param onStateUpdate Callback receiving card-record corrections that should keep the Card dialog open.
 */
@Composable
internal fun TeamCardDialog(
    state: GameState,
    team: TeamId,
    now: Long,
    onDismiss: () -> Unit,
    onAssessment: (GameState, String, String) -> Unit,
    onStateOnly: (GameState) -> Unit,
    onStateUpdate: (GameState) -> Unit,
) {
    var step by remember {
        mutableStateOf<TeamCardDialogStep>(TeamCardDialogStep.InitialCardChoice)
    }

    fun completeAssessment(result: CardAssessmentResult) {
        onAssessment(
            result.state,
            result.event.formatMessage(),
            result.event.formatPopupTitle(),
        )
    }

    fun cardedPlayerEntryStep(
        team: TeamId,
        cardType: CardType,
        entry: PlayerCardEntry,
    ): TeamCardDialogStep.CardedPlayerEntry {
        return TeamCardDialogStep.CardedPlayerEntry(team, cardType, entry)
    }

    fun blueCardConfirmationStep(team: TeamId): TeamCardDialogStep.BlueCardConfirmation {
        return TeamCardDialogStep.BlueCardConfirmation(team)
    }

    fun offenseDefenseChoiceStep(
        pending: PendingMisconductChoice
    ): TeamCardDialogStep.OffenseDefenseChoice {
        return TeamCardDialogStep.OffenseDefenseChoice(pending)
    }

    fun misconductResolutionStep(
        pending: PendingMisconductResolution
    ): TeamCardDialogStep.MisconductResolution {
        return TeamCardDialogStep.MisconductResolution(pending)
    }

    fun sameNumberConfirmationStep(
        confirmation: PendingSameNumberPlayerCardConfirmation
    ): TeamCardDialogStep.SameNumberConfirmation {
        return TeamCardDialogStep.SameNumberConfirmation(confirmation)
    }

    fun invalidAssignmentStep(
        message: String,
        returnTo: TeamCardDialogStep,
    ): TeamCardDialogStep.InvalidAssignment {
        return TeamCardDialogStep.InvalidAssignment(message, returnTo)
    }

    fun suspensionNoticeStep(
        message: String,
        returnTo: TeamCardDialogStep,
    ): TeamCardDialogStep.SuspensionNotice {
        return TeamCardDialogStep.SuspensionNotice(message, returnTo)
    }

    fun stepForPendingMisconductReturn(returnTo: PendingMisconductReturn): TeamCardDialogStep {
        // No else branch: every PendingMisconductReturn value is handled.
        return when (returnTo) {
            is PendingMisconductReturn.YellowEntry -> {
                cardedPlayerEntryStep(returnTo.team, CardType.YELLOW, returnTo.entry)
            }
            is PendingMisconductReturn.RedEntry -> {
                cardedPlayerEntryStep(returnTo.team, CardType.RED, returnTo.entry)
            }
            is PendingMisconductReturn.BlueCard -> {
                blueCardConfirmationStep(returnTo.team)
            }
        }
    }

    fun stepForMisconductResolutionDismissal(choice: PendingMisconductChoice): TeamCardDialogStep {
        return if (choice.returnTo is PendingMisconductReturn.BlueCard) {
            stepForPendingMisconductReturn(choice.returnTo)
        } else {
            offenseDefenseChoiceStep(choice)
        }
    }

    fun presentAssessment(result: CardAssessmentResult, returnTo: PendingMisconductReturn) {
        if (result.needsMisconductChoice) {
            step = offenseDefenseChoiceStep(PendingMisconductChoice(result, returnTo))
        } else {
            completeAssessment(result)
        }
    }

    fun stateWithPlayerCards(
        team: TeamId,
        records: List<PlayerRecord>,
        undoLabel: String,
    ): GameState {
        return state.adjustCardsAndTf(
            teamOneBlues = state.teamOne.blueCards,
            teamOneTechnicalFouls = state.teamOne.technicalFouls,
            teamTwoBlues = state.teamTwo.blueCards,
            teamTwoTechnicalFouls = state.teamTwo.technicalFouls,
            teamOnePlayers = if (team == TeamId.TEAM_ONE) records else state.teamOnePlayers,
            teamTwoPlayers = if (team == TeamId.TEAM_TWO) records else state.teamTwoPlayers,
            now = now,
            undoLabel = undoLabel,
        )
    }

    fun suspensionNoticeMessage(
        team: TeamId,
        records: List<PlayerRecord>,
        identity: PlayerIdentity
    ): String? {
        val status = playerSuspensionStatus(records, identity) ?: return null
        return "${state.teamFor(team).name} ${identity.displayText(compact = true)} " +
            status.noticeText
    }

    fun applyExistingCardEdit(
        pending: PendingManualCardEdit,
        entry: PlayerCardEntry
    ): Boolean {
        val returnTo = TeamCardDialogStep.ExistingCardEdit(pending, entry)
        if (entry.jerseyNumber.isBlank() && entry.playerName.isBlank()) {
            step = invalidAssignmentStep(
                "Enter a player number or name before recording this card.",
                returnTo,
            )
            return false
        }
        val identity = PlayerIdentity(entry.jerseyNumber, entry.playerName)
        val recordsAfterRemoval = removeEditablePlayerCard(
            state.playerCards(pending.team),
            pending.card,
        )
        val status = playerSuspensionStatus(recordsAfterRemoval, identity)
        if (status != null) {
            step = invalidAssignmentStep(
                "${state.teamFor(pending.team).name} ${identity.displayText(compact = true)} " +
                    status.rejectionText,
                returnTo,
            )
            return false
        }
        val updatedRecords = replaceEditablePlayerCard(
            records = state.playerCards(pending.team),
            editableCard = pending.card,
            jerseyNumber = identity.jerseyNumber,
            cardType = pending.card.cardType,
            playerName = identity.playerName,
            reason = entry.reason,
        )
        val noticeMessage = if (!identity.matches(pending.card.identity())) {
            suspensionNoticeMessage(pending.team, updatedRecords, identity)
        } else {
            null
        }
        onStateUpdate(
            stateWithPlayerCards(
                team = pending.team,
                records = updatedRecords,
                undoLabel = state.playerCardEditUndoLabel(
                    pending.team,
                    pending.card.cardType,
                    identity,
                ),
            )
        )
        val returnToList = TeamCardDialogStep.ExistingCards(pending.team)
        step = if (noticeMessage != null) {
            suspensionNoticeStep(noticeMessage, returnToList)
        } else {
            returnToList
        }
        return true
    }

    fun assessPlayerCardEntry(
        team: TeamId,
        cardType: CardType,
        entry: PlayerCardEntry,
        skipSameNumberWarning: Boolean = false,
    ): Boolean {
        val returnTo = cardedPlayerEntryStep(team, cardType, entry)
        if (entry.jerseyNumber.isBlank() && entry.playerName.isBlank()) {
            step = invalidAssignmentStep(
                "Enter a player number or name before recording this card.",
                returnTo,
            )
            return false
        }
        val identity = PlayerIdentity(entry.jerseyNumber, entry.playerName)
        val normalizedEntry = entry.copy(
            jerseyNumber = identity.jerseyNumber,
            playerName = identity.playerName
        )
        if (!skipSameNumberWarning) {
            val conflict = state.sameNumberPlayerIdentityConflict(
                team,
                identity.jerseyNumber,
                identity.playerName
            )
            if (conflict != null) {
                step = sameNumberConfirmationStep(
                    PendingSameNumberPlayerCardConfirmation(
                        team = team,
                        cardType = cardType,
                        entry = normalizedEntry,
                        conflict = conflict,
                    )
                )
                return true
            }
        }
        val status = playerSuspensionStatus(state.playerCards(team), identity)
        if (status != null) {
            step = invalidAssignmentStep(
                "${state.teamFor(team).name} ${identity.displayText(compact = true)} " +
                    status.rejectionText,
                returnTo,
            )
            return false
        }

        // No else branch: every CardType value is handled.
        when (cardType) {
            CardType.YELLOW -> {
                presentAssessment(
                    state.assessYellowCard(
                        team,
                        identity.jerseyNumber,
                        now,
                        identity.playerName,
                        entry.reason
                    ),
                    PendingMisconductReturn.YellowEntry(team, normalizedEntry),
                )
                return true
            }
            CardType.RED -> {
                presentAssessment(
                    state.assessRedCard(
                        team,
                        identity.jerseyNumber,
                        now,
                        identity.playerName,
                        entry.reason
                    ),
                    PendingMisconductReturn.RedEntry(team, normalizedEntry),
                )
                return true
            }
        }
    }

    // No else branch: every TeamCardDialogStep value is handled.
    when (val activeStep = step) {
        TeamCardDialogStep.InitialCardChoice -> {
            CardChoiceDialog(
                state = state,
                team = team,
                onYellow = {
                    step = cardedPlayerEntryStep(team, CardType.YELLOW, PlayerCardEntry(""))
                },
                onRed = {
                    step = cardedPlayerEntryStep(team, CardType.RED, PlayerCardEntry(""))
                },
                onBlue = { step = blueCardConfirmationStep(team) },
                onEditExisting = { step = TeamCardDialogStep.ExistingCards(team) },
                onDismiss = onDismiss,
            )
        }
        is TeamCardDialogStep.ExistingCards -> {
            EditablePlayerCardsDialog(
                teamName = state.teamFor(activeStep.team).name,
                cards = editablePlayerCards(state.playerCards(activeStep.team)),
                onDismiss = { step = TeamCardDialogStep.InitialCardChoice },
                onEdit = { card ->
                    step = TeamCardDialogStep.ExistingCardEdit(
                        PendingManualCardEdit(activeStep.team, card),
                        PlayerCardEntry(
                            jerseyNumber = card.jerseyNumber,
                            playerName = card.playerName,
                            reason = card.reason,
                        ),
                    )
                },
            )
        }
        is TeamCardDialogStep.ExistingCardEdit -> {
            val pending = activeStep.pending
            PlayerCardEntryDialog(
                title = "Edit ${pending.card.cardType.label.lowercase()} card",
                teamName = state.teamFor(pending.team).name,
                initialEntry = activeStep.initialEntry,
                candidates = emptyList(),
                cardType = pending.card.cardType,
                onDismiss = { step = TeamCardDialogStep.ExistingCards(pending.team) },
                onConfirm = { entry ->
                    applyExistingCardEdit(pending, entry)
                },
            )
        }
        is TeamCardDialogStep.CardedPlayerEntry -> {
            PlayerCardEntryDialog(
                title = "${activeStep.cardType.label} card",
                teamName = state.teamFor(activeStep.team).name,
                initialEntry = activeStep.initialEntry,
                candidates = state.cardedPlayerCandidates(activeStep.team),
                cardType = activeStep.cardType,
                onDismiss = { step = TeamCardDialogStep.InitialCardChoice },
                onConfirm = { entry ->
                    assessPlayerCardEntry(
                        team = activeStep.team,
                        cardType = activeStep.cardType,
                        entry = entry,
                    )
                },
            )
        }
        is TeamCardDialogStep.SameNumberConfirmation -> {
            val confirmation = activeStep.confirmation
            AlertDialog(
                onDismissRequest = {
                    step = cardedPlayerEntryStep(
                        confirmation.team,
                        confirmation.cardType,
                        confirmation.entry,
                    )
                },
                title = { Text("Same number, different names") },
                text = {
                    Text(
                        "${PlayerIdentity(
                            confirmation.conflict.existingJerseyNumber,
                            confirmation.conflict.existingPlayerName,
                        ).displayText(compact = false)} is already listed. Record ${PlayerIdentity(
                            confirmation.conflict.proposedJerseyNumber,
                            confirmation.conflict.proposedPlayerName,
                        ).displayText(compact = false)} as a different player with the same number?"
                    )
                },
                confirmButton = {
                    TextActionButton(
                        label = "Record",
                        onClick = {
                            assessPlayerCardEntry(
                                team = confirmation.team,
                                cardType = confirmation.cardType,
                                entry = confirmation.entry,
                                skipSameNumberWarning = true,
                            )
                        }
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Cancel",
                        tag = "same-number-warning-cancel",
                        onClick = {
                            step = cardedPlayerEntryStep(
                                confirmation.team,
                                confirmation.cardType,
                                confirmation.entry
                            )
                        }
                    )
                },
            )
        }
        is TeamCardDialogStep.InvalidAssignment -> {
            AlertDialog(
                onDismissRequest = { step = activeStep.returnTo },
                title = { Text("Invalid card assignment") },
                text = { Text(activeStep.message) },
                confirmButton = {
                    TextActionButton(label = "OK", onClick = { step = activeStep.returnTo })
                },
            )
        }
        is TeamCardDialogStep.SuspensionNotice -> {
            AlertDialog(
                onDismissRequest = { step = activeStep.returnTo },
                title = { Text("Card suspension") },
                text = { Text(activeStep.message) },
                confirmButton = {
                    TextActionButton(label = "OK", onClick = { step = activeStep.returnTo })
                },
            )
        }
        is TeamCardDialogStep.BlueCardConfirmation -> {
            val blueTeam = activeStep.team
            val event = state.previewBlueCard(blueTeam).event
            val misconductPrompt = if (event.needsMisconductChoice()) {
                GamePrompt.LivePointMisconduct(event)
            } else {
                null
            }
            AlertDialog(
                onDismissRequest = { step = TeamCardDialogStep.InitialCardChoice },
                title = { Text("Blue Card") },
                text = {
                    Text(
                        text = misconductPrompt?.formatMessage() ?: event.formatMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    if (misconductPrompt == null) {
                        TextActionButton(
                            label = "OK",
                            onClick = {
                                val result = state.assessBlueCard(blueTeam, now)
                                onStateOnly(result.state)
                            },
                        )
                    } else {
                        MisconductChoiceButtons(
                            firstLabel = "Cancel",
                            onFirst = { step = TeamCardDialogStep.InitialCardChoice },
                            onOffense = {
                                val result = state.assessBlueCard(blueTeam, now)
                                step = misconductResolutionStep(
                                    PendingMisconductResolution(
                                        choice = PendingMisconductChoice(
                                            result = result,
                                            returnTo = PendingMisconductReturn.BlueCard(blueTeam),
                                        ),
                                        againstOffense = true,
                                    )
                                )
                            },
                            onDefense = {
                                val result = state.assessBlueCard(blueTeam, now)
                                step = misconductResolutionStep(
                                    PendingMisconductResolution(
                                        choice = PendingMisconductChoice(
                                            result = result,
                                            returnTo = PendingMisconductReturn.BlueCard(blueTeam),
                                        ),
                                        againstOffense = false,
                                    )
                                )
                            },
                        )
                    }
                },
                dismissButton = if (misconductPrompt == null) {
                    {
                        TextActionButton(
                            label = "Cancel",
                            onClick = { step = TeamCardDialogStep.InitialCardChoice },
                        )
                    }
                } else {
                    null
                },
            )
        }
        is TeamCardDialogStep.OffenseDefenseChoice -> {
            val pending = activeStep.pending
            val prompt = GamePrompt.LivePointMisconduct(pending.result.event)
            AlertDialog(
                onDismissRequest = {
                    step = stepForPendingMisconductReturn(pending.returnTo)
                },
                title = { Text(prompt.formatTitle()) },
                text = {
                    Text(
                        text = prompt.formatMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    MisconductChoiceButtons(
                        firstLabel = "Back",
                        firstTag = "misconduct-choice-back",
                        onFirst = { step = stepForPendingMisconductReturn(pending.returnTo) },
                        onOffense = {
                            step = misconductResolutionStep(
                                PendingMisconductResolution(
                                    choice = pending,
                                    againstOffense = true,
                                )
                            )
                        },
                        onDefense = {
                            step = misconductResolutionStep(
                                PendingMisconductResolution(
                                    choice = pending,
                                    againstOffense = false,
                                )
                            )
                        },
                    )
                },
            )
        }
        is TeamCardDialogStep.MisconductResolution -> {
            val pending = activeStep.pending
            val prompt = GamePrompt.LivePointMisconduct(pending.choice.result.event)
            AlertDialog(
                onDismissRequest = {
                    step = stepForMisconductResolutionDismissal(pending.choice)
                },
                title = { Text(prompt.formatTitle()) },
                text = {
                    Text(
                        text = prompt.resolutionMessage(pending.againstOffense),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = {
                            onStateOnly(
                                pending.choice.result.state.withPendingMisconductCountdown()
                            )
                        },
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Back",
                        tag = "misconduct-resolution-back",
                        onClick = {
                            step = stepForMisconductResolutionDismissal(pending.choice)
                        },
                    )
                },
            )
        }
    }
}

/**
 * Render the first card dialog, where the observer chooses the assessed card color.
 *
 * @param state The current live game state used for team names and counts.
 * @param team The team receiving the card.
 * @param onYellow Callback starting the yellow-card workflow.
 * @param onRed Callback starting the red-card workflow.
 * @param onBlue Callback recording a blue card.
 * @param onEditExisting Callback opening the existing in-game card editor.
 * @param onDismiss Callback closing the dialog without recording a card.
 */
@Composable
private fun CardChoiceDialog(
    state: GameState,
    team: TeamId,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onEditExisting: () -> Unit,
    onDismiss: () -> Unit,
) {
    val teamState = state.teamFor(team)
    val roleSuffix = state.cardsRoleSuffix(team)
    val yellowCount = state.teamYellowCards(team)
    val redCount = state.teamRedCards(team)
    val blueCount = teamState.blueCards
    val hasEditableCards = editablePlayerCards(state.playerCards(team)).isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assess a card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${teamState.name}$roleSuffix", fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BigActionButton(
                        label = "Yellow",
                        containerColor = YellowCardButtonColor,
                        contentColor = Color.Black,
                        borderColor = null,
                        modifier = Modifier.weight(1f),
                        tag = "card-dialog-${team.name}-yellow",
                        onClick = onYellow,
                    )
                    BigActionButton(
                        label = "Red",
                        containerColor = RedCardButtonColor,
                        contentColor = Color.Black,
                        borderColor = null,
                        modifier = Modifier.weight(1f),
                        tag = "card-dialog-${team.name}-red",
                        onClick = onRed,
                    )
                    BigActionButton(
                        label = "Blue",
                        containerColor = BlueCardButtonColor,
                        contentColor = Color.White,
                        borderColor = null,
                        modifier = Modifier.weight(1f),
                        tag = "card-dialog-${team.name}-blue",
                        onClick = onBlue,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Current cards:", style = MaterialTheme.typography.bodyMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.padding(start = 18.dp),
                    ) {
                        Text("$yellowCount yellow", style = MaterialTheme.typography.bodyMedium)
                        Text("$redCount red", style = MaterialTheme.typography.bodyMedium)
                        Text("$blueCount blue", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        state.teamCardTotalDetailLine(team),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ExistingCardsButton(
                    hasEditableCards = hasEditableCards,
                    onClick = onEditExisting,
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "Close", onClick = onDismiss)
        },
    )
}

/**
 * Render the white existing-card editor button.
 *
 * @param hasEditableCards Whether the button should open editable in-game cards.
 * @param onClick Callback opening the existing-card editor.
 */
@Composable
private fun ExistingCardsButton(
    hasEditableCards: Boolean,
    onClick: () -> Unit,
) {
    MenuButton(
        label = if (hasEditableCards) "Edit existing cards" else "No existing cards",
        enabled = hasEditableCards,
        colors = neutralOutlinedButtonColors(EmphasizedLightNeutralColor),
        onClick = onClick,
    )
}

// Compact +/- row for a single card or tech count.
@Composable
private fun CardCountRow(
    label: String,
    value: Int,
    testTagPrefix: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    CorrectionCountRow(
        label = label,
        value = value,
        incrementTag = "$testTagPrefix-increment",
        decrementTag = "$testTagPrefix-decrement",
        onIncrement = onIncrement,
        onDecrement = onDecrement,
    )
}

/**
 * Render player-card actions for one team in the manual adjustment dialog.
 *
 * @param hasEditableCards Whether this team has in-game player cards that can be edited.
 * @param yellowCount Current in-game yellow-card count for this team.
 * @param redCount Current in-game red-card count for this team.
 * @param onEditExisting Callback opening the existing-card editor.
 * @param onAddYellow Callback adding one yellow card.
 * @param onAddRed Callback adding one red card.
 * @param addYellowTestTag Test tag for the add-yellow action.
 * @param addRedTestTag Test tag for the add-red action.
 * @param editExistingTestTag Test tag for the existing-card editor action.
 */
@Composable
private fun PlayerCardAdjustmentActions(
    hasEditableCards: Boolean,
    yellowCount: Int,
    redCount: Int,
    onEditExisting: () -> Unit,
    onAddYellow: () -> Unit,
    onAddRed: () -> Unit,
    addYellowTestTag: String,
    addRedTestTag: String,
    editExistingTestTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BigActionButton(
                label = "Add yellow ($yellowCount)",
                containerColor = YellowCardButtonColor,
                contentColor = Color.Black,
                borderColor = null,
                onClick = onAddYellow,
                modifier = Modifier.weight(1f),
                tag = addYellowTestTag,
            )
            BigActionButton(
                label = "Add red ($redCount)",
                containerColor = RedCardButtonColor,
                contentColor = Color.Black,
                borderColor = null,
                onClick = onAddRed,
                modifier = Modifier.weight(1f),
                tag = addRedTestTag,
            )
        }
        MenuButton(
            label = if (hasEditableCards) "Edit/remove existing cards" else "No existing cards",
            enabled = hasEditableCards,
            tag = editExistingTestTag,
            colors = neutralOutlinedButtonColors(EmphasizedLightNeutralColor),
            onClick = onEditExisting,
        )
    }
}

/**
 * Render one editable in-game player-card record row.
 *
 * @param card The card event represented by this row.
 * @param onEdit Callback editing this card.
 * @param onRemove Optional callback asking to remove this card.
 */
@Composable
private fun EditablePlayerCardRow(
    card: EditablePlayerCard,
    onEdit: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val identity = editablePlayerCardIdentityText(card)
    DialogListItemCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = identity,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${card.cardType.label} card",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val reason = card.reason.text()
                if (reason.isNotEmpty()) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconActionButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Edit $identity",
                    onClick = onEdit,
                )
                onRemove?.let { remove ->
                    IconActionButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "Remove $identity",
                        onClick = remove,
                    )
                }
            }
        }
    }
}

/**
 * Render editable in-game player-card rows for one team.
 *
 * @param teamName The team whose cards are listed.
 * @param cards The editable in-game cards.
 * @param onDismiss Callback closing this page.
 * @param onEdit Callback editing one card.
 * @param onRemove Optional callback asking to remove one card.
 */
@Composable
private fun EditablePlayerCardsDialog(
    teamName: String,
    cards: List<EditablePlayerCard>,
    onDismiss: () -> Unit,
    onEdit: (EditablePlayerCard) -> Unit,
    onRemove: ((EditablePlayerCard) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit existing cards") },
        text = {
            ScrollableDialogRegion(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                cards.forEach { card ->
                    EditablePlayerCardRow(
                        card = card,
                        onEdit = { onEdit(card) },
                        onRemove = onRemove?.let { remove -> { remove(card) } },
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Done",
                tag = "editable-player-cards-done",
                onClick = onDismiss,
            )
        },
    )
}

/**
 * Confirm removal of one in-game player card.
 *
 * @param card The card that would be removed.
 * @param onDismiss Callback keeping the card.
 * @param onConfirm Callback removing the card.
 */
@Composable
private fun RemoveEditablePlayerCardDialog(
    card: EditablePlayerCard,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val playerIdentity = PlayerIdentity(card.jerseyNumber, card.playerName)
        .displayText(compact = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove card?") },
        text = {
            Text("Remove this ${card.cardType.label.lowercase()} card assessed to $playerIdentity?")
        },
        confirmButton = {
            TextActionButton(label = "Remove", onClick = onConfirm)
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/// Return the player identity text for one editable card row.
private fun editablePlayerCardIdentityText(card: EditablePlayerCard): String {
    return PlayerIdentity(card.jerseyNumber, card.playerName).displayText(compact = false)
}

/**
 * Render the player details prompt for live yellow/red card flows.
 *
 * @param title The dialog title describing the card type.
 * @param teamName The team receiving the player card.
 * @param initialEntry Card details to restore when returning from a later dialog step.
 * @param candidates Known carded players for this team.
 * @param cardType The card being assessed.
 * @param onDismiss Callback closing the dialog without recording.
 * @param onConfirm Callback receiving the entered card details.
 */
@Composable
private fun PlayerCardEntryDialog(
    title: String,
    teamName: String,
    initialEntry: PlayerCardEntry,
    candidates: List<PlayerCardCandidate>,
    cardType: CardType,
    onDismiss: () -> Unit,
    onConfirm: (PlayerCardEntry) -> Unit,
) {
    var jerseyNumber by remember(initialEntry) {
        mutableStateOf(initialEntry.jerseyNumber)
    }
    var playerName by remember(initialEntry) {
        mutableStateOf(initialEntry.playerName)
    }
    var reason by remember(initialEntry) {
        mutableStateOf(initialEntry.reason)
    }
    var showingReasonDialog by remember {
        mutableStateOf(false)
    }
    if (showingReasonDialog) {
        CardReasonDialog(
            cardType = cardType,
            initialReason = reason,
            onDismiss = { showingReasonDialog = false },
            onConfirm = { selectedReason ->
                reason = selectedReason
                showingReasonDialog = false
            },
        )
    } else {
        AlertDialog(
            modifier = dialogInitialFocusModifier(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                ScrollableDialogRegion(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(teamName, fontWeight = FontWeight.SemiBold)
                    TextEntry(
                        value = jerseyNumber,
                        onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                        labelText = "Player number",
                        keyboardType = KeyboardType.Number,
                        tag = "card-player-number",
                    )
                    TextEntry(
                        value = playerName,
                        onValueChange = { playerName = it },
                        labelText = "Player name",
                        tag = "card-player-name",
                    )
                    if (candidates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text("Players with cards:", style = MaterialTheme.typography.labelLarge)
                            candidates.forEach { candidate ->
                                PlayerCardCandidateRow(
                                    candidate = candidate,
                                    onCopy = {
                                        jerseyNumber = candidate.jerseyNumber
                                        playerName = candidate.playerName
                                    },
                                )
                            }
                        }
                    }
                    MenuButton(
                        label = reason.text().ifBlank { "Reason" },
                        colors = neutralOutlinedButtonColors(EmphasizedLightNeutralColor),
                        onClick = { showingReasonDialog = true },
                    )
                }
            },
            confirmButton = {
                TextActionButton(
                    label = "Record",
                    onClick = {
                        onConfirm(
                            PlayerCardEntry(
                                jerseyNumber = jerseyNumber.trim(),
                                playerName = playerName.trim(),
                                reason = reason,
                            )
                        )
                    },
                )
            },
            dismissButton = {
                TextActionButton(
                    label = "Cancel",
                    tag = "card-entry-cancel",
                    onClick = onDismiss,
                )
            },
        )
    }
}

/**
 * Render one known player row with an action to copy its identity into the entry fields.
 *
 * @param candidate The known player to show.
 * @param onCopy Callback copying the candidate identity into the current entry.
 */
@Composable
private fun PlayerCardCandidateRow(candidate: PlayerCardCandidate, onCopy: () -> Unit) {
    val detail = candidate.detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${PlayerIdentity(candidate.jerseyNumber, candidate.playerName).displayText(compact = false)}$detail",
            modifier = Modifier.weight(1f),
        )
        TextActionButton(
            label = "Copy",
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            onClick = onCopy,
        )
    }
}

/**
 * Render the optional reason picker for yellow/red cards.
 *
 * @param cardType The card being assessed.
 * @param initialReason Existing reason fields to restore.
 * @param onDismiss Callback closing the reason dialog without changing the reason.
 * @param onConfirm Callback receiving the selected reason.
 */
@Composable
private fun CardReasonDialog(
    cardType: CardType,
    initialReason: CardReason,
    onDismiss: () -> Unit,
    onConfirm: (CardReason) -> Unit,
) {
    val presets = cardReasonPresets(cardType)
    var selectedPreset by remember(initialReason) {
        mutableStateOf(initialReason.preset)
    }
    var otherReason by remember(initialReason) {
        mutableStateOf(initialReason.otherText)
    }
    var details by remember(initialReason) {
        mutableStateOf(initialReason.details)
    }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text("${cardType.label} card reason") },
        text = {
            ScrollableDialogRegion(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(IntrinsicSize.Max),
                ) {
                    presets.forEach { preset ->
                        ReasonChoiceButton(
                            label = preset,
                            selected = selectedPreset == preset,
                            onClick = {
                                focusManager.clearFocus(force = true)
                                selectedPreset = preset
                            },
                        )
                    }
                    ReasonChoiceButton(
                        label = "Other",
                        selected = selectedPreset == "Other",
                        onClick = {
                            focusManager.clearFocus(force = true)
                            selectedPreset = "Other"
                        },
                    )
                }
                if (selectedPreset == "Other") {
                    TextEntry(
                        value = otherReason,
                        onValueChange = { otherReason = it },
                        labelText = "Other reason",
                        tag = "card-other-reason",
                    )
                }
                TextEntry(
                    value = details,
                    onValueChange = { details = it },
                    labelText = "More details",
                    singleLine = false,
                    tag = "card-reason-details",
                )
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                onClick = {
                    onConfirm(
                        CardReason(
                            preset = selectedPreset.trim(),
                            otherText = otherReason.trim(),
                            details = details.trim(),
                        )
                    )
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Back", onClick = onDismiss)
        },
    )
}

/**
 * Render a preset card-reason selector.
 *
 * @param label The preset reason label.
 * @param selected Whether this preset is currently selected.
 * @param onClick Callback selecting this preset.
 */
@Composable
private fun ReasonChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChipButton(
        label = label,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * Return the Card-dialog role suffix for a team while between points or at halftime.
 *
 * @param team The team whose pulling/receiving role should be displayed.
 */
private fun GameState.cardsRoleSuffix(team: TeamId): String {
    return if (phase == GamePhase.BETWEEN_POINTS || phase == GamePhase.HALFTIME) {
        if (team == pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}

/**
 * Return known players for quick live-card selection.
 *
 * @param team The team whose known players should be listed.
 */
private fun GameState.cardedPlayerCandidates(team: TeamId): List<PlayerCardCandidate> {
    return playerCards(team).playerCardCandidates()
}

/// Return known players for quick player-card selection.
private fun List<PlayerRecord>.playerCardCandidates(): List<PlayerCardCandidate> {
    return map { player ->
        PlayerCardCandidate(
            jerseyNumber = player.jerseyNumber,
            playerName = player.playerName,
            detail = player.cardDetail(compact = true, includeGame = true),
        )
    }
}

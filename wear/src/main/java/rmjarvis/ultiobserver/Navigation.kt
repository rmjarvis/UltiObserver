package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearPhoneCardEntrySnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt

/** Watch-owned navigation reconciled with the authoritative phone state. */
internal data class NavigationState(
    val selectedTeam: TeamId? = null,
    val teamInfoOpen: Boolean = false,
    val rulesOpen: Boolean = false,
    val timingControlsOpen: Boolean = false,
    val pendingActionPrompt: WearTeamActionPrompt? = null,
    val cardChoiceStateToken: String? = null,
    val playerCard: WearTeamAction.PlayerCard? = null,
    private val phoneCardEntryWasActive: Boolean = false,
    private val returnToCardEntry: WearPhoneCardEntrySnapshot? = null,
) {
    /** Discard obsolete local workflows and finish phone handoff navigation. */
    fun receive(snapshot: WearStateSnapshot?, connection: ConnectionState): NavigationState {
        if (connection != ConnectionState.CONNECTED || snapshot!!.status != WearSnapshotStatus.ACTIVE_GAME) {
            return NavigationState()
        }
        val game = snapshot.activeGame!!
        val phoneCardEntry = game.phoneCardEntry
        var selectedTeam = this.selectedTeam
        var pendingActionPrompt = this.pendingActionPrompt
        var cardChoiceStateToken = this.cardChoiceStateToken
        var playerCard = this.playerCard
        var phoneCardEntryWasActive = this.phoneCardEntryWasActive
        var returnToCardEntry = this.returnToCardEntry
        if (phoneCardEntry != null) {
            phoneCardEntryWasActive = true
        } else if (phoneCardEntryWasActive) {
            val returnEntry = returnToCardEntry
            if (returnEntry == null) {
                selectedTeam = null
                cardChoiceStateToken = null
                playerCard = null
            } else {
                selectedTeam = returnEntry.team
                cardChoiceStateToken = game.stateToken
                playerCard = returnEntry.cardType?.let {
                    WearTeamAction.PlayerCard(it, returnEntry.jerseyNumber)
                }
            }
            phoneCardEntryWasActive = false
            returnToCardEntry = null
        }
        if (!game.actionsAvailable && phoneCardEntry == null) {
            selectedTeam = null
            cardChoiceStateToken = null
            playerCard = null
        }
        if (pendingActionPrompt?.stateToken != game.stateToken) {
            pendingActionPrompt = null
        }
        if (cardChoiceStateToken != game.stateToken) {
            if (cardChoiceStateToken != null && phoneCardEntry != null) {
                selectedTeam = null
            }
            cardChoiceStateToken = null
            playerCard = null
        }
        return copy(
            selectedTeam = selectedTeam,
            teamInfoOpen = teamInfoOpen && selectedTeam != null,
            timingControlsOpen = timingControlsOpen && game.actionsAvailable &&
                game.timingControls != null && game.countdown != null,
            pendingActionPrompt = pendingActionPrompt,
            cardChoiceStateToken = cardChoiceStateToken,
            playerCard = playerCard,
            phoneCardEntryWasActive = phoneCardEntryWasActive,
            returnToCardEntry = returnToCardEntry,
        )
    }

    /** Return from card choices to team actions, or from team actions to the game. */
    fun back(): NavigationState = if (rulesOpen) {
        copy(rulesOpen = false)
    } else if (teamInfoOpen) {
        copy(teamInfoOpen = false)
    } else if (cardChoiceStateToken != null) {
        copy(cardChoiceStateToken = null)
    } else {
        copy(selectedTeam = null, timingControlsOpen = false)
    }

    fun beginPhoneCancellation(entry: WearPhoneCardEntrySnapshot): NavigationState =
        copy(returnToCardEntry = entry)

    fun finishPhoneCancellation(cancelled: Boolean): NavigationState =
        if (cancelled) this else copy(returnToCardEntry = null)

    fun finishConfirmation(applied: Boolean): NavigationState =
        if (applied) copy(
            pendingActionPrompt = null, selectedTeam = null,
            timingControlsOpen = false,
            cardChoiceStateToken = null, playerCard = null,
        ) else copy(pendingActionPrompt = null)

    fun finishGoal(applied: Boolean): NavigationState =
        if (applied) copy(selectedTeam = null) else this

    fun finishHandoff(applied: Boolean): NavigationState =
        if (applied) copy(playerCard = null) else this

    /** Select the phone-owned surface before any local game navigation. */
    fun screen(snapshot: WearStateSnapshot?, connection: ConnectionState): WatchScreen {
        val reachable = connection == ConnectionState.CONNECTED
        return when {
            connection is ConnectionState.UpdateRequired -> WatchScreen.UPDATE_REQUIRED
            connection == ConnectionState.DISABLED -> WatchScreen.DISABLED
            snapshot == null && connection == ConnectionState.CONNECTING -> WatchScreen.CONNECTING
            snapshot == null -> WatchScreen.UNREACHABLE
            snapshot.status == WearSnapshotStatus.DISABLED -> WatchScreen.DISABLED
            snapshot.status == WearSnapshotStatus.NO_ACTIVE_GAME && !reachable -> WatchScreen.DISCONNECTED
            snapshot.status == WearSnapshotStatus.NO_ACTIVE_GAME -> WatchScreen.IDLE
            reachable && snapshot.activeGame!!.pendingDecision != null -> WatchScreen.CONFIRMATION
            reachable && snapshot.activeGame!!.phoneCardEntry != null -> WatchScreen.PHONE_ENTRY
            reachable && pendingActionPrompt != null -> WatchScreen.ACTION_PROMPT
            else -> WatchScreen.GAME
        }
    }

    fun gameScreen(stateToken: String): GameSurface = when {
        rulesOpen -> GameSurface.RULES
        selectedTeam == null -> GameSurface.SCORE
        teamInfoOpen -> GameSurface.TEAM_INFO
        cardChoiceStateToken == stateToken && playerCard != null -> GameSurface.PLAYER_CARD
        cardChoiceStateToken == stateToken -> GameSurface.CARD_CHOICES
        else -> GameSurface.TEAM_ACTIONS
    }
}

/** Top-level watch surfaces, selected independently of Compose. */
internal enum class WatchScreen {
    DISABLED, CONNECTING, UNREACHABLE, DISCONNECTED, UPDATE_REQUIRED, IDLE, CONFIRMATION, PHONE_ENTRY, ACTION_PROMPT, GAME,
}

/** Local surfaces within an active phone game. */
internal enum class GameSurface { SCORE, RULES, TEAM_ACTIONS, TEAM_INFO, CARD_CHOICES, PLAYER_CARD }

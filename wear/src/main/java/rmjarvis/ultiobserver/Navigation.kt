package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt

/** Watch-owned navigation reconciled with the authoritative phone state. */
internal data class NavigationState(
    val selectedTeam: Int = 0,
    val teamInfoOpen: Boolean = false,
    val rulesOpen: Boolean = false,
    val timingControlsOpen: Boolean = false,
    val pendingActionPrompt: WearTeamActionPrompt? = null,
    val cardChoiceStateToken: String? = null,
    val playerCard: WearTeamAction.PlayerCard? = null,
    private val phoneCardEntryWasActive: Boolean = false,
    private val returnToCardChoicesForTeam: TeamId? = null,
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
        var returnToCardChoicesForTeam = this.returnToCardChoicesForTeam
        if (phoneCardEntry != null) {
            phoneCardEntryWasActive = true
        } else if (phoneCardEntryWasActive) {
            val returnTeam = returnToCardChoicesForTeam
            if (returnTeam == null) {
                selectedTeam = 0
                cardChoiceStateToken = null
                playerCard = null
            } else {
                selectedTeam = if (returnTeam == TeamId.TEAM_ONE) 1 else 2
                cardChoiceStateToken = game.stateToken
            }
            phoneCardEntryWasActive = false
            returnToCardChoicesForTeam = null
        }
        if (!game.actionsAvailable && phoneCardEntry == null) {
            selectedTeam = 0
            cardChoiceStateToken = null
            playerCard = null
        }
        if (pendingActionPrompt?.stateToken != game.stateToken) {
            pendingActionPrompt = null
        }
        if (cardChoiceStateToken != game.stateToken) {
            if (cardChoiceStateToken != null && phoneCardEntry != null) {
                selectedTeam = 0
            }
            cardChoiceStateToken = null
            playerCard = null
        }
        return copy(
            selectedTeam = selectedTeam,
            teamInfoOpen = teamInfoOpen && selectedTeam != 0,
            timingControlsOpen = timingControlsOpen && game.actionsAvailable &&
                game.timingControls != null && game.countdown != null,
            pendingActionPrompt = pendingActionPrompt,
            cardChoiceStateToken = cardChoiceStateToken,
            playerCard = playerCard,
            phoneCardEntryWasActive = phoneCardEntryWasActive,
            returnToCardChoicesForTeam = returnToCardChoicesForTeam,
        )
    }

    val handlesBack: Boolean
        get() = (selectedTeam != 0 || timingControlsOpen || rulesOpen) && pendingActionPrompt == null && playerCard == null

    /** Return from card choices to team actions, or from team actions to the game. */
    fun back(): NavigationState = if (rulesOpen) {
        copy(rulesOpen = false)
    } else if (teamInfoOpen) {
        copy(teamInfoOpen = false)
    } else if (cardChoiceStateToken != null) {
        copy(cardChoiceStateToken = null)
    } else {
        copy(selectedTeam = 0, timingControlsOpen = false)
    }

    fun beginPhoneCancellation(team: TeamId): NavigationState =
        copy(returnToCardChoicesForTeam = team)

    fun finishPhoneCancellation(cancelled: Boolean): NavigationState =
        if (cancelled) this else copy(returnToCardChoicesForTeam = null)

    fun finishConfirmation(applied: Boolean): NavigationState =
        if (applied) copy(
            pendingActionPrompt = null, selectedTeam = 0,
            timingControlsOpen = false,
            cardChoiceStateToken = null, playerCard = null,
        ) else copy(pendingActionPrompt = null)

    fun finishGoal(applied: Boolean): NavigationState =
        if (applied) copy(selectedTeam = 0) else this

    fun finishHandoff(applied: Boolean): NavigationState =
        if (applied) copy(playerCard = null) else this

    /** Select the phone-owned surface before any local game navigation. */
    fun screen(snapshot: WearStateSnapshot?, connection: ConnectionState): WatchScreen {
        val reachable = connection == ConnectionState.CONNECTED
        return when {
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
        selectedTeam == 0 -> GameSurface.SCORE
        teamInfoOpen -> GameSurface.TEAM_INFO
        cardChoiceStateToken == stateToken && playerCard != null -> GameSurface.PLAYER_CARD
        cardChoiceStateToken == stateToken -> GameSurface.CARD_CHOICES
        else -> GameSurface.TEAM_ACTIONS
    }
}

/** Top-level watch surfaces, selected independently of Compose. */
internal enum class WatchScreen {
    DISABLED, CONNECTING, UNREACHABLE, DISCONNECTED, IDLE, CONFIRMATION, PHONE_ENTRY, ACTION_PROMPT, GAME,
}

/** Local surfaces within an active phone game. */
internal enum class GameSurface { SCORE, RULES, TEAM_ACTIONS, TEAM_INFO, CARD_CHOICES, PLAYER_CARD }

package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.*

/** Navigation through watch actions and authoritative phone updates. */
class TestNavigation {
    /** Open team and card surfaces, then return through the same Back hierarchy. */
    @Test
    fun localNavigation() {
        var navigation = NavigationState()
        assertEquals(GameSurface.SCORE, navigation.gameScreen("playing"))

        // Timing adjustments stay open across countdown updates and close when play resumes.
        val snapshot = navigationSnapshot()
        val timedGame = snapshot.activeGame!!.copy(
            countdown = WearCountdownSnapshot("Pull in", 100_000L, null, emptyList()),
            timingControls = WearTimingControlsSnapshot(
                listOf(WearCountdownAction.PLUS_FIVE), WearCountdownAction.START_POINT,
            ),
        )
        navigation = navigation.copy(timingControlsOpen = true)
            .receive(snapshot.copy(activeGame = timedGame), ConnectionState.CONNECTED)
        assertTrue(navigation.timingControlsOpen)
        assertEquals(NavigationState(), navigation.back())

        // Rules opened directly from the scores handle Back without a team or timing panel.
        val rulesOnly = NavigationState(rulesOpen = true)
        assertEquals(GameSurface.RULES, rulesOnly.gameScreen("playing"))
        assertEquals(NavigationState(), rulesOnly.back())

        // Rules open without a cap and return to the same timing panel when dismissed.
        navigation = NavigationState(timingControlsOpen = true, rulesOpen = true)
        assertEquals(GameSurface.RULES, navigation.gameScreen("playing"))
        navigation = navigation.back()
        assertFalse(navigation.rulesOpen)
        assertTrue(navigation.timingControlsOpen)
        assertEquals(GameSurface.SCORE, navigation.gameScreen("playing"))
        navigation = NavigationState(timingControlsOpen = true)
        navigation = navigation.receive(snapshot.copy(activeGame = timedGame.copy(
            stateToken = "adjusted", countdown = timedGame.countdown!!.copy(targetEpochMillis = 105_000L),
        )), ConnectionState.CONNECTED)
        assertTrue(navigation.timingControlsOpen)
        assertFalse(navigation.receive(snapshot, ConnectionState.CONNECTED).timingControlsOpen)
        assertFalse(navigation.receive(snapshot, ConnectionState.DISCONNECTED).timingControlsOpen)
        assertFalse(navigation.receive(snapshot.copy(activeGame = timedGame.copy(
            actionsAvailable = false,
        )), ConnectionState.CONNECTED).timingControlsOpen)
        assertFalse(navigation.receive(snapshot.copy(activeGame = timedGame.copy(
            countdown = null,
        )), ConnectionState.CONNECTED).timingControlsOpen)
        navigation = navigation.back()

        // Opening a team enables local Back; opening cards adds one level to that hierarchy.
        navigation = navigation.copy(selectedTeam = TeamId.TEAM_ONE)
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen("playing"))
        navigation = navigation.copy(cardChoiceStateToken = "playing")
        assertEquals(GameSurface.CARD_CHOICES, navigation.gameScreen("playing"))
        assertEquals("playing", navigation.cardChoiceStateToken)
        assertEquals(TeamId.TEAM_ONE, navigation.selectedTeam)

        // Number entry and its confirmation own Back themselves, rather than skipping the picker.
        val card = WearTeamAction.PlayerCard(CardType.YELLOW, "17")
        navigation = navigation.copy(playerCard = card)
        assertEquals(card, navigation.playerCard)
        assertEquals(GameSurface.PLAYER_CARD, navigation.gameScreen("playing"))
        navigation = navigation.copy(playerCard = null, pendingActionPrompt = timeoutConfirmation())
        navigation = navigation.copy(pendingActionPrompt = null)
        navigation = navigation.back()
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen("playing"))
        navigation = navigation.back()
        assertEquals(GameSurface.SCORE, navigation.gameScreen("playing"))

        // Team information returns to its team actions and closes when the connection is lost.
        navigation = navigation.copy(selectedTeam = TeamId.TEAM_TWO, teamInfoOpen = true)
        assertEquals(GameSurface.TEAM_INFO, navigation.gameScreen("playing"))
        assertEquals(NavigationState(), navigation.receive(snapshot, ConnectionState.DISCONNECTED))
        assertTrue(navigation.receive(snapshot, ConnectionState.CONNECTED).teamInfoOpen)
        assertFalse(navigation.receive(snapshot.copy(activeGame = snapshot.activeGame!!.copy(
            actionsAvailable = false,
        )), ConnectionState.CONNECTED).teamInfoOpen)
        navigation = navigation.back()
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen("playing"))
        assertEquals(TeamId.TEAM_TWO, navigation.selectedTeam)
        assertFalse(navigation.teamInfoOpen)
        assertEquals(NavigationState(), navigation.back())
    }

    /** Finish actions without discarding a local workflow when the phone rejects them. */
    @Test
    fun commandResults() {
        val card = WearTeamAction.PlayerCard(CardType.YELLOW, "17")
        val entry = NavigationState(
            selectedTeam = TeamId.TEAM_TWO, cardChoiceStateToken = "playing", playerCard = card,
            pendingActionPrompt = timeoutConfirmation(),
        )

        // A successful confirmation closes all local entry state. A rejection closes only the
        // obsolete prompt so the subsequent phone snapshot can reconcile the remaining workflow.
        assertEquals(NavigationState(), entry.finishConfirmation(true))
        val rejected = entry.finishConfirmation(false)
        assertNull(rejected.pendingActionPrompt)
        assertEquals(card, rejected.playerCard)
        assertEquals(TeamId.TEAM_TWO, rejected.selectedTeam)
        assertEquals("playing", rejected.cardChoiceStateToken)

        // Confirming a water break returns to the scores; rejection keeps timing controls open.
        val waterBreak = NavigationState(
            timingControlsOpen = true,
            pendingActionPrompt = WearActionConfirmation.WaterBreak(
                "playing", timeoutConfirmation().prompt,
            ),
        )
        assertEquals(NavigationState(), waterBreak.finishConfirmation(true))
        assertTrue(waterBreak.finishConfirmation(false).timingControlsOpen)

        // Goal and handoff results make different transitions: a goal returns to the score,
        // whereas a handoff clears number entry while awaiting the phone's entry snapshot.
        val team = NavigationState(selectedTeam = TeamId.TEAM_ONE)
        assertEquals(team, team.finishGoal(false))
        assertEquals(NavigationState(), team.finishGoal(true))
        assertEquals(entry, entry.finishHandoff(false))
        assertNull(entry.finishHandoff(true).playerCard)

        // A phone goal overtakes the watch's goal. Rejection arrives before the newer snapshot;
        // reconciling both leaves team actions open against the phone's current game.
        val playing = navigationSnapshot()
        var phoneGame = playing.activeGame!!.copy(
            stateToken = "phone-goal", teamOne = playing.activeGame!!.teamOne.copy(score = 1),
        )
        var navigation = team.finishGoal(false)
            .receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen(phoneGame.stateToken))
        assertNull(navigation.pendingActionPrompt)

        // A timeout-prompt request is also overtaken. Its null result must not open a prompt,
        // and receiving the next goal still leaves the team actions available.
        navigation = navigation.copy(pendingActionPrompt = null)
        phoneGame = phoneGame.copy(stateToken = "second-phone-goal", teamOne = phoneGame.teamOne.copy(score = 2))
        navigation = navigation.receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertEquals(WatchScreen.GAME, navigation.screen(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED))
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen(phoneGame.stateToken))

        // Retrying opens a fresh timeout confirmation, but the phone scores before OK arrives.
        // The rejected confirmation closes; its subsequent snapshot cannot revive that prompt.
        navigation = navigation.copy(pendingActionPrompt = timeoutConfirmation().copy(stateToken = phoneGame.stateToken))
        assertEquals(WatchScreen.ACTION_PROMPT, navigation.screen(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED))
        phoneGame = phoneGame.copy(stateToken = "third-phone-goal", teamOne = phoneGame.teamOne.copy(score = 3))
        navigation = navigation.finishConfirmation(false)
            .receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertNull(navigation.pendingActionPrompt)
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen(phoneGame.stateToken))

        // A numbered-card handoff loses the same race. The new token discards the local number
        // entry instead of showing a phone handoff that was never accepted.
        navigation = navigation.copy(cardChoiceStateToken = phoneGame.stateToken, playerCard = card)
        phoneGame = phoneGame.copy(stateToken = "fourth-phone-goal", teamOne = phoneGame.teamOne.copy(score = 4))
        navigation = navigation.finishHandoff(false)
            .receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertNull(navigation.playerCard)
        assertNull(navigation.cardChoiceStateToken)
        assertEquals(GameSurface.TEAM_ACTIONS, navigation.gameScreen(phoneGame.stateToken))

        // Retrying the handoff succeeds. The watch then cancels just as the phone records the
        // card. Rejection followed by the completed entry returns to the score, not the picker.
        navigation = navigation.copy(cardChoiceStateToken = phoneGame.stateToken, playerCard = card)
            .finishHandoff(true)
        phoneGame = phoneGame.copy(actionsAvailable = false,
            phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_ONE, CardType.YELLOW, "17"))
        navigation = navigation.receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertEquals(WatchScreen.PHONE_ENTRY, navigation.screen(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED))
        navigation = navigation.beginPhoneCancellation(phoneGame.phoneCardEntry!!).finishPhoneCancellation(false)
        phoneGame = phoneGame.copy(stateToken = "card-recorded", actionsAvailable = true, phoneCardEntry = null)
        navigation = navigation.receive(playing.copy(activeGame = phoneGame), ConnectionState.CONNECTED)
        assertEquals(GameSurface.SCORE, navigation.gameScreen(phoneGame.stateToken))
        assertNull(navigation.cardChoiceStateToken)
        assertNull(navigation.playerCard)
    }

    /** Restore the card type and number when cancellation was requested on the watch. */
    @Test
    fun phoneHandoff() {
        val playing = navigationSnapshot()
        for (team in TeamId.entries) for (cardType in listOf(CardType.YELLOW, CardType.RED, null)) {
            // Watch cancellation restores the card's number and color, or the picker if no
            // color had been chosen on the phone.
            val entry = WearPhoneCardEntrySnapshot(team, cardType, "17")
            val handoff = playing.copy(activeGame = playing.activeGame!!.copy(
                actionsAvailable = false,
                phoneCardEntry = entry,
            ))
            var navigation = NavigationState(
                selectedTeam = team,
                cardChoiceStateToken = "playing",
            ).receive(handoff, ConnectionState.CONNECTED)
            assertEquals(WatchScreen.PHONE_ENTRY, navigation.screen(handoff, ConnectionState.CONNECTED))
            navigation = navigation.beginPhoneCancellation(entry).finishPhoneCancellation(true)
                .receive(playing, ConnectionState.CONNECTED)
            assertEquals(team, navigation.selectedTeam)
            if (cardType == null) {
                assertEquals(GameSurface.CARD_CHOICES, navigation.gameScreen("playing"))
                assertNull(navigation.playerCard)
            } else {
                assertEquals(GameSurface.PLAYER_CARD, navigation.gameScreen("playing"))
                assertEquals(WearTeamAction.PlayerCard(cardType, "17"), navigation.playerCard)
            }

            // Phone-side completion, including a cancellation rejected because the phone already
            // finished, returns to the score instead of restoring the old picker.
            navigation = navigation.receive(handoff, ConnectionState.CONNECTED)
                .beginPhoneCancellation(entry).finishPhoneCancellation(false)
                .receive(playing, ConnectionState.CONNECTED)
            assertEquals(GameSurface.SCORE, navigation.gameScreen("playing"))
        }
    }

    /** Discard local screens superseded by phone changes or loss of connection. */
    @Test
    fun phoneChanges() {
        val playing = navigationSnapshot()
        val entry = NavigationState(
            selectedTeam = TeamId.TEAM_ONE, cardChoiceStateToken = "playing",
            playerCard = WearTeamAction.PlayerCard(CardType.RED, "3"),
            pendingActionPrompt = timeoutConfirmation(),
        )
        assertEquals(entry, entry.receive(playing, ConnectionState.CONNECTED))

        // A new goal invalidates the number and prompt, but leaves team actions available.
        val goal = playing.copy(activeGame = playing.activeGame!!.copy(stateToken = "new-goal"))
        val changed = entry.receive(goal, ConnectionState.CONNECTED)
        assertNull(changed.playerCard)
        assertNull(changed.pendingActionPrompt)
        assertNull(changed.cardChoiceStateToken)
        assertEquals(GameSurface.TEAM_ACTIONS, changed.gameScreen("new-goal"))

        // If that goal is followed by phone card entry, the phone workflow takes over entirely.
        val handoff = goal.copy(activeGame = goal.activeGame!!.copy(
            actionsAvailable = false,
            phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_TWO, CardType.RED, "3"),
        ))
        val onPhone = entry.receive(handoff, ConnectionState.CONNECTED)
        assertNull(onPhone.selectedTeam)
        assertEquals(NavigationState(), onPhone.receive(goal, ConnectionState.CONNECTED))

        // Leaving the phone game closes team actions. Disconnection also clears prompts and
        // handoff return state, rather than restoring them after reconnection.
        val paused = playing.copy(activeGame = playing.activeGame!!.copy(actionsAvailable = false))
        assertNull(entry.receive(paused, ConnectionState.CONNECTED).selectedTeam)
        assertEquals(NavigationState(), entry.receive(playing, ConnectionState.DISCONNECTED))
        assertEquals(NavigationState(), onPhone.receive(playing, ConnectionState.DISCONNECTED))
        assertEquals(NavigationState(), entry.receive(null, ConnectionState.CONNECTING))
        val idle = WearStateSnapshot(status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null)
        assertEquals(NavigationState(), entry.receive(idle, ConnectionState.CONNECTED))
    }

    /** Choose startup, idle, and phone-owned screens before local navigation. */
    @Test
    fun screenSelection() {
        val navigation = NavigationState()
        assertEquals(WatchScreen.CONNECTING, navigation.screen(null, ConnectionState.CONNECTING))
        assertEquals(WatchScreen.UNREACHABLE, navigation.screen(null, ConnectionState.DISCONNECTED))
        assertEquals(WatchScreen.DISABLED, navigation.screen(null, ConnectionState.DISABLED))
        val idle = WearStateSnapshot(status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null)
        assertEquals(WatchScreen.IDLE, navigation.screen(idle, ConnectionState.CONNECTED))
        assertEquals(WatchScreen.DISCONNECTED, navigation.screen(idle, ConnectionState.DISCONNECTED))
        assertEquals(WatchScreen.DISABLED, navigation.screen(
            idle.copy(status = WearSnapshotStatus.DISABLED), ConnectionState.CONNECTED,
        ))

        // A phone confirmation outranks local team actions. Disconnecting leaves the score
        // readable instead of offering an action on that stale confirmation.
        val playing = navigationSnapshot()
        val confirmation = playing.copy(activeGame = playing.activeGame!!.copy(
            pendingDecision = timeoutConfirmation().prompt,
        ))
        assertEquals(WatchScreen.CONFIRMATION, navigation.screen(confirmation, ConnectionState.CONNECTED))
        assertEquals(WatchScreen.GAME, navigation.screen(confirmation, ConnectionState.DISCONNECTED))
        val prompt = navigation.copy(pendingActionPrompt = timeoutConfirmation())
        assertEquals(WatchScreen.ACTION_PROMPT, prompt.screen(playing, ConnectionState.CONNECTED))
        assertEquals(WatchScreen.GAME, prompt.screen(playing, ConnectionState.DISCONNECTED))
        assertEquals(WatchScreen.GAME, navigation.screen(playing, ConnectionState.CONNECTED))
    }
}

internal fun navigationSnapshot(): WearStateSnapshot {
    val actions = WearTeamActionsSnapshot(
        "Time viol.", "Offsides", "Card", "Tech", "Timeout (2)",
        true, true, true, true, true, true,
    )
    return WearStateSnapshot(
        status = WearSnapshotStatus.ACTIVE_GAME,
        activeGame = WearActiveGameSnapshot(
            leftTeam = TeamId.TEAM_ONE,
            stateToken = "playing", actionsAvailable = true,
            officialClockOffsetMillis = 0,
            officialTimeZoneId = "UTC",
            rulesReference = listOf(WearRulesReferenceItemSnapshot("Game to", "15", false)),
            countdownActions = emptyList(),
            timingControls = null,
            countdown = null,
            teamOne = WearTeamSnapshot("Animal", 0, "Far end", 0xFFFFFFFF, 0xFF000000, actions, emptyList()),
            teamTwo = WearTeamSnapshot("Viscous Coupling", 0, "Near end", 0xFF000000, 0xFFFFFFFF, actions, emptyList()),
            pullDirection = WearSnapshotPullDirection.LEFT_TO_RIGHT, ratio = null,
            ratioChooser = null,
            redoAvailable = false,
            undoDescription = null, pendingDecision = null, phoneCardEntry = null,
        ),
    )
}

internal fun timeoutConfirmation() = WearActionConfirmation.Timeout(
    "playing", TeamId.TEAM_ONE, 1_000L,
    WearPromptSnapshot("Timeout", emptyList(), "OK", "Cancel", WearGuidancePresentation.VISIBLE, null),
)

package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rmjarvis.ultiobserver.CardType
import rmjarvis.ultiobserver.PlayerIdentity
import rmjarvis.ultiobserver.PullViolationType
import rmjarvis.ultiobserver.TeamId

/** Tests for the complete versioned payload exchanged by the phone and watch. */
class TestWearProtocol {
    /** Every state value needed to render a game survives the shared JSON codec. */
    @Test
    fun stateSnapshotRoundTrip() {
        val actions = WearTeamActionsSnapshot(
            timeViolationLabel = "Time viol. (1)",
            pullViolationLabel = "Offsides (2)",
            cardLabel = "Card (3)",
            technicalFoulLabel = "Tech (4)",
            timeoutLabel = "Timeout (1)",
            goalEnabled = true,
            timeViolationEnabled = false,
            pullViolationEnabled = true,
            cardEnabled = false,
            technicalFoulEnabled = true,
            timeoutEnabled = false,
        )
        val teamOne = WearTeamSnapshot(
            name = "Animal",
            score = 8,
            fieldEndName = "Clubhouse",
            backgroundArgb = 0xFF123456,
            contentArgb = 0xFFFFFFFF,
            actions = actions,
            nameInfo = listOf(WearGuidanceLineSnapshot("Coach", true), WearGuidanceLineSnapshot("Alex", false)),
        )
        val teamTwo = WearTeamSnapshot(
            name = "Machine",
            nameInfo = emptyList(),
            score = 7,
            fieldEndName = "Road",
            backgroundArgb = 0xFFABCDEF,
            contentArgb = 0xFF000000,
            actions = actions.copy(
                goalEnabled = false,
                timeoutEnabled = true,
            ),
        )
        val cue = WearCueSnapshot(
            message = "Give hand. 20 seconds to pull",
            targetEpochMillis = 1_100L,
        )
        val countdown = WearCountdownSnapshot(
            label = "Pull in",
            targetEpochMillis = 2_000L,
            pausedAtEpochMillis = 1_250L,
            cues = listOf(cue),
        )
        val cap = WearCapSnapshot("Hard cap", 3_000L)
        val transition = WearStatusMessageTransition(
            targetEpochMillis = 3_000L,
            message = "Hard cap passed. It will apply at the end of this point.",
        )
        val ratio = WearRatioSnapshot(
            label = "4M/3W",
            backgroundArgb = 0xFF010203,
            contentArgb = 0xFFFDFCFB,
        )
        val decision = prompt("Halftime")
        val phoneCardEntry = WearPhoneCardEntrySnapshot(
            team = TeamId.TEAM_TWO,
            cardType = CardType.RED,
            jerseyNumber = "23",
        )
        val activeGame = WearActiveGameSnapshot(
            stateToken = "state-token",
            actionsAvailable = false,
            gameOver = true,
            officialClockOffsetMillis = 42_000L,
            officialTimeZoneId = "America/New_York",
            upcomingCaps = listOf(cap),
            countdownActions = emptyList(),
            timingControls = null,
            countdown = countdown,
            statusMessageTransitions = listOf(transition),
            teamOne = teamOne,
            teamTwo = teamTwo,
            pullDirection = WearSnapshotPullDirection.RIGHT_TO_LEFT,
            ratio = ratio,
            ratioChooser = null,
            undoDescription = "Undo Goal by Animal",
            pendingDecision = decision,
            phoneCardEntry = phoneCardEntry,
        )
        val snapshot = WearStateSnapshot(
            protocolVersion = 17,
            status = WearSnapshotStatus.ACTIVE_GAME,
            activeGame = activeGame,
        )

        // A complete nondefault snapshot round-trips as one value.
        val decoded = roundTrip(WearStateSnapshot.serializer(), snapshot)
        assertEquals(snapshot, decoded)
        assertEquals(17, decoded.protocolVersion)
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, decoded.status)
        val game = decoded.activeGame!!
        assertEquals("state-token", game.stateToken)
        assertFalse(game.actionsAvailable)
        assertTrue(game.gameOver)
        assertEquals(42_000L, game.officialClockOffsetMillis)
        assertEquals("America/New_York", game.officialTimeZoneId)
        assertEquals("Hard cap", game.upcomingCaps.single().label)
        assertEquals(3_000L, game.upcomingCaps.single().targetEpochMillis)
        val decodedCountdown = game.countdown!!
        assertEquals("Pull in", decodedCountdown.label)
        assertEquals(2_000L, decodedCountdown.targetEpochMillis)
        assertEquals(1_250L, decodedCountdown.pausedAtEpochMillis)
        assertEquals("Give hand. 20 seconds to pull", decodedCountdown.cues.single().message)
        assertEquals(1_100L, decodedCountdown.cues.single().targetEpochMillis)
        assertEquals(3_000L, game.statusMessageTransitions.single().targetEpochMillis)
        assertEquals(
            "Hard cap passed. It will apply at the end of this point.",
            game.statusMessageTransitions.single().message,
        )
        assertEquals("Animal", game.teamOne.name)
        assertEquals(8, game.teamOne.score)
        assertEquals(0xFF123456, game.teamOne.backgroundArgb)
        assertEquals(0xFFFFFFFF, game.teamOne.contentArgb)
        assertEquals("Time viol. (1)", game.teamOne.actions.timeViolationLabel)
        assertEquals("Offsides (2)", game.teamOne.actions.pullViolationLabel)
        assertEquals("Card (3)", game.teamOne.actions.cardLabel)
        assertEquals("Tech (4)", game.teamOne.actions.technicalFoulLabel)
        assertEquals("Timeout (1)", game.teamOne.actions.timeoutLabel)
        assertTrue(game.teamOne.actions.goalEnabled)
        assertFalse(game.teamOne.actions.timeViolationEnabled)
        assertTrue(game.teamOne.actions.pullViolationEnabled)
        assertFalse(game.teamOne.actions.cardEnabled)
        assertTrue(game.teamOne.actions.technicalFoulEnabled)
        assertFalse(game.teamOne.actions.timeoutEnabled)
        assertEquals("Machine", game.teamTwo.name)
        assertEquals(7, game.teamTwo.score)
        assertEquals(WearSnapshotPullDirection.RIGHT_TO_LEFT, game.pullDirection)
        val decodedRatio = game.ratio!!
        assertEquals("4M/3W", decodedRatio.label)
        assertEquals(0xFF010203, decodedRatio.backgroundArgb)
        assertEquals(0xFFFDFCFB, decodedRatio.contentArgb)
        assertEquals("Undo Goal by Animal", game.undoDescription)
        assertEquals(decision, game.pendingDecision)
        val decodedCardEntry = game.phoneCardEntry!!
        assertEquals(TeamId.TEAM_TWO, decodedCardEntry.team)
        assertEquals(CardType.RED, decodedCardEntry.cardType)
        assertEquals("23", decodedCardEntry.jerseyNumber)

        // The compact ordinary-game constructor supplies every documented default, and the codec
        // emits those values so both sides agree on the complete versioned representation.
        val defaultGame = activeGame.copy(
            gameOver = false,
            upcomingCaps = emptyList(),
            statusMessageTransitions = emptyList(),
        )
        val constructedWithDefaults = WearActiveGameSnapshot(
            stateToken = defaultGame.stateToken,
            actionsAvailable = defaultGame.actionsAvailable,
            officialClockOffsetMillis = defaultGame.officialClockOffsetMillis,
            officialTimeZoneId = defaultGame.officialTimeZoneId,
            countdownActions = emptyList(),
            timingControls = null,
            countdown = defaultGame.countdown,
            teamOne = defaultGame.teamOne,
            teamTwo = defaultGame.teamTwo,
            pullDirection = defaultGame.pullDirection,
            ratio = defaultGame.ratio,
            ratioChooser = null,
            undoDescription = defaultGame.undoDescription,
            pendingDecision = defaultGame.pendingDecision,
            phoneCardEntry = defaultGame.phoneCardEntry,
        )
        assertFalse(constructedWithDefaults.gameOver)
        assertTrue(constructedWithDefaults.upcomingCaps.isEmpty())
        assertTrue(constructedWithDefaults.statusMessageTransitions.isEmpty())
        assertEquals(
            constructedWithDefaults,
            roundTrip(WearActiveGameSnapshot.serializer(), constructedWithDefaults),
        )

        // Top-level idle and disabled states use the current protocol version and no game.
        WearSnapshotStatus.entries.forEach { status ->
            if (status != WearSnapshotStatus.ACTIVE_GAME) {
                val noGame = WearStateSnapshot(status = status, activeGame = null)
                assertEquals(WEAR_PROTOCOL_VERSION, noGame.protocolVersion)
                assertNull(roundTrip(WearStateSnapshot.serializer(), noGame).activeGame)
            }
        }
    }

    /** Every observer command and team-action choice survives its request envelope. */
    @Test
    fun gameCommandRoundTrips() {
        // Every registered path maps back to its command, while unrelated Data Layer traffic does
        // not become an UltiObserver action.
        WearRequestAction.entries.forEach { action ->
            assertEquals(action, WearRequestAction.fromPath(action.path))
        }
        assertNull(WearRequestAction.fromPath("/some-other-app/action"))

        // Send a goal inside the same command envelope used by the watch. The phone can recover
        // both the request ID needed for acknowledgement and the exact action arguments.
        val goal = WearGoalRequest("goal-state", TeamId.TEAM_ONE)
        val command = roundTrip(
            WearCommandRequest.serializer(),
            WearCommandRequest("watch-goal", WearProtocolCodec.encode(WearGoalRequest.serializer(), goal)),
        )
        assertEquals("watch-goal", command.requestId)
        assertEquals(goal, WearProtocolCodec.decode(WearGoalRequest.serializer(), command.arguments))

        // Other direct game requests retain the exact state token and observer choice.
        assertRoundTrip(WearUndoRequest.serializer(), WearUndoRequest("undo-state"))
        assertRoundTrip(
            WearDecisionRequest.serializer(),
            WearDecisionRequest("decision-state", accept = false),
        )

        // Each team-action subtype remains distinguishable inside its polymorphic request.
        val teamActions = listOf(
            WearTeamAction.Timeout,
            WearTeamAction.TimeViolation,
            WearTeamAction.PullViolation,
            WearTeamAction.BlueCard,
            WearTeamAction.TechnicalFoul,
            WearTeamAction.PlayerCard(CardType.YELLOW, "8"),
            WearTeamAction.PlayerCard(CardType.RED, "19"),
        )
        teamActions.forEach { action ->
            val request = WearTeamActionRequest("team-state", TeamId.TEAM_TWO, action)
            val decoded = roundTrip(WearTeamActionRequest.serializer(), request)
            assertEquals("team-state", decoded.stateToken)
            assertEquals(TeamId.TEAM_TWO, decoded.team)
            assertEquals(action, decoded.action)
        }

        // Starting and cancelling phone card entry retain both populated and absent card details.
        assertRoundTrip(
            WearCardEntryRequest.serializer(),
            WearCardEntryRequest("card-state", TeamId.TEAM_ONE, CardType.RED, "42"),
        )
        listOf(CardType.YELLOW, null).forEach { cardType ->
            assertRoundTrip(
                WearCancelCardEntryRequest.serializer(),
                WearCancelCardEntryRequest(
                    stateToken = "cancel-state",
                    team = TeamId.TEAM_TWO,
                    cardType = cardType,
                    jerseyNumber = "7",
                ),
            )
        }
    }

    /** Every phone-owned prompt preserves the context needed for its eventual response. */
    @Test
    fun actionPromptRoundTrips() {
        val visiblePrompt = prompt("Confirm action")
        val timedPrompt = WearPromptSnapshot(
            title = "Timed notice",
            messageLines = listOf(WearGuidanceLineSnapshot("Brief guidance", bold = false)),
            confirmLabel = "OK",
            dismissLabel = "Not yet",
            presentation = WearGuidancePresentation.VISIBLE_TIMED,
            autoAcceptDelayMillis = 5_000L,
        )
        val hiddenPrompt = timedPrompt.copy(
            title = "Hidden acknowledgement",
            presentation = WearGuidancePresentation.HIDDEN_AUTO_ACCEPT,
            autoAcceptDelayMillis = null,
        )
        val pullOptions = listOf(
            WearPullViolationOption(PullViolationType.OFFSIDES, visiblePrompt),
            WearPullViolationOption(PullViolationType.MAJORITY_PULL, timedPrompt),
        )
        val prompts: List<WearTeamActionPrompt> = listOf(
            WearTeamActionPrompt.Notice("notice-state", hiddenPrompt),
            WearActionConfirmation.Timeout(
                "timeout-state",
                TeamId.TEAM_ONE,
                1_000L,
                visiblePrompt,
            ),
            WearActionConfirmation.TimeViolation(
                "time-state",
                TeamId.TEAM_TWO,
                2_000L,
                timedPrompt,
            ),
            WearActionConfirmation.PullViolation(
                stateToken = "pull-state",
                team = TeamId.TEAM_ONE,
                requestedAtPhoneEpochMillis = 3_000L,
                selectedViolation = PullViolationType.OFFSIDES,
                options = pullOptions,
                prompt = visiblePrompt,
            ),
            WearActionConfirmation.BlueCard(
                "blue-state",
                TeamId.TEAM_TWO,
                4_000L,
                timedPrompt,
            ),
            WearActionConfirmation.TechnicalFoul(
                "tech-state",
                TeamId.TEAM_ONE,
                5_000L,
                hiddenPrompt,
            ),
            WearActionConfirmation.PlayerCard(
                stateToken = "player-state",
                team = TeamId.TEAM_TWO,
                cardType = CardType.YELLOW,
                identity = PlayerIdentity("23", "Morgan"),
                requestedAtPhoneEpochMillis = 6_000L,
                prompt = visiblePrompt,
            ),
            WearActionConfirmation.CardEntryHandoff(
                stateToken = "handoff-state",
                team = TeamId.TEAM_ONE,
                cardType = CardType.RED,
                jerseyNumber = "31",
                prompt = timedPrompt,
            ),
        )

        // The sealed prompt envelope round-trips every notice and confirmation subtype.
        prompts.forEach { actionPrompt ->
            val decoded = roundTrip(WearTeamActionPrompt.serializer(), actionPrompt)
            assertEquals(actionPrompt, decoded)
            assertEquals(actionPrompt.stateToken, decoded.stateToken)
            assertEquals(actionPrompt.prompt, decoded.prompt)
        }
        assertEquals(PullViolationType.OFFSIDES, pullOptions.first().violation)
        assertEquals(visiblePrompt, pullOptions.first().prompt)
        assertEquals("Primary guidance", visiblePrompt.messageLines.first().text)
        assertTrue(visiblePrompt.messageLines.first().bold)
        assertFalse(visiblePrompt.messageLines.last().bold)

        // Confirm-action and response envelopes preserve their nested polymorphic prompts.
        val confirmation = prompts.filterIsInstance<WearActionConfirmation.PlayerCard>().single()
        assertRoundTrip(
            WearConfirmActionRequest.serializer(),
            WearConfirmActionRequest(confirmation),
        )
        val state = WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
        )
        assertRoundTrip(
            WearGameActionResponse.serializer(),
            WearGameActionResponse(
                applied = true,
                snapshot = state,
                nextPrompt = prompts.first(),
            ),
        )
        assertRoundTrip(
            WearGameActionResponse.serializer(),
            WearGameActionResponse(
                applied = false,
                snapshot = state,
                nextPrompt = null,
            ),
        )
        assertRoundTrip(
            WearStateUpdate.serializer(),
            WearStateUpdate(
                snapshot = state,
                acknowledgement = WearStartupAcknowledgement("startup"),
            ),
        )
        assertRoundTrip(WearStartupRequest.serializer(), WearStartupRequest("startup"))
        assertRoundTrip(WearStartupResponse.serializer(), WearStartupResponse(false, 123_456L))
        assertRoundTrip(WearStartupResponse.serializer(), WearStartupResponse(true, 123_456L))

        // Startup preserves the ID the phone acknowledges and advertises the protocol version
        // the watch checks before accepting the reply.
        val startupRequest = roundTrip(WearStartupRequest.serializer(), WearStartupRequest("startup"))
        val startupResponse = roundTrip(
            WearStartupResponse.serializer(), WearStartupResponse(true, 123_456L),
        )
        assertEquals("startup", startupRequest.requestId)
        assertEquals(WEAR_PROTOCOL_VERSION, startupResponse.protocolVersion)
    }

    private fun prompt(title: String): WearPromptSnapshot {
        return WearPromptSnapshot(
            title = title,
            messageLines = listOf(
                WearGuidanceLineSnapshot("Primary guidance", bold = true),
                WearGuidanceLineSnapshot("Supporting guidance", bold = false),
            ),
            confirmLabel = "OK",
            dismissLabel = "Cancel",
            presentation = WearGuidancePresentation.VISIBLE,
            autoAcceptDelayMillis = null,
        )
    }

    private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T) {
        assertEquals(value, roundTrip(serializer, value))
    }

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T {
        return WearProtocolCodec.decode(
            serializer,
            WearProtocolCodec.encode(serializer, value),
        )
    }
}

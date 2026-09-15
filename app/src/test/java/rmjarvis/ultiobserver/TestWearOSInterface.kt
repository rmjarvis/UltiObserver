package rmjarvis.ultiobserver

import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearCancelCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearConfirmActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearCountdownAction
import rmjarvis.ultiobserver.wearprotocol.WearCountdownActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearUndoRequest
import rmjarvis.ultiobserver.wearprotocol.WearCommandRequest
import rmjarvis.ultiobserver.wearprotocol.WearCommandAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotReceiver
import rmjarvis.ultiobserver.wearprotocol.WearPendingCommand

/// Tests for the phone-side Wear OS interface.
class TestWearOSInterface : GameDomainTestFixtures() {
    private val coordinators = mutableMapOf<AppState, WearPhoneCoordinator>()
    private val publications = mutableMapOf<AppState, MutableList<WearStateUpdate>>()

    private fun coordinatorFor(appState: AppState): WearPhoneCoordinator {
        return coordinators.getOrPut(appState) {
            WearPhoneCoordinator(
                appState,
                publish = { publications.getOrPut(appState) { mutableListOf() }.add(it) },
                clock = { 0L },
            )
        }
    }

    private fun activeWearState(
        settings: Settings,
        game: GameState = standardLiveGameState().continueLivePoint(),
    ): AppState {
        return AppState(NoOpAppStateStorage).also { appState ->
            appState.updateSettings(settings)
            appState.updateCurrentGame(game)
            appState.resumeCurrentGame()
        }
    }

    private fun liveScreenWearState(settings: Settings, game: GameState): AppState {
        return activeWearState(settings).also { appState -> appState.updateCurrentGame(game) }
    }

    private fun requestResponse(
        appState: AppState,
        action: WearRequestAction,
        request: ByteArray,
        now: Long,
    ): WearGameActionResponse {
        coordinatorFor(appState).handleRequest(
            requestedActionPath = action.path,
            request = WearCommandRequest(UUID.randomUUID().toString(), request),
            now = now,
        )
        val update = publications.getValue(appState).last()
        val acknowledgement = update.acknowledgement as WearCommandAcknowledgement
        return WearGameActionResponse(
            acknowledgement.applied, update.snapshot, acknowledgement.nextPrompt,
        )
    }

    private fun teamActionResponse(
        appState: AppState,
        action: WearTeamAction,
        now: Long,
        team: TeamId = TeamId.TEAM_ONE,
    ): WearGameActionResponse {
        val game = requireNotNull(appState.currentGame)
        return requestResponse(
            appState = appState,
            action = WearRequestAction.TEAM_ACTION,
            request = WearProtocolCodec.encode(
                WearTeamActionRequest.serializer(),
                WearTeamActionRequest(wearStateToken(game), team, action),
            ),
            now = now,
        )
    }

    private fun confirmationResponse(
        confirmation: WearActionConfirmation,
        game: GameState,
        settings: Settings,
        now: Long,
    ): WearGameActionResponse {
        return requestResponse(
            appState = activeWearState(settings, game),
            action = WearRequestAction.CONFIRM_ACTION,
            request = WearProtocolCodec.encode(
                WearConfirmActionRequest.serializer(),
                WearConfirmActionRequest(confirmation),
            ),
            now = now,
        )
    }

    /** A connected-node query reports availability only when at least one node was returned. */
    @Test
    fun wearNodeAvailability() {
        assertFalse(hasAvailableWearNode(0))
        assertTrue(hasAvailableWearNode(1))
    }

    /**
     * Verify the phone produces a self-contained, authoritative state representation from which
     * the watch can render the companion experience.
     */
    @Test
    fun watchStateSnapshot() {
        val now = timestampAt(
            standardLiveGameState(),
            LocalTime.of(11, 0),
        )

        // The ordinary Off setting explicitly tells an installed watch that companion use is
        // disabled, without exposing game state.
        val disabled = buildWearStateSnapshot(
            game = standardLiveGameState(),
            settings = Settings(),
            now = now,
        )
        assertEquals(WearSnapshotStatus.DISABLED, disabled.status)
        assertNull(disabled.activeGame)

        // Wear OS mode distinguishes an idle phone from a disabled connection.
        val settings = Settings().copy(
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val noGame = buildWearStateSnapshot(
            game = null,
            settings = settings,
            now = now,
        )
        assertEquals(WearSnapshotStatus.NO_ACTIVE_GAME, noGame.status)
        assertNull(noGame.activeGame)

        // A default game carries the default team presentation and action labels while omitting
        // optional state that does not apply.
        val defaultGame = standardLiveGameState()
        val default = buildWearStateSnapshot(
            game = defaultGame,
            settings = settings,
            now = now,
        )
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, default.status)
        assertNotNull(default.activeGame)
        val defaultSnapshot = default.activeGame!!
        assertEquals(wearStateToken(defaultGame), defaultSnapshot.stateToken)
        assertTrue(defaultSnapshot.actionsAvailable)
        assertFalse(defaultSnapshot.gameOver)
        assertEquals(0L, defaultSnapshot.officialClockOffsetMillis)
        assertEquals("America/New_York", defaultSnapshot.officialTimeZoneId)
        assertTrue(defaultSnapshot.upcomingCaps.isEmpty())
        assertNotNull(defaultSnapshot.countdown)
        assertNull(defaultSnapshot.ratio)
        assertNull(defaultSnapshot.pendingDecision)
        assertNull(defaultSnapshot.undoDescription)
        assertTrue(defaultSnapshot.statusMessageTransitions.isEmpty())
        assertEquals(WearSnapshotPullDirection.LEFT_TO_RIGHT, defaultSnapshot.pullDirection)
        assertEquals("Viscous Coupling", defaultSnapshot.teamOne.name)
        assertEquals(0, defaultSnapshot.teamOne.score)
        assertEquals("Far end", defaultSnapshot.teamOne.fieldEndName)
        assertEquals("Near end", defaultSnapshot.teamTwo.fieldEndName)
        assertEquals(TeamColorChoice.WHITE.accentArgb, defaultSnapshot.teamOne.backgroundArgb)
        assertEquals(TeamColorChoice.WHITE.contentArgb, defaultSnapshot.teamOne.contentArgb)
        assertEquals("Time viol.", defaultSnapshot.teamOne.actions.timeViolationLabel)
        assertEquals("Offsides", defaultSnapshot.teamOne.actions.pullViolationLabel)
        assertEquals("Card", defaultSnapshot.teamOne.actions.cardLabel)
        assertEquals("Tech", defaultSnapshot.teamOne.actions.technicalFoulLabel)
        assertEquals("Timeout (2)", defaultSnapshot.teamOne.actions.timeoutLabel)
        assertTrue(defaultSnapshot.teamOne.actions.goalEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.timeViolationEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.pullViolationEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.cardEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.technicalFoulEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.timeoutEnabled)

        // Custom field-end names follow the teams when they switch ends after a goal.
        val namedEnds = defaultGame.copy(nearEndName = "Road", farEndName = "Clubhouse")
        val namedSnapshot = buildWearStateSnapshot(namedEnds, settings, now).activeGame!!
        assertEquals("Clubhouse", namedSnapshot.teamOne.fieldEndName)
        assertEquals("Road", namedSnapshot.teamTwo.fieldEndName)
        val scored = namedEnds.recordGoal(TeamId.TEAM_ONE, now)
        val scoredSnapshot = buildWearStateSnapshot(scored, settings, now).activeGame!!
        assertEquals("Road", scoredSnapshot.teamOne.fieldEndName)
        assertEquals("Clubhouse", scoredSnapshot.teamTwo.fieldEndName)
        val undoneSnapshot = buildWearStateSnapshot(scored.undoLastAction(), settings, now).activeGame!!
        assertEquals(namedSnapshot.teamOne.fieldEndName, undoneSnapshot.teamOne.fieldEndName)
        assertEquals(namedSnapshot.teamTwo.fieldEndName, undoneSnapshot.teamTwo.fieldEndName)

        // Leaving the active-game screen keeps the game visible but disables every watch action.
        val unavailable = buildWearStateSnapshot(
            game = defaultGame,
            settings = settings,
            now = now,
            actionsAvailable = false,
        ).activeGame!!
        assertFalse(unavailable.actionsAvailable)

        // A pending phone decision also blocks watch actions until the phone accepts or rejects it.
        val pendingGame = defaultGame.copy(
            pendingScoreTransition = PendingScoreTransition(
                transition = ScoreTransition.HALFTIME,
                effectiveEpoch = now,
            ),
        )
        val pending = buildWearStateSnapshot(
            game = pendingGame,
            settings = settings,
            now = now,
        ).activeGame!!
        assertFalse(pending.actionsAvailable)

        // A live point without an interruption countdown omits countdown state entirely.
        val livePointSnapshot = buildWearStateSnapshot(
            game = standardLiveGameState().continueLivePoint(),
            settings = settings,
            now = now,
        ).activeGame!!
        assertNull(livePointSnapshot.countdown)

        val capTimelineGame = standardLiveGameState(
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = true,
                nominalSoftCapMinutes = 1,
                useHardCap = true,
                nominalHardCapMinutes = 2,
            ),
        ).continueLivePoint()
        val softCapEpoch = capTimelineGame.capEpoch(CapType.SOFT)
        val hardCapEpoch = capTimelineGame.capEpoch(CapType.HARD)
        val capTimelineSnapshot = buildWearStateSnapshot(
            game = capTimelineGame,
            settings = settings,
            now = softCapEpoch - 10_000L,
        ).activeGame!!

        // A live-point snapshot carries every future relevant cap, allowing the watch to advance
        // from the soft-cap countdown to the hard-cap countdown using phone time.
        assertEquals(
            listOf("Soft cap", "Hard cap"),
            capTimelineSnapshot.upcomingCaps.map { cap -> cap.label },
        )
        assertEquals(
            listOf(softCapEpoch, hardCapEpoch),
            capTimelineSnapshot.upcomingCaps.map { cap -> cap.targetEpochMillis },
        )

        // The same snapshot schedules the cap messages the phone will show after those countdowns
        // expire, allowing the watch to advance through that text using phone time as well.
        assertEquals(
            listOf(softCapEpoch, hardCapEpoch),
            capTimelineSnapshot.statusMessageTransitions.map { transition ->
                transition.targetEpochMillis
            },
        )
        assertEquals(
            listOf(
                "Soft cap passed. It will apply at the end of this point.",
                "Hard cap passed. It will apply at the end of this point.",
            ),
            capTimelineSnapshot.statusMessageTransitions.map { transition -> transition.message },
        )

        // A separate halftime game makes all context-dependent team actions unavailable.
        val halftimeBaseGame = standardLiveGameState()
        val halftimeGame = halftimeBaseGame.copy(
            division = GameDivision.OPEN,
            phase = GamePhase.HALFTIME,
            countdown = buildHalftimeCountdown(
                halftimeMinutes = halftimeBaseGame.rules.halftimeMinutes,
                sequenceStart = now,
            ),
            halftimeTaken = true,
        )
        val halftimeSnapshot = buildWearStateSnapshot(
            game = halftimeGame,
            settings = settings,
            now = now,
        ).activeGame!!
        assertFalse(halftimeSnapshot.teamOne.actions.timeViolationEnabled)
        assertFalse(halftimeSnapshot.teamOne.actions.pullViolationEnabled)
        assertFalse(halftimeSnapshot.teamOne.actions.timeoutEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.timeViolationEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.pullViolationEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.timeoutEnabled)

        // An active mixed game carries exact phone colors, phone-style action labels, timing
        // deadlines, future cues, ratio settings, pull direction, and Undo availability.
        val baseGame = standardLiveGameState(
            pullingTeam = TeamId.TEAM_TWO,
        )
        val activeGame = baseGame.copy(
            division = GameDivision.MIXED,
            rules = baseGame.rules.copy(
                gameTo = 15,
                useHardCap = true,
                nominalHardCapMinutes = 180,
                genderRatioRule = GenderRatioRule.ABBA,
            ),
            officialClockOffsetMillis = 42_000L,
            teamOne = baseGame.teamOne.copy(
                name = "Animal",
                color = TeamColorChoice.BLUE,
                score = 8,
                timeViolations = 1,
                falseStarts = 2,
                technicalFouls = 1,
            ),
            teamTwo = baseGame.teamTwo.copy(
                name = "Machine",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF123456,
                score = 7,
            ),
            undoEntry = UndoEntry("Undo goal by Animal", baseGame),
        )
        val mixedSettings = settings.copy(
            fourMenThreeWomenBadgeColorArgb = TeamColorChoice.BLACK.accentArgb,
            fourWomenThreeMenBadgeColorArgb = TeamColorChoice.PINK.accentArgb,
        )
        val active = buildWearStateSnapshot(
            game = activeGame,
            settings = mixedSettings,
            now = now,
        )
        assertNotNull(active.activeGame)
        val gameSnapshot = active.activeGame!!
        val expectedCountdown = activeGame.activeCountdown(now)!!
        val expectedCaps = activeGame.upcomingCapStatuses(now)
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, active.status)
        assertEquals(42_000L, gameSnapshot.officialClockOffsetMillis)
        assertEquals("America/New_York", gameSnapshot.officialTimeZoneId)
        assertEquals(
            expectedCaps.map { status -> status.label },
            gameSnapshot.upcomingCaps.map { cap -> cap.label },
        )
        assertEquals(
            expectedCaps.map { status -> status.targetEpoch },
            gameSnapshot.upcomingCaps.map { cap -> cap.targetEpochMillis },
        )
        assertEquals("Animal", gameSnapshot.teamOne.name)
        assertEquals(8, gameSnapshot.teamOne.score)
        assertEquals(TeamColorChoice.BLUE.accentArgb, gameSnapshot.teamOne.backgroundArgb)
        assertEquals("Time viol. (1)", gameSnapshot.teamOne.actions.timeViolationLabel)
        assertEquals("False start (2)", gameSnapshot.teamOne.actions.pullViolationLabel)
        assertEquals("Tech (1)", gameSnapshot.teamOne.actions.technicalFoulLabel)
        assertEquals(0xFF123456, gameSnapshot.teamTwo.backgroundArgb)
        assertEquals(WearSnapshotPullDirection.RIGHT_TO_LEFT, gameSnapshot.pullDirection)
        assertEquals("Undo goal by Animal", gameSnapshot.undoDescription)
        assertNotNull(gameSnapshot.ratio)
        assertNotNull(gameSnapshot.countdown)
        assertEquals(expectedCountdown.targetEpoch, gameSnapshot.countdown!!.targetEpochMillis)
        assertNull(gameSnapshot.countdown!!.pausedAtEpochMillis)
        assertFalse(gameSnapshot.countdown!!.cues.isEmpty())
        assertEquals(
            expectedCountdown.upcomingTimingCues(now).map { cue -> cue.targetEpoch },
            gameSnapshot.countdown!!.cues.map { cue -> cue.targetEpochMillis },
        )

        // A paused countdown carries its pause epoch so the watch freezes the same target-derived
        // duration and suppresses upcoming cues without receiving a derived remaining duration.
        val pausedGame = activeGame.copy(
            countdown = expectedCountdown.pause(now),
        )
        val pausedSnapshot = buildWearStateSnapshot(
            game = pausedGame,
            settings = mixedSettings,
            now = now + 5_000L,
        ).activeGame!!.countdown!!
        assertEquals(expectedCountdown.targetEpoch, pausedSnapshot.targetEpochMillis)
        assertEquals(now, pausedSnapshot.pausedAtEpochMillis)
        assertTrue(pausedSnapshot.cues.isEmpty())

        // Setup has no active watch game.
        assertEquals(
            WearSnapshotStatus.NO_ACTIVE_GAME,
            buildWearStateSnapshot(
                standardLiveGameState().copy(phase = GamePhase.SETUP),
                mixedSettings,
                now,
            ).status,
        )

        // Cap transitions disappear after their deadlines.
        val cappedGame = standardLiveGameState(
            rules = GameRules(
                useHalfCap = false,
                useSoftCap = true,
                nominalSoftCapMinutes = 1,
                useHardCap = true,
                nominalHardCapMinutes = 2,
            ),
        ).continueLivePoint()
        assertTrue(
            buildWearStateSnapshot(
                cappedGame,
                mixedSettings,
                cappedGame.capEpoch(CapType.HARD) + 1L,
            ).activeGame!!.upcomingCaps.isEmpty()
        )

        // The shared codec preserves the complete protocol value across the Data Layer payload.
        assertEquals(
            active,
            WearProtocolCodec.decode(
                WearStateSnapshot.serializer(),
                WearProtocolCodec.encode(WearStateSnapshot.serializer(), active),
            ),
        )

        // A published startup update carries the state with an acknowledgement and phone clock
        // reading, independently of the snapshot's sequence number.
        val startupUpdate = WearStateUpdate(
            snapshot = active,
            acknowledgement = WearStartupAcknowledgement("startup"),
        )
        assertEquals(
            startupUpdate,
            WearProtocolCodec.decode(
                WearStateUpdate.serializer(),
                WearProtocolCodec.encode(WearStateUpdate.serializer(), startupUpdate),
            ),
        )
    }

    /**
     * Exercise watch-issued goals, including the ordinary game decisions those goals expose
     * before the phone can progress to its next state.
     */
    @Test
    fun watchGoal() {
        val settings = Settings(
            automaticallyAdvanceNewCountdowns = true,
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        val baseGame = standardLiveGameState().let { game ->
            game.copy(
                rules = game.rules.copy(
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                ),
            ).continueLivePoint()
        }
        appState.updateCurrentGame(baseGame)
        appState.resumeCurrentGame()

        // The goal request preserves its state token and scoring team through the protocol codec.
        val goalTime = timestampAt(baseGame, LocalTime.of(11, 0))
        val goalRequest = WearGoalRequest(
            stateToken = wearStateToken(baseGame),
            scoringTeam = TeamId.TEAM_ONE,
        )
        assertEquals(
            goalRequest,
            WearProtocolCodec.decode(
                WearGoalRequest.serializer(),
                WearProtocolCodec.encode(WearGoalRequest.serializer(), goalRequest),
            ),
        )

        // Sending that request applies the countdown adjustment, records the goal through the
        // shared state coordinator, and changes the state token returned to the watch.
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.GOAL,
                request = WearProtocolCodec.encode(WearGoalRequest.serializer(), goalRequest),
                now = goalTime,
            ).applied
        )
        val scoredGame = appState.currentGame!!
        val adjustedGoalTime = settings.adjustedCountdownStartEpoch(goalTime)
        val expectedScoredGame = baseGame.recordGoalFromCurrentState(
            TeamId.TEAM_ONE,
            adjustedGoalTime,
        )
        assertEquals(1, scoredGame.teamOne.score)
        assertEquals(GamePhase.BETWEEN_POINTS, scoredGame.phase)
        assertEquals(expectedScoredGame.countdown?.targetEpoch, scoredGame.countdown?.targetEpoch)
        assertEquals("Undo Goal by ${baseGame.teamOne.name}", scoredGame.undoEntry?.label)
        assertFalse(wearStateToken(scoredGame) == goalRequest.stateToken)

        // Repeating the old request or committing a phone action calculated from the old game
        // cannot apply a second score or overwrite the accepted watch result.
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.GOAL,
                request = WearProtocolCodec.encode(WearGoalRequest.serializer(), goalRequest),
                now = goalTime,
            ).applied
        )

        // A stale phone-side update also cannot overwrite the accepted watch result.
        assertFalse(appState.updateCurrentGame(baseGame, baseGame.continueLivePoint()))
        assertEquals(scoredGame, appState.currentGame)

        // An in-progress phone card entry also rejects an otherwise current goal request.
        appState.updateCurrentGame(baseGame)
        appState.updateCardEntry(
            baseGame,
            null,
            ActiveCardEntry(TeamId.TEAM_ONE, CardType.YELLOW, ""),
        )
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.GOAL,
                request = WearProtocolCodec.encode(WearGoalRequest.serializer(), goalRequest),
                now = goalTime,
            ).applied
        )
        appState.updateCardEntry(baseGame, appState.state.value.activeCardEntry, null)

        // A goal reaching halftime leaves the phone's ordinary prompt pending. The watch receives
        // the same copy and explicit-response behavior selected by Full guidance.
        val halftimeGame = baseGame.copy(
            rules = baseGame.rules.copy(gameTo = 5),
            teamOne = baseGame.teamOne.copy(score = 2),
        )
        appState.updateCurrentGame(halftimeGame)
        appState.recordGoal(
            currentGame = halftimeGame,
            scoringTeam = TeamId.TEAM_ONE,
            now = goalTime,
        )
        val pendingHalftime = appState.currentGame!!
        assertEquals(GamePhase.BETWEEN_POINTS, pendingHalftime.phase)
        assertFalse(pendingHalftime.halftimeTaken)
        assertTrue(pendingHalftime.pendingGameDecision() is GamePrompt.HalftimeStarted)
        val halftimeSnapshot = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings,
            now = goalTime,
        ).activeGame!!
        assertFalse(halftimeSnapshot.actionsAvailable)
        val halftimeDecision = halftimeSnapshot.pendingDecision!!
        assertEquals("Halftime", halftimeDecision.title)
        assertEquals(listOf("Announce halftime."), halftimeDecision.messageLines.map { it.text })
        assertEquals("Not yet", halftimeDecision.dismissLabel)
        assertEquals("OK", halftimeDecision.confirmLabel)
        assertEquals(WearGuidancePresentation.VISIBLE, halftimeDecision.presentation)
        assertNull(halftimeDecision.autoAcceptDelayMillis)

        // Timed shows the prompt before accepting the optional acknowledgement.
        val timedHalftimeDecision = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.TIMED),
            now = goalTime,
        ).activeGame!!.pendingDecision!!
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, timedHalftimeDecision.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, timedHalftimeDecision.autoAcceptDelayMillis)

        // None immediately accepts the same optional acknowledgement without rendering it.
        val noneHalftimeDecision = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.NONE),
            now = goalTime,
        ).activeGame!!.pendingDecision!!
        assertEquals(WearGuidancePresentation.HIDDEN_AUTO_ACCEPT, noneHalftimeDecision.presentation)
        assertNull(noneHalftimeDecision.autoAcceptDelayMillis)

        // Not yet follows the same defer path as the phone and leaves the scored point in place.
        val deferredHalftimeToken = wearStateToken(pendingHalftime)
        val deferRequest = WearDecisionRequest(
            stateToken = deferredHalftimeToken,
            accept = false,
        )
        assertEquals(
            deferRequest,
            WearProtocolCodec.decode(
                WearDecisionRequest.serializer(),
                WearProtocolCodec.encode(WearDecisionRequest.serializer(), deferRequest),
            ),
        )
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(WearDecisionRequest.serializer(), deferRequest),
                now = goalTime,
            ).applied
        )
        assertNull(appState.currentGame!!.pendingScoreTransition)
        assertFalse(appState.currentGame!!.halftimeTaken)

        // Once deferred, the game has no decision for another current request to resolve.
        val noDecisionGame = appState.currentGame!!
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    WearDecisionRequest(wearStateToken(noDecisionGame), accept = true),
                ),
                now = goalTime,
            ).applied
        )

        // Recording the same halftime-reaching point again allows the watch's OK action to enter
        // halftime through the shared phone transition.
        appState.updateCurrentGame(halftimeGame)
        appState.recordGoal(
            currentGame = halftimeGame,
            scoringTeam = TeamId.TEAM_ONE,
            now = goalTime,
        )
        val acceptHalftime = WearDecisionRequest(
            stateToken = wearStateToken(appState.currentGame!!),
            accept = true,
        )
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    acceptHalftime,
                ),
                now = goalTime,
            ).applied
        )
        assertEquals(GamePhase.HALFTIME, appState.currentGame?.phase)
        assertTrue(appState.currentGame!!.halftimeTaken)
        assertNull(appState.currentGame!!.pendingScoreTransition)

        // The earlier halftime request is stale after the accepted transition.
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    acceptHalftime.copy(stateToken = deferredHalftimeToken),
                ),
                now = goalTime,
            ).applied
        )

        // The watch sends Not yet for halftime, but the phone has not handled the request yet.
        // Establish the watch receiver from the actual published halftime notice.
        appState.updateCurrentGame(pendingHalftime)
        val noticeSnapshot = publications.getValue(appState).last().snapshot
        val receiver = WearSnapshotReceiver(noticeSnapshot)
        val pendingCommand = WearPendingCommand()
        val deferId = pendingCommand.begin(deferredHalftimeToken)

        // Meanwhile, the phone changes Full guidance to Timed. Its publication changes the watch's
        // prompt presentation and advances the sequence, but leaves the game token unchanged.
        // This update neither acknowledges Not yet nor makes it obsolete: keep waiting.
        appState.updateSettings(settings.copy(ruleGuidanceMode = RuleGuidanceMode.TIMED))
        val guidanceUpdate = publications.getValue(appState).last()
        assertEquals(WearGuidancePresentation.VISIBLE,
            noticeSnapshot.activeGame!!.pendingDecision!!.presentation)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED,
            guidanceUpdate.snapshot.activeGame!!.pendingDecision!!.presentation)
        assertTrue(guidanceUpdate.snapshot.sequenceNumber > noticeSnapshot.sequenceNumber)
        assertEquals(deferredHalftimeToken, guidanceUpdate.snapshot.activeGame!!.stateToken)
        assertTrue(receiver.receive(guidanceUpdate.snapshot))
        assertFalse(pendingCommand.complete(guidanceUpdate.acknowledgement))
        assertFalse(pendingCommand.supersede(receiver.current.activeGame!!.stateToken))
        assertEquals(deferId, pendingCommand.requestId)

        // Phone OK then crosses the still-outstanding Not yet. Unlike the settings update, this
        // changes the game token.
        assertTrue(appState.resolveDecision(pendingHalftime, true, goalTime))

        // The watch accepts halftime and stops waiting before the phone acknowledges the Not yet.
        val acceptedHalftime = appState.currentGame!!
        val phoneUpdate = publications.getValue(appState).last()
        assertEquals(GamePhase.HALFTIME, acceptedHalftime.phase)
        assertTrue(receiver.receive(phoneUpdate.snapshot))
        assertTrue(pendingCommand.supersede(phoneUpdate.snapshot.activeGame!!.stateToken))
        assertNull(pendingCommand.requestId)

        // A new watch command can start immediately. Handling the crossed Not yet changes nothing,
        // and its late rejection cannot clear the new command.
        val nextCommandId = pendingCommand.begin(wearStateToken(acceptedHalftime))
        coordinatorFor(appState).handleRequest(
            WearRequestAction.DECISION.path,
            WearCommandRequest(deferId, WearProtocolCodec.encode(
                WearDecisionRequest.serializer(), deferRequest,
            )),
            goalTime,
        )
        val rejectedDefer = publications.getValue(appState).last().acknowledgement as WearCommandAcknowledgement
        assertFalse(rejectedDefer.applied)
        assertEquals(acceptedHalftime, appState.currentGame)
        assertFalse(pendingCommand.complete(rejectedDefer))
        assertEquals(nextCommandId, pendingCommand.requestId)

        // Restore Full guidance for the remaining prompt examples.
        appState.updateSettings(settings)

        // A required water-break notice remains visible briefly in None mode, then its OK action
        // extends the new pull countdown through the same transition used by the phone.
        val waterBreakGame = baseGame.copy(
            rules = baseGame.rules.copy(
                gameTo = 15,
                heatLevel = HeatLevel.LEVEL_1,
            ),
            teamOne = baseGame.teamOne.copy(score = 3),
            halftimeTargetScore = null,
        )
        appState.updateCurrentGame(waterBreakGame)
        appState.recordGoal(
            currentGame = waterBreakGame,
            scoringTeam = TeamId.TEAM_ONE,
            now = goalTime,
        )
        val pendingWaterBreak = appState.currentGame!!
        assertTrue(pendingWaterBreak.pendingWaterBreakOffer)

        // The watch renders the required water-break notice even in None mode.
        val waterBreakDecision = buildWearStateSnapshot(
            game = pendingWaterBreak,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.NONE),
            now = goalTime,
        ).activeGame!!.pendingDecision!!
        assertEquals("Water break", waterBreakDecision.title)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, waterBreakDecision.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, waterBreakDecision.autoAcceptDelayMillis)

        // Accepting the pending notice applies the water break through the phone coordinator.
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    WearDecisionRequest(wearStateToken(pendingWaterBreak), accept = true),
                ),
                now = goalTime,
            ).applied
        )
        val waterBreakResult = appState.currentGame!!
        val expectedWaterBreak = waterBreakGame.recordGoalFromCurrentState(
            TeamId.TEAM_ONE,
            adjustedGoalTime,
        ).applyWaterBreak(goalTime)
        assertFalse(waterBreakResult.pendingWaterBreakOffer)
        assertEquals(EventLogType.WATER_BREAK, waterBreakResult.eventLog.last().type)
        assertEquals(
            expectedWaterBreak.countdown?.targetEpoch,
            waterBreakResult.countdown?.targetEpoch,
        )

        // A due hard cap and its resulting game-over acknowledgement remain two distinct phone
        // decisions; resolving each one exposes the next authoritative snapshot to the watch.
        val hardCapGame = baseGame.copy(
            rules = baseGame.rules.copy(
                gameTo = 15,
                useHardCap = true,
                nominalHardCapMinutes = 0,
            ),
            teamOne = baseGame.teamOne.copy(score = 1),
        )
        appState.updateCurrentGame(hardCapGame)
        val hardCapGoalTime = hardCapGame.capEpoch(CapType.HARD) + 10_000L
        appState.recordGoal(
            currentGame = hardCapGame,
            scoringTeam = TeamId.TEAM_ONE,
            now = hardCapGoalTime,
        )
        val pendingHardCap = appState.currentGame!!
        assertFalse(pendingHardCap.hardCapApplied)
        assertNotNull(pendingHardCap.pendingCapOffer)

        // The watch renders the pending hard-cap decision.
        val hardCapDecision = buildWearStateSnapshot(
            game = pendingHardCap,
            settings = settings,
            now = hardCapGoalTime,
        ).activeGame!!.pendingDecision!!
        assertEquals("Hard cap", hardCapDecision.title)
        assertEquals(
            listOf(
                "Hard cap was at 11:00 AM, so it applies now. " +
                    "Score is not tied, so the game is over.",
            ),
            hardCapDecision.messageLines.map { line -> line.text },
        )
        assertEquals(WearGuidancePresentation.VISIBLE, hardCapDecision.presentation)

        // Accepting the hard cap exposes the resulting game-over decision.
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    WearDecisionRequest(wearStateToken(pendingHardCap), accept = true),
                ),
                now = hardCapGoalTime,
            ).applied
        )
        val pendingGameOver = appState.currentGame!!
        assertTrue(pendingGameOver.hardCapApplied)
        assertNull(pendingGameOver.pendingCapOffer)

        // The watch renders the newly pending game-over acknowledgement.
        val gameOverDecision = buildWearStateSnapshot(
            game = pendingGameOver,
            settings = settings,
            now = hardCapGoalTime,
        ).activeGame!!.pendingDecision!!
        assertEquals("Game over", gameOverDecision.title)
        assertEquals(WearGuidancePresentation.VISIBLE, gameOverDecision.presentation)

        // Accepting that acknowledgement completes the game.
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.DECISION,
                request = WearProtocolCodec.encode(
                    WearDecisionRequest.serializer(),
                    WearDecisionRequest(wearStateToken(pendingGameOver), accept = true),
                ),
                now = hardCapGoalTime,
            ).applied
        )
        val gameOverResult = appState.currentGame!!
        assertTrue(gameOverResult.hardCapApplied)
        assertEquals(GamePhase.GAME_OVER, gameOverResult.phase)
        assertNull(gameOverResult.pendingCapOffer)
        assertNull(gameOverResult.pendingScoreTransition)

        // A completed current game remains visible on the watch with its final score, while
        // countdown and game actions no longer apply.
        val finalSnapshot = buildWearStateSnapshot(
            game = gameOverResult,
            settings = settings,
            now = goalTime,
            actionsAvailable = false,
        )
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, finalSnapshot.status)
        val completedGameSnapshot = finalSnapshot.activeGame!!
        assertTrue(completedGameSnapshot.gameOver)
        assertFalse(completedGameSnapshot.actionsAvailable)
        assertEquals(gameOverResult.teamOne.score, completedGameSnapshot.teamOne.score)
        assertEquals(gameOverResult.teamTwo.score, completedGameSnapshot.teamTwo.score)
        assertNull(completedGameSnapshot.countdown)
        assertEquals(
            listOf("Game over"),
            completedGameSnapshot.statusMessageTransitions.map { transition ->
                transition.message
            },
        )

        // A pending decision and a completed game reject concurrent goal requests even while the
        // phone still displays the live-game screen.
        listOf(pendingHalftime, gameOverResult).forEach { unavailableGame ->
            assertFalse(
                requestResponse(
                    liveScreenWearState(settings, unavailableGame),
                    WearRequestAction.GOAL,
                    WearProtocolCodec.encode(
                        WearGoalRequest.serializer(),
                        goalRequest.copy(stateToken = wearStateToken(unavailableGame)),
                    ),
                    goalTime,
                ).applied
            )
        }

        // The response holds whether the action was applied and the resulting snapshot, which
        // both survive the encoding and decoding round trip when sent to the watch.
        val response = WearGameActionResponse(
            applied = true,
            snapshot = finalSnapshot,
            nextPrompt = null,
        )
        assertEquals(
            response,
            WearProtocolCodec.decode(
                WearGameActionResponse.serializer(),
                WearProtocolCodec.encode(WearGameActionResponse.serializer(), response),
            ),
        )

    }

    /** Exercise watch Undo through the same conditional state replacement used by the phone. */
    @Test
    fun watchUndo() {
        val settings = Settings(
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val appState = activeWearState(settings)
        val baseGame = appState.currentGame!!
        val goalTime = timestampAt(baseGame, LocalTime.of(11, 0))
        appState.recordGoal(baseGame, TeamId.TEAM_ONE, goalTime)
        val scoredGame = appState.currentGame!!
        val undoRequest = WearUndoRequest(wearStateToken(scoredGame))

        // The request preserves the exact game token through protocol serialization.
        assertEquals(
            undoRequest,
            WearProtocolCodec.decode(
                WearUndoRequest.serializer(),
                WearProtocolCodec.encode(WearUndoRequest.serializer(), undoRequest),
            ),
        )

        // Undo restores the preceding game, publishes it to the watch, and retains Redo exactly
        // as the phone's ordinary Undo action does.
        assertTrue(
            requestResponse(
                appState = appState,
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(WearUndoRequest.serializer(), undoRequest),
                now = goalTime,
            ).applied
        )
        assertEquals(scoredGame.undoLastAction(), appState.currentGame)
        assertNotNull(appState.currentGame!!.redoEntry)

        // Repeating the stale request cannot undo an older action or overwrite the current game.
        val restoredGame = appState.currentGame!!
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(WearUndoRequest.serializer(), undoRequest),
                now = goalTime,
            ).applied
        )

        // Card entry makes all ordinary game actions unavailable, including an otherwise current
        // Undo request.
        val cardEntryState = activeWearState(settings, scoredGame)
        cardEntryState.updateCardEntry(
            currentGame = scoredGame,
            expectedCardEntry = null,
            updatedCardEntry = ActiveCardEntry(
                team = TeamId.TEAM_ONE,
                cardType = CardType.YELLOW,
                jerseyNumber = "8",
            ),
        )
        assertFalse(
            requestResponse(
                appState = cardEntryState,
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(WearUndoRequest.serializer(), undoRequest),
                now = goalTime,
            ).applied
        )

        // A pending game decision owns the watch surface and prevents Undo until the observer
        // resolves that decision.
        val pendingDecisionGame = scoredGame.copy(
            pendingScoreTransition = PendingScoreTransition(
                transition = ScoreTransition.HALFTIME,
                effectiveEpoch = goalTime,
            ),
        )
        assertFalse(
            requestResponse(
                appState = activeWearState(settings, pendingDecisionGame),
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(
                    WearUndoRequest.serializer(),
                    WearUndoRequest(wearStateToken(pendingDecisionGame)),
                ),
                now = goalTime,
            ).applied
        )

        // Completed games keep their final state visible but expose no watch actions. Undoing game
        // end remains a phone-summary action.
        val completedGame = scoredGame.endGameNow(goalTime)
        assertFalse(
            requestResponse(
                appState = liveScreenWearState(settings, completedGame),
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(
                    WearUndoRequest.serializer(),
                    WearUndoRequest(wearStateToken(completedGame)),
                ),
                now = goalTime,
            ).applied
        )
        assertEquals(restoredGame, appState.currentGame)

        // A current game without an Undo action also rejects the request.
        val noUndoGame = restoredGame.copy(undoEntry = null, redoEntry = null)
        appState.updateCurrentGame(noUndoGame)
        assertFalse(
            requestResponse(
                appState = appState,
                action = WearRequestAction.UNDO,
                request = WearProtocolCodec.encode(
                    WearUndoRequest.serializer(),
                    WearUndoRequest(wearStateToken(noUndoGame)),
                ),
                now = goalTime,
            ).applied
        )
    }

    /** Start misconduct timing and restart expired pulls from the watch's countdown area. */
    @Test
    fun watchCountdownActions() {
        val settings = Settings(timingAlerts = TimingAlertPreferences(
            watchConnectionMode = WatchConnectionMode.WEAR_OS,
        ))
        val now = 1_000_000L

        // A third live-point blue card offers a start button, then starts the phone's 30 seconds.
        val liveGame = standardLiveGameState().continueLivePoint()
        val misconduct = liveGame.copy(teamOne = liveGame.teamOne.copy(blueCards = 2))
            .assessBlueCard(TeamId.TEAM_ONE, now).state
        val appState = activeWearState(settings, misconduct)
        val before = buildWearStateSnapshot(misconduct, settings, now).activeGame!!
        assertEquals(listOf(WearCountdownAction.START_MISCONDUCT), before.countdownActions)
        assertEquals("Start misconduct\ncountdown", before.countdownActions.single().label)
        assertNull(before.countdown)
        val request = WearCountdownActionRequest(before.stateToken, before.countdownActions.single())
        val encoded = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(), request)
        assertEquals(request, WearProtocolCodec.decode(WearCountdownActionRequest.serializer(), encoded))
        val started = requestResponse(appState, WearRequestAction.COUNTDOWN, encoded, now + 5_000L)
        assertTrue(started.applied)
        assertEquals(misconduct.startMisconductCountdown(now + 5_000L), appState.currentGame)
        assertTrue(started.snapshot.activeGame!!.countdownActions.isEmpty())
        assertEquals(now + 35_000L, started.snapshot.activeGame!!.countdown!!.targetEpochMillis)

        // A delayed repeat cannot restart a countdown that has already begun.
        assertFalse(requestResponse(appState, WearRequestAction.COUNTDOWN, encoded, now + 8_000L).applied)
        assertEquals(now + 35_000L, appState.currentGame!!.countdown!!.targetEpoch)

        // Live misconduct timing supports repeated adjustments, pause/resume, and continuing play.
        val timingGame = appState.currentGame!!
        val controls = started.snapshot.activeGame!!.timingControls!!
        assertEquals(listOf(WearCountdownAction.PAUSE, WearCountdownAction.MINUS_FIVE,
            WearCountdownAction.PLUS_FIVE), controls.adjustments)
        assertEquals(WearCountdownAction.CONTINUE_POINT, controls.pointAction)
        val plusFive = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(timingGame), WearCountdownAction.PLUS_FIVE))
        assertTrue(requestResponse(appState, WearRequestAction.COUNTDOWN, plusFive, now + 8_000L).applied)
        assertEquals(timingGame.addTimeToCountdown(5), appState.currentGame)
        assertFalse(requestResponse(appState, WearRequestAction.COUNTDOWN, plusFive, now + 8_000L).applied)
        val minusFive = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(appState.currentGame!!), WearCountdownAction.MINUS_FIVE))
        assertTrue(requestResponse(appState, WearRequestAction.COUNTDOWN, minusFive, now + 8_000L).applied)
        assertEquals(timingGame.countdown, appState.currentGame!!.countdown)

        // Pause changes the offered icon to Resume and preserves the remaining time on resumption.
        val beforePause = appState.currentGame!!
        val pause = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(beforePause), WearCountdownAction.PAUSE))
        val paused = requestResponse(appState, WearRequestAction.COUNTDOWN, pause, now + 10_000L)
        assertTrue(paused.applied)
        assertEquals(beforePause.toggleCountdownPaused(now + 10_000L), appState.currentGame)
        assertTrue(WearCountdownAction.RESUME in paused.snapshot.activeGame!!.timingControls!!.adjustments)
        assertFalse(WearCountdownAction.PAUSE in paused.snapshot.activeGame!!.timingControls!!.adjustments)
        val resume = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(appState.currentGame!!), WearCountdownAction.RESUME))
        assertTrue(requestResponse(appState, WearRequestAction.COUNTDOWN, resume, now + 15_000L).applied)
        assertEquals(timingGame.countdown!!.targetEpoch + 5_000L, appState.currentGame!!.countdown!!.targetEpoch)

        // Enabling explicit defense timing replaces Continue point with Offense is set.
        val defenseSettings = settings.copy(showDefenseCountdowns = true)
        val offenseState = activeWearState(defenseSettings, appState.currentGame!!)
        val offenseSnapshot = buildWearStateSnapshot(offenseState.currentGame!!, defenseSettings, now + 15_000L)
        assertEquals(WearCountdownAction.OFFENSE_SET, offenseSnapshot.activeGame!!.timingControls!!.pointAction)
        val offenseGame = offenseState.currentGame!!
        val offenseSet = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(offenseGame), WearCountdownAction.OFFENSE_SET))
        val defense = requestResponse(offenseState, WearRequestAction.COUNTDOWN, offenseSet, now + 15_000L)
        assertTrue(defense.applied)
        assertEquals(offenseGame.reportOffenseSet(now + 15_000L), offenseState.currentGame)
        assertEquals(WearCountdownAction.CONTINUE_POINT, defense.snapshot.activeGame!!.timingControls!!.pointAction)
        val continuePoint = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(offenseState.currentGame!!), WearCountdownAction.CONTINUE_POINT))
        val continued = requestResponse(offenseState, WearRequestAction.COUNTDOWN, continuePoint, now + 16_000L)
        assertTrue(continued.applied)
        assertNull(offenseState.currentGame!!.countdown)
        assertNull(continued.snapshot.activeGame!!.timingControls)

        // Water breaks use the same prompt in every guidance mode and require a current token.
        val waterGame = standardLiveGameState(rules = GameRules().withHeatLevel(HeatLevel.MANUAL))
            .restartPullCountdown(now)
        for (guidanceMode in RuleGuidanceMode.entries) {
            val waterSettings = settings.copy(ruleGuidanceMode = guidanceMode)
            val waterState = activeWearState(waterSettings, waterGame)
            val waterSnapshot = buildWearStateSnapshot(waterGame, waterSettings, now).activeGame!!
            assertEquals(listOf(WearCountdownAction.WATER_BREAK, WearCountdownAction.PAUSE,
                WearCountdownAction.MINUS_FIVE, WearCountdownAction.PLUS_FIVE),
                waterSnapshot.timingControls!!.adjustments)
            assertEquals(WearCountdownAction.START_POINT, waterSnapshot.timingControls!!.pointAction)
            val waterRequest = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
                WearCountdownActionRequest(waterSnapshot.stateToken, WearCountdownAction.WATER_BREAK))
            val offered = requestResponse(waterState, WearRequestAction.COUNTDOWN, waterRequest, now)
            val confirmation = offered.nextPrompt as WearActionConfirmation.WaterBreak
            assertEquals(GamePrompt.ManualWaterBreak(waterGame).wearSnapshot(guidanceMode), confirmation.prompt)
            assertEquals(waterGame, waterState.currentGame)
            val confirm = WearProtocolCodec.encode(WearConfirmActionRequest.serializer(),
                WearConfirmActionRequest(confirmation))
            assertTrue(requestResponse(waterState, WearRequestAction.CONFIRM_ACTION, confirm, now).applied)
            assertEquals(waterGame.applyWaterBreak(now), waterState.currentGame)
            assertFalse(requestResponse(waterState, WearRequestAction.CONFIRM_ACTION, confirm, now).applied)
        }

        // An expired pull offers Restart countdown and keeps the usual undo behavior.
        val expired = standardLiveGameState().copy(countdown = null)
        appState.updateCurrentGame(expired)
        val restartDisplay = buildWearStateSnapshot(expired, settings, now).activeGame!!
        assertEquals(listOf(WearCountdownAction.RESTART_PULL, WearCountdownAction.START_POINT),
            restartDisplay.countdownActions)
        assertEquals("Restart countdown", restartDisplay.countdownActions.first().label)
        val restart = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(restartDisplay.stateToken, WearCountdownAction.RESTART_PULL))
        val restarted = requestResponse(appState, WearRequestAction.COUNTDOWN, restart, now)
        assertTrue(restarted.applied)
        assertEquals(expired.restartPullCountdown(now), appState.currentGame)
        assertTrue(restarted.snapshot.activeGame!!.countdownActions.isEmpty())
        assertEquals("Undo Restart countdown", restarted.snapshot.activeGame!!.undoDescription)

        // Once the opening countdown expires, Start point works even while the countdown remains.
        val openingPull = appState.currentGame!!
        val pullExpiredAt = openingPull.countdown!!.targetEpoch
        assertEquals(GamePhase.PRE_GAME, openingPull.phase)
        val openingChoices = buildWearStateSnapshot(openingPull, settings, pullExpiredAt)
            .activeGame!!.countdownActions
        assertEquals(restartDisplay.countdownActions, openingChoices)
        val startOpeningPoint = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(openingPull), WearCountdownAction.START_POINT))
        val openingStarted = requestResponse(appState, WearRequestAction.COUNTDOWN,
            startOpeningPoint, pullExpiredAt)
        assertTrue(openingStarted.applied)
        assertEquals(openingPull.beginLivePoint(pullExpiredAt), appState.currentGame)
        assertTrue(openingStarted.snapshot.activeGame!!.countdownActions.isEmpty())

        // Between points, the observer may start play instead of restarting the countdown.
        val betweenPoints = expired.copy(phase = GamePhase.BETWEEN_POINTS)
        appState.updateCurrentGame(betweenPoints)
        val choices = buildWearStateSnapshot(betweenPoints, settings, now).activeGame!!.countdownActions
        assertEquals(listOf(WearCountdownAction.RESTART_PULL, WearCountdownAction.START_POINT), choices)
        assertEquals("Start point", choices.last().label)
        val startPoint = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(betweenPoints), WearCountdownAction.START_POINT))
        val playing = requestResponse(appState, WearRequestAction.COUNTDOWN, startPoint, now)
        assertTrue(playing.applied)
        assertEquals(betweenPoints.beginLivePoint(now), appState.currentGame)
        assertTrue(playing.snapshot.activeGame!!.countdownActions.isEmpty())
        assertEquals("Undo Start point", playing.snapshot.activeGame!!.undoDescription)
        val restored = appState.currentGame!!.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, restored.phase)
        assertNull(restored.countdown)
        assertEquals(choices, buildWearStateSnapshot(restored, settings, now).activeGame!!.countdownActions)

        // Start point cannot be repeated once play is live.
        val repeatedStart = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(appState.currentGame!!), WearCountdownAction.START_POINT))
        assertFalse(requestResponse(appState, WearRequestAction.COUNTDOWN, repeatedStart, now).applied)

        // A different action cannot be substituted for the one currently shown.
        appState.updateCurrentGame(expired)
        val wrongAction = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(),
            WearCountdownActionRequest(wearStateToken(expired), WearCountdownAction.START_MISCONDUCT))
        assertFalse(requestResponse(appState, WearRequestAction.COUNTDOWN, wrongAction, now).applied)
        assertEquals(expired, appState.currentGame)
    }

    /** Exercise a watch timeout that is requested before it is confirmed and recorded. */
    @Test
    fun watchTimeout() {
        val settings = Settings(
            automaticallyAdvanceNewCountdowns = true,
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        val game = standardLiveGameState().continueLivePoint()
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        val requestTime = timestampAt(game, LocalTime.of(11, 0))
        val requestedAt = settings.adjustedCountdownStartEpoch(requestTime)
        val confirmation = GamePrompt.TimeoutConfirmation(
            state = game,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        )

        // A live-point timeout request dispatches to a phone confirmation.
        val actionResponse = teamActionResponse(appState, WearTeamAction.Timeout, requestTime)
        val protocolConfirmation = actionResponse.nextPrompt as WearActionConfirmation.Timeout
        assertFalse(actionResponse.applied)
        assertEquals(requestedAt, protocolConfirmation.requestedAtPhoneEpochMillis)

        // A between-points timeout request dispatches to the same confirmation type.
        val betweenPointsConfirmation = teamActionResponse(
            activeWearState(settings, standardLiveGameState()),
            WearTeamAction.Timeout,
            requestTime,
        ).nextPrompt as WearActionConfirmation.Timeout
        assertEquals(requestTime, betweenPointsConfirmation.requestedAtPhoneEpochMillis)

        // Requesting the confirmation supplies the same Full guidance as the phone without
        // changing the current game. Cancel therefore needs no phone command or state rollback.
        val prompt = confirmation.wearSnapshot(settings.ruleGuidanceMode)
        assertEquals("Timeout", prompt.title)
        assertEquals(
            listOf(
                "Timeout charged to Viscous Coupling. " +
                    "They have 1 timeout remaining in this half."
            ),
            prompt.messageLines.map { line -> line.text },
        )
        assertEquals("Cancel", prompt.dismissLabel)
        assertEquals("OK", prompt.confirmLabel)
        assertEquals(WearGuidancePresentation.VISIBLE, prompt.presentation)
        assertEquals(prompt, protocolConfirmation.prompt)
        assertEquals(game, appState.currentGame)

        // Brief, Timed, and None select the same presentation and copy policy used by the phone.
        val briefPrompt = confirmation.wearSnapshot(RuleGuidanceMode.BRIEF)
        assertEquals(
            listOf("Timeout charged to Viscous Coupling."),
            briefPrompt.messageLines.map { line -> line.text },
        )
        assertEquals(WearGuidancePresentation.VISIBLE, briefPrompt.presentation)
        val timedPrompt = confirmation.wearSnapshot(RuleGuidanceMode.TIMED)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, timedPrompt.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, timedPrompt.autoAcceptDelayMillis)
        val nonePrompt = confirmation.wearSnapshot(RuleGuidanceMode.NONE)
        assertEquals(WearGuidancePresentation.HIDDEN_AUTO_ACCEPT, nonePrompt.presentation)
        assertNull(nonePrompt.autoAcceptDelayMillis)

        // An invalid timeout remains visible briefly in None mode, matching the phone's required
        // warning rather than silently accepting an action that cannot be charged.
        val outOfTimeoutsGame = game.copy(
            teamOne = game.teamOne.copy(timeoutsUsedThisHalf = 2),
        )
        val invalidPrompt = (
            teamActionResponse(
                activeWearState(
                    settings.copy(ruleGuidanceMode = RuleGuidanceMode.NONE),
                    outOfTimeoutsGame,
                ),
                WearTeamAction.Timeout,
                requestTime,
            ).nextPrompt as WearActionConfirmation.Timeout
        ).prompt
        assertEquals("Invalid timeout", invalidPrompt.title)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, invalidPrompt.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, invalidPrompt.autoAcceptDelayMillis)

        // OK commits the timeout through AppState
        val confirmationRequest = WearConfirmActionRequest(protocolConfirmation)
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
                requestTime,
            ).applied
        )
        val timeoutGame = appState.currentGame!!
        assertEquals(game.assessTimeout(TeamId.TEAM_ONE, requestedAt).state, timeoutGame)
        assertEquals(1, timeoutGame.teamOne.timeoutsUsedThisHalf)
        assertEquals(CountdownKind.TIME_OUT, timeoutGame.countdown?.kind)
        assertEquals("Undo Timeout by Viscous Coupling", timeoutGame.undoEntry?.label)

        // Reusing the stale confirmation cannot overwrite the accepted result.
        assertFalse(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
                requestTime,
            ).applied
        )
        assertEquals(timeoutGame, appState.currentGame)

        // Action and confirmation payloads preserve the exact state token, team, phone timestamp,
        // prompt, and authoritative snapshot across their protocol round trips.
        val stateToken = wearStateToken(game)
        val actionRequest = WearTeamActionRequest(
            stateToken = stateToken,
            team = TeamId.TEAM_ONE,
            action = WearTeamAction.Timeout,
        )
        assertEquals(
            actionRequest,
            WearProtocolCodec.decode(
                WearTeamActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearTeamActionRequest.serializer(),
                    actionRequest,
                ),
            ),
        )
        assertEquals(
            actionResponse,
            WearProtocolCodec.decode(
                WearGameActionResponse.serializer(),
                WearProtocolCodec.encode(
                    WearGameActionResponse.serializer(),
                    actionResponse,
                ),
            ),
        )
        assertEquals(
            confirmationRequest,
            WearProtocolCodec.decode(
                WearConfirmActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
            ),
        )
    }

    /**
     * Exercise watch time- and pull-violation actions that the watch can request and confirm
     * directly.
     */
    @Test
    fun watchTimeAndPullViolations() {
        val settings = Settings(
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val now = timestampAt(standardLiveGameState(), LocalTime.of(11, 0))
        val requestedAt = now - 10_000L

        // A time violation uses its action-request time for the same domain action as the phone.
        var game = standardLiveGameState()
        var confirmation: GamePrompt.ActionConfirmation = GamePrompt.TimeViolationConfirmation(
            state = game,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        )
        var prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        )
        assertEquals(confirmation.event.formatTitle(), prompt.prompt.title)
        assertEquals(
            requestedAt,
            (prompt as WearActionConfirmation.TimeViolation).requestedAtPhoneEpochMillis,
        )

        // The coordinator dispatches that same time-violation action.
        var appState = activeWearState(settings, game)
        assertTrue(
            teamActionResponse(appState, WearTeamAction.TimeViolation, now).nextPrompt
                is WearActionConfirmation.TimeViolation
        )

        // Confirming the returned action records the violation at its original request time.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(prompt),
                ),
                now,
            ).applied
        )
        assertEquals(
            game.assessTimeViolation(TeamId.TEAM_ONE, requestedAt).state,
            appState.currentGame,
        )

        // Mixed pulling-team violations carry both phone-formatted choices. Selecting Majority
        // pull commits that exact alternative rather than the initially displayed Offsides.
        game = standardLiveGameState().copy(division = GameDivision.MIXED)
        val pullingTeam = game.pullingTeam
        confirmation = GamePrompt.PullViolationConfirmation(
            state = game,
            team = pullingTeam,
            requestedAt = requestedAt,
            violation = PullViolationType.OFFSIDES,
        )
        prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        )
        assertEquals(
            requestedAt,
            (prompt as WearActionConfirmation.PullViolation).requestedAtPhoneEpochMillis,
        )
        assertEquals(
            setOf(PullViolationType.OFFSIDES, PullViolationType.MAJORITY_PULL),
            prompt.options.map { option -> option.violation }.toSet(),
        )

        // Selecting Majority pull produces the confirmation that the watch sends back.
        confirmation = GamePrompt.PullViolationConfirmation(
            state = game,
            team = pullingTeam,
            requestedAt = requestedAt,
            violation = PullViolationType.MAJORITY_PULL,
        )
        appState = activeWearState(settings, game)

        // The coordinator dispatches the pull-violation request with the available choices.
        assertTrue(
            teamActionResponse(
                appState,
                WearTeamAction.PullViolation,
                now,
                pullingTeam,
            ).nextPrompt is WearActionConfirmation.PullViolation
        )

        // Confirming the selected Majority pull records that alternative rather than Offsides.
        val majorityPullPrompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        )
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(majorityPullPrompt),
                ),
                now,
            ).applied
        )
        assertEquals(
            game.assessPullViolation(
                team = pullingTeam,
                now = requestedAt,
                violation = PullViolationType.MAJORITY_PULL,
            ).state,
            appState.currentGame,
        )

        // False start has no mixed-division alternative, so it sends no selection options.
        val falseStartPrompt = GamePrompt.PullViolationConfirmation(
            state = game,
            team = pullingTeam.flip(),
            requestedAt = requestedAt,
            violation = PullViolationType.FALSE_START,
        ).wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        ) as WearActionConfirmation.PullViolation
        assertTrue(falseStartPrompt.options.isEmpty())

        // Action and confirmation requests preserve their concrete actions, choices, and prompts
        // on the wire.
        val actionRequest = WearTeamActionRequest(
            stateToken = wearStateToken(game),
            team = if (pullingTeam == TeamId.TEAM_ONE) {
                TeamId.TEAM_ONE
            } else {
                TeamId.TEAM_TWO
            },
            action = WearTeamAction.PullViolation,
        )
        assertEquals(
            actionRequest,
            WearProtocolCodec.decode(
                WearTeamActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearTeamActionRequest.serializer(),
                    actionRequest,
                ),
            ),
        )
        listOf(prompt).forEach { protocolConfirmation ->
            val request = WearConfirmActionRequest(protocolConfirmation)
            assertEquals(
                request,
                WearProtocolCodec.decode(
                    WearConfirmActionRequest.serializer(),
                    WearProtocolCodec.encode(WearConfirmActionRequest.serializer(), request),
                ),
            )
        }
    }

    /** Exercise the watch blue-card request and confirmation. */
    @Test
    fun watchBlueCard() {
        val settings = Settings(
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val requestedAt = standardLiveGameState().startEpoch + 10_000L

        // An ordinary blue card remains uncommitted until its confirmation is accepted.
        var game = standardLiveGameState()
        var confirmation = GamePrompt.BlueCardConfirmation(
            state = game,
            team = TeamId.TEAM_TWO,
            requestedAt = requestedAt,
        )
        var prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        ) as WearActionConfirmation.BlueCard
        assertEquals(confirmation.event.formatTitle(), prompt.prompt.title)
        assertEquals(requestedAt, prompt.requestedAtPhoneEpochMillis)
        var appState = activeWearState(settings, game)
        assertEquals(game, appState.currentGame)

        // The coordinator dispatches the blue-card request without changing the game.
        assertTrue(
            teamActionResponse(
                appState,
                WearTeamAction.BlueCard,
                requestedAt,
                TeamId.TEAM_TWO,
            ).nextPrompt is WearActionConfirmation.BlueCard
        )

        // Confirming the returned action records the blue card.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(prompt),
                ),
                requestedAt,
            ).applied
        )
        assertEquals(
            game.assessBlueCard(TeamId.TEAM_TWO, requestedAt).state,
            appState.currentGame,
        )

        // A third live-point card carries both misconduct consequences in Full guidance.
        game = standardLiveGameState().continueLivePoint()
        game = game.copy(
            teamOne = game.teamOne.copy(blueCards = 2),
        )
        confirmation = GamePrompt.BlueCardConfirmation(
            state = game,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        )
        prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        ) as WearActionConfirmation.BlueCard
        assertTrue(
            prompt.prompt.messageLines.any { line -> line.text == "If against offense:" }
        )
        assertTrue(
            prompt.prompt.messageLines.any { line -> line.text == "If against defense:" }
        )

        // Confirming the third card records it and starts the misconduct countdown.
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        appState.confirmAction(confirmation)
        assertEquals(3, appState.currentGame!!.teamOne.blueCards)
        assertTrue(appState.currentGame!!.pendingMisconductCountdown)

        // The blue-card action and its confirmation survive their protocol encoding round trips.
        val actionRequest = WearTeamActionRequest(
            stateToken = wearStateToken(game),
            team = TeamId.TEAM_ONE,
            action = WearTeamAction.BlueCard,
        )
        assertEquals(
            actionRequest,
            WearProtocolCodec.decode(
                WearTeamActionRequest.serializer(),
                WearProtocolCodec.encode(WearTeamActionRequest.serializer(), actionRequest),
            ),
        )
        val confirmationRequest = WearConfirmActionRequest(prompt)
        assertEquals(
            confirmationRequest,
            WearProtocolCodec.decode(
                WearConfirmActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
            ),
        )
    }

    /** Exercise the various player-card paths that start on the watch. */
    @Test
    fun watchPlayerCards() {
        val settings = Settings(
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val requestedAt = standardLiveGameState().startEpoch + 10_000L

        // A numbered Yellow card follows the ordinary card confirmation and records only after
        // that confirmation is accepted.
        var game = standardLiveGameState()
        var appState = activeWearState(settings, game)
        var prompt = teamActionResponse(
            appState,
            WearTeamAction.PlayerCard(CardType.YELLOW, "8"),
            requestedAt,
        ).nextPrompt as WearActionConfirmation.PlayerCard
        assertEquals(CardType.YELLOW, prompt.cardType)
        assertEquals(PlayerIdentity("8"), prompt.identity)
        assertEquals("Back", prompt.prompt.dismissLabel)
        assertTrue(
            prompt.prompt.messageLines.any { line ->
                line.text.contains("Yellow card on player 8.")
            }
        )

        // Confirming the returned prompt records the Yellow card.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(prompt),
                ),
                requestedAt,
            ).applied
        )
        assertEquals(
            CardType.YELLOW,
            appState.currentGame!!.teamOnePlayers.single().cards.single().cardType,
        )

        // Red uses the same number-only path and differs only in the card type being recorded.
        game = standardLiveGameState()
        prompt = game.wearPlayerCardPrompt(
            stateToken = wearStateToken(game),
            team = TeamId.TEAM_TWO,
            cardType = CardType.RED,
            jerseyNumber = "17",
            requestedAt = requestedAt,
            guidanceMode = settings.ruleGuidanceMode,
        ) as WearActionConfirmation.PlayerCard
        assertEquals(CardType.RED, prompt.cardType)
        assertEquals(PlayerIdentity("17"), prompt.identity)

        // Confirming the Red prompt records the card for Team Two.
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        val confirmation = GamePrompt.PlayerCardConfirmation(
            state = game,
            team = TeamId.TEAM_TWO,
            cardType = CardType.RED,
            identity = PlayerIdentity("17"),
            reason = CardReason(),
            requestedAt = requestedAt,
        )
        appState.confirmAction(confirmation)
        assertEquals(
            CardType.RED,
            appState.currentGame!!.teamTwoPlayers.single().cards.single().cardType,
        )

        // If one number identifies multiple known players, the phone supplies a handoff prompt.
        // Cancel returns to the still-populated watch entry; Continue on phone carries that number
        // into the full card workflow.
        game = standardLiveGameState().copy(
            teamOnePlayers = listOf(
                PlayerRecord(jerseyNumber = "8", playerName = "Alice"),
                PlayerRecord(jerseyNumber = "8", playerName = "Bob"),
            ),
        )
        appState = activeWearState(settings, game)
        val handoff = teamActionResponse(
            appState,
            WearTeamAction.PlayerCard(CardType.YELLOW, "8"),
            requestedAt,
        ).nextPrompt as WearActionConfirmation.CardEntryHandoff
        assertEquals("Multiple players", handoff.prompt.title)
        assertEquals(
            listOf("Player number 8 corresponds to multiple players already recorded."),
            handoff.prompt.messageLines.map { line -> line.text },
        )
        assertEquals("Continue on phone", handoff.prompt.confirmLabel)
        assertEquals("Cancel", handoff.prompt.dismissLabel)
        assertEquals("8", handoff.jerseyNumber)

        // Continuing on the phone starts card entry with the ambiguous number preserved.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(handoff),
                ),
                requestedAt,
            ).applied
        )
        assertEquals(
            ActiveCardEntry(
                team = TeamId.TEAM_ONE,
                cardType = CardType.YELLOW,
                jerseyNumber = "8",
            ),
            appState.state.value.activeCardEntry,
        )

        // A number identifying an already suspended player is rejected on the watch. Dismissing
        // the notice returns to the number entry without starting a phone card workflow.
        game = standardLiveGameState().copy(
            teamOnePlayers = listOf(playerRecordWithCards("8", yellows = 2)),
        )
        appState = activeWearState(settings, game)
        val notice = teamActionResponse(
            appState,
            WearTeamAction.PlayerCard(CardType.YELLOW, "8"),
            requestedAt,
        ).nextPrompt as WearTeamActionPrompt.Notice
        assertEquals("Invalid card assignment", notice.prompt.title)
        assertEquals(
            listOf("Viscous Coupling #8 already has two yellow cards and has been suspended."),
            notice.prompt.messageLines.map { line -> line.text },
        )
        assertEquals("OK", notice.prompt.dismissLabel)

        // Empty and nonnumeric player numbers do not produce a card prompt.
        assertNull(
            teamActionResponse(
                activeWearState(settings),
                WearTeamAction.PlayerCard(CardType.YELLOW, ""),
                requestedAt,
            ).nextPrompt
        )
        assertNull(
            teamActionResponse(
                activeWearState(settings),
                WearTeamAction.PlayerCard(CardType.YELLOW, "8A"),
                requestedAt,
            ).nextPrompt
        )


        // Selecting Yellow and continuing on the phone starts its player-entry workflow without
        // changing the game.
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        game = standardLiveGameState()
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        val entry = ActiveCardEntry(
            team = TeamId.TEAM_ONE,
            cardType = CardType.YELLOW,
            jerseyNumber = "",
        )
        val entryRequest = WearCardEntryRequest(
            stateToken = wearStateToken(game),
            team = entry.team,
            cardType = CardType.YELLOW,
            jerseyNumber = entry.jerseyNumber,
        )
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CARD_ENTRY,
                WearProtocolCodec.encode(WearCardEntryRequest.serializer(), entryRequest),
                requestedAt,
            ).applied
        )
        assertEquals(game, appState.currentGame)
        assertEquals(entry, appState.state.value.activeCardEntry)

        // A second card-entry request cannot replace the active phone workflow.
        assertFalse(
            requestResponse(
                appState,
                WearRequestAction.CARD_ENTRY,
                WearProtocolCodec.encode(
                    WearCardEntryRequest.serializer(),
                    WearCardEntryRequest(
                        stateToken = wearStateToken(game),
                        team = TeamId.TEAM_TWO,
                        cardType = CardType.RED,
                        jerseyNumber = "17",
                    ),
                ),
                requestedAt,
            ).applied
        )
        val pendingState = buildWearStateSnapshot(
            game = game,
            settings = settings,
            now = 123_000L,
            activeCardEntry = entry,
        )
        val pendingSnapshot = pendingState.activeGame!!
        assertFalse(pendingSnapshot.actionsAvailable)
        assertNull(pendingSnapshot.pendingDecision)
        val cardEntrySnapshot = pendingSnapshot.phoneCardEntry!!
        assertEquals(TeamId.TEAM_ONE, cardEntrySnapshot.team)
        assertEquals(CardType.YELLOW, cardEntrySnapshot.cardType)
        assertEquals("", cardEntrySnapshot.jerseyNumber)
        assertEquals(
            pendingState,
            WearProtocolCodec.decode(
                WearStateSnapshot.serializer(),
                WearProtocolCodec.encode(WearStateSnapshot.serializer(), pendingState),
            ),
        )

        // If the phone has changed from yellow to red or vice versa, then canceling from the
        // watch is rejected. The delayed cancellation from the watch is obsolete.
        // Here we actually test the reverse -- that the watch thinks the initiated card entry
        // was with a red card, but the phone is currently working on yellow.
        // The point is that the color is different and thus rejected.
        val cancelRequest = WearCancelCardEntryRequest(
            stateToken = entryRequest.stateToken,
            team = entryRequest.team,
            cardType = entryRequest.cardType,
            jerseyNumber = entryRequest.jerseyNumber,
        )
        assertFalse(
            requestResponse(
                appState,
                WearRequestAction.CANCEL_CARD_ENTRY,
                WearProtocolCodec.encode(
                    WearCancelCardEntryRequest.serializer(),
                    cancelRequest.copy(cardType = CardType.RED),
                ),
                requestedAt,
            ).applied
        )

        // Cancelling the active Yellow entry clears the workflow and restores watch actions.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CANCEL_CARD_ENTRY,
                WearProtocolCodec.encode(
                    WearCancelCardEntryRequest.serializer(),
                    cancelRequest,
                ),
                requestedAt,
            ).applied
        )
        assertNull(appState.state.value.activeCardEntry)

        // Clearing the entry restores all ordinary watch actions.
        val restoredSnapshot = buildWearStateSnapshot(
            game = game,
            settings = settings,
            now = 123_000L,
        ).activeGame!!
        assertTrue(restoredSnapshot.actionsAvailable)
        assertNull(restoredSnapshot.phoneCardEntry)

        // Continuing on the phone and completing Yellow entry records the player details, commits
        // the card, and closes the workflow in the same state change.
        val nextEntry = ActiveCardEntry(
            team = TeamId.TEAM_TWO,
            cardType = CardType.YELLOW,
            jerseyNumber = "",
        )
        appState.updateCardEntry(game, null, nextEntry)

        // Completing that entry records its player identity and reason, then closes the workflow.
        val identity = PlayerIdentity("8", "Alex")
        val reason = CardReason(details = "Late contact")
        val completedGame = game.assessYellowCard(
            team = nextEntry.team,
            identity = identity,
            now = 124_000L,
            reason = reason,
        ).state
        appState.completeCardEntry(game, completedGame, nextEntry)
        assertNull(appState.state.value.activeCardEntry)
        assertEquals(completedGame, appState.currentGame)
        val recordedPlayer = completedGame.teamTwoPlayers.single()
        assertEquals(identity, recordedPlayer.identity())
        assertEquals(reason, recordedPlayer.cards.single().reason)

        // Starting and cancelling card entry preserve the exact game, team, selected color, and
        // entered number through their protocol encoding round trips.
        assertEquals(
            entryRequest,
            WearProtocolCodec.decode(
                WearCardEntryRequest.serializer(),
                WearProtocolCodec.encode(WearCardEntryRequest.serializer(), entryRequest),
            ),
        )
        assertEquals(
            cancelRequest,
            WearProtocolCodec.decode(
                WearCancelCardEntryRequest.serializer(),
                WearProtocolCodec.encode(
                    WearCancelCardEntryRequest.serializer(),
                    cancelRequest,
                ),
            ),
        )

        // A stale request cannot start a card workflow.
        assertFalse(
            requestResponse(
                activeWearState(settings),
                WearRequestAction.CARD_ENTRY,
                WearProtocolCodec.encode(
                    WearCardEntryRequest.serializer(),
                    entryRequest.copy(stateToken = "stale"),
                ),
                requestedAt,
            ).applied
        )

        // Pending-decision and completed games cannot start a card workflow.
        val pendingGame = standardLiveGameState().copy(
            pendingScoreTransition = PendingScoreTransition(ScoreTransition.HALFTIME, requestedAt),
        )
        val completedRequestGame = standardLiveGameState().copy(phase = GamePhase.GAME_OVER)
        listOf(pendingGame, completedRequestGame).forEach { unavailableGame ->
            val unavailableRequest = entryRequest.copy(stateToken = wearStateToken(unavailableGame))
            assertFalse(
                requestResponse(
                    liveScreenWearState(settings, unavailableGame),
                    WearRequestAction.CARD_ENTRY,
                    WearProtocolCodec.encode(
                        WearCardEntryRequest.serializer(),
                        unavailableRequest,
                    ),
                    requestedAt,
                ).applied
            )
        }

        // A stale request cannot cancel a card workflow.
        assertFalse(
            requestResponse(
                activeWearState(settings),
                WearRequestAction.CANCEL_CARD_ENTRY,
                WearProtocolCodec.encode(
                    WearCancelCardEntryRequest.serializer(),
                    cancelRequest.copy(stateToken = "stale"),
                ),
                requestedAt,
            ).applied
        )

        // Conditional phone updates reject stale game or workflow state.
        appState = activeWearState(settings)
        game = appState.currentGame!!
        val currentEntry = ActiveCardEntry(TeamId.TEAM_ONE, CardType.YELLOW, "8")
        appState.updateCardEntry(game, null, currentEntry)

        // Stale game state and a different active workflow cannot complete or clear that entry.
        val staleGame = game.copy(teamOne = game.teamOne.copy(score = game.teamOne.score + 1))
        assertFalse(appState.updateCardEntry(staleGame, currentEntry, null))
        assertFalse(appState.completeCardEntry(staleGame, game, currentEntry))
        assertFalse(
            appState.completeCardEntry(
                game,
                game,
                currentEntry.copy(cardType = CardType.RED),
            )
        )

        // Numbered actions, prompts, notices, and phone-workflow requests all preserve their
        // contents through the protocol encoding round trip.
        val actionRequest = WearTeamActionRequest(
            stateToken = wearStateToken(game),
            team = TeamId.TEAM_ONE,
            action = WearTeamAction.PlayerCard(CardType.YELLOW, "8"),
        )
        assertEquals(
            actionRequest,
            WearProtocolCodec.decode(
                WearTeamActionRequest.serializer(),
                WearProtocolCodec.encode(WearTeamActionRequest.serializer(), actionRequest),
            ),
        )
        val confirmationRequest = WearConfirmActionRequest(handoff)
        assertEquals(
            confirmationRequest,
            WearProtocolCodec.decode(
                WearConfirmActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
            ),
        )
        val noticeResponse = WearGameActionResponse(
            applied = false,
            snapshot = buildWearStateSnapshot(game, settings, requestedAt),
            nextPrompt = notice,
        )
        assertEquals(
            noticeResponse,
            WearProtocolCodec.decode(
                WearGameActionResponse.serializer(),
                WearProtocolCodec.encode(WearGameActionResponse.serializer(), noticeResponse),
            ),
        )
    }

    /**
     * Exercise the watch technical foul action.
     */
    @Test
    fun watchTechnicalFoul() {
        val settings = Settings(
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val requestedAt = standardLiveGameState().startEpoch + 10_000L

        // An ordinary technical foul remains uncommitted until its confirmation is accepted.
        var game = standardLiveGameState()
        var confirmation = GamePrompt.TechnicalFoulConfirmation(
            state = game,
            team = TeamId.TEAM_TWO,
            requestedAt = requestedAt,
        )
        var prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        ) as WearActionConfirmation.TechnicalFoul
        assertEquals(requestedAt, prompt.requestedAtPhoneEpochMillis)
        var appState = activeWearState(settings, game)

        // The coordinator dispatches the technical-foul request without changing the game.
        assertTrue(
            teamActionResponse(
                appState,
                WearTeamAction.TechnicalFoul,
                requestedAt,
                TeamId.TEAM_TWO,
            ).nextPrompt is WearActionConfirmation.TechnicalFoul
        )

        // Confirming the returned action records the technical foul.
        assertTrue(
            requestResponse(
                appState,
                WearRequestAction.CONFIRM_ACTION,
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    WearConfirmActionRequest(prompt),
                ),
                requestedAt,
            ).applied
        )
        assertEquals(
            game.assessTechnicalFoul(TeamId.TEAM_TWO, requestedAt).state,
            appState.currentGame,
        )

        // A third live-point foul carries both consequences in its Full-guidance confirmation.
        game = standardLiveGameState().continueLivePoint()
        game = game.copy(
            teamOne = game.teamOne.copy(technicalFouls = 2),
        )
        confirmation = GamePrompt.TechnicalFoulConfirmation(
            state = game,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        )
        prompt = confirmation.wearConfirmation(
            wearStateToken(game),
            settings.ruleGuidanceMode,
        ) as WearActionConfirmation.TechnicalFoul
        assertTrue(
            prompt.prompt.messageLines.any { line -> line.text == "If against offense:" }
        )
        assertTrue(
            prompt.prompt.messageLines.any { line -> line.text == "If against defense:" }
        )

        // Confirming the third foul records it and starts the misconduct countdown.
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        appState.confirmAction(confirmation)
        assertEquals(3, appState.currentGame!!.teamOne.technicalFouls)
        assertTrue(appState.currentGame!!.pendingMisconductCountdown)

        // The technical-foul action and its resulting confirmation survive their protocol
        // encoding round trips.
        val actionRequest = WearTeamActionRequest(
            stateToken = wearStateToken(game),
            team = TeamId.TEAM_ONE,
            action = WearTeamAction.TechnicalFoul,
        )
        assertEquals(
            actionRequest,
            WearProtocolCodec.decode(
                WearTeamActionRequest.serializer(),
                WearProtocolCodec.encode(WearTeamActionRequest.serializer(), actionRequest),
            ),
        )
        val confirmationRequest = WearConfirmActionRequest(prompt)
        assertEquals(
            confirmationRequest,
            WearProtocolCodec.decode(
                WearConfirmActionRequest.serializer(),
                WearProtocolCodec.encode(
                    WearConfirmActionRequest.serializer(),
                    confirmationRequest,
                ),
            ),
        )
    }

    /** Exercise startup request routing and phone-clock calibration. */
    @Test
    fun watchStartupRequests() {
        val settings = Settings(
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val appState = activeWearState(settings)
        val now = timestampAt(appState.currentGame!!, LocalTime.of(11, 0))

        // Unknown request paths are ignored without publishing state.
        coordinatorFor(appState).handleRequest(
            requestedActionPath = "/unknown",
            request = WearCommandRequest("unknown", byteArrayOf()),
            now = now,
        )
        assertTrue(publications[appState].isNullOrEmpty())

        // Enabled startup publishes state and its matching acknowledgement through the standard path.
        val startupReply = coordinatorFor(appState).startup("first-startup", now)
        assertTrue(startupReply.enabled)
        assertEquals(now, startupReply.phoneEpochMillis)
        val startup = publications.getValue(appState).single()
        assertEquals(WearStartupAcknowledgement("first-startup"), startup.acknowledgement)
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, startup.snapshot.status)

        // Retain startup confirmation in subsequent updates, so a later phone action cannot erase it
        // before the watch sees it. A newer snapshot can establish the connection just as well.
        appState.goHome()
        val later = publications.getValue(appState).last()
        assertEquals(startup.acknowledgement, later.acknowledgement)
        assertTrue(later.snapshot.sequenceNumber > startup.snapshot.sequenceNumber)

        // A fresh retry must publish even if the snapshot is unchanged. Only the retained startup
        // request ID changes; the snapshot keeps the same sequence number.
        assertTrue(coordinatorFor(appState).startup("retry", now).enabled)
        val retry = publications.getValue(appState).last()
        assertEquals(later.snapshot, retry.snapshot)
        assertEquals("retry", retry.acknowledgement!!.requestId)

        // After a phone process restart, opening the phone app announces its new session even
        // before the watch sends a request. Resuming the game and scoring then publish normally.
        val restartedState = activeWearState(settings)
        restartedState.goHome()
        val restartedCoordinator = coordinatorFor(restartedState)
        restartedCoordinator.publishCurrentState()
        val announced = publications.getValue(restartedState).single()
        assertNull(announced.acknowledgement)
        assertFalse(announced.snapshot.activeGame!!.actionsAvailable)
        assertTrue(announced.snapshot.sessionId != startup.snapshot.sessionId)
        restartedState.resumeCurrentGame()
        val resumed = publications.getValue(restartedState).last()
        assertTrue(resumed.snapshot.activeGame!!.actionsAvailable)
        assertTrue(resumed.snapshot.sequenceNumber > announced.snapshot.sequenceNumber)
        restartedState.recordGoal(restartedState.currentGame!!, TeamId.TEAM_ONE, now)
        assertEquals(1, publications.getValue(restartedState).last().snapshot.activeGame!!.teamOne.score)

        // Foreground refreshes retain a completed handshake's acknowledgement and snapshot order.
        restartedCoordinator.startup("reconnected", now)
        val reconnected = publications.getValue(restartedState).last()
        restartedCoordinator.publishCurrentState()
        assertEquals(reconnected, publications.getValue(restartedState).last())

        // Enabled but idle phones also publish snapshots; absence of a game is not disconnection.
        val idleState = AppState(NoOpAppStateStorage)
        idleState.updateSettings(settings)
        assertTrue(coordinatorFor(idleState).startup("idle", now).enabled)
        assertEquals(WearSnapshotStatus.NO_ACTIVE_GAME,
            publications.getValue(idleState).single().snapshot.status)

        // Disabled support takes the abbreviated path, without publishing or consuming a sequence
        // number. Enabling it afterward produces the first tagged snapshot of that session.
        val disabledState = AppState(NoOpAppStateStorage)
        val disabledCoordinator = coordinatorFor(disabledState)
        assertFalse(disabledCoordinator.startup("disabled", now).enabled)
        disabledCoordinator.publishCurrentState()
        assertTrue(publications[disabledState].isNullOrEmpty())
        disabledState.updateSettings(settings)
        assertEquals(1L, publications.getValue(disabledState).single().snapshot.sequenceNumber)
    }

    /**
     * Verify that one published update carries both ordered state and the retained watch-command
     * result, so receiving a later phone-initiated update can also complete a pending command.
     */
    @Test
    fun commandAcknowledgementsAndPhoneUpdates() {
        // A watch goal publishes its updated score and acknowledgement together, exactly once.
        val settings = Settings(timingAlerts = TimingAlertPreferences(
            watchConnectionMode = WatchConnectionMode.WEAR_OS,
        ))
        val appState = activeWearState(settings)
        val updates = mutableListOf<WearStateUpdate>()
        publications[appState] = updates
        val game = appState.currentGame!!
        val now = timestampAt(game, LocalTime.of(11, 0))
        coordinators[appState] = WearPhoneCoordinator(
            appState, publish = { updates.add(it) }, clock = { now },
        )
        coordinators.getValue(appState).startup("startup", now)
        assertEquals(WearStartupAcknowledgement("startup"), updates.single().acknowledgement)
        val snapshotReceiver = WearSnapshotReceiver(updates.single().snapshot)
        updates.clear()
        val pending = WearPendingCommand()
        val requestId = pending.begin(wearStateToken(game))
        coordinators.getValue(appState).handleRequest(
            WearRequestAction.GOAL.path,
            WearCommandRequest(requestId, WearProtocolCodec.encode(
                WearGoalRequest.serializer(),
                WearGoalRequest(wearStateToken(game), TeamId.TEAM_ONE),
            )),
            now,
        )
        val goalUpdate = updates.single()
        assertTrue((goalUpdate.acknowledgement as WearCommandAcknowledgement).applied)
        assertEquals(requestId, goalUpdate.acknowledgement!!.requestId)
        assertEquals(1, goalUpdate.snapshot.activeGame!!.teamOne.score)

        // The phone initiates an undo action after recording the watch-initiated goal.
        // If for whatever reason (temporary disconnection, communication failure, synchronization
        // issue with the update delivery, watch not listening for a bit, etc.) the watch doesn't
        // see the first update that the goal was recorded, the later update still has the
        // acknowledgement the watch needs to confirm the goal action was completed.
        appState.updateCurrentGame(appState.currentGame!!.undoLastAction())
        val phoneUpdate = updates.last()
        assertEquals(2, updates.size)
        assertEquals(goalUpdate.acknowledgement, phoneUpdate.acknowledgement)
        assertEquals(goalUpdate.snapshot.sessionId, phoneUpdate.snapshot.sessionId)
        assertTrue(phoneUpdate.snapshot.sequenceNumber > goalUpdate.snapshot.sequenceNumber)
        assertEquals(0, phoneUpdate.snapshot.activeGame!!.teamOne.score)
        assertTrue(snapshotReceiver.receive(phoneUpdate.snapshot))
        assertTrue(pending.complete(phoneUpdate.acknowledgement))
        assertNull(pending.requestId)
        assertFalse((phoneUpdate.acknowledgement as WearCommandAcknowledgement).matchesSnapshot(snapshotReceiver.current))

        // A watch Undo using the old goal state is rejected. The snapshot and its sequence number
        // stay unchanged, but the new acknowledgement is still published to complete this request.
        val rejected = requestResponse(
            appState, WearRequestAction.UNDO,
            WearProtocolCodec.encode(WearUndoRequest.serializer(),
                WearUndoRequest(goalUpdate.snapshot.activeGame!!.stateToken)),
            now,
        )
        assertFalse(rejected.applied)
        assertEquals(phoneUpdate.snapshot, rejected.snapshot)
        assertEquals(3, updates.size)
        assertFalse((updates.last().acknowledgement as WearCommandAcknowledgement).applied)

        // Phone Redo restores the goal's game payload, but not its old sequence number:
        // the intervening Undo makes this a new position in the sequence.
        appState.updateCurrentGame(appState.currentGame!!.redoLastAction())
        val restoredGoal = updates.last().snapshot
        assertEquals(goalUpdate.snapshot.activeGame, restoredGoal.activeGame)
        assertTrue(restoredGoal.sequenceNumber > phoneUpdate.snapshot.sequenceNumber)

        // A recovery startup replaces the command acknowledgement in that same field. Subsequent
        // phone changes retain startup until another watch request supplies its replacement.
        coordinators.getValue(appState).startup("recovery", now)
        assertEquals(WearStartupAcknowledgement("recovery"), updates.last().acknowledgement)
        appState.updateCurrentGame(appState.currentGame!!.undoLastAction())
        assertEquals(WearStartupAcknowledgement("recovery"), updates.last().acknowledgement)
    }

    /** Exercise coordinator rejection paths shared across watch request types. */
    @Test
    fun watchRequestRejections() {
        val settings = Settings(
            ruleGuidanceMode = RuleGuidanceMode.FULL,
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val now = timestampAt(standardLiveGameState(), LocalTime.of(11, 0))
        val pendingGame = standardLiveGameState().copy(
            pendingScoreTransition = PendingScoreTransition(ScoreTransition.HALFTIME, now),
        )
        val completedGame = standardLiveGameState().copy(phase = GamePhase.GAME_OVER)

        // A pending decision blocks an otherwise valid team action.
        assertNull(
            teamActionResponse(
                activeWearState(settings, pendingGame),
                WearTeamAction.BlueCard,
                now,
            ).nextPrompt
        )

        // A completed game blocks an otherwise valid team action.
        assertNull(
            teamActionResponse(
                liveScreenWearState(settings, completedGame),
                WearTeamAction.BlueCard,
                now,
            ).nextPrompt
        )

        // Leaving the live-game screen blocks an otherwise valid team action.
        var appState = activeWearState(settings)
        appState.goHome()
        assertNull(teamActionResponse(appState, WearTeamAction.BlueCard, now).nextPrompt)

        // An active phone card entry blocks an otherwise valid team action.
        appState = activeWearState(settings)
        val game = appState.currentGame!!
        appState.updateCardEntry(
            game,
            null,
            ActiveCardEntry(TeamId.TEAM_ONE, CardType.YELLOW, ""),
        )
        assertNull(teamActionResponse(appState, WearTeamAction.BlueCard, now).nextPrompt)

        // The same unavailable states reject confirmations rebuilt against current phone state.
        val bluePrompt = GamePrompt.BlueCardConfirmation(
            standardLiveGameState(),
            TeamId.TEAM_ONE,
            now,
        ).wearConfirmation(
            wearStateToken(standardLiveGameState()),
            RuleGuidanceMode.FULL,
        ) as WearActionConfirmation.BlueCard
        listOf(
            liveScreenWearState(settings, pendingGame),
            liveScreenWearState(settings, completedGame),
            appState,
        ).forEach { unavailableState ->
            val current = unavailableState.currentGame!!
            val confirmation = bluePrompt.copy(stateToken = wearStateToken(current))
            assertFalse(
                requestResponse(
                    unavailableState,
                    WearRequestAction.CONFIRM_ACTION,
                    WearProtocolCodec.encode(
                        WearConfirmActionRequest.serializer(),
                        WearConfirmActionRequest(confirmation),
                    ),
                    now,
                ).applied
            )
        }

        // A current token cannot confirm a timeout that is unavailable during halftime.
        val halftimeGame = standardLiveGameState().copy(phase = GamePhase.HALFTIME)
        val halftimeToken = wearStateToken(halftimeGame)
        val timeoutPrompt = GamePrompt.TimeoutConfirmation(
            standardLiveGameState(),
            TeamId.TEAM_ONE,
            now,
        ).wearSnapshot(RuleGuidanceMode.FULL)
        assertFalse(
            confirmationResponse(
                WearActionConfirmation.Timeout(
                    halftimeToken,
                    TeamId.TEAM_ONE,
                    now,
                    timeoutPrompt,
                ),
                halftimeGame,
                settings,
                now,
            ).applied
        )

        // A current token cannot confirm a time violation that is unavailable during halftime.
        assertFalse(
            confirmationResponse(
                WearActionConfirmation.TimeViolation(
                    halftimeToken,
                    TeamId.TEAM_ONE,
                    now,
                    timeoutPrompt,
                ),
                halftimeGame,
                settings,
                now,
            ).applied
        )

        // A current token cannot confirm a false start against the pulling team.
        val liveGame = standardLiveGameState().continueLivePoint()
        assertFalse(
            confirmationResponse(
                WearActionConfirmation.PullViolation(
                    stateToken = wearStateToken(liveGame),
                    team = liveGame.pullingTeam,
                    requestedAtPhoneEpochMillis = now,
                    selectedViolation = PullViolationType.FALSE_START,
                    options = emptyList(),
                    prompt = timeoutPrompt,
                ),
                liveGame,
                settings,
                now,
            ).applied
        )

        // Halftime also prevents a new timeout request from producing a prompt.
        assertNull(
            teamActionResponse(
                liveScreenWearState(settings, halftimeGame),
                WearTeamAction.Timeout,
                now,
            ).nextPrompt
        )

        // Halftime prevents a new time-violation request from producing a prompt.
        assertNull(
            teamActionResponse(
                liveScreenWearState(settings, halftimeGame),
                WearTeamAction.TimeViolation,
                now,
            ).nextPrompt
        )

        // Halftime prevents a new pull-violation request from producing a prompt.
        assertNull(
            teamActionResponse(
                liveScreenWearState(settings, halftimeGame),
                WearTeamAction.PullViolation,
                now,
            ).nextPrompt
        )

        // Missing phone state still returns an authoritative rejection snapshot.
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        assertFalse(
            requestResponse(
                appState,
                WearRequestAction.GOAL,
                WearProtocolCodec.encode(
                    WearGoalRequest.serializer(),
                    WearGoalRequest("missing", TeamId.TEAM_ONE),
                ),
                now,
            ).applied
        )

        // Disabled watch communication also returns an authoritative rejection snapshot.
        appState = activeWearState(settings)
        appState.updateSettings(Settings())
        assertFalse(
            requestResponse(
                appState,
                WearRequestAction.GOAL,
                WearProtocolCodec.encode(
                    WearGoalRequest.serializer(),
                    WearGoalRequest(wearStateToken(appState.currentGame!!), TeamId.TEAM_ONE),
                ),
                now,
            ).applied
        )
    }

    /** Reject startup accidentally routed through the game-command path instead of startup(). */
    @Test
    fun startupMisroutedAsGameCommand() {
        // The Android service separates startup from game commands before reaching this path.
        // An internal caller that violates that boundary must fail explicitly without publishing.
        val appState = AppState(NoOpAppStateStorage)
        val previous = appState.state.value
        val coordinator = coordinatorFor(appState)
        val error = assertThrows(IllegalStateException::class.java) {
            coordinator.handleRequest(
                requestedActionPath = WearRequestAction.STARTUP.path,
                request = WearCommandRequest("misrouted-startup", byteArrayOf()),
                now = 0L,
            )
        }
        assertEquals("Startup is handled separately from game commands", error.message)
        assertEquals(previous, appState.state.value)
        assertTrue(publications[appState].isNullOrEmpty())
    }
}

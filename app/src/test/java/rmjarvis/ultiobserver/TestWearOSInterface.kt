package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearConfirmActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearTeamId
import rmjarvis.ultiobserver.wearprotocol.WearPullViolationType

/// Tests for the phone-side Wear OS interface.
class TestWearOSInterface : GameDomainTestFixtures() {
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
            actionsAvailable = true,
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
            actionsAvailable = true,
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
            actionsAvailable = true,
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
            actionsAvailable = true,
        ).activeGame!!
        assertFalse(pending.actionsAvailable)

        // A live point without an interruption countdown omits countdown state entirely.
        val livePointSnapshot = buildWearStateSnapshot(
            game = standardLiveGameState().continueLivePoint(),
            settings = settings,
            now = now,
            actionsAvailable = true,
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
            actionsAvailable = true,
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
            actionsAvailable = true,
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
            actionsAvailable = true,
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
            actionsAvailable = true,
        ).activeGame!!.countdown!!
        assertEquals(expectedCountdown.targetEpoch, pausedSnapshot.targetEpochMillis)
        assertEquals(now, pausedSnapshot.pausedAtEpochMillis)
        assertTrue(pausedSnapshot.cues.isEmpty())

        // The shared codec preserves the complete protocol value across the Data Layer payload.
        assertEquals(
            active,
            WearProtocolCodec.decode(
                WearStateSnapshot.serializer(),
                WearProtocolCodec.encode(WearStateSnapshot.serializer(), active),
            ),
        )

        // A live startup response carries that same authoritative state together with the phone
        // clock reading used to calibrate the watch.
        val startupResponse = WearStartupResponse(
            phoneEpochMillis = now,
            snapshot = active,
        )
        assertEquals(
            startupResponse,
            WearProtocolCodec.decode(
                WearStartupResponse.serializer(),
                WearProtocolCodec.encode(WearStartupResponse.serializer(), startupResponse),
            ),
        )
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
        val requestedAt = settings.adjustedCountdownStartEpoch(
            timestampAt(game, LocalTime.of(11, 0))
        )
        val confirmation = GamePrompt.TimeoutConfirmation(
            state = game,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        )

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
        val invalidPrompt = GamePrompt.TimeoutConfirmation(
            state = outOfTimeoutsGame,
            team = TeamId.TEAM_ONE,
            requestedAt = requestedAt,
        ).wearSnapshot(RuleGuidanceMode.NONE)
        assertEquals("Invalid timeout", invalidPrompt.title)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, invalidPrompt.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, invalidPrompt.autoAcceptDelayMillis)

        // OK commits the timeout through AppState, and reusing the stale confirmation cannot
        // overwrite that accepted result.
        assertTrue(appState.confirmAction(confirmation))
        val timeoutGame = appState.currentGame!!
        assertEquals(game.assessTimeout(TeamId.TEAM_ONE, requestedAt).state, timeoutGame)
        assertEquals(1, timeoutGame.teamOne.timeoutsUsedThisHalf)
        assertEquals(CountdownKind.TIME_OUT, timeoutGame.countdown?.kind)
        assertEquals("Undo Timeout by Viscous Coupling", timeoutGame.undoEntry?.label)
        assertFalse(appState.confirmAction(confirmation))
        assertEquals(timeoutGame, appState.currentGame)

        // Action and confirmation payloads preserve the exact state token, team, phone timestamp,
        // prompt, and authoritative snapshot across their protocol round trips.
        val stateToken = wearStateToken(game)
        val actionRequest = WearTeamActionRequest(
            stateToken = stateToken,
            team = WearTeamId.TEAM_ONE,
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
        val protocolConfirmation = WearActionConfirmation.Timeout(
            stateToken = stateToken,
            team = WearTeamId.TEAM_ONE,
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = prompt,
        )
        val actionResponse = WearGameActionResponse(
            applied = false,
            confirmation = protocolConfirmation,
            snapshot = buildWearStateSnapshot(
                game = game,
                settings = settings,
                now = requestedAt,
                actionsAvailable = true,
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
        val confirmationRequest = WearConfirmActionRequest(protocolConfirmation)
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
     * Exercise the self-contained team actions that the watch can request and confirm without
     * moving the observer to the phone.
     */
    @Test
    fun watchTeamActions() {
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
        assertEquals(confirmation.event.formatPopupTitle(), prompt.prompt.title)
        assertEquals(requestedAt, prompt.requestedAtPhoneEpochMillis)
        var appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        assertTrue(appState.confirmAction(confirmation))
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
        assertEquals(requestedAt, prompt.requestedAtPhoneEpochMillis)
        assertEquals(
            setOf(WearPullViolationType.OFFSIDES, WearPullViolationType.MAJORITY_PULL),
            (prompt as WearActionConfirmation.PullViolation)
                .options.map { option -> option.violation }.toSet(),
        )

        confirmation = GamePrompt.PullViolationConfirmation(
            state = game,
            team = pullingTeam,
            requestedAt = requestedAt,
            violation = PullViolationType.MAJORITY_PULL,
        )
        appState = AppState(NoOpAppStateStorage)
        appState.updateSettings(settings)
        appState.updateCurrentGame(game)
        appState.resumeCurrentGame()
        assertTrue(appState.confirmAction(confirmation))
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
                WearTeamId.TEAM_ONE
            } else {
                WearTeamId.TEAM_TWO
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

    /**
     * Exercise watch-issued goals as authoritative phone actions, including the ordinary game
     * decisions those goals expose before the phone can progress to its next state.
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

        // An ordinary goal uses the configured countdown adjustment, persists the score through
        // the shared state coordinator, and changes the state token returned to the watch.
        val goalTime = timestampAt(baseGame, LocalTime.of(11, 0))
        val goalRequest = WearGoalRequest(
            stateToken = wearStateToken(baseGame),
            scoringTeam = WearTeamId.TEAM_ONE,
        )
        assertEquals(
            goalRequest,
            WearProtocolCodec.decode(
                WearGoalRequest.serializer(),
                WearProtocolCodec.encode(WearGoalRequest.serializer(), goalRequest),
            ),
        )
        assertTrue(
            appState.recordGoal(
                currentGame = baseGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = goalTime,
            )
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
            appState.recordGoal(
                currentGame = baseGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = goalTime,
            )
        )
        assertFalse(appState.updateCurrentGame(baseGame, baseGame.continueLivePoint()))
        assertEquals(scoredGame, appState.currentGame)

        // A goal reaching halftime leaves the phone's ordinary prompt pending. The watch receives
        // the same copy and explicit-response behavior selected by Full guidance.
        val halftimeGame = baseGame.copy(
            rules = baseGame.rules.copy(gameTo = 5),
            teamOne = baseGame.teamOne.copy(score = 2),
        )
        appState.updateCurrentGame(halftimeGame)
        assertTrue(
            appState.recordGoal(
                currentGame = halftimeGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = goalTime,
            )
        )
        val pendingHalftime = appState.currentGame!!
        assertEquals(GamePhase.BETWEEN_POINTS, pendingHalftime.phase)
        assertFalse(pendingHalftime.halftimeTaken)
        assertTrue(pendingHalftime.pendingGameDecision() is GamePrompt.HalftimeStarted)
        val halftimeSnapshot = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings,
            now = goalTime,
            actionsAvailable = true,
        ).activeGame!!
        assertFalse(halftimeSnapshot.actionsAvailable)
        val halftimeDecision = halftimeSnapshot.pendingDecision!!
        assertEquals("Halftime", halftimeDecision.title)
        assertEquals(listOf("Announce halftime."), halftimeDecision.messageLines.map { it.text })
        assertEquals("Not yet", halftimeDecision.dismissLabel)
        assertEquals("OK", halftimeDecision.confirmLabel)
        assertEquals(WearGuidancePresentation.VISIBLE, halftimeDecision.presentation)
        assertNull(halftimeDecision.autoAcceptDelayMillis)

        // Timed and None use the same phone policy: Timed shows the prompt before accepting it,
        // while None immediately accepts this optional acknowledgement without rendering it.
        val timedHalftimeDecision = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.TIMED),
            now = goalTime,
            actionsAvailable = true,
        ).activeGame!!.pendingDecision!!
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, timedHalftimeDecision.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, timedHalftimeDecision.autoAcceptDelayMillis)
        val noneHalftimeDecision = buildWearStateSnapshot(
            game = pendingHalftime,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.NONE),
            now = goalTime,
            actionsAvailable = true,
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
            appState.resolveDecision(
                currentGame = pendingHalftime,
                accept = deferRequest.accept,
                now = goalTime,
            )
        )
        assertNull(appState.currentGame!!.pendingScoreTransition)
        assertFalse(appState.currentGame!!.halftimeTaken)

        // Recording the same halftime-reaching point again allows the watch's OK action to enter
        // halftime through the shared phone transition.
        appState.updateCurrentGame(halftimeGame)
        assertTrue(
            appState.recordGoal(
                currentGame = halftimeGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = goalTime,
            )
        )
        assertTrue(
            appState.resolveDecision(
                currentGame = appState.currentGame!!,
                accept = true,
                now = goalTime,
            )
        )
        assertEquals(GamePhase.HALFTIME, appState.currentGame?.phase)
        assertTrue(appState.currentGame!!.halftimeTaken)
        assertNull(appState.currentGame!!.pendingScoreTransition)
        assertFalse(
            appState.resolveDecision(
                currentGame = pendingHalftime,
                accept = true,
                now = goalTime,
            )
        )

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
        assertTrue(
            appState.recordGoal(
                currentGame = waterBreakGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = goalTime,
            )
        )
        val pendingWaterBreak = appState.currentGame!!
        assertTrue(pendingWaterBreak.pendingWaterBreakOffer)
        val waterBreakDecision = buildWearStateSnapshot(
            game = pendingWaterBreak,
            settings = settings.copy(ruleGuidanceMode = RuleGuidanceMode.NONE),
            now = goalTime,
            actionsAvailable = true,
        ).activeGame!!.pendingDecision!!
        assertEquals("Water break", waterBreakDecision.title)
        assertEquals(WearGuidancePresentation.VISIBLE_TIMED, waterBreakDecision.presentation)
        assertEquals(ruleGuidanceTimeoutMillis, waterBreakDecision.autoAcceptDelayMillis)
        assertTrue(
            appState.resolveDecision(
                currentGame = pendingWaterBreak,
                accept = true,
                now = goalTime,
            )
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
        assertTrue(
            appState.recordGoal(
                currentGame = hardCapGame,
                scoringTeam = TeamId.TEAM_ONE,
                now = hardCapGoalTime,
            )
        )
        val pendingHardCap = appState.currentGame!!
        assertFalse(pendingHardCap.hardCapApplied)
        assertNotNull(pendingHardCap.pendingCapOffer)
        assertTrue(
            appState.resolveDecision(
                currentGame = pendingHardCap,
                accept = true,
                now = hardCapGoalTime,
            )
        )
        val pendingGameOver = appState.currentGame!!
        assertTrue(pendingGameOver.hardCapApplied)
        assertNull(pendingGameOver.pendingCapOffer)
        val gameOverDecision = buildWearStateSnapshot(
            game = pendingGameOver,
            settings = settings,
            now = hardCapGoalTime,
            actionsAvailable = true,
        ).activeGame!!.pendingDecision!!
        assertEquals("Game over", gameOverDecision.title)
        assertEquals(WearGuidancePresentation.VISIBLE, gameOverDecision.presentation)
        assertTrue(
            appState.resolveDecision(
                currentGame = pendingGameOver,
                accept = true,
                now = hardCapGoalTime,
            )
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

        // The response holds whether the action was applied and the resulting snapshot, which
        // both survive the encoding and decoding round trip when sent to the watch.
        val response = WearGameActionResponse(
            applied = true,
            snapshot = finalSnapshot,
            confirmation = null,
        )
        assertEquals(
            response,
            WearProtocolCodec.decode(
                WearGameActionResponse.serializer(),
                WearProtocolCodec.encode(WearGameActionResponse.serializer(), response),
            ),
        )

    }
}

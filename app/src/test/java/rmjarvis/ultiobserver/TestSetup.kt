package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for setup-state conversion and applying setup edits to live games.
class TestSetup : GameDomainTestFixtures() {
    /**
     * Test live-game creation and setup-state conversion from setup form data.
     */
    @Test
    fun setupRoundTrip() {
        val VC = TeamId.TEAM_ONE
        val teamOnePlayers = teamOnePriorPlayers()
        val teamTwoPlayers = teamTwoPriorPlayers()

        // A new setup draft starts with the current standard game rules.
        val newSetupState = newGameSetupState(LocalDateTime.of(2026, 1, 1, 9, 0))
        assertEquals(GameRules(), newSetupState.rules)

        // A setup-created live game has empty player lists when no prior card holders are entered.
        val noPriorCardsState = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(8, 30))
        )
        assertTrue(noPriorCardsState.teamOnePlayers.isEmpty())
        assertTrue(noPriorCardsState.teamTwoPlayers.isEmpty())
        assertFalse(noPriorCardsState.teamOne.hasCoachOrCaptainInfo())
        assertFalse(noPriorCardsState.teamTwo.hasCoachOrCaptainInfo())

        // Full setup data round-trips through a live game.
        val setup = fullSetup()
        val state = createLiveGameState(setup)
        assertEquals(setup, state.toSetupState())
        assertEquals("Potlatch", state.tournamentName)
        assertEquals(GameDivision.MIXED, state.division)
        assertEquals("Grandmasters", state.level)
        assertEquals("Pool play", state.gameContext)
        assertEquals("Mike and Gary", state.observers)
        assertEquals("Road", state.nearEndName)
        assertEquals("Trees", state.farEndName)
        assertEquals(PullPromptTarget.FAR, state.pullPromptTarget)
        assertEquals(TeamColorChoice.CUSTOM, state.teamOne.color)
        assertEquals(0xFF123456L, state.teamOne.customColorArgb)
        assertNull(state.teamTwo.customColorArgb)
        assertEquals("Coach VC", state.teamOne.coaches)
        assertEquals("Animal field captains", state.teamTwo.fieldCaptains)
        assertTrue(state.teamOne.hasCoachOrCaptainInfo())
        assertTrue(state.teamTwo.hasCoachOrCaptainInfo())
        assertEquals(VC, state.openingPullingTeam)
        assertEquals(FieldEnd.FAR, state.openingPullingFromEnd)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(teamOnePlayers, state.teamOnePlayers)
        assertEquals(teamTwoPlayers, state.teamTwoPlayers)

        // The opening pull countdown starts from the game start epoch.
        assertEquals(GamePhase.PRE_GAME, state.phase)
        assertEquals(CountdownKind.OPENING_PULL, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 40_000L, state.countdown?.targetEpoch)

        // Returning from Update game setup before play starts is a no-op when nothing changed.
        val unchangedState = applySetupToLiveGame(state, state.toSetupState(), 10_000L)
        assertEquals(state, unchangedState)
        assertNull(unchangedState.undoEntry)

        // Returning from Update game setup after play starts is also a no-op with unchanged setup.
        val liveState = state.beginLivePoint()
        assertEquals(GamePhase.LIVE_POINT, liveState.phase)
        val unchangedLiveState = applySetupToLiveGame(
            liveState,
            liveState.toSetupState(),
            20_000L,
        )
        assertEquals(liveState, unchangedLiveState)
    }

    /**
     * Test setup start date/time calculations derived from the current local time.
     */
    @Test
    fun setupStartDateAndTime() {
        // Times already on a half-hour boundary advance to the following half hour.
        assertEquals(LocalTime.of(9, 0), nextHalfHourFrom(LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(10, 0), nextHalfHourFrom(LocalTime.of(9, 30)))

        // Times after a boundary round up to the next half-hour mark.
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 0, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 29)))

        // Late-night times wrap to midnight.
        assertEquals(LocalTime.MIDNIGHT, nextHalfHourFrom(LocalTime.of(23, 45)))

        // New setup start times round to the next half hour on the same date.
        val sameDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 0))
        assertEquals(LocalDate.of(2026, 1, 1), sameDaySetup.startDate)
        assertEquals(LocalTime.of(23, 0), sameDaySetup.startTime)

        // Rounding past midnight advances the setup date.
        val nextDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 45))
        assertEquals(LocalDate.of(2026, 1, 2), nextDaySetup.startDate)
        assertEquals(LocalTime.MIDNIGHT, nextDaySetup.startTime)

        // Setup time zones let different local start times refer to the same real instant.
        val utcSetup = standardGameSetup(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(18, 0),
            timeZone = ZoneId.of("UTC"),
        )
        val pacificSetup = standardGameSetup(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(10, 0),
            timeZone = ZoneId.of("America/Los_Angeles"),
        )
        val utcState = createLiveGameState(utcSetup)
        val pacificState = createLiveGameState(pacificSetup)
        assertEquals(utcSetup.timeZone, utcState.timeZone)
        assertEquals(pacificSetup.timeZone, pacificState.timeZone)
        assertEquals(
            LocalDateTime.of(2026, 1, 1, 18, 0)
                .atZone(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
            utcState.startEpoch,
        )
        assertEquals(utcState.startEpoch, pacificState.startEpoch)
    }

    /**
     * Test team-color labels, values, and custom-color guards.
     */
    @Test
    fun teamColorGuards() {
        // Built-in team colors expose label and color values.
        assertEquals("Pink", TeamColorChoice.PINK.label)
        assertEquals(0xFFFF4FA3, TeamColorChoice.PINK.accentArgb)
        assertEquals(0xFF2F1022, TeamColorChoice.PINK.contentArgb)

        // Custom team colors require an explicit ARGB value.
        assertThrows(IllegalArgumentException::class.java) {
            TeamColorChoice.CUSTOM.accent
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamColorChoice.CUSTOM.content
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamSetup("Custom", TeamColorChoice.CUSTOM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamLiveState("Custom", TeamColorChoice.CUSTOM)
        }
    }

    /**
     * Test setup display text for field ends, pull prompts, and mixed-division choices.
     */
    @Test
    fun setupDisplayText() {
        val setup = standardGameSetup(startTime = LocalTime.of(10, 0)).copy(
            division = GameDivision.MIXED,
            nearEndName = "Road",
            farEndName = "Trees",
            pullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )
        val state = createLiveGameState(setup)

        // Configured field-end names flow into setup summaries and live-state display helpers.
        assertEquals("Road", setup.fieldEndName(FieldEnd.NEAR))
        assertEquals("Trees", setup.fieldEndName(FieldEnd.FAR))
        assertEquals("Viscous Coupling pulls from Road", setup.startingPullSummary())
        assertEquals("Pull prompts for both ends", setup.pullPromptSummary())
        assertTrue(setup.usesMixedDivision())
        assertEquals("Road", state.fieldEndDisplayName(FieldEnd.NEAR))
        assertEquals("Trees", state.fieldEndDisplayName(FieldEnd.FAR))

        // Blank field-end names fall back to default labels while prompt targets use configured
        // names.
        val defaultEndsSetup = setup.copy(
            nearEndName = "",
            farEndName = "",
            pullPromptTarget = PullPromptTarget.NEAR,
        )
        val defaultEndsState = state.copy(nearEndName = "", farEndName = "")
        assertEquals("Near end", defaultEndsSetup.fieldEndName(FieldEnd.NEAR))
        assertEquals("Far end", defaultEndsSetup.fieldEndName(FieldEnd.FAR))
        assertEquals("Near end", defaultEndsState.fieldEndDisplayName(FieldEnd.NEAR))
        assertEquals("Far end", defaultEndsState.fieldEndDisplayName(FieldEnd.FAR))
        assertEquals("Pull prompts for Near end", defaultEndsSetup.pullPromptSummary())

        // Pull-prompt choices use configured field-end names when they refer to one end.
        assertEquals("Road", PullPromptTarget.NEAR.displayText(setup))
        assertEquals("Trees", PullPromptTarget.FAR.displayText(setup))
        assertEquals("both ends", PullPromptTarget.BOTH.displayText(setup))
        assertEquals("neither end", PullPromptTarget.NEITHER.displayText(setup))
    }

    /**
     * Test applying setup edits before the first point starts.
     */
    @Test
    fun setupEditsBeforeOpeningPull() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start from the canonical full setup, but do not start the first point yet.
        val setup = fullSetup()
        val initialState = createLiveGameState(setup)
        val setupEdit1 = editSetup1(setup)

        // Applying setup edits before play starts resyncs the current pull and field state.
        val state = applySetupToLiveGame(initialState, setupEdit1, 10_000L)
        assertEquals(LocalTime.of(8, 45), state.startTime)
        assertEquals("Spring Reign", state.tournamentName)
        assertEquals(GameDivision.OPEN, state.division)
        assertEquals("Youth", state.level)
        assertEquals("Quarterfinal", state.gameContext)
        assertEquals("Alex and Blake", state.observers)
        assertEquals("Parking", state.nearEndName)
        assertEquals("Scoreboard", state.farEndName)
        assertEquals(PullPromptTarget.BOTH, state.pullPromptTarget)
        assertEquals(15, state.rules.gameTo)
        assertEquals(2, state.rules.timeoutsPerHalf)
        assertEquals("VC", state.teamOne.name)
        assertEquals(TeamColorChoice.WHITE, state.teamOne.color)
        assertEquals(0xFF123456L, state.teamOne.customColorArgb)
        assertEquals("Coach edits", state.teamOne.coaches)
        assertEquals("Field captain edits", state.teamOne.fieldCaptains)
        assertEquals("Spirit captain edits", state.teamOne.spiritCaptains)
        assertEquals("Animal Ultimate", state.teamTwo.name)
        assertEquals(TeamColorChoice.RED, state.teamTwo.color)
        assertEquals("Other coach edits", state.teamTwo.coaches)
        assertEquals("Other field captain edits", state.teamTwo.fieldCaptains)
        assertEquals("Other spirit captain edits", state.teamTwo.spiritCaptains)
        assertEquals(setupEdit1.teamOnePlayers, state.teamOnePlayers)
        assertEquals(setupEdit1.teamTwoPlayers, state.teamTwoPlayers)
        assertEquals(ANIMAL, state.openingPullingTeam)
        assertEquals(FieldEnd.NEAR, state.openingPullingFromEnd)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)

        // The updated opening-pull state is undo-backed.
        assertEquals("Pull sequence started.", state.lastEvent)
        assertEquals(CountdownKind.OPENING_PULL, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(50_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Update game setup", state.undoEntry?.label)
        assertEquals(initialState, state.undoEntry?.previous)
        assertTrue(state.eventLog.isEmpty())
        assertTrue(state.isInitialLivePreview())

        // One consequence of being in the initial live preview state is that pressing Back
        // returns to setup rather than Home. Show this by inserting this state into a ViewModel
        // that otherwise came from starting a normal game.
        val previewModel = AppViewModel(NoOpAppStateStorage)
        previewModel.startNewGame()
        previewModel.updateSetup(setup)
        previewModel.finishSetup()
        previewModel.updateLiveGame(state)
        assertEquals(AppScreen.LIVE, previewModel.screen)
        previewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, previewModel.screen)
        assertTrue(previewModel.hasSetupDraft)
        assertNull(previewModel.liveState)

        // A blue card before the first point starts is a real game event, so Back returns Home
        // rather than setup even though the game is still in the pre-game phase.
        val blueCardState = state.assessBlueCard(
            ANIMAL,
            state.countdown!!.targetEpoch - 1_000L,
        ).state
        assertEquals(GamePhase.PRE_GAME, blueCardState.phase)
        assertFalse(blueCardState.isInitialLivePreview())

        val blueCardModel = AppViewModel(NoOpAppStateStorage)
        blueCardModel.startNewGame()
        blueCardModel.updateSetup(setup)
        blueCardModel.finishSetup()
        blueCardModel.updateLiveGame(blueCardState)
        assertEquals(AppScreen.LIVE, blueCardModel.screen)
        blueCardModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, blueCardModel.screen)
        assertFalse(blueCardModel.hasSetupDraft)
        assertEquals(blueCardState, blueCardModel.liveState)

        // Until the opening pull starts, setup edits to pull orientation should still retarget the
        // first pull even after a real pre-game event.
        val blueCardPullingTeamBeforeEdit = blueCardState.pullingTeam
        val blueCardPullingFromEndBeforeEdit = blueCardState.pullingFromEnd
        assertEquals(blueCardPullingTeamBeforeEdit, blueCardState.openingPullingTeam)
        assertEquals(blueCardPullingFromEndBeforeEdit, blueCardState.openingPullingFromEnd)

        val blueCardSetupEdit = blueCardState.toSetupState().copy(
            pullingTeam = blueCardPullingTeamBeforeEdit.flip(),
            pullingFromEnd = blueCardPullingFromEndBeforeEdit.flip(),
        )
        val blueCardSetupUpdate = applySetupToLiveGame(
            blueCardState,
            blueCardSetupEdit,
            20_000L,
        )
        assertEquals(GamePhase.PRE_GAME, blueCardSetupUpdate.phase)
        assertFalse(blueCardSetupUpdate.isInitialLivePreview())
        assertEquals(blueCardPullingTeamBeforeEdit.flip(), blueCardSetupUpdate.openingPullingTeam)
        assertEquals(
            blueCardPullingFromEndBeforeEdit.flip(),
            blueCardSetupUpdate.openingPullingFromEnd,
        )
        assertEquals(blueCardPullingTeamBeforeEdit.flip(), blueCardSetupUpdate.pullingTeam)
        assertEquals(blueCardPullingFromEndBeforeEdit.flip(), blueCardSetupUpdate.pullingFromEnd)
        assertEquals(CountdownKind.OPENING_PULL, blueCardSetupUpdate.countdown?.kind)
    }

    /**
     * Test applying setup edits after play has begun.
     */
    @Test
    fun setupEditsAfterPlayStarts() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start play from an edited setup, start the game, then edit the setup again.
        val setup = fullSetup()
        val setupEdit1 = editSetup1(setup)
        val fieldStateAfterGoal = stateAfterGoal(setup, setupEdit1)
        val setupEdit2 = editSetup2(fieldStateAfterGoal.toSetupState())
        val state = applySetupToLiveGame(fieldStateAfterGoal, setupEdit2, 200_000L)

        // After play starts, setup metadata changes but current pull and field state are preserved.
        assertEquals(LocalTime.of(9, 0), state.startTime)
        assertEquals("College Nationals", state.tournamentName)
        assertEquals(GameDivision.WOMENS, state.division)
        assertEquals("Semi-pro showcase", state.level)
        assertEquals("Final", state.gameContext)
        assertEquals("Casey", state.observers)
        assertEquals("South", state.nearEndName)
        assertEquals("North", state.farEndName)
        assertEquals(PullPromptTarget.NEITHER, state.pullPromptTarget)
        assertEquals(17, state.rules.gameTo)
        assertFalse(state.rules.hasFloaterTimeout)
        assertEquals("Viscous", state.teamOne.name)
        assertEquals(TeamColorChoice.BLACK, state.teamOne.color)
        assertEquals("Post-play coach", state.teamOne.coaches)
        assertEquals("Post-play field captain", state.teamOne.fieldCaptains)
        assertEquals("Post-play spirit captain", state.teamOne.spiritCaptains)
        assertEquals("Animal", state.teamTwo.name)
        assertEquals(TeamColorChoice.CUSTOM, state.teamTwo.color)
        assertEquals(0xFFABCDEFL, state.teamTwo.customColorArgb)
        assertEquals("Post-play other coach", state.teamTwo.coaches)
        assertEquals("Post-play other field captain", state.teamTwo.fieldCaptains)
        assertEquals("Post-play other spirit captain", state.teamTwo.spiritCaptains)
        assertEquals(fieldStateAfterGoal.teamOne.score, state.teamOne.score)
        assertEquals(fieldStateAfterGoal.teamTwo.score, state.teamTwo.score)
        assertEquals(setupEdit2.teamOnePlayers, state.playerCards(VC))
        assertEquals(setupEdit2.teamTwoPlayers, state.playerCards(ANIMAL))
        assertEquals(setupEdit2.pullingTeam, state.openingPullingTeam)
        assertEquals(setupEdit2.pullingFromEnd, state.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, state.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, state.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.nearAttackingTeam, state.nearAttackingTeam)
        assertEquals(fieldStateAfterGoal.phase, state.phase)
        assertEquals(fieldStateAfterGoal.countdown?.targetEpoch, state.countdown?.targetEpoch)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertNull(state.countdown?.nextTimingCue(state.countdown!!.targetEpoch - 20_000L))
        assertEquals(fieldStateAfterGoal.pendingCapOffer, state.pendingCapOffer)
        assertEquals(setupEdit2.teamOnePlayers, state.teamOnePlayers)
        assertEquals(setupEdit2.teamTwoPlayers, state.teamTwoPlayers)
        assertEquals("Undo Update game setup", state.undoEntry?.label)
        assertEquals(fieldStateAfterGoal, state.undoEntry?.previous)

        // Once the game is going, setup edits to pull orientation are basically ignored.
        // Don't change the actual direction of the pulls in the active game.
        // The only thing that changes is the nominal openingPullingTeam and End.
        val flippedPullSetup = fieldStateAfterGoal.toSetupState().copy(
            pullingTeam = setup.pullingTeam.flip(),
            pullingFromEnd = setup.pullingFromEnd.flip(),
        )
        val flippedPullState = applySetupToLiveGame(
            fieldStateAfterGoal,
            flippedPullSetup,
            20_000L,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, flippedPullState.phase)
        assertEquals(flippedPullSetup.pullingTeam, flippedPullState.openingPullingTeam)
        assertEquals(flippedPullSetup.pullingFromEnd, flippedPullState.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, flippedPullState.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, flippedPullState.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.nearAttackingTeam, flippedPullState.nearAttackingTeam)
    }

    /**
     * Test setup edits that should preserve an in-progress countdown.
     */
    @Test
    fun setupEditsPreserveCountdown() {
        val setup = fullSetup()
        val setupEdit1 = editSetup1(setup)

        // Setup edits do not restart an in-progress countdown.
        val fieldStateAfterGoal = stateAfterGoal(setup, setupEdit1)
        val setupEdit2 = editSetup2(fieldStateAfterGoal.toSetupState())
        val pendingCountdown = fieldStateAfterGoal.countdown
        val state = applySetupToLiveGame(
            fieldStateAfterGoal,
            setupEdit2.copy(rules = setupEdit2.rules.copy(gameTo = 19)),
            300_000L,
        )
        assertEquals(pendingCountdown?.targetEpoch, state.countdown?.targetEpoch)
        assertEquals("Pull in", state.countdown?.label)
        assertNull(state.countdown?.nextTimingCue(state.countdown!!.targetEpoch - 20_000L))
        assertEquals(19, state.rules.gameTo)
        assertEquals("Undo Update game setup", state.undoEntry?.label)
    }

    /**
     * Test team-name normalization when setup edits leave names blank.
     */
    @Test
    fun blankTeamNamesNormalize() {
        // Start with a setup that has real team names.
        val setup = fullSetup()
        var stateBeforeBlankNames = createLiveGameState(setup)
        assertEquals("Viscous Coupling", stateBeforeBlankNames.teamOne.name)
        assertEquals("Animal", stateBeforeBlankNames.teamTwo.name)

        // Now edit the setup to remove the names.  They should normalize as Team 1/2.
        var state = applySetupToLiveGame(
            stateBeforeBlankNames,
            setup.copy(
                teamOne = TeamSetup("", TeamColorChoice.WHITE),
                teamTwo = TeamSetup("", TeamColorChoice.BLUE),
            ),
            400_000L,
        )
        assertEquals("Team 1", state.teamOne.name)
        assertEquals("Team 2", state.teamTwo.name)

        // This is also true if the game has really started with goals and such.
        stateBeforeBlankNames = stateBeforeBlankNames.recordGoalFromCurrentState(
            TeamId.TEAM_ONE,
            350_000L,
        )
        state = applySetupToLiveGame(
            stateBeforeBlankNames,
            setup.copy(
                teamOne = TeamSetup("", TeamColorChoice.WHITE),
                teamTwo = TeamSetup("", TeamColorChoice.BLUE),
            ),
            400_000L,
        )
        assertEquals("Team 1", state.teamOne.name)
        assertEquals("Team 2", state.teamTwo.name)
    }

    /// Return the prior-card records used by the full setup fixture.
    private fun teamOnePriorPlayers(): List<PlayerRecord> {
        return listOf(priorPlayerRecord("17", priorYellows = 1))
    }

    /// Return the prior-card records used by the full setup fixture.
    private fun teamTwoPriorPlayers(): List<PlayerRecord> {
        return listOf(priorPlayerRecord("23", priorReds = 1))
    }

    /// Return a setup state with all editable fields populated.
    private fun fullSetup(): GameSetupState {
        val VC = TeamId.TEAM_ONE
        return standardGameSetup(
            startTime = LocalTime.of(8, 30),
            rules = GameRules(
                gameTo = 13,
                halftimeMinutes = 8,
                halfCapMinutes = 40,
                softCapMinutes = 80,
                hardCapMinutes = 95,
                timeoutsPerHalf = 1,
                hasFloaterTimeout = true,
            ),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
        ).copy(
            tournamentName = "Potlatch",
            division = GameDivision.MIXED,
            level = "Grandmasters",
            gameContext = "Pool play",
            observers = "Mike and Gary",
            nearEndName = "Road",
            farEndName = "Trees",
            teamOne = TeamSetup(
                name = "Viscous Coupling",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF123456L,
                coaches = "Coach VC",
                fieldCaptains = "VC field captain",
                spiritCaptains = "VC spirit captain",
            ),
            teamTwo = TeamSetup(
                name = "Animal",
                color = TeamColorChoice.YELLOW,
                coaches = "Animal coaches",
                fieldCaptains = "Animal field captains",
                spiritCaptains = "Animal spirit captains",
            ),
            teamOnePlayers = teamOnePriorPlayers(),
            teamTwoPlayers = teamTwoPriorPlayers(),
            pullPromptTarget = PullPromptTarget.FAR,
        )
    }

    /// Return the first alternate setup used by setup-edit tests.
    private fun editSetup1(setup: GameSetupState): GameSetupState {
        val ANIMAL = TeamId.TEAM_TWO
        return setup.copy(
            startTime = LocalTime.of(8, 45),
            tournamentName = "Spring Reign",
            division = GameDivision.OPEN,
            level = "Youth",
            gameContext = "Quarterfinal",
            observers = "Alex and Blake",
            nearEndName = "Parking",
            farEndName = "Scoreboard",
            rules = setup.rules.copy(gameTo = 15, timeoutsPerHalf = 2),
            teamOne = TeamSetup(
                name = "VC",
                color = TeamColorChoice.WHITE,
                customColorArgb = 0xFF123456L,
                coaches = "Coach edits",
                fieldCaptains = "Field captain edits",
                spiritCaptains = "Spirit captain edits",
            ),
            teamTwo = TeamSetup(
                name = "Animal Ultimate",
                color = TeamColorChoice.RED,
                coaches = "Other coach edits",
                fieldCaptains = "Other field captain edits",
                spiritCaptains = "Other spirit captain edits",
            ),
            teamOnePlayers = teamOnePriorPlayers() + priorPlayerRecord("8", priorYellows = 2),
            teamTwoPlayers = teamTwoPriorPlayers(),
            pullingTeam = ANIMAL,
            pullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )
    }

    /// Return the second alternate setup used by setup-edit tests.
    private fun editSetup2(setupEdit1: GameSetupState): GameSetupState {
        val VC = TeamId.TEAM_ONE
        return setupEdit1.copy(
            startTime = LocalTime.of(9, 0),
            tournamentName = "College Nationals",
            division = GameDivision.WOMENS,
            level = "Semi-pro showcase",
            gameContext = "Final",
            observers = "Casey",
            nearEndName = "South",
            farEndName = "North",
            rules = setupEdit1.rules.copy(gameTo = 17, hasFloaterTimeout = false),
            teamOne = TeamSetup(
                name = "Viscous",
                color = TeamColorChoice.BLACK,
                coaches = "Post-play coach",
                fieldCaptains = "Post-play field captain",
                spiritCaptains = "Post-play spirit captain",
            ),
            teamTwo = TeamSetup(
                name = "Animal",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFFABCDEFL,
                coaches = "Post-play other coach",
                fieldCaptains = "Post-play other field captain",
                spiritCaptains = "Post-play other spirit captain",
            ),
            teamOnePlayers = emptyList(),
            teamTwoPlayers = emptyList(),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
            pullPromptTarget = PullPromptTarget.NEITHER,
        )
    }

    /// Return a live game state after play has started and Viscous Coupling has scored.
    private fun stateAfterGoal(
        setup: GameSetupState,
        setupEdit1: GameSetupState,
    ): GameState {
        val VC = TeamId.TEAM_ONE
        var state = createLiveGameState(setup)
        state = applySetupToLiveGame(state, setupEdit1, 10_000L)
        state = state.beginLivePoint()
        state = state.assessYellowCard(VC, "17").state
        return recordGoalAt(state, VC, LocalTime.of(8, 50))
    }
}

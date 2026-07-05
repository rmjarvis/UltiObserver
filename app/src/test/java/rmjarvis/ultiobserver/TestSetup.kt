package rmjarvis.ultiobserver

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for setup-state conversion and applying setup edits to current games.
class TestSetup : GameDomainTestFixtures() {
    /**
     * Test current-game creation and setup-state conversion from setup form data.
     */
    @Test
    fun setupRoundTrip() {
        val VC = TeamId.TEAM_ONE
        val teamOnePlayers = teamOnePriorPlayers()
        val teamTwoPlayers = teamTwoPriorPlayers()

        // A new setup draft starts with the current standard game rules.
        val newSetupGame = newSetupGameState(
            now = epochTimestamp(
                LocalDate.of(2026, 1, 1),
                LocalTime.of(9, 0),
                ZoneId.systemDefault(),
            ),
        )
        assertFalse(newSetupGame.hasStarted())
        assertEquals(GameRules(), newSetupGame.rules)

        // A new setup draft can carry tournament context from the previous game while keeping
        // its own freshly calculated start date and time.
        val previousGameInfo = fullSetup().copy(
            rules = GameRules(gameTo = 11),
            teamOne = TeamState("Previous A", TeamColorChoice.GREEN),
            teamTwo = TeamState("Previous B", TeamColorChoice.YELLOW),
        )
        val repeatedTournamentSetup = newSetupGameState(
            now = epochTimestamp(
                LocalDate.of(2026, 1, 1),
                LocalTime.of(9, 15),
                ZoneId.systemDefault(),
            ),
            defaultsFrom = previousGameInfo,
        )
        assertEquals(LocalDate.of(2026, 1, 1), repeatedTournamentSetup.startDate)
        assertEquals(LocalTime.of(9, 30), repeatedTournamentSetup.startTime)
        assertEquals("Potlatch", repeatedTournamentSetup.tournamentName)
        assertEquals(GameDivision.MIXED, repeatedTournamentSetup.division)
        assertEquals("Grandmasters", repeatedTournamentSetup.level)
        assertEquals(GameRules(gameTo = 11), repeatedTournamentSetup.rules)
        assertEquals("", repeatedTournamentSetup.gameContext)
        assertEquals(emptyList<String>(), repeatedTournamentSetup.observerNames)
        assertEquals("", repeatedTournamentSetup.teamOne.name)
        assertEquals("", repeatedTournamentSetup.teamTwo.name)

        // A profile observer default seeds the first observer, while whitespace stays empty.
        assertEquals(
            listOf("Casey Observer"),
            newSetupGameState(
                now = epochTimestamp(
                    LocalDate.of(2026, 1, 1),
                    LocalTime.of(9, 15),
                    ZoneId.systemDefault(),
                ),
                defaultObserverName = " Casey Observer ",
            ).observerNames,
        )
        assertEquals(
            emptyList<String>(),
            newSetupGameState(
                now = epochTimestamp(
                    LocalDate.of(2026, 1, 1),
                    LocalTime.of(9, 15),
                    ZoneId.systemDefault(),
                ),
                defaultObserverName = "   ",
            ).observerNames,
        )

        // A setup-created current game has empty player lists when no prior card holders are entered.
        val noPriorCardsState = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(8, 30))
        )
        assertTrue(noPriorCardsState.teamOnePlayers.isEmpty())
        assertTrue(noPriorCardsState.teamTwoPlayers.isEmpty())
        assertFalse(noPriorCardsState.teamOne.hasCoachOrCaptainInfo())
        assertFalse(noPriorCardsState.teamTwo.hasCoachOrCaptainInfo())

        // Any individual coach/captain field makes team staff information visible.
        assertTrue(
            noPriorCardsState.teamOne
                .copy(coaches = "Coach")
                .hasCoachOrCaptainInfo()
        )
        assertTrue(
            noPriorCardsState.teamOne
                .copy(fieldCaptains = "Field captain")
                .hasCoachOrCaptainInfo()
        )
        assertTrue(
            noPriorCardsState.teamOne
                .copy(spiritCaptains = "Spirit captain")
                .hasCoachOrCaptainInfo()
        )

        // Full setup data carries directly into a current game.
        val setup = fullSetup()
        val state = createLiveGameState(setup)
        assertEquals("Potlatch", state.tournamentName)
        assertEquals(GameDivision.MIXED, state.division)
        assertEquals("Grandmasters", state.level)
        assertEquals("Pool play", state.gameContext)
        assertEquals(listOf("Mike", "Gary"), state.observerNames)
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
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(teamOnePlayers, state.teamOnePlayers)
        assertEquals(teamTwoPlayers, state.teamTwoPlayers)

        // Setup-stage game state preserves exact setup fields before opening-pull flow starts.
        val blankNameSetup = setup.copy(
            teamOne = setup.teamOne.copy(name = ""),
            teamTwo = setup.teamTwo.copy(name = ""),
        )
        val setupGame = blankNameSetup
        assertEquals(GamePhase.SETUP, setupGame.phase)
        assertFalse(setupGame.phase.isBeforeLivePoint)
        assertNull(setupGame.countdown)
        assertEquals(blankNameSetup, setupGame)
        assertEquals("", setupGame.teamOne.name)
        assertEquals("", setupGame.teamTwo.name)
        val startedSetupGame = setupGame.startGame()
        assertEquals(GamePhase.PRE_GAME, startedSetupGame.phase)
        assertTrue(startedSetupGame.phase.isBeforeLivePoint)
        assertEquals("Team 1", startedSetupGame.teamOne.name)
        assertEquals("Team 2", startedSetupGame.teamTwo.name)
        assertEquals(CountdownKind.OPENING_PULL, startedSetupGame.countdown?.kind)
        assertEquals(createLiveGameState(blankNameSetup), startedSetupGame)

        // The opening pull countdown starts from the game start epoch.
        assertEquals(GamePhase.PRE_GAME, state.phase)
        assertEquals(CountdownKind.OPENING_PULL, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 40_000L, state.countdown?.targetEpoch)

        // Returning from Update game setup before play starts is a no-op when nothing changed.
        val unchangedState = applySetupToLiveGame(state, state, 10_000L)
        assertEquals(state, unchangedState)
        assertNull(unchangedState.undoEntry)

        // Returning from Update game setup after play starts is also a no-op with unchanged setup.
        val liveState = state.beginLivePoint()
        assertEquals(GamePhase.LIVE_POINT, liveState.phase)
        val unchangedLiveState = applySetupToLiveGame(
            liveState,
            liveState,
            20_000L,
        )
        assertEquals(liveState, unchangedLiveState)

    }

    /**
     * Test that every setup-editable game field is recognized as a meaningful update.
     */
    @Test
    fun setupEditDifferences() {
        // Each setup field should make Done apply one undo-backed setup update.
        val setup = fullSetup()
        val state = createLiveGameState(setup)
        val alternateTimeZone = if (setup.timeZone == ZoneId.of("UTC")) {
            ZoneId.of("America/New_York")
        } else {
            ZoneId.of("UTC")
        }
        val setupChanges = listOf<Pair<String, (GameState) -> GameState>>(
            "start date" to { it.copy(startDate = it.startDate.plusDays(1)) },
            "start time" to { it.copy(startTime = it.startTime.plusMinutes(5)) },
            "time zone" to { it.copy(timeZone = alternateTimeZone) },
            "tournament" to { it.copy(tournamentName = "Changed Tournament") },
            "division" to { it.copy(division = GameDivision.WOMENS) },
            "level" to { it.copy(level = "Changed level") },
            "context" to { it.copy(gameContext = "Changed context") },
            "observers" to { it.copy(observerNames = listOf("Changed observer")) },
            "field" to { it.copy(fieldName = "Changed field") },
            "near end" to { it.copy(nearEndName = "Changed near end") },
            "far end" to { it.copy(farEndName = "Changed far end") },
            "rules" to { it.copy(rules = it.rules.copy(gameTo = it.rules.gameTo + 1)) },
            "team one name" to { it.copy(teamOne = it.teamOne.copy(name = "Changed team")) },
            "team one color" to { it.copy(teamOne = it.teamOne.copy(color = TeamColorChoice.BLACK)) },
            "team one custom color" to {
                it.copy(
                    teamOne = it.teamOne.copy(
                        color = TeamColorChoice.CUSTOM,
                        customColorArgb = 0xFFABCDEF,
                    )
                )
            },
            "team one coaches" to { it.copy(teamOne = it.teamOne.copy(coaches = "Changed coach")) },
            "team one field captains" to {
                it.copy(teamOne = it.teamOne.copy(fieldCaptains = "Changed field captain"))
            },
            "team one spirit captains" to {
                it.copy(teamOne = it.teamOne.copy(spiritCaptains = "Changed spirit captain"))
            },
            "team two name" to { it.copy(teamTwo = it.teamTwo.copy(name = "Changed opponent")) },
            "team one players" to {
                it.copy(teamOnePlayers = it.teamOnePlayers + PlayerRecord("99"))
            },
            "team two players" to {
                it.copy(teamTwoPlayers = it.teamTwoPlayers + PlayerRecord("98"))
            },
            "pull prompts" to { it.copy(pullPromptTarget = PullPromptTarget.NEITHER) },
            "initial ratio" to {
                it.copy(initialGenderRatio = GenderRatio.FOUR_WOMEN_THREE_MEN)
            },
            "Gen Zone end" to { it.copy(firstHalfGenZone = it.firstHalfGenZone.flip()) },
            "opening pulling team" to {
                it.copy(openingPullingTeam = it.openingPullingTeam.flip())
            },
            "opening pulling end" to {
                it.copy(openingPullingFromEnd = it.openingPullingFromEnd.flip())
            },
        )

        setupChanges.forEach { (label, edit) ->
            val updated = applySetupToLiveGame(state, edit(state), 10_000L)
            assertEquals(label, "Undo Update game setup", updated.undoEntry?.label)
        }
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
        val sameDaySetup = newSetupGameState(
            now = epochTimestamp(
                LocalDate.of(2026, 1, 1),
                LocalTime.of(23, 0),
                ZoneId.systemDefault(),
            ),
        )
        assertEquals(LocalDate.of(2026, 1, 1), sameDaySetup.startDate)
        assertEquals(LocalTime.of(23, 0), sameDaySetup.startTime)

        // Rounding past midnight advances the setup date.
        val nextDaySetup = newSetupGameState(
            now = epochTimestamp(
                LocalDate.of(2026, 1, 1),
                LocalTime.of(23, 45),
                ZoneId.systemDefault(),
            ),
        )
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
            epochTimestamp(LocalDate.of(2026, 1, 1), LocalTime.of(18, 0), ZoneId.of("UTC")),
            utcState.startEpoch,
        )
        assertEquals(utcState.startEpoch, pacificState.startEpoch)

        // Starting a game rebuilds the epoch from the edited setup date and time, rather than
        // trusting a stale epoch left over from the original setup draft.
        val editedStartSetup = sameDaySetup.copy(
            startDate = LocalDate.of(2026, 2, 3),
            startTime = LocalTime.of(14, 15),
            timeZone = testTimeZone,
        )
        val editedStartState = createLiveGameState(editedStartSetup)
        val expectedStartEpoch = epochTimestamp(
            LocalDate.of(2026, 2, 3),
            LocalTime.of(14, 15),
            testTimeZone,
        )
        assertEquals(expectedStartEpoch, editedStartState.startEpoch)
        assertEquals(
            expectedStartEpoch + editedStartState.countdown!!.durationSeconds * 1000L,
            editedStartState.countdown!!.targetEpoch,
        )
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
        assertEquals(Color(0xFFFF4FA3), TeamColorChoice.PINK.accent)
        assertEquals(Color(0xFF2F1022), TeamColorChoice.PINK.content)

        // Setup and live teams use built-in colors directly unless the observer picked a custom
        // color.
        val builtInSetupTeam = TeamState("Built in", TeamColorChoice.PINK)
        val builtInLiveTeam = TeamState("Built in", TeamColorChoice.PINK)
        assertEquals(Color(0xFFFF4FA3), builtInSetupTeam.accent)
        assertEquals(Color(0xFF2F1022), builtInSetupTeam.content)
        assertEquals(Color(0xFFFF4FA3), builtInLiveTeam.accent)
        assertEquals(Color(0xFF2F1022), builtInLiveTeam.content)

        // Custom team colors keep the observer-picked background and choose readable text from
        // the background luminance.
        val lightCustomSetupTeam = TeamState(
            name = "Light",
            color = TeamColorChoice.CUSTOM,
            customColorArgb = 0xFFE7F0F7L,
        )
        val darkCustomLiveTeam = TeamState(
            name = "Dark",
            color = TeamColorChoice.CUSTOM,
            customColorArgb = 0xFF203040L,
        )
        assertEquals(Color(0xFFE7F0F7), lightCustomSetupTeam.accent)
        assertEquals(Color(0xFF1F1A17), lightCustomSetupTeam.content)
        assertEquals(Color(0xFF203040), darkCustomLiveTeam.accent)
        assertEquals(Color.White, darkCustomLiveTeam.content)
        assertEquals(Color(0xFF1F1A17), readableContentColor(Color(0xFFE7F0F7)))
        assertEquals(Color.White, readableContentColor(Color(0xFF203040)))

        // Custom team colors require an explicit ARGB value.
        assertThrows(IllegalArgumentException::class.java) {
            TeamColorChoice.CUSTOM.accent
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamColorChoice.CUSTOM.content
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamState("Custom", TeamColorChoice.CUSTOM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TeamState("Custom", TeamColorChoice.CUSTOM)
        }
    }

    /**
     * Test setup display text for field ends, pull prompts, team fields, and setup choices.
     */
    @Test
    fun setupDisplayText() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val setup = standardGameSetup(startTime = LocalTime.of(10, 0)).copy(
            division = GameDivision.MIXED,
            nearEndName = "Road",
            farEndName = "Trees",
            openingPullingFromEnd = FieldEnd.NEAR,
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

        // Pull-prompt choice labels are title-cased for the setup dialog option list.
        assertEquals("Road", PullPromptTarget.NEAR.choiceLabel("Road", "Trees"))
        assertEquals("Trees", PullPromptTarget.FAR.choiceLabel("Road", "Trees"))
        assertEquals("Both", PullPromptTarget.BOTH.choiceLabel("Road", "Trees"))
        assertEquals("Neither", PullPromptTarget.NEITHER.choiceLabel("Road", "Trees"))

        // Division and level option helpers supply the setup dialog lists in display order.
        assertEquals(
            listOf(GameDivision.OPEN, GameDivision.WOMENS, GameDivision.MIXED, null),
            orderedSetupDivisions(),
        )
        assertEquals(
            listOf(
                "Youth",
                "College",
                "Club",
                "Masters",
                "Grandmasters",
                "Great Grandmasters",
                "Legends",
            ),
            setupLevelPresets(),
        )
        assertEquals("Open Division", GameDivision.OPEN.setupSummaryLine())
        assertEquals("Women’s Division", GameDivision.WOMENS.setupSummaryLine())
        assertEquals("Mixed Division", GameDivision.MIXED.setupSummaryLine())
        assertEquals("Open", GameDivision.OPEN.displayText)
        assertEquals("Women’s", GameDivision.WOMENS.displayText)
        assertEquals("Mixed", GameDivision.MIXED.displayText)
        assertFalse(setup.copy(division = GameDivision.OPEN).usesMixedDivision())
        assertFalse(setup.copy(division = null).usesMixedDivision())

        // Team field helpers let the setup UI update either side through the same editor path.
        assertEquals("Team 1", VC.defaultName())
        assertEquals("Team 2", ANIMAL.defaultName())
        assertEquals(
            "Edited VC",
            setup.copy(teamOne = setup.teamOne.copy(name = "Edited VC")).teamOne.name,
        )
        assertEquals(
            "Edited Animal",
            setup.copy(teamTwo = setup.teamTwo.copy(name = "Edited Animal")).teamTwo.name,
        )
        assertEquals(setup.teamOnePlayers, setup.playersFor(VC))
        assertEquals(setup.teamTwoPlayers, setup.playersFor(ANIMAL))
        assertEquals(
            listOf(priorPlayerRecord("3", priorYellows = 1)),
            setup.withPlayersFor(VC, listOf(priorPlayerRecord("3", priorYellows = 1)))
                .teamOnePlayers,
        )
        assertEquals(
            listOf(priorPlayerRecord("4", priorReds = 1)),
            setup.withPlayersFor(ANIMAL, listOf(priorPlayerRecord("4", priorReds = 1)))
                .teamTwoPlayers,
        )

        // Blank team names fall back to stable setup labels in summaries.
        val blankTeamSetup = defaultEndsSetup.copy(
            teamOne = TeamState("", TeamColorChoice.WHITE),
            teamTwo = TeamState("", TeamColorChoice.BLUE),
            openingPullingTeam = ANIMAL,
            openingPullingFromEnd = FieldEnd.FAR,
        )
        assertEquals("Team 1", VC.setupName(blankTeamSetup))
        assertEquals("Team 2 pulls from Far end", blankTeamSetup.startingPullSummary())
    }

    /**
     * Test compact setup summary text for game information, teams, prior cards, and rules.
     */
    @Test
    fun setupSummaryText() {
        val setup = standardGameSetup(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(10, 0),
        ).copy(
            tournamentName = " Potlatch ",
            division = GameDivision.MIXED,
            level = " Club ",
            gameContext = " Final ",
            observerNames = listOf(" Mike ", " Gary "),
            fieldName = " Field 7 ",
        )

        // Game-information summary lines trim optional text and omit blank fields.
        assertEquals(
            listOf(
                "Potlatch",
                "Mixed Division",
                "Club",
                "Final",
                "Observers: Mike, Gary",
                "Jan 1, 2026",
                "Start at 10:00 AM",
                "Field: Field 7",
            ),
            setup.gameInformationSummaryLines(),
        )
        assertEquals(
            listOf("Jan 1, 2026", "Start at 10:00 AM"),
            setup.copy(
                tournamentName = " ",
                division = null,
                level = "",
                gameContext = " ",
                observerNames = listOf(" ", ""),
                fieldName = " ",
            ).gameInformationSummaryLines(),
        )

        // Coach and captain summaries trim each line and skip empty staff fields.
        val staffTeam = TeamState(
            name = "Viscous Coupling",
            color = TeamColorChoice.WHITE,
            coaches = " Coach A \n\n Coach B ",
            fieldCaptains = " Field captain ",
            spiritCaptains = " Spirit captain ",
        )
        assertEquals(
            listOf(
                "Coach:" to "Coach A\nCoach B",
                "Field:" to "Field captain",
                "Spirit:" to "Spirit captain",
            ),
            staffTeam.namesSummary().map { summary -> summary.label to summary.value },
        )
        assertTrue(
            staffTeam.copy(coaches = "", fieldCaptains = "", spiritCaptains = "")
                .namesSummary()
                .isEmpty()
        )

        // Prior-card summaries use the compact player identity and compact card counts.
        assertEquals(
            "#17: Y 1\n#23: R 1",
            listOf(
                priorPlayerRecord("17", priorYellows = 1),
                priorPlayerRecord("23", playerName = "Morgan", priorReds = 1),
            ).teamPriorCardsSummary(),
        )

        // Rule summaries show enabled caps as minute offsets and disabled caps as dashes.
        assertEquals(
            "+45/+90/+105",
            GameRules(
                halfCapMinutes = 45,
                softCapMinutes = 90,
                hardCapMinutes = 105,
            ).capRulesSummary(),
        )
        assertEquals(
            "-/-/-",
            GameRules(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ).capRulesSummary(),
        )
        assertEquals(
            "2/half + floater",
            GameRules(timeoutsPerHalf = 2, hasFloaterTimeout = true).formatTimeoutRules(),
        )
        assertEquals(
            "1/half",
            GameRules(timeoutsPerHalf = 1, hasFloaterTimeout = false).formatTimeoutRules(),
        )
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
        assertEquals(listOf("Alex", "Blake"), state.observerNames)
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
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))

        // The updated opening-pull state is undo-backed.
        assertEquals("Pull sequence started.", state.lastEvent)
        assertEquals(CountdownKind.OPENING_PULL, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(50_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Update game setup", state.undoEntry?.label)
        assertEquals(initialState, state.undoEntry?.previous)
        assertTrue(state.eventLog.isEmpty())
        assertFalse(state.hasStarted())

        // One consequence of being in the pre-pull preview state is that pressing Back
        // returns to setup rather than Home. Show this by inserting this state into a ViewModel
        // that otherwise came from starting a normal game.
        val previewModel = AppViewModel(NoOpAppStateStorage)
        previewModel.startNewGame(now = 123_000L)
        previewModel.updateSetup(setup)
        previewModel.finishSetup(now = 123_000L)
        previewModel.updateCurrentGame(state)
        assertEquals(AppScreen.LIVE, previewModel.screen)
        previewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, previewModel.screen)
        assertTrue(previewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, previewModel.currentGame?.phase)

        // A blue card before the first point starts is a real game event, so Back returns Home
        // rather than setup even though the game is still in the pre-game phase.
        val blueCardState = state.assessBlueCard(
            ANIMAL,
            state.countdown!!.targetEpoch - 1_000L,
        ).state
        assertEquals(GamePhase.PRE_GAME, blueCardState.phase)
        assertTrue(blueCardState.hasStarted())

        val blueCardModel = AppViewModel(NoOpAppStateStorage)
        blueCardModel.startNewGame(now = 123_000L)
        blueCardModel.updateSetup(setup)
        blueCardModel.finishSetup(now = 123_000L)
        blueCardModel.updateCurrentGame(blueCardState)
        assertEquals(AppScreen.LIVE, blueCardModel.screen)
        blueCardModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, blueCardModel.screen)
        assertFalse(blueCardModel.hasSetupDraft)
        assertEquals(blueCardState, blueCardModel.currentGame)

        // Until the opening pull starts, setup edits to pull orientation should still retarget the
        // first pull even after a real pre-game event.
        val blueCardPullingTeamBeforeEdit = blueCardState.pullingTeam
        val blueCardPullingFromEndBeforeEdit = blueCardState.pullingFromEnd
        assertEquals(blueCardPullingTeamBeforeEdit, blueCardState.openingPullingTeam)
        assertEquals(blueCardPullingFromEndBeforeEdit, blueCardState.openingPullingFromEnd)

        val blueCardSetupEdit = blueCardState.copy(
            openingPullingTeam = blueCardPullingTeamBeforeEdit.flip(),
            openingPullingFromEnd = blueCardPullingFromEndBeforeEdit.flip(),
        )
        val blueCardSetupUpdate = applySetupToLiveGame(
            blueCardState,
            blueCardSetupEdit,
            20_000L,
        )
        assertEquals(GamePhase.PRE_GAME, blueCardSetupUpdate.phase)
        assertTrue(blueCardSetupUpdate.hasStarted())
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
        val setupEdit2 = editSetup2(fieldStateAfterGoal)
        val state = applySetupToLiveGame(fieldStateAfterGoal, setupEdit2, 200_000L)

        // After play starts, setup metadata changes but current pull and field state are preserved.
        assertEquals(LocalTime.of(9, 0), state.startTime)
        assertEquals("College Nationals", state.tournamentName)
        assertEquals(GameDivision.WOMENS, state.division)
        assertEquals("Semi-pro showcase", state.level)
        assertEquals("Final", state.gameContext)
        assertEquals(listOf("Casey"), state.observerNames)
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
        assertEquals(setupEdit2.openingPullingTeam, state.openingPullingTeam)
        assertEquals(setupEdit2.openingPullingFromEnd, state.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, state.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, state.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.teamDefendingEnd(FieldEnd.FAR), state.teamDefendingEnd(FieldEnd.FAR))
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
        val flippedPullSetup = fieldStateAfterGoal.copy(
            openingPullingTeam = setup.openingPullingTeam.flip(),
            openingPullingFromEnd = setup.openingPullingFromEnd.flip(),
        )
        val flippedPullState = applySetupToLiveGame(
            fieldStateAfterGoal,
            flippedPullSetup,
            20_000L,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, flippedPullState.phase)
        assertEquals(flippedPullSetup.openingPullingTeam, flippedPullState.openingPullingTeam)
        assertEquals(flippedPullSetup.openingPullingFromEnd, flippedPullState.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, flippedPullState.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, flippedPullState.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.teamDefendingEnd(FieldEnd.FAR), flippedPullState.teamDefendingEnd(FieldEnd.FAR))
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
        val setupEdit2 = editSetup2(fieldStateAfterGoal)
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
                teamOne = TeamState("", TeamColorChoice.WHITE),
                teamTwo = TeamState("", TeamColorChoice.BLUE),
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
                teamOne = TeamState("", TeamColorChoice.WHITE),
                teamTwo = TeamState("", TeamColorChoice.BLUE),
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
    private fun fullSetup(): GameState {
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
            observerNames = listOf("Mike", "Gary"),
            nearEndName = "Road",
            farEndName = "Trees",
            teamOne = TeamState(
                name = "Viscous Coupling",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF123456L,
                coaches = "Coach VC",
                fieldCaptains = "VC field captain",
                spiritCaptains = "VC spirit captain",
            ),
            teamTwo = TeamState(
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
    private fun editSetup1(setup: GameState): GameState {
        val ANIMAL = TeamId.TEAM_TWO
        return setup.copy(
            startTime = LocalTime.of(8, 45),
            tournamentName = "Spring Reign",
            division = GameDivision.OPEN,
            level = "Youth",
            gameContext = "Quarterfinal",
            observerNames = listOf("Alex", "Blake"),
            nearEndName = "Parking",
            farEndName = "Scoreboard",
            rules = setup.rules.copy(gameTo = 15, timeoutsPerHalf = 2),
            teamOne = TeamState(
                name = "VC",
                color = TeamColorChoice.WHITE,
                customColorArgb = 0xFF123456L,
                coaches = "Coach edits",
                fieldCaptains = "Field captain edits",
                spiritCaptains = "Spirit captain edits",
            ),
            teamTwo = TeamState(
                name = "Animal Ultimate",
                color = TeamColorChoice.RED,
                coaches = "Other coach edits",
                fieldCaptains = "Other field captain edits",
                spiritCaptains = "Other spirit captain edits",
            ),
            teamOnePlayers = teamOnePriorPlayers() + priorPlayerRecord("8", priorYellows = 2),
            teamTwoPlayers = teamTwoPriorPlayers(),
            openingPullingTeam = ANIMAL,
            openingPullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )
    }

    /// Return the second alternate setup used by setup-edit tests.
    private fun editSetup2(setupEdit1: GameState): GameState {
        val VC = TeamId.TEAM_ONE
        return setupEdit1.copy(
            startTime = LocalTime.of(9, 0),
            tournamentName = "College Nationals",
            division = GameDivision.WOMENS,
            level = "Semi-pro showcase",
            gameContext = "Final",
            observerNames = listOf("Casey"),
            nearEndName = "South",
            farEndName = "North",
            rules = setupEdit1.rules.copy(gameTo = 17, hasFloaterTimeout = false),
            teamOne = setupEdit1.teamOne.copy(
                name = "Viscous",
                color = TeamColorChoice.BLACK,
                coaches = "Post-play coach",
                fieldCaptains = "Post-play field captain",
                spiritCaptains = "Post-play spirit captain",
            ),
            teamTwo = setupEdit1.teamTwo.copy(
                name = "Animal",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFFABCDEFL,
                coaches = "Post-play other coach",
                fieldCaptains = "Post-play other field captain",
                spiritCaptains = "Post-play other spirit captain",
            ),
            teamOnePlayers = emptyList(),
            teamTwoPlayers = emptyList(),
            openingPullingTeam = VC,
            openingPullingFromEnd = FieldEnd.FAR,
            pullPromptTarget = PullPromptTarget.NEITHER,
        )
    }

    /// Return a current-game state after play has started and Viscous Coupling has scored.
    private fun stateAfterGoal(
        setup: GameState,
        setupEdit1: GameState,
    ): GameState {
        val VC = TeamId.TEAM_ONE
        var state = createLiveGameState(setup)
        state = applySetupToLiveGame(state, setupEdit1, 10_000L)
        state = state.beginLivePoint()
        state = state.assessYellowCard(VC, "17").state
        return recordGoalAt(state, VC, LocalTime.of(8, 50))
    }
}

package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/// Tests for the shared policy behind live-game rule-guidance presentation.
class TestRuleGuidance : GameDomainTestFixtures() {
    /**
     * Verify each mode's optional, concise, and automatic-resolution policies.
     */
    @Test
    fun modePolicies() {
        // Check each mode's presentation style in terms of:
        // * is the rule explanation the shorter "brief" copy.
        // * how is the dialog shown for optional notices
        // * how is the dialog shown for required notices
        data class ExpectedPolicy(
            val usesBrief: Boolean,
            val optional: RuleGuidancePresentation,
            val required: RuleGuidancePresentation,
        )

        val expectedPolicies = mapOf(
            RuleGuidanceMode.FULL to ExpectedPolicy(
                usesBrief = false,
                optional = RuleGuidancePresentation.VISIBLE,
                required = RuleGuidancePresentation.VISIBLE,
            ),
            RuleGuidanceMode.BRIEF to ExpectedPolicy(
                usesBrief = true,
                optional = RuleGuidancePresentation.VISIBLE,
                required = RuleGuidancePresentation.VISIBLE,
            ),
            RuleGuidanceMode.TIMED to ExpectedPolicy(
                usesBrief = true,
                optional = RuleGuidancePresentation.VISIBLE_TIMED,
                required = RuleGuidancePresentation.VISIBLE_TIMED,
            ),
            RuleGuidanceMode.NONE to ExpectedPolicy(
                usesBrief = true,
                optional = RuleGuidancePresentation.HIDDEN_AUTO_ACCEPT,
                required = RuleGuidancePresentation.VISIBLE_TIMED,
            ),
        )
        expectedPolicies.forEach { (mode, expected) ->
            assertEquals(expected.usesBrief, mode.usesBriefGuidance())
            assertEquals(expected.optional, mode.presentation(requiredInNone = false))
            assertEquals(expected.required, mode.presentation(requiredInNone = true))
        }

        // Check the descriptions that get displayed on the Settings page.
        assertEquals(
            listOf("Full", "Brief", "Timed", "None"),
            RuleGuidanceMode.entries.map { it.label },
        )
        assertEquals(
            "Show a short, but fairly complete, summary of the restart location, timings, and " +
                "other relevant rules and wait for confirmation.",
            RuleGuidanceMode.FULL.description,
        )
        assertEquals(
            "Show only a brief rule reminder and wait for confirmation.",
            RuleGuidanceMode.BRIEF.description,
        )
        assertEquals(
            "Show a brief reminder and automatically accept or close after 5 seconds.",
            RuleGuidanceMode.TIMED.description,
        )
        assertEquals(
            "Skip optional reminders. Some required notices still appear, but close after 5 seconds.",
            RuleGuidanceMode.NONE.description,
        )
    }

    /**
     * Verify the shared brief-message dispatcher reaches every event-specific formatter.
     */
    @Test
    fun briefMessageDispatch() {
        val state = standardLiveGameState()

        // Timeout events retain their distinct accepted and invalid-action messages.
        val timeoutEvents = listOf(
            GameEvent.TimeoutCharged(state, TeamId.TEAM_ONE) to
                "Timeout charged to Viscous Coupling.",
            GameEvent.TimeoutUnavailable(state) to
                "Timeouts are not available now.",
            GameEvent.TeamOutOfTimeouts(state, TeamId.TEAM_ONE) to
                "Viscous Coupling is out of timeouts.",
        )
        timeoutEvents.forEach { (event, expected) ->
            assertEquals(expected, event.formatBriefMessage().plainText)
        }

        // Team-card dispatch covers both player-specific and team-only concise results.
        val yellowEvent: GameEvent =
            state.assessYellowCard(TeamId.TEAM_ONE, "4", 0L).event
        assertEquals(
            "Yellow card on player 4.",
            yellowEvent.formatBriefMessage().plainText,
        )
        val blueEvent: GameEvent = state.previewBlueCard(TeamId.TEAM_ONE).event
        assertEquals(
            "Blue card on Viscous Coupling.",
            blueEvent.formatBriefMessage().plainText,
        )

        // Technical-foul, pull-violation, and pull-time events each reach their own formatter.
        val technicalFoulEvent: GameEvent = GameEvent.TechnicalFoulsChanged(
            state = state,
            team = TeamId.TEAM_ONE,
            technicalFoulTotal = 1,
        )
        assertEquals(
            "First technical foul on Viscous Coupling.",
            technicalFoulEvent.formatBriefMessage().plainText,
        )
        val pullViolationEvent: GameEvent =
            state.previewPullViolation(TeamId.TEAM_ONE, PullViolationType.OFFSIDES)!!.event
        assertEquals(
            "Animal starts at the brick mark.",
            pullViolationEvent.formatBriefMessage().plainText,
        )
        val timeViolationEvent: GameEvent = GameEvent.TimeViolationRecorded(
            state = state,
            team = TeamId.TEAM_TWO,
            outcome = TimeViolationOutcome.WARNING,
        )
        assertEquals(
            "Warning only. Animal has 20 seconds to signal readiness.",
            timeViolationEvent.formatBriefMessage().plainText,
        )
    }

    /**
     * Verify domain events and prompts decide which notices remain visible in None mode.
     */
    @Test
    fun noneModeRequirements() {
        val state = standardLiveGameState()

        // Both invalid-timeout results remain visible because the observer needs to know no
        // action was taken.
        assertEquals(
            true,
            GameEvent.TimeoutUnavailable(state).requiresGuidanceInNone(),
        )
        assertEquals(
            true,
            GameEvent.TeamOutOfTimeouts(state, TeamId.TEAM_ONE).requiresGuidanceInNone(),
        )

        // An ordinary timeout confirmation can be accepted without showing its reminder.
        assertEquals(
            false,
            GameEvent.TimeoutCharged(state, TeamId.TEAM_ONE).requiresGuidanceInNone(),
        )

        // Card results are required only when they announce a player suspension.
        val ordinaryCardEvent = state.assessYellowCard(TeamId.TEAM_ONE, "4", 0L).event
        assertEquals(false, ordinaryCardEvent.requiresGuidanceInNone())
        val suspensionCardEvent = state.assessRedCard(TeamId.TEAM_ONE, "4", 0L).event
        assertEquals(true, suspensionCardEvent.requiresGuidanceInNone())

        // A pull violation remains visible only when the majority-pull alternative must be
        // offered. Ordinary pull-violation confirmations can be accepted immediately.
        val ordinaryPullEvent = state
            .previewPullViolation(TeamId.TEAM_ONE, PullViolationType.OFFSIDES)!!.event
        assertEquals(false, ordinaryPullEvent.requiresGuidanceInNone())
        val majorityPullAlternativeEvent = state.copy(division = GameDivision.MIXED)
            .previewPullViolation(TeamId.TEAM_ONE, PullViolationType.OFFSIDES)!!.event
        assertEquals(true, majorityPullAlternativeEvent.requiresGuidanceInNone())

        // Technical-foul and time-violation results have no independently required notice.
        assertEquals(
            false,
            GameEvent.TechnicalFoulsChanged(
                state = state,
                team = TeamId.TEAM_ONE,
                technicalFoulTotal = 1,
            ).requiresGuidanceInNone(),
        )
        assertEquals(
            false,
            GameEvent.TimeViolationRecorded(
                state = state,
                team = TeamId.TEAM_ONE,
                outcome = TimeViolationOutcome.WARNING,
            ).requiresGuidanceInNone(),
        )

        // Apply-cap is the only prompt type retained in None mode, giving the observer a chance
        // to choose Not yet before the cap applies.
        assertEquals(
            true,
            GamePrompt.ApplyCap(state, CapType.HALF).requiresGuidanceInNone(),
        )
        assertEquals(
            false,
            GamePrompt.HalftimeStarted(state).requiresGuidanceInNone(),
        )
        assertEquals(
            false,
            GamePrompt.LivePointMisconduct(
                GameEvent.TechnicalFoulsChanged(
                    state = state.beginLivePoint(),
                    team = TeamId.TEAM_ONE,
                    technicalFoulTotal = 3,
                )
            ).requiresGuidanceInNone(),
        )
        assertEquals(
            false,
            GamePrompt.GameOver(state).requiresGuidanceInNone(),
        )
    }

    /**
     * Verify only Full guidance asks the offense/defense misconduct question.
     */
    @Test
    fun misconductChoice() {
        // The third technical foul during a live point triggers the misconduct restart rules.
        val event = GameEvent.TechnicalFoulsChanged(
            state = standardLiveGameState().beginLivePoint(),
            team = TeamId.TEAM_ONE,
            technicalFoulTotal = 3,
        )
        assertEquals(true, event.triggersMisconductPenalty())

        // Full is the only mode that asks whether the misconduct was against offense or defense.
        assertEquals(true, event.needsMisconductChoice(RuleGuidanceMode.FULL))
        assertEquals(false, event.needsMisconductChoice(RuleGuidanceMode.BRIEF))
        assertEquals(false, event.needsMisconductChoice(RuleGuidanceMode.TIMED))
        assertEquals(false, event.needsMisconductChoice(RuleGuidanceMode.NONE))

        // Full shows the question, while concise modes show the restart alternatives directly.
        assertEquals(
            GamePrompt.LivePointMisconduct(event).formatMessage().plainText,
            event.misconductConfirmationMessage(RuleGuidanceMode.FULL).plainText,
        )
        val briefReminder = "If offense: reverse brick\n" +
            "If defense: attacking brick or middle\n" +
            "Offense has 30 seconds to set."
        assertEquals(
            briefReminder,
            event.misconductConfirmationMessage(RuleGuidanceMode.BRIEF).plainText,
        )
        assertEquals(
            "Third technical foul on Viscous Coupling.\n\n$briefReminder",
            event.resultGuidanceMessage(RuleGuidanceMode.BRIEF).plainText,
        )

        // An ordinary card does not ask the misconduct question or append restart guidance.
        val ordinaryCardResult = standardLiveGameState()
            .assessYellowCard(TeamId.TEAM_ONE, "4", 0L)
        val ordinaryCardEvent = ordinaryCardResult.event
        assertEquals(false, ordinaryCardEvent.needsMisconductChoice(RuleGuidanceMode.FULL))
        assertEquals(
            ordinaryCardEvent.formatMessage().plainText,
            ordinaryCardEvent.misconductConfirmationMessage(RuleGuidanceMode.FULL).plainText,
        )
        assertEquals(
            "Yellow card on player 4.",
            ordinaryCardEvent.misconductConfirmationMessage(RuleGuidanceMode.BRIEF).plainText,
        )
        assertEquals(
            "Yellow card on player 4.",
            ordinaryCardEvent.resultGuidanceMessage(RuleGuidanceMode.BRIEF).plainText,
        )
        assertEquals(
            ordinaryCardResult,
            ordinaryCardResult.finalizedForGuidanceMode(RuleGuidanceMode.BRIEF),
        )

        // Resolving a non-threshold assessment directly violates the helper's strict contract.
        val invalidResolution = assertThrows(IllegalArgumentException::class.java) {
            ordinaryCardResult.withResolvedMisconductPenalty()
        }
        assertEquals(
            "A misconduct countdown requires an assessment that triggered the penalty.",
            invalidResolution.message,
        )

        // Full card guidance carries explicit emphasis metadata for the suspension consequence.
        val redEvent = standardLiveGameState()
            .assessRedCard(TeamId.TEAM_ONE, "4", 0L)
            .event
        val redGuidance = redEvent.resultGuidanceMessage(RuleGuidanceMode.FULL)
        assertEquals(
            listOf(false, true, false),
            redGuidance.lines.map { it.bold },
        )
        assertEquals(
            "Player 4 receives a game suspension.",
            redGuidance.lines.single { it.bold }.text,
        )

        // Full waits for the misconduct answer before starting its countdown; concise guidance
        // resolves the omitted choice in the JVM and leaves the countdown ready to start.
        val thresholdState = event.state.copy(
            teamOne = event.state.teamOne.copy(technicalFouls = 2),
        )
        val fullResult = thresholdState.assessTechnicalFoul(
            TeamId.TEAM_ONE,
            0L,
            RuleGuidanceMode.FULL,
        )
        assertEquals(false, fullResult.state.pendingMisconductCountdown)
        val briefResult = thresholdState.assessTechnicalFoul(
            TeamId.TEAM_ONE,
            0L,
            RuleGuidanceMode.BRIEF,
        )
        assertEquals(true, briefResult.state.pendingMisconductCountdown)

        // Blue-card thresholds use the same JVM finalization policy as technical fouls.
        var cardState = standardLiveGameState().beginLivePoint()
        cardState = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L).state
        cardState = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L).state
        val rawCardResult = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L)
        assertEquals(false, rawCardResult.state.pendingMisconductCountdown)
        assertEquals(
            true,
            rawCardResult.finalizedForGuidanceMode(RuleGuidanceMode.BRIEF)
                .state.pendingMisconductCountdown,
        )
    }
}

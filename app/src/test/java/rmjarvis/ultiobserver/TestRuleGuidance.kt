package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
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
        val blueEvent: GameEvent = state.previewBlueCard(TeamId.TEAM_ONE, state.startEpoch)
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
            GamePrompt.GameOver(state).requiresGuidanceInNone(),
        )
    }

    /**
     * Verify live-point misconduct guidance explains both possible restart situations directly.
     */
    @Test
    fun misconductGuidance() {
        // The third technical foul during a live point triggers the misconduct restart rules.
        val event = GameEvent.TechnicalFoulsChanged(
            state = standardLiveGameState().beginLivePoint(),
            team = TeamId.TEAM_ONE,
            technicalFoulTotal = 3,
        )
        // Full explains both cases in one message, with headings that remain visibly distinct.
        assertEquals(
            "This is Viscous Coupling's third technical foul.\n\n" +
            "If against offense:\n" +
            "Offense moves the disc to the reverse brick in the end zone they are defending. " +
            "Defense may instead leave the disc where it is, keeping the current stall count " +
            "+1 (maximum 9).\n\n" +
            "If against defense:\n" +
            "Offense may move the disc to the brick mark nearest the end zone they are " +
            "attacking. They may instead leave the disc where it is or center it.\n\n" +
            "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in.",
            event.guidanceMessage(RuleGuidanceMode.FULL).plainText,
        )
        assertEquals(
            listOf("If against offense:", "If against defense:"),
            event.guidanceMessage(RuleGuidanceMode.FULL).lines
                .filter { it.bold }
                .map { it.text },
        )

        // Concise modes retain the same two-case operational reminder.
        val briefReminder =
            "If offense: reverse brick\n" +
            "If defense: attacking brick or middle\n" +
            "Offense has 30 seconds to set."
        assertEquals(
            "Third technical foul on Viscous Coupling.\n\n$briefReminder",
            event.guidanceMessage(RuleGuidanceMode.BRIEF).plainText,
        )

        // An ordinary card does not append misconduct restart guidance.
        val ordinaryCardResult = standardLiveGameState()
            .assessYellowCard(TeamId.TEAM_ONE, "4", 0L)
        val ordinaryCardEvent = ordinaryCardResult.event
        assertEquals(
            ordinaryCardEvent.formatMessage().plainText,
            ordinaryCardEvent.guidanceMessage(RuleGuidanceMode.FULL).plainText,
        )
        assertEquals(
            "Yellow card on player 4.",
            ordinaryCardEvent.guidanceMessage(RuleGuidanceMode.BRIEF).plainText,
        )

        // Full card guidance carries explicit emphasis metadata for the suspension consequence.
        val redEvent = standardLiveGameState()
            .assessRedCard(TeamId.TEAM_ONE, "4", 0L)
            .event
        val redGuidance = redEvent.guidanceMessage(RuleGuidanceMode.FULL)
        assertEquals(
            listOf(false, true, false),
            redGuidance.lines.map { it.bold },
        )
        assertEquals(
            "Player 4 receives a game suspension.",
            redGuidance.lines.single { it.bold }.text,
        )

        // A threshold technical foul during a live point leaves its countdown ready to start.
        val thresholdState = event.state.copy(
            teamOne = event.state.teamOne.copy(technicalFouls = 2),
        )
        val technicalFoulResult = thresholdState.assessTechnicalFoul(TeamId.TEAM_ONE, 0L)
        assertEquals(true, technicalFoulResult.state.pendingMisconductCountdown)

        // Blue-card thresholds use the same domain behavior as technical fouls.
        var cardState = standardLiveGameState().beginLivePoint()
        cardState = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L).state
        cardState = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L).state
        val blueCardResult = cardState.assessBlueCard(TeamId.TEAM_ONE, 0L)
        assertEquals(true, blueCardResult.state.pendingMisconductCountdown)
    }
}

package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for pull infractions and pull time-violation model behavior.
class TestGamePull : GameDomainTestFixtures() {
    /// Test compact field-button labels for each pull-infraction type.
    @Test
    fun pullInfractionFieldActionLabels() {
        val team = TeamLiveState(
            name = "Viscous Coupling",
            color = TeamColorChoice.WHITE,
            offsides = 1,
            falseStarts = 2,
            majorityPullViolations = 3,
        )

        assertEquals("Offsides (1)", PullInfractionType.OFFSIDES.fieldActionLabel(team))
        assertEquals("False start (2)", PullInfractionType.FALSE_START.fieldActionLabel(team))
        assertEquals("Majority pull (3)", PullInfractionType.MAJORITY_PULL.fieldActionLabel(team))
    }

    /// Test direct invalid pull-infraction calls fail loudly instead of returning empty previews.
    @Test
    fun invalidPullInfractionSelectionsFailLoudly() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val state = standardLiveGameState()

        val invalidAssess = assertThrows(IllegalArgumentException::class.java) {
            state.assessPullInfraction(VC, 0L, PullInfractionType.FALSE_START)
        }
        assertEquals(
            "Pull infraction FALSE_START cannot be recorded for TEAM_ONE on this pull.",
            invalidAssess.message,
        )

        val invalidPreview = assertThrows(IllegalArgumentException::class.java) {
            state.previewPullInfraction(ANIMAL, PullInfractionType.OFFSIDES)
        }
        assertEquals(
            "Pull infraction OFFSIDES cannot be previewed for TEAM_TWO on this pull.",
            invalidPreview.message,
        )

        val disabledPreview = assertThrows(IllegalArgumentException::class.java) {
            state.assessPullInfraction(VC).state.previewPullInfraction(VC, PullInfractionType.OFFSIDES)
        }
        assertEquals(
            "Pull infraction cannot be previewed after the button is disabled for TEAM_ONE.",
            disabledPreview.message,
        )

        val disabledMajorityPullPreview = assertThrows(IllegalArgumentException::class.java) {
            state.previewPullInfraction(VC, PullInfractionType.MAJORITY_PULL)
        }
        assertEquals(
            "Pull infraction MAJORITY_PULL cannot be previewed for TEAM_ONE on this pull.",
            disabledMajorityPullPreview.message,
        )
    }

    /// Verify pull countdown display and cue selection for one-end, both-end, and neither-end prompts.
    @Test
    fun pullCountdownDisplayAndCueTargets() {
        assertEquals("Signal in" to Duration.ofSeconds(60), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 1_000L))
        assertEquals("Signal in" to Duration.ofSeconds(30), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 31_000L))
        assertEquals("Signal in" to Duration.ZERO, betweenPointsDisplay(FieldEnd.FAR, 1_000L, 70_000L))
        assertEquals("Pull in" to Duration.ofSeconds(80), betweenPointsDisplay(FieldEnd.NEAR, 2_000L, 2_000L))
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.FAR,
            ),
        )
        assertEquals(
            "Signal in" to Duration.ofSeconds(60),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.FAR,
            ),
        )
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.BOTH,
            ),
        )
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.NEITHER,
            ),
        )
        val standardPullCountdown = buildBetweenPointsCountdown(FieldEnd.NEAR, 2_000L)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, standardPullCountdown.nextTimingCue(2_000L)?.id)
        val standardPullTiming = standardPullCountdown.pullTiming!!
        assertEquals(60, standardPullTiming.offenseReadySeconds)
        assertEquals(80, standardPullTiming.pullSeconds)
        assertEquals(60, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.OFFENSE_READY))
        assertEquals(80, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.PULL))
        assertEquals(80, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.BOTH))
        assertEquals(80, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.NEITHER))
        assertEquals(
            10,
            standardPullTiming.remainingSecondsBeforeOffenseReady(10, BetweenPointsCountdownTarget.OFFENSE_READY),
        )
        assertThrows(IllegalStateException::class.java) {
            standardPullTiming.remainingSecondsBeforeOffenseReady(10, BetweenPointsCountdownTarget.NEITHER)
        }
        val bothCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.BOTH,
        )
        assertEquals(80, bothCountdown.durationSeconds)
        assertEquals(
            40,
            bothCountdown.pullTiming!!.remainingSecondsBeforeOffenseReady(20, BetweenPointsCountdownTarget.BOTH),
        )
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, bothCountdown.nextTimingCue(2_000L)?.id)
        assertEquals(Duration.ofSeconds(40), bothCountdown.nextTimingCue(2_000L)?.countdownTime)
        assertEquals(TimingCueId.RECEIVING_TEN_FOR_HAND, bothCountdown.nextTimingCue(43_000L)?.id)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, bothCountdown.nextTimingCue(62_000L)?.id)
        assertEquals("Give hand. 20 seconds to pull", bothCountdown.nextTimingCue(62_000L)?.message)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, bothCountdown.dueTimingCue(62_000L)?.id)
        assertEquals("Give hand. 20 seconds to pull", bothCountdown.dueTimingCue(62_000L)?.message)
        val neitherCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEITHER,
        )
        assertEquals(80, neitherCountdown.durationSeconds)
        assertNull(neitherCountdown.nextTimingCue(2_000L))
        assertNull(neitherCountdown.dueTimingCue(82_000L))
        assertEquals(60, BetweenPointsCountdownTarget.OFFENSE_READY.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(80, BetweenPointsCountdownTarget.PULL.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(80, BetweenPointsCountdownTarget.BOTH.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(80, BetweenPointsCountdownTarget.NEITHER.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(40, BetweenPointsCountdownTarget.NEITHER.baseDurationSeconds(CountdownKind.OPENING_PULL))
        assertEquals(30, BetweenPointsCountdownTarget.OFFENSE_READY.baseDurationSeconds(CountdownKind.PULL_RESET))
        assertEquals(30, BetweenPointsCountdownTarget.PULL.baseDurationSeconds(CountdownKind.PULL_RESET))
        assertEquals(BetweenPointsCountdownTarget.PULL, BetweenPointsCountdownTarget.OFFENSE_READY.flip())
        assertEquals(BetweenPointsCountdownTarget.OFFENSE_READY, BetweenPointsCountdownTarget.PULL.flip())
        assertEquals(BetweenPointsCountdownTarget.BOTH, BetweenPointsCountdownTarget.BOTH.flip())
        assertEquals(BetweenPointsCountdownTarget.NEITHER, BetweenPointsCountdownTarget.NEITHER.flip())
        assertEquals(emptyList<TimingCueId>(), BetweenPointsCountdownTarget.NEITHER.timeoutCueIds())

        val legacyCountdownWithoutTiming = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Signal in",
            durationSeconds = 60,
            targetEpoch = 62_000L,
            betweenPointsTarget = BetweenPointsCountdownTarget.OFFENSE_READY,
        )
        val retargetedLegacyCountdown = legacyCountdownWithoutTiming.withPullPromptTarget(
            pullingFromEnd = FieldEnd.NEAR,
            promptTarget = PullPromptTarget.NEAR,
        )
        assertEquals("Pull in", retargetedLegacyCountdown.label)
        assertEquals(80, retargetedLegacyCountdown.durationSeconds)
        assertEquals(82_000L, retargetedLegacyCountdown.targetEpoch)
        assertEquals(BetweenPointsCountdownTarget.PULL, retargetedLegacyCountdown.betweenPointsTarget)

        val legacyPullCountdown = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Pull in",
            durationSeconds = 80,
            targetEpoch = 81_000L,
            betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
        )
        val swappedLegacyPullCountdown = legacyPullCountdown.swapOD()
        assertEquals("Signal in", swappedLegacyPullCountdown.label)
        assertEquals(60, swappedLegacyPullCountdown.durationSeconds)
        assertEquals(61_000L, swappedLegacyPullCountdown.targetEpoch)
        assertEquals(BetweenPointsCountdownTarget.OFFENSE_READY, swappedLegacyPullCountdown.betweenPointsTarget)

        val legacyNeitherCountdownWithTimeoutExtension = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Pull in",
            durationSeconds = 140,
            targetEpoch = 142_000L,
            betweenPointsTarget = BetweenPointsCountdownTarget.NEITHER,
        )
        assertTrue(legacyNeitherCountdownWithTimeoutExtension.betweenPointsTimingCues().isEmpty())

        val openingReceiveCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
        )
        assertEquals(CountdownKind.OPENING_PULL, openingReceiveCountdown.kind)
        assertEquals(20, openingReceiveCountdown.durationSeconds)
        assertEquals(1_000L, openingReceiveCountdown.nextTimingCue(1_000L)?.targetEpoch)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, openingReceiveCountdown.dueTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.RECEIVING_TEN_FOR_HAND, openingReceiveCountdown.nextTimingCue(2_000L)?.id)
        assertNull(openingReceiveCountdown.dueTimingCue(999L))
        assertNull(openingReceiveCountdown.nextTimingCue(openingReceiveCountdown.targetEpoch + 1L))
        assertNull(openingReceiveCountdown.dueTimingCue(openingReceiveCountdown.targetEpoch + 1_101L))

        val openingPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
        )
        assertEquals(40, openingPullCountdown.durationSeconds)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, openingPullCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.remaining)
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.countdownTime)
        val farEndOpeningPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
            promptTarget = PullPromptTarget.FAR,
        )
        assertEquals("Pull in", farEndOpeningPullCountdown.label)
        assertEquals(40, farEndOpeningPullCountdown.durationSeconds)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, farEndOpeningPullCountdown.nextTimingCue(1_000L)?.id)
        val bothEndOpeningPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
            promptTarget = PullPromptTarget.BOTH,
        )
        assertEquals("Pull in", bothEndOpeningPullCountdown.label)
        assertEquals(40, bothEndOpeningPullCountdown.durationSeconds)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, bothEndOpeningPullCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, bothEndOpeningPullCountdown.nextTimingCue(21_000L)?.id)
        assertEquals("Give hand. 20 seconds to pull", bothEndOpeningPullCountdown.nextTimingCue(21_000L)?.message)

        val invalidBetweenPointsKindException = assertThrows(IllegalArgumentException::class.java) {
            buildBetweenPointsCountdown(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 1_000L,
                kind = CountdownKind.TIME_OUT,
            )
        }
        assertEquals(
            "Countdown kind TIME_OUT does not use between-points timing.",
            invalidBetweenPointsKindException.message,
        )
        assertTrue(CountdownKind.OPENING_PULL.usesBetweenPointsTarget())
        assertTrue(CountdownKind.BETWEEN_POINTS.usesBetweenPointsTarget())
        assertTrue(CountdownKind.PULL_RESET.usesBetweenPointsTarget())
        assertFalse(CountdownKind.MISCONDUCT_BETWEEN_POINTS.usesBetweenPointsTarget())
        assertFalse(CountdownKind.DEFENSE_CHECK.usesBetweenPointsTarget())
        assertFalse(CountdownKind.TIME_OUT.usesBetweenPointsTarget())
        assertFalse(CountdownKind.HALFTIME.usesBetweenPointsTarget())
    }

    /**
     * Test pull infractions from the observer-facing actions.
     * Offsides belongs to the pulling team; false start belongs to the receiving team.
     */
    @Test
    fun pullInfractions() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start from a pull sequence with Viscous Coupling pulling to Animal.
        var state = standardLiveGameState()
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(VC, state.pullingTeam)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)

        // Preview branches shape popup state without mutating the live pull sequence.
        var previewEvent = state.previewPullInfraction(VC, PullInfractionType.OFFSIDES)
            .event as GameEvent.PullInfractionRecorded
        assertEquals(1, previewEvent.state.teamOne.offsides)
        assertEquals(0, state.teamOne.offsides)
        previewEvent = state.previewPullInfraction(ANIMAL, PullInfractionType.FALSE_START)
            .event as GameEvent.PullInfractionRecorded
        assertEquals(1, previewEvent.state.teamTwo.falseStarts)
        assertEquals(0, state.teamTwo.falseStarts)

        // Record offsides and verify only the pulling team's offsides count increments.
        var pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)
        assertEquals("Offsides on Viscous Coupling.", state.lastEvent)
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Verify the first pull-violation message sends play to the brick mark.
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullInfractionResult.message(),
        )
        assertEquals("Offsides", pullInfractionResult.event!!.formatPopupTitle())
        val pullInfractionEvent = pullInfractionResult.event as GameEvent.PullInfractionRecorded
        assertEquals(state, pullInfractionEvent.state)
        assertEquals(VC, pullInfractionEvent.team)
        assertFalse(pullInfractionResult.event!!.needsMisconductChoice())

        // Verify the same pull sequence cannot record a second offsides for the same team.
        pullInfractionResult = state.assessPullInfraction(VC)
        assertEquals(state, pullInfractionResult.state)
        assertNull(pullInfractionResult.message())
        assertEquals(state, state.recordOffsides())

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        state = pullInfractionResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals(
            "This is Animal's first pull violation.\n\n" +
                "Viscous Coupling starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullInfractionResult.message(),
        )

        // In a fresh pull sequence, record false start and verify only the receiving team's count increments.
        state = standardLiveGameState()
        val animalPullingPreviewState = standardLiveGameState(pullingTeam = ANIMAL)
        previewEvent = animalPullingPreviewState.previewPullInfraction(ANIMAL, PullInfractionType.OFFSIDES)
            .event as GameEvent.PullInfractionRecorded
        assertEquals(1, previewEvent.state.teamTwo.offsides)
        previewEvent = animalPullingPreviewState.previewPullInfraction(VC, PullInfractionType.FALSE_START)
            .event as GameEvent.PullInfractionRecorded
        assertEquals(1, previewEvent.state.teamOne.falseStarts)
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        state = pullInfractionResult.state
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertNotNull(state.countdown)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("False start on Animal.", state.lastEvent)
        assertEquals("Undo False start on Animal", state.undoEntry?.label)

        // Verify false-start guidance says the defense gets to set up.
        assertEquals(
            "This is Animal's first pull violation.\n\n" +
                "Viscous Coupling gets to set up on defense.",
            pullInfractionResult.message(),
        )
        assertEquals("False start", pullInfractionResult.event!!.formatPopupTitle())
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.",
            state.assessPullInfraction(VC).message(),
        )

        // The same pull sequence cannot record a second false start.
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        assertEquals(state, pullInfractionResult.state)
        assertNull(pullInfractionResult.message())
        assertEquals(state, state.recordFalseStart())

        // If the receiving team is late, the first time violation is a warning and shortened pull reset.
        state = standardLiveGameState()
        val firstViolationMoment = state.countdown!!.targetEpoch
        assertFalse(state.hasExpiredPullActions())
        assertTrue(state.canAssessTimeViolation())
        assertEquals(state, state.restartPullCountdown(firstViolationMoment))
        assertFalse(
            state.copy(
                phase = GamePhase.LIVE_POINT,
                pullCountdownExpired = true,
            ).hasExpiredPullActions()
        )
        // Defensive only: stale UI should not assess a time violation after the game leaves pull timing.
        val wrongPhaseState = state.copy(phase = GamePhase.HALFTIME)
        assertFalse(wrongPhaseState.canAssessTimeViolation())
        assertEquals(wrongPhaseState, wrongPhaseState.assessTimeViolation(VC, firstViolationMoment).state)
        assertNull(wrongPhaseState.previewTimeViolation(VC).event)

        var warningPreview = state.previewTimeViolation(VC).event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.WARNING, warningPreview.outcome)
        assertEquals(1, warningPreview.state.teamOne.timeViolations)
        assertEquals(0, warningPreview.state.teamOne.timeoutsUsedThisHalf)
        warningPreview = state.previewTimeViolation(ANIMAL).event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.WARNING, warningPreview.outcome)
        assertEquals(1, warningPreview.state.teamTwo.timeViolations)
        assertEquals(0, warningPreview.state.teamTwo.timeoutsUsedThisHalf)

        var timeViolationResult = state.assessTimeViolation(ANIMAL, firstViolationMoment)
        val warningEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        var timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, warningEvent.team)
        assertEquals(TimeViolationOutcome.WARNING, warningEvent.outcome)
        assertEquals("Time violation", warningEvent.formatPopupTitle())
        val warningGameEvent: GameEvent = warningEvent
        assertEquals("Time violation", warningGameEvent.formatPopupTitle())
        assertEquals(1, timeViolationState.teamTwo.timeViolations)
        assertEquals(0, timeViolationState.teamOne.timeViolations)
        assertEquals(CountdownKind.PULL_RESET, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(20, timeViolationState.countdown?.durationSeconds)
        assertEquals(firstViolationMoment + 20_000L, timeViolationState.countdown?.targetEpoch)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timeViolationState.countdown?.nextTimingCue(firstViolationMoment)?.id)
        assertEquals(TimingCueId.RECEIVING_GIVE_HAND, timeViolationState.countdown?.nextTimingCue(firstViolationMoment + 20_000L)?.id)
        assertEquals(
            "This is Animal's first time violation.\n\n" +
                "The first time violation is a warning. Animal now has 20 seconds to signal readiness.",
            timeViolationResult.message(),
        )
        assertUndoRestores(state, timeViolationState)

        // The next offense time violation by that team charges a 70-second timeout clock.
        val secondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(ANIMAL, secondViolationMoment)
        val timeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, timeoutEvent.team)
        assertEquals(TimeViolationOutcome.TIMEOUT, timeoutEvent.outcome)
        assertEquals(1, timeViolationState.teamTwo.timeoutsUsedThisHalf)
        assertEquals(CountdownKind.BETWEEN_POINTS, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)
        assertEquals(secondViolationMoment + 70_000L, timeViolationState.countdown?.targetEpoch)
        assertEquals(
            "This is Animal's second time violation.\n\n" +
                "Animal is required to use one of their 2 remaining timeouts available for this half. " +
                "Reset pull timing to the usual timeout duration.",
            timeViolationResult.message(),
        )

        // A defense time violation warning also gets a 30-second reset, and the next timeout clock is 90 seconds.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        val defenseWarningEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        assertEquals(VC, defenseWarningEvent.team)
        assertEquals(TimeViolationOutcome.WARNING, defenseWarningEvent.outcome)
        assertEquals(CountdownKind.PULL_RESET, timeViolationState.countdown?.kind)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        val defenseSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(VC, defenseSecondViolationMoment)
        timeViolationState = timeViolationResult.state
        val defenseTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.TIMEOUT, defenseTimeoutEvent.outcome)
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(90, timeViolationState.countdown?.durationSeconds)
        assertEquals(defenseSecondViolationMoment + 90_000L, timeViolationState.countdown?.targetEpoch)

        // Far-end prompts apply the same warning and timeout reset rules to the far side.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.FAR).withPullPromptTarget(PullPromptTarget.FAR)
        assertEquals(FieldEnd.FAR, state.fieldEndForTeam(VC))
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        val farDefenseSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(VC, farDefenseSecondViolationMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(TimeViolationOutcome.TIMEOUT, (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(90, timeViolationState.countdown?.durationSeconds)

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR).withPullPromptTarget(PullPromptTarget.FAR)
        assertEquals(FieldEnd.FAR, state.fieldEndForTeam(ANIMAL))
        assertEquals("Signal in", state.countdown?.label)
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(20, timeViolationState.countdown?.durationSeconds)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timeViolationState.countdown?.nextTimingCue(state.countdown!!.targetEpoch)?.id)
        assertEquals(TimingCueId.RECEIVING_GIVE_HAND, timeViolationState.countdown?.nextTimingCue(state.countdown!!.targetEpoch + 20_000L)?.id)
        val farOffenseSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(ANIMAL, farOffenseSecondViolationMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(TimeViolationOutcome.TIMEOUT, (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR).withPullPromptTarget(PullPromptTarget.FAR)
        assertEquals(FieldEnd.NEAR, state.fieldEndForTeam(VC))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertNull(timeViolationState.countdown?.nextTimingCue(timeViolationState.countdown!!.targetEpoch - 20_000L))

        // A violation on either side starts that side's reset timer, even after an automatic live-point transition.
        state = standardLiveGameState()
        val farPullingWarningMoment = state.countdown!!.targetEpoch
        timeViolationResult = state.applyExpiredCountdownTransitions(farPullingWarningMoment, showDefenseCountdowns = false)
            .assessTimeViolation(VC, farPullingWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals(1, timeViolationState.teamOne.timeViolations)
        assertEquals(GamePhase.BETWEEN_POINTS, timeViolationState.phase)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertEquals(
            "This is Viscous Coupling's first time violation.\n\n" +
                "The first time violation is a warning. Viscous Coupling now has 30 seconds to pull.",
            timeViolationResult.message(),
        )

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        val farReceivingWarningMoment = state.countdown!!.targetEpoch
        timeViolationResult = state.applyExpiredCountdownTransitions(farReceivingWarningMoment, showDefenseCountdowns = false)
            .assessTimeViolation(ANIMAL, farReceivingWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals(1, timeViolationState.teamTwo.timeViolations)
        assertEquals(GamePhase.BETWEEN_POINTS, timeViolationState.phase)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(50, timeViolationState.countdown?.durationSeconds)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, timeViolationState.countdown?.nextTimingCue(farReceivingWarningMoment)?.id)
        assertEquals(
            "This is Animal's first time violation.\n\n" +
                "The first time violation is a warning. Animal now has 20 seconds to signal readiness.",
            timeViolationResult.message(),
        )

        // A forced timeout uses ordinary timeout-between-points timing even when the pulling team violated.
        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(TimeViolationOutcome.TIMEOUT, (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome)
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timeViolationState.countdown?.nextTimingCue(state.countdown!!.targetEpoch)?.id)

        val teamOneTimeoutPreview = state.previewTimeViolation(VC).event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.TIMEOUT, teamOneTimeoutPreview.outcome)
        assertEquals(2, teamOneTimeoutPreview.state.teamOne.timeViolations)
        assertEquals(1, teamOneTimeoutPreview.state.teamOne.timeoutsUsedThisHalf)

        val teamTwoTimeoutPreviewState = standardLiveGameState()
            .copy(teamTwo = standardLiveGameState().teamTwo.copy(timeViolations = 1))
        val teamTwoTimeoutPreview = teamTwoTimeoutPreviewState.previewTimeViolation(ANIMAL)
            .event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.TIMEOUT, teamTwoTimeoutPreview.outcome)
        assertEquals(2, teamTwoTimeoutPreview.state.teamTwo.timeViolations)
        assertEquals(1, teamTwoTimeoutPreview.state.teamTwo.timeoutsUsedThisHalf)

        state = standardLiveGameState(rules = GameRules(timeoutsPerHalf = 1))
        state = state.copy(teamOne = state.teamOne.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        assertEquals(
            "This is Viscous Coupling's second time violation.\n\n" +
                "Viscous Coupling is required to use their last remaining timeout for this half. " +
                "Reset pull timing to the usual timeout duration.",
            timeViolationResult.message(),
        )

        // Both-end prompts restart the full receiving-team reset, but only the pull-side reset for the pulling team.
        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH)
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(50, timeViolationState.countdown?.durationSeconds)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timeViolationState.countdown?.nextTimingCue(state.countdown!!.targetEpoch)?.id)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, timeViolationState.countdown?.nextTimingCue(timeViolationState.countdown!!.targetEpoch - 20_000L)?.id)

        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)

        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.NEITHER)
        assertNull(state.countdown?.nextTimingCue(state.countdown!!.targetEpoch - 20_000L))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertNull(timeViolationState.countdown?.nextTimingCue(timeViolationState.countdown!!.targetEpoch - 20_000L))

        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.NEITHER)
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(1, timeViolationState.teamTwo.timeViolations)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(50, timeViolationState.countdown?.durationSeconds)
        assertNull(timeViolationState.countdown?.nextTimingCue(timeViolationState.countdown!!.targetEpoch - 20_000L))

        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.NEITHER)
            .copy(teamTwo = standardLiveGameState().teamTwo.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(TimeViolationOutcome.TIMEOUT, (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(90, timeViolationState.countdown?.durationSeconds)
        assertNull(timeViolationState.countdown?.nextTimingCue(timeViolationState.countdown!!.targetEpoch - 20_000L))

        // If the receiving team has no timeout left after its warning, the point starts with no pull.
        val noTimeoutRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 0,
        )
        state = standardLiveGameState(rules = noTimeoutRules)
        state = state.copy(teamTwo = state.teamTwo.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        val receivingNoTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, receivingNoTimeoutEvent.team)
        assertEquals(TimeViolationOutcome.NO_TIMEOUT, receivingNoTimeoutEvent.outcome)
        assertEquals(GamePhase.BETWEEN_POINTS, timeViolationState.phase)
        assertNull(timeViolationState.countdown)
        assertTrue(timeViolationState.pullSkippedForCurrentPoint)
        assertFalse(timeViolationState.canAssessTimeViolation())
        assertEquals(timeViolationState, timeViolationState.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch).state)
        assertNull(timeViolationState.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch).event)
        assertNull(timeViolationState.previewTimeViolation(ANIMAL).event)
        assertEquals(timeViolationState, timeViolationState.recordFalseStart())
        assertEquals(timeViolationState, timeViolationState.recordOffsides())
        assertEquals(
                "This is Animal's second time violation.\n\n" +
                "Animal has no time outs remaining for this half, so a yardage penalty is assessed. " +
                "No pull. Animal starts at midpoint of their defending end zone.",
            timeViolationResult.message(),
        )

        // A pulling-team time violation with no timeout left sends the receiving team to midfield.
        state = standardLiveGameState(
            rules = noTimeoutRules,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.copy(teamOne = state.teamOne.copy(timeViolations = 1))
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        val pullingNoTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(VC, pullingNoTimeoutEvent.team)
        assertEquals(TimeViolationOutcome.NO_TIMEOUT, pullingNoTimeoutEvent.outcome)
        assertNull(timeViolationState.countdown)
        assertTrue(timeViolationState.pullSkippedForCurrentPoint)
        assertEquals(
                "This is Viscous Coupling's second time violation.\n\n" +
                "Viscous Coupling has no time outs remaining for this half, so a yardage penalty is assessed. " +
                "No pull. Animal starts at midfield.",
            timeViolationResult.message(),
        )

        // Restarting the expired pull countdown restores the ordinary countdown and clears the action surface.
        state = standardLiveGameState()
        val expiredPullDecisionState = state.expiredPullDecisionState()
        timeViolationState = expiredPullDecisionState.restartPullCountdown(state.countdown!!.targetEpoch)
        assertEquals(CountdownKind.BETWEEN_POINTS, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(60, timeViolationState.countdown?.durationSeconds)
        assertFalse(timeViolationState.hasExpiredPullActions())
        assertEquals("Undo Restart countdown", timeViolationState.undoEntry?.label)

        // Record offsides and false start on the same pull and verify both counts and both consequences apply.
        state = standardLiveGameState()
        val offsidesResult = state.assessPullInfraction(VC)
        state = offsidesResult.state
        val falseStartResult = state.assessPullInfraction(ANIMAL)
        state = falseStartResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            offsidesResult.message(),
        )
        assertEquals(
            "This is Animal's first pull violation.\n\n" +
                "Viscous Coupling gets to set up on defense.",
            falseStartResult.message(),
        )

        // Score the point and verify pull-sequence infraction locks reset for the next pull.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 5))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(VC, state.pullingTeam)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)
        assertEquals("Pull in", state.countdown?.label)

        // Build a later pull where Viscous Coupling already has a violation and verify the guidance changes to midfield.
        pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(2, state.teamOne.offsides)
        assertEquals(
            "This is Viscous Coupling's second pull violation.\n\n" +
                "Animal starts at midfield.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullInfractionResult.message(),
        )

        // A previous false start by Viscous Coupling also stacks with a later Viscous Coupling offsides.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.assessPullInfraction(VC).state
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 10))
        assertEquals(VC, state.pullingTeam)
        pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(
            "This is Viscous Coupling's second pull violation.\n\n" +
                "Animal starts at midfield.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullInfractionResult.message(),
        )

        // Manually adjust pull infractions and verify values are clamped and undo-backed.
        state = state.adjustPullInfractions(
            teamOneOffsides = -1,
            teamOneFalseStarts = 2,
            teamTwoOffsides = 3,
            teamTwoFalseStarts = -4,
        )
        assertEquals(0, state.teamOne.offsides)
        assertEquals(2, state.teamOne.falseStarts)
        assertEquals(3, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertEquals("Pull infractions adjusted.", state.lastEvent)
        assertEquals("Undo Pull infraction adjustment", state.undoEntry?.label)
    }
}

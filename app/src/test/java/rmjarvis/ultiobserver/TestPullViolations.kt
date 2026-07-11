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

/// Tests for pull violation and time violation model behavior.
class TestPullViolations : GameDomainTestFixtures() {
    /**
     * Test compact field-button labels for each pull-violation type.
     */
    @Test
    fun fieldActionLabels() {
        // Field labels include the current total pull-violation count for every type.
        val team = TeamState(
            name = "Viscous Coupling",
            color = TeamColorChoice.WHITE,
            offsides = 1,
            falseStarts = 2,
            majorityPullViolations = 3,
            timeViolations = 4,
        )

        assertEquals("Offsides (6)", PullViolationType.OFFSIDES.fieldActionLabel(team))
        assertEquals("False start (6)", PullViolationType.FALSE_START.fieldActionLabel(team))
        assertEquals("Majority pull (6)", PullViolationType.MAJORITY_PULL.fieldActionLabel(team))
        assertEquals("Time viol. (4)", team.timeViolationFieldActionLabel())

        // Zero-count field labels stay compact until the observer has recorded that event.
        val newTeam = team.copy(
            offsides = 0,
            falseStarts = 0,
            majorityPullViolations = 0,
            timeViolations = 0,
        )
        assertEquals("Offsides", PullViolationType.OFFSIDES.fieldActionLabel(newTeam))
        assertEquals("False start", PullViolationType.FALSE_START.fieldActionLabel(newTeam))
        assertEquals("Majority pull", PullViolationType.MAJORITY_PULL.fieldActionLabel(newTeam))
        assertEquals("Time viol.", newTeam.timeViolationFieldActionLabel())
    }

    /**
     * Test direct invalid pull-violation calls fail loudly instead of returning empty previews.
     */
    @Test
    fun invalidSelections() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val state = standardLiveGameState()

        // Direct model calls fail loudly when a team/type pairing is illegal for the current pull.
        val invalidAssess = assertThrows(IllegalArgumentException::class.java) {
            state.assessPullViolation(VC, 0L, PullViolationType.FALSE_START)
        }
        assertEquals(
            "Pull violation FALSE_START cannot be recorded for TEAM_ONE on this pull.",
            invalidAssess.message,
        )

        val invalidPreview = assertThrows(IllegalArgumentException::class.java) {
            state.previewPullViolation(ANIMAL, PullViolationType.OFFSIDES)
        }
        assertEquals(
            "Pull violation OFFSIDES cannot be previewed for TEAM_TWO on this pull.",
            invalidPreview.message,
        )

        val disabledPreview = assertThrows(IllegalArgumentException::class.java) {
            state.assessPullViolation(VC).state.previewPullViolation(
                VC,
                PullViolationType.OFFSIDES,
            )
        }
        assertEquals(
            "Pull violation cannot be previewed after the button is disabled for TEAM_ONE.",
            disabledPreview.message,
        )

        val disabledMajorityPullPreview = assertThrows(IllegalArgumentException::class.java) {
            state.previewPullViolation(VC, PullViolationType.MAJORITY_PULL)
        }
        assertEquals(
            "Pull violation MAJORITY_PULL cannot be previewed for TEAM_ONE on this pull.",
            disabledMajorityPullPreview.message,
        )
    }

    /**
     * Test countdown display labels and remaining time for pull-prompt targets.
     */
    @Test
    fun countdownDisplay() {
        // Standard between-points countdowns show either Signal or Pull based on which end
        // is pulling relative to the end we are getting prompts.
        assertEquals(
            "Signal in" to Duration.ofSeconds(60),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 1_000L,
                now = 1_000L,
                promptTarget = PullPromptTarget.NEAR,
                rules = GameRules(),
            ),
        )
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.NEAR,
                rules = GameRules(),
            ),
        )

        // When the prompt is for the "far" end, the labels and durations are reversed.
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.FAR,
                rules = GameRules(),
            ),
        )
        assertEquals(
            "Signal in" to Duration.ofSeconds(60),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.FAR,
                rules = GameRules(),
            ),
        )

        // Both-end and neither-end prompts use the full pull window for the visible countdown.
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.BOTH,
                rules = GameRules(),
            ),
        )
        assertEquals(
            "Pull in" to Duration.ofSeconds(80),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 2_000L,
                now = 2_000L,
                promptTarget = PullPromptTarget.NEITHER,
                rules = GameRules(),
            ),
        )

        // Remaining time counts down from the sequence start.
        assertEquals(
            "Signal in" to Duration.ofSeconds(30),
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 1_000L,
                now = 31_000L,
                promptTarget = PullPromptTarget.NEAR,
                rules = GameRules(),
            ),
        )

        // Once the countdown expires, the display clamps remaining time to zero.
        assertEquals(
            "Signal in" to Duration.ZERO,
            betweenPointsDisplay(
                pullingFromEnd = FieldEnd.FAR,
                sequenceStart = 1_000L,
                now = 70_000L,
                promptTarget = PullPromptTarget.NEAR,
                rules = GameRules(),
            ),
        )
    }

    /**
     * Test the internal timing model that converts pull prompts into target windows.
     */
    @Test
    fun promptTargetTimingModel() {
        // Standard timing keeps separate offense-ready and pull deadlines for one pull sequence.
        val standardPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(),
        )
        val standardPullTiming = standardPullCountdown.pullTiming!!
        assertEquals(60, standardPullTiming.offenseReadySeconds)
        assertEquals(80, standardPullTiming.pullSeconds)

        // Use a custom time between points.
        val customPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(timeBetweenPointsSeconds = 50),
        )
        assertEquals(50, customPullCountdown.pullTiming?.offenseReadySeconds)
        assertEquals(70, customPullCountdown.pullTiming?.pullSeconds)
        assertEquals(70, customPullCountdown.durationSeconds)

        // Youth defaults have an extra twenty seconds before offense readiness.
        val youthPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEAR,
            rules = usauDefaultGameRules("Youth"),
        )
        assertEquals(80, youthPullCountdown.pullTiming?.offenseReadySeconds)
        assertEquals(100, youthPullCountdown.pullTiming?.pullSeconds)
        assertEquals(100, youthPullCountdown.durationSeconds)

        // Prompt targets choose which deadline controls the countdown duration.
        assertEquals(
            60,
            standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.OFFENSE_READY),
        )
        assertEquals(80, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.PULL))
        assertEquals(80, standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.BOTH))
        assertEquals(
            80,
            standardPullTiming.durationSecondsFor(BetweenPointsCountdownTarget.NEITHER),
        )

        // Offense-ready timing only applies to targets that include receiving-team readiness cues.
        assertEquals(
            10,
            standardPullTiming.remainingSecondsBeforeOffenseReady(
                10,
                BetweenPointsCountdownTarget.OFFENSE_READY,
            ),
        )
        assertEquals(
            30,
            standardPullTiming.remainingSecondsBeforeOffenseReady(
                10,
                BetweenPointsCountdownTarget.BOTH,
            ),
        )
        assertThrows(IllegalStateException::class.java) {
            standardPullTiming.remainingSecondsBeforeOffenseReady(
                10,
                BetweenPointsCountdownTarget.PULL,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            standardPullTiming.remainingSecondsBeforeOffenseReady(
                10,
                BetweenPointsCountdownTarget.NEITHER,
            )
        }

        // Both- and neither-end prompt settings keep distinct internal targets instead of
        // collapsing to one end.
        val bothCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.BOTH,
            rules = GameRules(),
        )
        assertEquals(BetweenPointsCountdownTarget.BOTH, bothCountdown.betweenPointsTarget)
        assertEquals(80, bothCountdown.durationSeconds)

        val neitherCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEITHER,
            rules = GameRules(),
        )
        assertEquals(BetweenPointsCountdownTarget.NEITHER, neitherCountdown.betweenPointsTarget)
        assertEquals(80, neitherCountdown.durationSeconds)

        // Flipping pull orientation swaps one-sided prompt targets and preserves all-side modes.
        assertEquals(
            BetweenPointsCountdownTarget.PULL,
            BetweenPointsCountdownTarget.OFFENSE_READY.flip(),
        )
        assertEquals(
            BetweenPointsCountdownTarget.OFFENSE_READY,
            BetweenPointsCountdownTarget.PULL.flip(),
        )
        assertEquals(BetweenPointsCountdownTarget.BOTH, BetweenPointsCountdownTarget.BOTH.flip())
        assertEquals(
            BetweenPointsCountdownTarget.NEITHER,
            BetweenPointsCountdownTarget.NEITHER.flip(),
        )

        // The no-prompt target has no timeout-extension cues to schedule.
        assertEquals(emptyList<TimingCueId>(), BetweenPointsCountdownTarget.NEITHER.timeoutCueIds())
    }

    /**
     * Test timing-cue selection for standard, both-end, neither-end, and opening-pull countdowns.
     */
    @Test
    fun timingCues() {
        val standardSequenceStart = 2_000L
        val standardReadyTarget = 62_000L
        val standardReadyTwentyCue = standardReadyTarget - 20_000L
        val standardReadyTenCue = standardReadyTarget - 10_000L
        val standardPullTarget = 82_000L
        val standardPullTwentyCue = standardPullTarget - 20_000L
        val standardPullTenCue = standardPullTarget - 10_000L

        // Standard one-end offense-ready countdowns cue the prompted receiving side at
        // twenty seconds, ten seconds, and readiness.
        val nearReceivingCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = standardSequenceStart,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(),
        )
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            nearReceivingCountdown.nextTimingCue(standardSequenceStart)?.id,
        )
        assertEquals(
            Duration.ofSeconds(20),
            nearReceivingCountdown.nextTimingCue(standardSequenceStart)?.countdownTime,
        )
        assertEquals(
            TimingCueId.RECEIVING_TEN_FOR_HAND,
            nearReceivingCountdown.nextTimingCue(standardReadyTwentyCue + 1_000L)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_GIVE_HAND,
            nearReceivingCountdown.nextTimingCue(standardReadyTenCue + 1_000L)?.id,
        )

        // A cue becomes due at its cue epoch and remains due through the alert delivery window.
        assertNull(nearReceivingCountdown.dueTimingCue(standardReadyTwentyCue - 1L))
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            nearReceivingCountdown.dueTimingCue(standardReadyTwentyCue)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            nearReceivingCountdown.dueTimingCue(
                standardReadyTwentyCue + TIMING_ALERT_DUE_WINDOW_MS,
            )?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_GIVE_HAND,
            nearReceivingCountdown.dueTimingCue(standardReadyTarget)?.id,
        )

        // Same thing for the opposite field orientation.
        val farReceivingCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = standardSequenceStart,
            promptTarget = PullPromptTarget.FAR,
            rules = GameRules(),
        )
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            farReceivingCountdown.nextTimingCue(standardSequenceStart)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            farReceivingCountdown.dueTimingCue(standardReadyTwentyCue)?.id,
        )

        // Standard one-end pull countdowns cue the prompted pulling side at twenty seconds,
        // ten seconds, and the pull deadline.
        val nearPullingCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = standardSequenceStart,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(),
        )
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            nearPullingCountdown.nextTimingCue(standardSequenceStart)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TEN_TO_PULL,
            nearPullingCountdown.nextTimingCue(standardPullTwentyCue + 1_000L)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TIME_VIOLATION,
            nearPullingCountdown.nextTimingCue(standardPullTenCue + 1_000L)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            nearPullingCountdown.dueTimingCue(standardPullTwentyCue)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TIME_VIOLATION,
            nearPullingCountdown.dueTimingCue(standardPullTarget)?.id,
        )

        // Same thing for the opposite field orientation.
        val farPullingCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = standardSequenceStart,
            promptTarget = PullPromptTarget.FAR,
            rules = GameRules(),
        )
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            farPullingCountdown.nextTimingCue(standardSequenceStart)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            farPullingCountdown.dueTimingCue(standardPullTwentyCue)?.id,
        )

        // Both-end prompts cue receiving-team readiness first, then merge the give-hand cue with
        // the twenty-seconds-to-pull cue when those instants match.
        val bothCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.BOTH,
            rules = GameRules(),
        )
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            bothCountdown.nextTimingCue(2_000L)?.id,
        )
        assertEquals(Duration.ofSeconds(40), bothCountdown.nextTimingCue(2_000L)?.countdownTime)
        assertEquals(TimingCueId.RECEIVING_TEN_FOR_HAND, bothCountdown.nextTimingCue(43_000L)?.id)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, bothCountdown.nextTimingCue(62_000L)?.id)
        assertEquals(
            "Give hand. 20 seconds to pull",
            bothCountdown.nextTimingCue(62_000L)?.message,
        )

        // Neither-end prompts keep countdown timing but suppress timing cues.
        val neitherCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 2_000L,
            promptTarget = PullPromptTarget.NEITHER,
            rules = GameRules(),
        )
        assertNull(neitherCountdown.nextTimingCue(2_000L))
        assertNull(neitherCountdown.dueTimingCue(82_000L))

        // Opening-pull receiving cues use the abbreviated twenty-second readiness window, so the
        // first cue is due immediately.
        val openingReceiveCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(),
        )
        assertEquals(1_000L, openingReceiveCountdown.nextTimingCue(1_000L)?.targetEpoch)
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            openingReceiveCountdown.dueTimingCue(1_000L)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_TEN_FOR_HAND,
            openingReceiveCountdown.nextTimingCue(2_000L)?.id,
        )
        assertNull(openingReceiveCountdown.dueTimingCue(999L))
        assertNull(openingReceiveCountdown.nextTimingCue(openingReceiveCountdown.targetEpoch + 1L))
        assertNull(
            openingReceiveCountdown.dueTimingCue(openingReceiveCountdown.targetEpoch + 1_101L)
        )

        // Opening-pull pulling cues use the abbreviated forty-second pull window.
        val openingPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
            promptTarget = PullPromptTarget.NEAR,
            rules = GameRules(),
        )
        assertEquals(20, openingPullCountdown.pullTiming?.offenseReadySeconds)
        assertEquals(40, openingPullCountdown.pullTiming?.pullSeconds)
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            openingPullCountdown.nextTimingCue(1_000L)?.id,
        )
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.remaining)
        assertEquals(
            Duration.ofSeconds(20),
            openingPullCountdown.nextTimingCue(1_000L)?.countdownTime,
        )

        // The pull-countdown builder rejects countdown kinds from unrelated workflows.
        val invalidBetweenPointsKindException = assertThrows(IllegalArgumentException::class.java) {
            buildBetweenPointsCountdown(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 1_000L,
                kind = CountdownKind.TIME_OUT,
                promptTarget = PullPromptTarget.NEAR,
                rules = GameRules(),
            )
        }
        assertEquals(
            "Countdown kind TIME_OUT does not use between-points timing.",
            invalidBetweenPointsKindException.message,
        )
    }

    /**
     * Test the basic actions around offsides and false start.
     */
    @Test
    fun pullViolationBasics() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start from a pull sequence with Viscous Coupling pulling to Animal.
        var state = standardLiveGameState()
        assertEquals(GamePhase.PRE_GAME, state.phase)
        assertEquals(VC, state.pullingTeam)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)

        // Before recording a pull violation, it gets previewed, so the user can
        // apply it or cancel it still.  The preview does not change any state yet.
        var previewEvent = state.previewPullViolation(VC, PullViolationType.OFFSIDES).event
        assertEquals(1, previewEvent.state.teamOne.offsides)
        assertEquals(0, state.teamOne.offsides)
        previewEvent = state.previewPullViolation(ANIMAL, PullViolationType.FALSE_START).event
        assertEquals(1, previewEvent.state.teamTwo.falseStarts)
        assertEquals(0, state.teamTwo.falseStarts)

        // Record offsides and verify only the pulling team's offsides count increments.
        var pullViolationResult = state.assessPullViolation(VC)
        state = pullViolationResult.state
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

        // The first offsides message sends play to the brick mark.
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullViolationResult.message(),
        )
        assertEquals("Offsides", pullViolationResult.event!!.formatPopupTitle())
        val pullViolationEvent = pullViolationResult.event as GameEvent.PullViolationRecorded
        assertEquals(state, pullViolationEvent.state)
        assertEquals(VC, pullViolationEvent.team)
        assertFalse(pullViolationResult.event!!.needsMisconductChoice())

        // The same pull sequence cannot record a second offsides for the same team.
        pullViolationResult = state.assessPullViolation(VC)
        assertEquals(state, pullViolationResult.state)
        assertNull(pullViolationResult.message())
        assertEquals(state, state.recordOffsides())

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        pullViolationResult = state.assessPullViolation(ANIMAL)
        state = pullViolationResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals(
            "This is Animal's first pull violation.\n\n" +
                "Viscous Coupling starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullViolationResult.message(),
        )

        // In a fresh pull sequence, record false start and verify only the receiving team's count
        // increments.
        state = standardLiveGameState()
        val animalPullingPreviewState = standardLiveGameState(pullingTeam = ANIMAL)
        previewEvent = animalPullingPreviewState.previewPullViolation(
            ANIMAL,
            PullViolationType.OFFSIDES,
        )
            .event
        assertEquals(1, previewEvent.state.teamTwo.offsides)
        previewEvent = animalPullingPreviewState.previewPullViolation(
            VC,
            PullViolationType.FALSE_START,
        )
            .event
        assertEquals(1, previewEvent.state.teamOne.falseStarts)
        pullViolationResult = state.assessPullViolation(ANIMAL)
        state = pullViolationResult.state
        assertEquals(GamePhase.PRE_GAME, state.phase)
        assertNotNull(state.countdown)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("False start on Animal.", state.lastEvent)
        assertEquals("Undo False start on Animal", state.undoEntry?.label)

        // For a false start, the defense gets to set up.
        assertEquals(
            "This is Animal's first pull violation.\n\n" +
                "Viscous Coupling gets to set up on defense.",
            pullViolationResult.message(),
        )
        assertEquals("False start", pullViolationResult.event!!.formatPopupTitle())
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.",
            state.assessPullViolation(VC).message(),
        )

        // The same pull sequence cannot record a second false start.
        pullViolationResult = state.assessPullViolation(ANIMAL)
        assertEquals(state, pullViolationResult.state)
        assertNull(pullViolationResult.message())
        assertEquals(state, state.recordFalseStart())
    }

    /**
     * Test how pull violations stack across a pull sequence and later points.
     */
    @Test
    fun pullViolationSequences() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // If offsides and false start both occur on the same pull, both consequences apply.
        var state = standardLiveGameState()
        val offsidesResult = state.assessPullViolation(VC)
        state = offsidesResult.state
        val falseStartResult = state.assessPullViolation(ANIMAL)
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

        // Score the point and verify pull-sequence violation locks reset for the next pull.
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

        // Build a later pull where Viscous Coupling already has a violation and verify the
        // guidance changes to midfield.
        var pullViolationResult = state.assessPullViolation(VC)
        state = pullViolationResult.state
        assertEquals(2, state.teamOne.offsides)
        assertEquals(
            "This is Viscous Coupling's second pull violation.\n\n" +
                "Animal starts at midfield.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullViolationResult.message(),
        )

        // A previous false start by Viscous Coupling also stacks with a later Viscous Coupling
        // offsides.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.assessPullViolation(VC).state
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 10))
        assertEquals(VC, state.pullingTeam)
        pullViolationResult = state.assessPullViolation(VC)
        state = pullViolationResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(
            "This is Viscous Coupling's second pull violation.\n\n" +
                "Animal starts at midfield.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullViolationResult.message(),
        )

        // Later false starts still have the same consequence that the defense sets up.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.assessPullViolation(VC).state
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(12, 15))
        assertEquals(ANIMAL, state.pullingTeam)
        pullViolationResult = state.assessPullViolation(VC)
        state = pullViolationResult.state
        assertEquals(0, state.teamOne.offsides)
        assertEquals(2, state.teamOne.falseStarts)
        assertEquals(
            "This is Viscous Coupling's second pull violation.\n\n" +
                "Animal gets to set up on defense.",
            pullViolationResult.message(),
        )
    }

    /**
     * Test manual pull-violation adjustments.
     */
    @Test
    fun pullViolationAdjustments() {
        // Manually adjust pull violations and verify values are clamped and undo-backed.
        val state = standardLiveGameState().adjustPullViolations(
            teamOneOffsides = -1,
            teamOneFalseStarts = 2,
            teamOneMajorityPulls = 0,
            teamOneTimeViolations = 0,
            teamTwoOffsides = 3,
            teamTwoFalseStarts = -4,
            teamTwoMajorityPulls = 0,
            teamTwoTimeViolations = 0,
            now = 0L,
        )
        assertEquals(0, state.teamOne.offsides)
        assertEquals(2, state.teamOne.falseStarts)
        assertEquals(3, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertEquals("Pull violations adjusted.", state.lastEvent)
        assertEquals("Undo Pull violation adjustment", state.undoEntry?.label)

        // The More actions correction also adjusts time violations alongside the strict pull
        // violations.
        val baseState = standardLiveGameState(startTime = LocalTime.of(12, 0))
        val seededState = baseState.copy(
            teamOne = baseState.teamOne.copy(timeViolations = 1),
            teamTwo = baseState.teamTwo.copy(timeViolations = 2),
        )
        val pullAndTimeState = seededState.adjustPullViolations(
            teamOneOffsides = -1,
            teamOneFalseStarts = 2,
            teamOneMajorityPulls = 0,
            teamOneTimeViolations = 3,
            teamTwoOffsides = 3,
            teamTwoFalseStarts = -4,
            teamTwoMajorityPulls = 0,
            teamTwoTimeViolations = 0,
            now = timestampAt(seededState, LocalTime.of(12, 20)),
        )
        assertEquals(0, pullAndTimeState.teamOne.offsides)
        assertEquals(2, pullAndTimeState.teamOne.falseStarts)
        assertEquals(3, pullAndTimeState.teamOne.timeViolations)
        assertEquals(3, pullAndTimeState.teamTwo.offsides)
        assertEquals(0, pullAndTimeState.teamTwo.falseStarts)
        assertEquals(0, pullAndTimeState.teamTwo.timeViolations)
        assertEquals("Pull violations adjusted.", pullAndTimeState.lastEvent)
        assertEquals("Undo Pull violation adjustment", pullAndTimeState.undoEntry?.label)
        assertTrue(
            pullAndTimeState.formatEventLogLines().contains(
                "12:20  Adjusted Viscous Coupling time violations +2"
            )
        )
        assertTrue(
            pullAndTimeState.formatEventLogLines().contains(
                "12:20  Adjusted Animal time violations -2"
            )
        )
    }

    /**
     * Test first pull time violations, which result in a warning and a short new countdown.
     */
    @Test
    fun timeViolationWarnings() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // If the receiving team is late, the first time violation is a warning and shortened
        // pull reset.
        var state = standardLiveGameState()
        val firstViolationMoment = state.countdown!!.targetEpoch
        assertFalse(state.hasExpiredPullActions(firstViolationMoment - 1L))
        assertTrue(state.canAssessTimeViolation())
        assertEquals(state, state.restartPullCountdown(firstViolationMoment - 1L))
        assertFalse(
            state.copy(
                phase = GamePhase.LIVE_POINT,
            ).hasExpiredPullActions(firstViolationMoment)
        )

        // If the game phase is not between points, then a time violation is not possible.
        // We defensively allow it in case the UI gets into a weird state, but it doesn't
        // assess a time violation.
        val wrongPhaseState = state.copy(phase = GamePhase.HALFTIME)
        assertFalse(wrongPhaseState.canAssessTimeViolation())
        assertEquals(
            wrongPhaseState,
            wrongPhaseState.assessTimeViolation(VC, firstViolationMoment).state,
        )
        val wrongPhasePreviewException = assertThrows(IllegalArgumentException::class.java) {
            wrongPhaseState.previewTimeViolation(VC)
        }
        assertEquals(
            "Time violation cannot be previewed when the button is disabled.",
            wrongPhasePreviewException.message,
        )

        // When clicking Time Violation, we start with a preview so the user can still choose
        // whether to apply the violation or cancel.  The state doesn't change yet.
        var warningPreview = state.previewTimeViolation(VC).event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.WARNING, warningPreview.outcome)
        assertEquals(1, warningPreview.state.teamOne.timeViolations)
        assertEquals(0, warningPreview.state.teamOne.timeoutsUsedThisHalf)
        warningPreview = state.previewTimeViolation(ANIMAL).event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.WARNING, warningPreview.outcome)
        assertEquals(1, warningPreview.state.teamTwo.timeViolations)
        assertEquals(0, warningPreview.state.teamTwo.timeoutsUsedThisHalf)

        // Recording the receiving-team warning starts the shortened offense-ready reset.
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
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            timeViolationState.countdown?.nextTimingCue(firstViolationMoment)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_GIVE_HAND,
            timeViolationState.countdown?.nextTimingCue(firstViolationMoment + 20_000L)?.id,
        )
        assertEquals(
            "This is Animal's first time violation.\n\n" +
                "The first time violation is a warning. Animal now has 20 seconds to " +
                "signal readiness.",
            timeViolationResult.message(),
        )
        assertUndoRestores(state, timeViolationState)

        // If the opening pull has already started the first live point, the reset still belongs
        // to the pre-game opening-pull sequence.
        val liveFirstPointState = state.beginLivePoint()
        val liveFirstPointResult = liveFirstPointState.assessTimeViolation(
            ANIMAL,
            firstViolationMoment,
        )
        val liveFirstPointWarningEvent =
            liveFirstPointResult.event as GameEvent.TimeViolationRecorded
        val liveFirstPointWarningState = liveFirstPointResult.state
        assertEquals(TimeViolationOutcome.WARNING, liveFirstPointWarningEvent.outcome)
        assertEquals(GamePhase.PRE_GAME, liveFirstPointWarningState.phase)
        assertEquals(CountdownKind.PULL_RESET, liveFirstPointWarningState.countdown?.kind)

        // Defensive fallback for restored/archived states that no longer have the start-point
        // undo entry: a live-point time violation reset returns to the ordinary between-points
        // phase rather than failing.
        val strippedLiveFirstPointState = liveFirstPointState.copy(undoEntry = null)
        val strippedLiveFirstPointResult = strippedLiveFirstPointState.assessTimeViolation(
            ANIMAL,
            firstViolationMoment,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, strippedLiveFirstPointResult.state.phase)
        assertEquals(CountdownKind.PULL_RESET, strippedLiveFirstPointResult.state.countdown?.kind)

        // Defensive fallback for stale live-point states whose undo entry is unrelated to the
        // pull-start transition: the reset still lands in a between-points phase.
        val unrelatedUndoLivePointState = liveFirstPointState.withUndo(
            liveFirstPointState,
            "Undo Unrelated live-point action",
        )
        val unrelatedUndoWarningResult = unrelatedUndoLivePointState.assessTimeViolation(
            ANIMAL,
            firstViolationMoment,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, unrelatedUndoWarningResult.state.phase)
        assertEquals(CountdownKind.PULL_RESET, unrelatedUndoWarningResult.state.countdown?.kind)

        // When the warning reset expires, it starts the live point like a normal pull countdown.
        val liveAfterWarningReset = timeViolationState.applyExpiredCountdownTransitions(
            firstViolationMoment + 20_000L,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, liveAfterWarningReset.phase)
        assertNull(liveAfterWarningReset.countdown)
        assertEquals("Point is live.", liveAfterWarningReset.lastEvent)

        // If this phone is prompting the pulling team, the same receiving-team warning shows the
        // full warning countdown to the pull instead of only the 20-second readiness window.
        // Note that the observer handbook says the defense has 30 seconds after the offense
        // is ready, rather than the usual 20.  So we do that here.  Weird.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        val pullingPromptWarningMoment = state.countdown!!.targetEpoch
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.assessTimeViolation(ANIMAL, pullingPromptWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals(CountdownKind.PULL_RESET, timeViolationState.countdown?.kind)
        assertEquals(
            BetweenPointsCountdownTarget.PULL,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(50, timeViolationState.countdown?.durationSeconds)

        // With both-end prompts, the same warning keeps both cue streams.  The handbook gives the
        // defense 30 seconds after the offense is ready here, so give-hand and pull cues are
        // separate instead of merged like the standard between-points timing.
        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH)
        val bothEndWarningMoment = state.countdown!!.targetEpoch
        timeViolationResult = state.assessTimeViolation(ANIMAL, bothEndWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(
            BetweenPointsCountdownTarget.BOTH,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals(50, timeViolationState.countdown?.durationSeconds)
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            timeViolationState.countdown?.nextTimingCue(bothEndWarningMoment)?.id,
        )
        assertEquals(
            TimingCueId.RECEIVING_GIVE_HAND,
            timeViolationState.countdown?.nextTimingCue(bothEndWarningMoment + 20_000L)?.id,
        )
        assertEquals(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            timeViolationState.countdown?.nextTimingCue(bothEndWarningMoment + 30_000L)?.id,
        )

        // If this phone is prompting the receiving end, a pulling-team warning still shows the
        // reset countdown but does not schedule pull cues.
        state = standardLiveGameState()
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(
            BetweenPointsCountdownTarget.NEITHER,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertNull(
            timeViolationState.countdown?.nextTimingCue(
                timeViolationState.countdown!!.targetEpoch - 20_000L,
            )
        )
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
            .withPullPromptTarget(PullPromptTarget.FAR)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(
            BetweenPointsCountdownTarget.NEITHER,
            timeViolationState.countdown?.betweenPointsTarget,
        )

        // A pulling-team time violation warning gets a 30-second reset before the pull when this
        // phone is prompting the pulling end.
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
        assertEquals(
            "This is Viscous Coupling's first time violation.\n\n" +
                "The first time violation is a warning. Viscous Coupling now has " +
                "30 seconds to pull.",
            timeViolationResult.message(),
        )

        // The same pulling-team reset applies to far-end and both-end prompt settings when they
        // include the pulling end.
        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.FAR)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(
            BetweenPointsCountdownTarget.PULL,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals(30, timeViolationState.countdown?.durationSeconds)

        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(
            BetweenPointsCountdownTarget.PULL,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals(30, timeViolationState.countdown?.durationSeconds)

        // If this phone is not giving pull prompts, the reset timer is still visible but does not
        // schedule timing-cue sounds.
        state = standardLiveGameState().withPullPromptTarget(PullPromptTarget.NEITHER)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertEquals(
            BetweenPointsCountdownTarget.NEITHER,
            timeViolationState.countdown?.betweenPointsTarget,
        )
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertNull(
            timeViolationState.countdown?.nextTimingCue(
                timeViolationState.countdown!!.targetEpoch - 20_000L,
            )
        )
    }

    /**
     * Test second and later time violations when the violating team has a timeout available.
     */
    @Test
    fun timeViolationTimeouts() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // After the first time, subsequent offense time violations trigger an automatic timeout
        // call if the team has one.
        var state = standardLiveGameState()
        var timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        var timeViolationState = timeViolationResult.state
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
                "Animal is required to use one of their 2 remaining timeouts available for " +
                "this half. " +
                "Reset pull timing to the usual timeout duration.",
            timeViolationResult.message(),
        )

        // Custom timeout duration is used for automatic time-violation timeout resets.
        state = standardLiveGameState(rules = GameRules(timeoutSeconds = 50))
        timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        val customSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(
            ANIMAL,
            customSecondViolationMoment,
        )
        timeViolationState = timeViolationResult.state
        assertEquals(CountdownKind.BETWEEN_POINTS, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(50, timeViolationState.countdown?.durationSeconds)
        assertEquals(
            customSecondViolationMoment + 50_000L,
            timeViolationState.countdown?.targetEpoch,
        )

        // If prompting the defense, the reset countdown has the pull in 90 seconds.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        val defenseSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.assessTimeViolation(
            VC,
            defenseSecondViolationMoment,
        )
        timeViolationState = timeViolationResult.state
        assertEquals(
            TimeViolationOutcome.TIMEOUT,
            (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome,
        )
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(90, timeViolationState.countdown?.durationSeconds)
        assertEquals(
            defenseSecondViolationMoment + 90_000L,
            timeViolationState.countdown?.targetEpoch,
        )

        // A second-violation state uses ordinary timeout-between-points timing even when
        // the pulling team violated.
        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(
            TimeViolationOutcome.TIMEOUT,
            (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome,
        )
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)
        assertEquals(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            timeViolationState.countdown?.nextTimingCue(state.countdown!!.targetEpoch)?.id,
        )

        // Timeout previews charge the team that would receive the violation without mutating the
        // current state.
        val teamOneTimeoutPreview =
            state.previewTimeViolation(VC).event as GameEvent.TimeViolationRecorded
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

        // The timeout message changes slightly when the violation uses the team's last timeout.
        state = standardLiveGameState(rules = GameRules(timeoutsPerHalf = 1))
        state = state.copy(teamOne = state.teamOne.copy(timeViolations = 1))
        timeViolationResult = state.assessTimeViolation(VC, state.countdown!!.targetEpoch)
        assertEquals(
            "This is Viscous Coupling's second time violation.\n\n" +
                "Viscous Coupling is required to use their last remaining timeout for this half. " +
                "Reset pull timing to the usual timeout duration.",
            timeViolationResult.message(),
        )
    }

    /**
     * Test second and later time violations when the violating team does not have any
     * timeouts avaiable.  There are yardage penalties in this case.
     */
    @Test
    fun timeViolationNoTimeoutPenalties() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // If the receiving team has no timeout left after its warning, the point starts with no
        // pull. The receiving team starts in the middle of their defending end zone.
        val noTimeoutRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 0,
        )
        var state = standardLiveGameState(rules = noTimeoutRules)
        state = state.copy(teamTwo = state.teamTwo.copy(timeViolations = 1))
        var timeViolationResult = state.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        val receivingNoTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        var timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, receivingNoTimeoutEvent.team)
        assertEquals(TimeViolationOutcome.NO_TIMEOUT, receivingNoTimeoutEvent.outcome)
        assertEquals(GamePhase.PRE_GAME, timeViolationState.phase)
        assertNull(timeViolationState.countdown)
        assertTrue(timeViolationState.pullSkippedForCurrentPoint)
        assertFalse(timeViolationState.canAssessTimeViolation())
        assertEquals(
            timeViolationState,
            timeViolationState.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch).state,
        )
        assertNull(
            timeViolationState.assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch).event
        )
        val unavailablePreviewException = assertThrows(IllegalArgumentException::class.java) {
            timeViolationState.previewTimeViolation(ANIMAL)
        }
        assertEquals(
            "Time violation cannot be previewed when the button is disabled.",
            unavailablePreviewException.message,
        )
        assertEquals(timeViolationState, timeViolationState.recordFalseStart())
        assertEquals(timeViolationState, timeViolationState.recordOffsides())
        assertEquals(
            "This is Animal's second time violation.\n\n" +
                "Animal has no time outs remaining for this half, so a yardage penalty is " +
                "assessed. " +
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
                "Viscous Coupling has no time outs remaining for this half, so a yardage " +
                "penalty is assessed. " +
                "No pull. Animal starts at midfield.",
            timeViolationResult.message(),
        )
    }

    /**
     * Test restarting the countdown from the expired-pull decision state.
     */
    @Test
    fun expiredPullRestart() {
        // Restarting the expired opening-pull countdown restores opening-pull timing and clears
        // the action surface.
        val state = standardLiveGameState()
        val expiredPullDecisionState = state.expiredPullDecisionState()
        val timeViolationState = expiredPullDecisionState.restartPullCountdown(
            state.countdown!!.targetEpoch
        )
        assertEquals(CountdownKind.OPENING_PULL, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(20, timeViolationState.countdown?.durationSeconds)
        assertFalse(timeViolationState.hasExpiredPullActions(state.countdown!!.targetEpoch))
        assertEquals("Undo Restart countdown", timeViolationState.undoEntry?.label)

        // An active pull countdown that reaches its target is also treated as expired for
        // restart purposes; the UI should not leave it sitting at 0:00.
        val activeExpiredRestart = state.restartPullCountdown(state.countdown!!.targetEpoch)
        assertEquals(CountdownKind.OPENING_PULL, activeExpiredRestart.countdown?.kind)
        assertEquals("Undo Restart countdown", activeExpiredRestart.undoEntry?.label)

        // Restarting an expired countdown after a scored point uses the normal between-points
        // timing rather than the shorter opening-pull timing.
        val betweenPointsState = recordGoalFromCurrentStateAt(
            state.beginLivePoint(),
            TeamId.TEAM_TWO,
            LocalTime.of(11, 5),
        )
        val betweenPointsRestart = betweenPointsState.expiredPullDecisionState()
            .restartPullCountdown(betweenPointsState.countdown!!.targetEpoch)
        assertEquals(CountdownKind.BETWEEN_POINTS, betweenPointsRestart.countdown?.kind)
        assertEquals("Signal in", betweenPointsRestart.countdown?.label)
        assertEquals(60, betweenPointsRestart.countdown?.durationSeconds)
    }
}

package rmjarvis.ultiobserver

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val BACKUP_CAP_BYTES = 25L * 1024L * 1024L

/// Serialize representative app-private backup files and print their byte sizes.
fun main() {
    val setup = backupEstimateSetup()
    val game = buildHighActivityFullGame(setup)
    val archivedGame = game.pruneUndoHistory()

    val reportDir = File("app/build/reports/backup-size-estimate")
    reportDir.mkdirs()

    val samples = listOf(
        BackupSample("current_game_state.json", encodeCurrentGame(game)),
        BackupSample("profile.json", encodeProfile(Profile(name = "Casey Observer"))),
        BackupSample("settings.json", encodeSettings(Settings())),
        BackupSample("archived_games/00000.json", encodeArchivedGame(archivedGame)),
    )

    samples.forEach { sample ->
        val file = File(reportDir, sample.path)
        file.parentFile!!.mkdirs()
        file.writeText(sample.json)
    }

    val totalBytes = samples.sumOf { it.bytes }

    println("UltiObserver backup size estimate")
    println("Scenario: 15-14 game, 20 card actions, 10 technical fouls, 5 offsides per team, 2 time violations")
    println("Output: ${reportDir.path}")
    println()
    samples.forEach { sample ->
        println("${sample.path.padEnd(30)} ${sample.bytes.toByteSizeString()}")
    }
    println("-".repeat(45))
    println("${"Total".padEnd(30)} ${totalBytes.toByteSizeString()} (${formatPercentOfCap(totalBytes)} of 25 MiB cap)")
    println()
    println("Final score: ${game.teamOne.score}-${game.teamTwo.score}")
    println("Event log entries: ${game.eventLog.size}")
    println("Current-game undo depth: ${game.undoDepth()}")
    println("Archived-game undo depth: ${archivedGame.undoDepth()}")
}

private data class BackupSample(
    val path: String,
    val json: String,
) {
    val bytes: Long = json.toByteArray(Charsets.UTF_8).size.toLong()
}

private fun backupEstimateSetup(): GameState {
    return newSetupGameState(
        now = epochTimestamp(
            LocalDate.of(2026, 6, 1),
            LocalTime.of(10, 0),
            ZoneId.systemDefault(),
        ),
    ).copy(
        startDate = LocalDate.of(2026, 6, 1),
        startTime = LocalTime.of(10, 0),
        timeZone = ZoneId.of("America/New_York"),
        tournamentName = "Backup Estimate Invitational",
        rules = GameRules(
            gameTo = 15,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 2,
        ),
        teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE),
        teamTwo = TeamState("Animal", TeamColorChoice.RED),
        pullingTeam = TeamId.TEAM_ONE,
        pullingFromEnd = FieldEnd.FAR,
        openingPullingTeam = TeamId.TEAM_ONE,
        openingPullingFromEnd = FieldEnd.FAR,
    )
}

private fun buildHighActivityFullGame(setup: GameState): GameState {
    val clock = EstimateClock(epochTimestamp(setup.startDate, setup.startTime, setup.timeZone))
    var state = setup.startGame().beginLivePoint(clock.next())

    state = addCardPressure(state, clock)
    state = addTechnicalFoulPressure(state, clock)

    val offsidesByTeam = mutableMapOf(TeamId.TEAM_ONE to 0, TeamId.TEAM_TWO to 0)
    var timeViolations = 0
    scoringSequence().forEach { scoringTeam ->
        state = state.prepareForNextStressPoint(
            clock = clock,
            offsidesByTeam = offsidesByTeam,
            timeViolationIndex = timeViolations,
        )
        if (state.eventLog.count { it.type == EventLogType.TIME_VIOLATION } > timeViolations) {
            timeViolations += 1
        }
        state = state.recordGoalFromCurrentState(scoringTeam, clock.next())
    }

    check(state.teamOne.score == 15 && state.teamTwo.score == 14) {
        "Backup estimate scenario expected a 15-14 final score."
    }
    check(offsidesByTeam.values.all { it == 5 }) {
        "Backup estimate scenario expected 5 offsides per team."
    }
    check(state.eventLog.count { it.type == EventLogType.TIME_VIOLATION } == 2) {
        "Backup estimate scenario expected 2 time violations."
    }
    return state
}

private fun addCardPressure(
    initialState: GameState,
    clock: EstimateClock,
): GameState {
    var state = initialState
    repeat(10) { index ->
        val team = if (index % 2 == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO
        state = state.assessYellowCard(team, "${10 + index}", clock.next()).state
    }
    repeat(5) { index ->
        val team = if (index % 2 == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO
        state = state.assessRedCard(team, "${30 + index}", clock.next()).state
    }
    repeat(5) { index ->
        val team = if (index % 2 == 0) TeamId.TEAM_TWO else TeamId.TEAM_ONE
        state = state.assessBlueCard(team, clock.next()).state
    }
    return state
}

private fun addTechnicalFoulPressure(
    initialState: GameState,
    clock: EstimateClock,
): GameState {
    var state = initialState
    repeat(10) { index ->
        val team = if (index % 2 == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO
        state = state.assessTechnicalFoul(
            team,
            clock.next(),
            RuleGuidanceMode.FULL,
        ).state
    }
    return state
}

private fun GameState.prepareForNextStressPoint(
    clock: EstimateClock,
    offsidesByTeam: MutableMap<TeamId, Int>,
    timeViolationIndex: Int,
): GameState {
    var state = this.finishHalftimeIfNeeded(clock)
    if (state.phase != GamePhase.BETWEEN_POINTS) {
        return state
    }

    if (timeViolationIndex < 2) {
        val violatingTeam = if (timeViolationIndex == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO
        state = state.expiredPullDecisionState()
            .assessTimeViolation(violatingTeam, clock.next())
            .state
        state = state.applyCountdownIfNeeded(clock)
    }

    if (state.phase == GamePhase.BETWEEN_POINTS) {
        val pullingTeam = state.pullingTeam
        if (offsidesByTeam.getValue(pullingTeam) < 5) {
            state = state.recordOffsides(clock.next())
            offsidesByTeam[pullingTeam] = offsidesByTeam.getValue(pullingTeam) + 1
        }
    }

    return state.applyCountdownIfNeeded(clock)
}

private fun GameState.finishHalftimeIfNeeded(clock: EstimateClock): GameState {
    if (phase != GamePhase.HALFTIME) {
        return this
    }
    val halftimeEnd = countdown?.targetEpoch ?: clock.next()
    clock.advancePast(halftimeEnd)
    return applyExpiredCountdownTransitions(clock.current, showDefenseCountdowns = false)
}

private fun GameState.applyCountdownIfNeeded(clock: EstimateClock): GameState {
    val targetEpoch = countdown?.targetEpoch ?: return this
    clock.advancePast(targetEpoch)
    return applyExpiredCountdownTransitions(clock.current, showDefenseCountdowns = false)
}

private fun scoringSequence(): List<TeamId> {
    return buildList {
        repeat(14) {
            add(TeamId.TEAM_ONE)
            add(TeamId.TEAM_TWO)
        }
        add(TeamId.TEAM_ONE)
    }
}

private fun GameState.undoDepth(): Int {
    var depth = 0
    var entry = undoEntry
    while (entry != null) {
        depth += 1
        entry = entry.previous.undoEntry
    }
    return depth
}

private fun Long.toByteSizeString(): String {
    val kib = this / 1024.0
    return if (kib < 1024.0) {
        "%,d bytes (%.1f KiB)".format(this, kib)
    } else {
        "%,d bytes (%.2f MiB)".format(this, kib / 1024.0)
    }
}

private fun formatPercentOfCap(bytes: Long): String {
    return "%.2f%%".format(bytes * 100.0 / BACKUP_CAP_BYTES)
}

private class EstimateClock(
    startEpoch: Long,
) {
    var current: Long = startEpoch
        private set

    fun next(): Long {
        current += 60_000L
        return current
    }

    fun advancePast(epoch: Long) {
        current = maxOf(current + 60_000L, epoch + 1L)
    }
}

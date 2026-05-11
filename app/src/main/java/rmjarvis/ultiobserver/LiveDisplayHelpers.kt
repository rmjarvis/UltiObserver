package rmjarvis.ultiobserver

// Show N/A for the unknown-player sentinel; otherwise format as a jersey number.
internal fun displayPlayerNumber(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "N/A"
    } else {
        "#$jerseyNumber"
    }
}

// Convenience lookup for Team 1 vs Team 2 in the live state.
internal fun LiveGameState.teamFor(team: TeamId): TeamLiveState {
    return if (team == TeamId.TEAM_ONE) teamOne else teamTwo
}

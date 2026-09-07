package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/// Identity of one of the two teams in setup and in-progress game state.
@Serializable
enum class TeamId {
    TEAM_ONE,
    TEAM_TWO;

    /// Return the other team identifier.
    fun flip(): TeamId {
        return if (this == TEAM_ONE) TEAM_TWO else TEAM_ONE
    }

    /// Return the default display name for this team.
    fun defaultName(): String {
        return if (this == TEAM_ONE) "Team 1" else "Team 2"
    }
}

/// Identity of the field end nearest or farthest from the observer.
@Serializable
enum class FieldEnd {
    NEAR,
    FAR;

    /// Return the opposite field end.
    fun flip(): FieldEnd {
        return if (this == NEAR) FAR else NEAR
    }
}

/// Type of pull violation recorded during a pull sequence.
@Serializable
enum class PullViolationType {
    OFFSIDES,
    FALSE_START,
    MAJORITY_PULL,
}

/**
 * Player-card type being assigned or reconciled.
 *
 * @param label The user-facing card label.
 */
@Serializable
enum class CardType(val label: String) {
    YELLOW("Yellow"),
    RED("Red"),
}

/// USA Ultimate heat level, plus an app-level disabled state.
@Serializable
enum class HeatLevel(val displayText: String) {
    NONE("None"),
    LEVEL_1("Level 1"),
    LEVEL_2("Level 2"),
    LEVEL_3("Level 3"),
    MANUAL("Manual"),
}

/// How water breaks should be offered during this game.
@Serializable
enum class WaterBreakMode {
    NONE,
    MANUAL,
    AUTOMATIC,
}

package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/**
 * Normalized identity for a player based on the jersey number and name.
 *
 * Either the number or the name may be missing, but not both.
 *
 * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
 * @param playerName The player's name, or blank when unknown.
 */
@Serializable
class PlayerIdentity private constructor(
    val jerseyNumber: String,
    val playerName: String,
) {
    init {
        require(jerseyNumber.isNotBlank() || playerName.isNotBlank()) {
            "A player identity requires a jersey number or player name."
        }
    }

    /**
     * Return the display text for this player identity.
     *
     * @param compact Whether to omit the name when a jersey number is available.
     */
    fun displayText(compact: Boolean): String {
        val number = jerseyNumber.trim()
        val name = playerName.trim()
        return if (number.isNotEmpty()) {
            if (!compact && name.isNotEmpty()) "#$number $name" else "#$number"
        } else {
            name
        }
    }

    /// Return the name used for player identity comparisons.
    fun normalizedPlayerName(): String {
        return playerName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()
    }

    /// Return a unique key for exact player identities.
    fun key(): Pair<String, String> {
        val name = normalizedPlayerName()
        val number = jerseyNumber.trim()
        return number to name
    }

    /**
     * Report whether this identity matches another using player-card matching rules.
     *
     * @param other The identity to compare with this one.
     */
    fun matches(other: PlayerIdentity): Boolean {
        val existingNumber = key().first
        val existingName = key().second
        val proposedNumber = other.key().first
        val proposedName = other.key().second
        if (proposedNumber.isNotEmpty() && existingNumber == proposedNumber) {
            return proposedName.isEmpty() ||
                existingName.isEmpty() ||
                proposedName == existingName
        }
        return proposedName.isNotEmpty() &&
            existingName == proposedName &&
            proposedNumber.isEmpty() &&
            existingNumber.isEmpty()
    }

    /**
     * Report whether this identity overlaps another without matching it.
     *
     * This is more permissive than `matches`: it includes similar identities that
     * should not usually be treated as the same player, such as players with the
     * same number but different names. Use it when the app should warn the user
     * about a similar player they might have meant, then ask for confirmation
     * before adding a separate record.
     *
     * @param other The identity to compare with this one.
     */
    fun hasOverlapWith(other: PlayerIdentity): Boolean {
        if (key() == other.key()) {
            return false
        }
        val existingNumber = key().first
        val proposedNumber = other.key().first
        val existingName = key().second
        val proposedName = other.key().second
        return (proposedNumber.isNotEmpty() && proposedNumber == existingNumber) ||
            (proposedName.isNotEmpty() && proposedName == existingName &&
                (proposedNumber.isEmpty() || existingNumber.isEmpty()))
    }

    /**
     * Return this identity with blank fields filled from another identity.
     *
     * @param fallback Identity that supplies a number or name when this identity is missing it.
     */
    fun withMissingFieldsFrom(fallback: PlayerIdentity): PlayerIdentity {
        return PlayerIdentity(
            jerseyNumber = jerseyNumber.ifBlank { fallback.jerseyNumber },
            playerName = playerName.ifBlank { fallback.playerName },
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is PlayerIdentity && key() == other.key()
    }

    override fun hashCode(): Int {
        return key().hashCode()
    }

    override fun toString(): String {
        return "PlayerIdentity(jerseyNumber=$jerseyNumber, playerName=$playerName)"
    }

    companion object {
        /**
         * Build a normalized player identity.
         *
         * Constructor-style calls like `PlayerIdentity("23", "Name")` resolve to this
         * companion `invoke` operator because the primary constructor is private.
         * That lets callers use constructor syntax while still normalizing inputs
         * before the actual stored fields are assigned.
         *
         * @param jerseyNumber The entered or stored player number, or blank for a name-only
         * identity.
         * @param playerName The entered or stored player name, or blank when unknown.
         */
        operator fun invoke(jerseyNumber: String, playerName: String = ""): PlayerIdentity {
            return PlayerIdentity(
                jerseyNumber = jerseyNumber.trim(),
                playerName = playerName.trim(),
            )
        }
    }
}

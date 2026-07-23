package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/// Decoded persistence bucket and whether the stored JSON was migrated.
internal data class PersistenceDecodeResult<T>(
    val value: T,
    val wasMigrated: Boolean,
)

/// JSON bucket migrated far enough for the current serializer to decode.
internal data class MigratedJson(
    val jsonElement: JsonElement,
    val wasMigrated: Boolean,
)

/// Independently decoded persistence bucket that may need a bucket-specific migration.
private enum class PersistenceBucket {
    CURRENT_GAME,
    PROFILE,
    SETTINGS,
    ARCHIVED_GAME,
}

private typealias BucketMigration = (JsonElement) -> JsonElement
private typealias UnknownPlayerReference = Pair<TeamId, Int>
private typealias MigratedEventLogEntry = Pair<JsonObject, UnknownPlayerReference?>

/**
 * One persistence-version migration step, with optional converters for each persistence bucket.
 *
 * @param sourceVersion The source persistence version, such as `1.0`.
 * @param targetVersion The next persistence version produced by this step.
 * @param currentGame Converter for the current-game bucket, or null when unchanged.
 * @param profile Converter for the profile bucket, or null when unchanged.
 * @param settings Converter for the settings bucket, or null when unchanged.
 * @param archivedGame Converter for one archived-game file, or null when unchanged.
 */
private class VersionMigration(
    val sourceVersion: String,
    val targetVersion: String,
    val currentGame: BucketMigration?,
    val profile: BucketMigration?,
    val settings: BucketMigration?,
    val archivedGame: BucketMigration?,
) {
    /**
     * Return the migration function for one bucket, or null if this step does not change
     * that bucket's persistence shape.
     *
     * @param bucket The bucket whose migration function should be returned.
     */
    fun converterFor(bucket: PersistenceBucket): BucketMigration? {
        return when (bucket) {
            PersistenceBucket.CURRENT_GAME -> currentGame
            PersistenceBucket.PROFILE -> profile
            PersistenceBucket.SETTINGS -> settings
            PersistenceBucket.ARCHIVED_GAME -> archivedGame
        }
    }
}

private val knownVersionMigrations = listOf(
    VersionMigration(
        sourceVersion = "1.0",
        targetVersion = "1.1",
        currentGame = V1_0ToV1_1::migrateCurrentGame,
        profile = null,
        settings = V1_0ToV1_1::migrateSettings,
        archivedGame = V1_0ToV1_1::migrateArchivedGame,
    ),
    VersionMigration(
        sourceVersion = "1.1",
        targetVersion = "1.2",
        currentGame = null,
        profile = V1_1ToV1_2::migrateProfile,
        settings = V1_1ToV1_2::migrateSettings,
        archivedGame = null,
    ),
)

/**
 * Return current-game JSON migrated to the current persistence version.
 *
 * @param jsonElement The stored current-game JSON.
 * @param version Version metadata read from the stored JSON.
 */
internal fun migrateCurrentGameJson(jsonElement: JsonElement, version: AppVersion): MigratedJson? {
    return migrateJsonToCurrent(jsonElement, version, PersistenceBucket.CURRENT_GAME)
}

/**
 * Return profile JSON migrated to the current persistence version.
 *
 * @param jsonElement The stored profile JSON.
 * @param version Version metadata read from the stored JSON.
 */
internal fun migrateProfileJson(jsonElement: JsonElement, version: AppVersion): MigratedJson? {
    return migrateJsonToCurrent(jsonElement, version, PersistenceBucket.PROFILE)
}

/**
 * Return settings JSON migrated to the current persistence version.
 *
 * @param jsonElement The stored settings JSON.
 * @param version Version metadata read from the stored JSON.
 */
internal fun migrateSettingsJson(jsonElement: JsonElement, version: AppVersion): MigratedJson? {
    return migrateJsonToCurrent(jsonElement, version, PersistenceBucket.SETTINGS)
}

/**
 * Return archived-game JSON migrated to the current persistence version.
 *
 * @param jsonElement The stored archived-game JSON.
 * @param version Version metadata read from the stored JSON.
 */
internal fun migrateArchivedGameJson(jsonElement: JsonElement, version: AppVersion): MigratedJson? {
    return migrateJsonToCurrent(jsonElement, version, PersistenceBucket.ARCHIVED_GAME)
}

private fun migrateJsonToCurrent(
    jsonElement: JsonElement,
    version: AppVersion,
    bucket: PersistenceBucket,
): MigratedJson? {
    val storedVersion = version.persistenceVersion() ?: return null
    val currentVersion = currentPersistenceVersion()
    if (storedVersion == currentVersion) {
        return MigratedJson(jsonElement = jsonElement, wasMigrated = false)
    }
    val migratedJson = migrateJsonBetweenVersions(
        jsonElement = jsonElement,
        sourceVersion = storedVersion,
        currentVersion = currentVersion,
        bucket = bucket,
    ) ?: return null
    return MigratedJson(
        jsonElement = migratedJson,
        wasMigrated = true,
    )
}

private fun migrateJsonBetweenVersions(
    jsonElement: JsonElement,
    sourceVersion: String,
    currentVersion: String,
    bucket: PersistenceBucket,
): JsonElement? {
    if (sourceVersion == currentVersion) {
        return jsonElement
    }
    val versionMigration = knownVersionMigrations.firstOrNull {
        it.sourceVersion == sourceVersion
    } ?: return null
    val stepJson = versionMigration.converterFor(bucket)?.invoke(jsonElement) ?: jsonElement
    return migrateJsonBetweenVersions(
        jsonElement = stepJson,
        sourceVersion = versionMigration.targetVersion,
        currentVersion = currentVersion,
        bucket = bucket,
    )
}

/// Return the persistence version from a full version name, such as `1.1` from `1.1.0alpha`.
internal fun AppVersion.persistenceVersion(): String? {
    val match = Regex("""^(\d+)\.(\d+)""").find(versionName) ?: return null
    return "${match.groupValues[1]}.${match.groupValues[2]}"
}

/// Return the current app's persistence version.
internal fun currentPersistenceVersion(
    versionName: String = APP_STATE_VERSION_NAME,
    versionCode: Int = APP_STATE_VERSION_CODE,
): String {
    return requireNotNull(
        AppVersion(versionName, versionCode).persistenceVersion()
    ) {
        "Current app version $versionName must start with an M.m version."
    }
}

/// Implementation details for converting version 1.1 JSON shapes to version 1.2 shapes.
private object V1_1ToV1_2 {
    fun migrateProfile(jsonElement: JsonElement): JsonElement {
        val jsonObject = jsonElement.jsonObject
        return JsonObject(
            jsonObject.toMutableMap().apply {
                this["name"] = getValue("profileName")
                remove("profileName")
            }
        )
    }

    fun migrateSettings(jsonElement: JsonElement): JsonElement {
        val jsonObject = jsonElement.jsonObject
        return JsonObject(
            jsonObject.toMutableMap().apply {
                this["timingAlerts"] = getValue("timingAlertPreferences")
                remove("timingAlertPreferences")
            }
        )
    }
}

/// Implementation details for converting version 1.0 JSON shapes to version 1.1 shapes.
private object V1_0ToV1_1 {
    private const val UNKNOWN_PLAYER_NUMBER = "N/A"

    fun migrateSettings(jsonElement: JsonElement): JsonElement {
        val jsonObject = jsonElement.jsonObject
        val timingAlertPreferences = jsonObject.getValue("timingAlertPreferences").jsonObject
        val migratedTimingAlertPreferences = JsonObject(
            timingAlertPreferences.toMutableMap().apply {
                this["cueModes"] = migrateV1_0TimingCueMap(
                    timingAlertPreferences.getValue("cueModes"),
                ) { cueId ->
                    JsonPrimitive(cueId.defaultAlertMode().name)
                }
                this["cueRepeatCounts"] = migrateV1_0TimingCueMap(
                    timingAlertPreferences.getValue("cueRepeatCounts"),
                ) { cueId -> JsonPrimitive(cueId.defaultRepeatCount()) }
            }
        )
        return JsonObject(
            jsonObject.toMutableMap().apply {
                this["timingAlertPreferences"] = migratedTimingAlertPreferences
            }
        )
    }

    private fun migrateV1_0TimingCueMap(
        jsonElement: JsonElement,
        defaultValue: (TimingCueId) -> JsonElement,
    ): JsonElement {
        val renamedKeys = mapOf(
            "TIMEOUT_OFFENSE_TWENTY" to "OFFENSE_TWENTY",
            "MISCONDUCT_OFFENSE_TWENTY" to "OFFENSE_TWENTY",
            "TIMEOUT_OFFENSE_TEN" to "OFFENSE_TEN",
            "MISCONDUCT_OFFENSE_TEN" to "OFFENSE_TEN",
            "TIMEOUT_COUNTDOWN_FROM_FIVE" to "OFFENSE_COUNTDOWN_FROM_FIVE",
            "MISCONDUCT_COUNTDOWN_FROM_FIVE" to "OFFENSE_COUNTDOWN_FROM_FIVE",
            "TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY" to "OFFENSE_SET_LIMIT",
            "MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY" to "OFFENSE_SET_LIMIT",
            "MISCONDUCT_DEFENSE_TWENTY" to "DEFENSE_TWENTY",
        )
        val currentCueNames = TimingCueId.entries.map { it.name }.toSet()
        val migratedValues = jsonElement.jsonObject
            .mapKeys { (key, _) -> renamedKeys[key] ?: key }
            .filterKeys { key -> key in currentCueNames }
        return JsonObject(
            TimingCueId.entries.associate { cueId ->
                cueId.name to (migratedValues[cueId.name] ?: defaultValue(cueId))
            }
        )
    }

    fun migrateCurrentGame(jsonElement: JsonElement): JsonElement {
        val jsonObject = jsonElement.jsonObject
        val migratedSetupState = migrateV1_0SetupStateToV1_1(
            jsonObject.getValue("setupState").jsonObject,
        )
        val liveState = jsonObject.getValue("liveState")
        val migratedLiveState = if (liveState is JsonNull) {
            JsonNull
        } else {
            val liveStateObject = liveState.jsonObject
            JsonObject(
                liveStateObject.toMutableMap().apply {
                    this["state"] = migrateV1_0GameStateToV1_1(
                        liveStateObject.getValue("state").jsonObject,
                        preserveEndGameUndo = false,
                    )
                    this["undoEntry"] = migrateV1_0SerializedEndGameUndoEntry(
                        liveStateObject.getValue("undoEntry"),
                    )
                    this["redoEntry"] = JsonNull
                }
            )
        }
        val hadSetupDraft = appStateJson.decodeFromJsonElement<Boolean>(
            jsonObject.getValue("hasSetupDraft"),
        )
        if (migratedLiveState !is JsonNull) {
            return migratedLiveState
        }
        if (!hadSetupDraft) {
            return JsonNull
        }
        return appStateJson.encodeToJsonElement(
            setupGameStateFromJson(migratedSetupState).toSerializedGameState()
        )
    }

    /**
     * Build a setup-phase game state from migrated v1.0 setup JSON.
     *
     * @param jsonObject The setup JSON after v1.0 field migrations have been applied.
     */
    private fun setupGameStateFromJson(jsonObject: JsonObject): GameState {
        val startDate = LocalDate.parse(jsonObject.getValue("startDate").jsonPrimitive.content)
        val startTime = LocalTime.parse(jsonObject.getValue("startTime").jsonPrimitive.content)
        val timeZone = ZoneId.of(jsonObject.getValue("timeZone").jsonPrimitive.content)
        val pullingTeam = jsonObject.decodeOptional("pullingTeam", TeamId.TEAM_ONE)
        val pullingFromEnd = jsonObject.decodeOptional("pullingFromEnd", FieldEnd.FAR)
        return GameState(
            startDate = startDate,
            startTime = startTime,
            timeZone = timeZone,
            tournamentName = jsonObject.decodeOptional("tournamentName", ""),
            rules = jsonObject.decodeRequired("rules"),
            teamOne = jsonObject.decodeOptional(
                "teamOne",
                TeamState(name = "", color = TeamColorChoice.WHITE),
            ),
            teamTwo = jsonObject.decodeOptional(
                "teamTwo",
                TeamState(name = "", color = TeamColorChoice.BLUE),
            ),
            teamOnePlayers = jsonObject.decodeOptional("teamOnePlayers", emptyList()),
            teamTwoPlayers = jsonObject.decodeOptional("teamTwoPlayers", emptyList()),
            pullingTeam = pullingTeam,
            pullingFromEnd = pullingFromEnd,
            openingPullingTeam = pullingTeam,
            openingPullingFromEnd = pullingFromEnd,
            phase = GamePhase.SETUP,
            countdown = null,
        )
    }

    /**
     * Decode a required field from a JSON object.
     *
     * @param key The field name to decode.
     */
    private inline fun <reified T> JsonObject.decodeRequired(key: String): T {
        return appStateJson.decodeFromJsonElement(getValue(key))
    }

    /**
     * Decode an optional field from a JSON object, returning a default when the key is absent.
     *
     * @param key The field name to decode.
     * @param default The value to use when the field is absent.
     */
    private inline fun <reified T> JsonObject.decodeOptional(key: String, default: T): T {
        return this[key]?.let { appStateJson.decodeFromJsonElement<T>(it) } ?: default
    }

    fun migrateArchivedGame(jsonElement: JsonElement): JsonElement {
        val jsonObject = jsonElement.jsonObject
        val restorableState = jsonObject.getValue("restorableState")
        return if (restorableState is JsonNull) {
            migrateV1_0GameStateToV1_1(
                jsonObject.getValue("state").jsonObject,
                preserveEndGameUndo = true,
            )
        } else {
            migrateV1_0GameStateToV1_1(restorableState.jsonObject, preserveEndGameUndo = false)
        }
    }

    private fun migrateV1_0SetupStateToV1_1(jsonObject: JsonObject): JsonObject {
        val priorCards = jsonObject.getValue("priorCards").jsonArray
        return JsonObject(
            jsonObject.toMutableMap().apply {
                remove("priorCards")
                this["teamOnePlayers"] = buildPriorPlayerRecords(priorCards, TeamId.TEAM_ONE)
                this["teamTwoPlayers"] = buildPriorPlayerRecords(priorCards, TeamId.TEAM_TWO)
            }
        )
    }

    private fun migrateV1_0GameStateToV1_1(
        jsonObject: JsonObject,
        preserveEndGameUndo: Boolean,
    ): JsonObject {
        val priorCards = jsonObject.getValue("priorCards").jsonArray
        val teamOneBuilder = TeamPlayerMigration(TeamId.TEAM_ONE, priorCards)
        val teamTwoBuilder = TeamPlayerMigration(TeamId.TEAM_TWO, priorCards)
        var nextCardIndex = 0
        nextCardIndex = teamOneBuilder.addInGameCards(
            jsonObject.getValue("teamOnePlayerCards").jsonArray,
            nextCardIndex,
        )
        teamTwoBuilder.addInGameCards(
            jsonObject.getValue("teamTwoPlayerCards").jsonArray,
            nextCardIndex,
        )
        val eventLog = jsonObject.getValue("eventLog").jsonArray
        val migratedEventLog = eventLog.mapNotNull { event ->
            migrateV1_0EventLogEntryToV1_1(
                event.jsonObject,
                teamOneBuilder,
                teamTwoBuilder,
            )
        }
        val phase = appStateJson.decodeFromJsonElement<GamePhase>(jsonObject.getValue("phase"))
        val migratedPhase = migrateV1_0OpeningPullPhase(jsonObject, phase)
        return JsonObject(
            jsonObject.toMutableMap().apply {
                remove("priorCards")
                remove("teamOnePlayerCards")
                remove("teamTwoPlayerCards")
                this["teamOnePlayers"] = teamOneBuilder.toJsonArray()
                this["teamTwoPlayers"] = teamTwoBuilder.toJsonArray()
                this["eventLog"] = JsonArray(migratedEventLog.map { entry ->
                    migratedEventLogEntryToJson(entry, teamOneBuilder, teamTwoBuilder)
                })
                this["phase"] = JsonPrimitive(migratedPhase.name)
                this["countdown"] = JsonNull
                this["undoEntry"] = migrateV1_0GameStateEndGameUndoEntry(
                    jsonObject.getValue("undoEntry"),
                    preserveEndGameUndo,
                )
                this["redoEntry"] = JsonNull
            }
        )
    }

    private fun migrateV1_0OpeningPullPhase(jsonObject: JsonObject, phase: GamePhase): GamePhase {
        if (phase != GamePhase.BETWEEN_POINTS) {
            return phase
        }
        val teamOneScore = jsonObject.getValue("teamOne")
            .jsonObject
            .getValue("score")
            .jsonPrimitive
            .int
        val teamTwoScore = jsonObject.getValue("teamTwo")
            .jsonObject
            .getValue("score")
            .jsonPrimitive
            .int
        val pointAlreadyPlayed = teamOneScore + teamTwoScore > 0
        return if (pointAlreadyPlayed) phase else GamePhase.PRE_GAME
    }

    private fun migrateV1_0EventLogEntryToV1_1(
        jsonObject: JsonObject,
        teamOneBuilder: TeamPlayerMigration,
        teamTwoBuilder: TeamPlayerMigration,
    ): MigratedEventLogEntry? {
        val oldType = appStateJson.decodeFromJsonElement<String>(jsonObject.getValue("type"))
        val newType = when (oldType) {
            "SECOND_YELLOW" -> EventLogType.YELLOW_CARD.name
            else -> oldType
        }
        val playerNumber = jsonObject["playerNumber"]?.jsonPrimitive?.contentOrNull
        val fields = jsonObject.toMutableMap().apply {
            this["type"] = JsonPrimitive(newType)
            remove("playerNumber")
        }
        if (playerNumber == null) {
            return JsonObject(fields) to null
        }
        val team = appStateJson.decodeFromJsonElement<TeamId>(fields.getValue("team"))
        if (playerNumber != UNKNOWN_PLAYER_NUMBER) {
            fields["player"] = buildPlayerIdentity(jerseyNumber = playerNumber, playerName = "")
            return JsonObject(fields) to null
        }
        val builder = if (team == TeamId.TEAM_ONE) teamOneBuilder else teamTwoBuilder
        return JsonObject(fields) to (team to builder.unknownIdForEvent(oldType))
    }

    private fun migratedEventLogEntryToJson(
        entry: MigratedEventLogEntry,
        teamOneBuilder: TeamPlayerMigration,
        teamTwoBuilder: TeamPlayerMigration,
    ): JsonObject {
        val unknownPlayer = entry.second ?: return entry.first
        val builder = if (unknownPlayer.first == TeamId.TEAM_ONE) {
            teamOneBuilder
        } else {
            teamTwoBuilder
        }
        return JsonObject(
            entry.first.toMutableMap().apply {
                this["player"] = buildPlayerIdentity(
                    jerseyNumber = "",
                    playerName = builder.unknownName(unknownPlayer.second),
                )
            }
        )
    }

    /**
     * Preserve the first archived-game End game undo entry while pruning older history.
     *
     * Archived-game files store undo as normal `UndoEntry` values inside `GameState`,
     * so `previous` is a full old-format game state that must be migrated.
     */
    private fun migrateV1_0GameStateEndGameUndoEntry(
        jsonElement: JsonElement,
        preserveEndGameUndo: Boolean,
    ): JsonElement {
        if (!preserveEndGameUndo || jsonElement is JsonNull) {
            return JsonNull
        }
        val jsonObject = jsonElement.jsonObject
        val previous = jsonObject.getValue("previous").jsonObject
        return JsonObject(mapOf(
            "label" to JsonPrimitive("Undo End game"),
            "previous" to migrateV1_0GameStateToV1_1(previous, preserveEndGameUndo = false),
        ))
    }

    /**
     * Preserve the first current-game End game undo entry while pruning older history.
     *
     * Current-game files store undo as `SerializedUndoEntry` patch chains beside `state`.
     * Archived-game files store undo as normal `UndoEntry` values inside `GameState`.
     */
    private fun migrateV1_0SerializedEndGameUndoEntry(jsonElement: JsonElement): JsonElement {
        if (jsonElement is JsonNull) {
            return JsonNull
        }
        val jsonObject = jsonElement.jsonObject
        val label = appStateJson.decodeFromJsonElement<String>(jsonObject.getValue("label"))
        if (!label.equals("Undo End Game", ignoreCase = true)) {
            return JsonNull
        }
        return JsonObject(
            jsonObject.toMutableMap().apply {
                this["label"] = JsonPrimitive("Undo End game")
                this["previousUndoEntry"] = JsonNull
            }
        )
    }

    private fun buildPriorPlayerRecords(priorCards: JsonArray, team: TeamId): JsonArray {
        return TeamPlayerMigration(team, priorCards).toJsonArray()
    }

    private class TeamPlayerMigration(
        private val team: TeamId,
        priorCards: JsonArray,
    ) {
        private val records = mutableListOf<PlayerRecordMigration>()
        private val firstYellowEventUsed = mutableSetOf<Int>()
        private val redEventUsed = mutableSetOf<Int>()
        private var lastFirstYellowUnknownId: Int? = null
        private var nextUnknownId = 1

        init {
            priorCards
                .map { it.jsonObject }
                .filter { priorCard ->
                    appStateJson.decodeFromJsonElement<TeamId>(priorCard.getValue("team")) == team
                }
                .forEach { priorCard ->
                    val oldNumber = appStateJson.decodeFromJsonElement<String>(
                        priorCard.getValue("jerseyNumber"),
                    )
                    val unknownId = if (oldNumber == UNKNOWN_PLAYER_NUMBER) nextUnknownId++ else null
                    records += PlayerRecordMigration(
                        jerseyNumber = if (oldNumber == UNKNOWN_PLAYER_NUMBER) "" else oldNumber,
                        unknownId = unknownId,
                        priorYellows = priorCard.getValue("priorYellows").jsonPrimitive.int,
                        priorReds = priorCard.getValue("priorReds").jsonPrimitive.int,
                    )
                }
        }

        fun addInGameCards(playerCards: JsonArray, startIndex: Int): Int {
            var nextIndex = startIndex
            playerCards.map { it.jsonObject }.forEach { playerCard ->
                val oldNumber = appStateJson.decodeFromJsonElement<String>(
                    playerCard.getValue("jerseyNumber"),
                )
                val yellows = playerCard.getValue("yellows").jsonPrimitive.int
                val reds = playerCard.getValue("reds").jsonPrimitive.int
                val record = recordForInGameCards(oldNumber, yellows, reds)
                repeat(yellows) {
                    record.cards += cardEvent(CardType.YELLOW, nextIndex)
                    nextIndex += 1
                }
                repeat(reds) {
                    record.cards += cardEvent(CardType.RED, nextIndex)
                    nextIndex += 1
                }
            }
            return nextIndex
        }

        fun unknownIdForEvent(oldEventType: String): Int {
            // v1.0 accidentally serialized first-yellow events as SECOND_YELLOW and
            // second-yellow events as YELLOW_CARD. When an unknown-player second yellow
            // follows an unknown-player first yellow, treat it as the same player.
            // Otherwise, an unknown-player first yellow is a new distinct unknown player
            // unless it can sensibly attach to a prior-card unknown.
            return if (oldEventType == "RED_CARD") {
                unknownForRedEvent()
            } else if (oldEventType == "YELLOW_CARD" && lastFirstYellowUnknownId != null) {
                lastFirstYellowUnknownId!!
            } else {
                unknownForFirstYellowEvent()
            }
        }

        fun toJsonArray(): JsonArray {
            return JsonArray(records.map { it.toJson(totalUnknowns = totalUnknowns()) })
        }

        fun unknownName(unknownId: Int): String {
            return if (totalUnknowns() == 1) {
                UNKNOWN_PLAYER_NUMBER
            } else {
                "$UNKNOWN_PLAYER_NUMBER ($unknownId)"
            }
        }

        private fun recordForInGameCards(
            oldNumber: String,
            yellows: Int,
            reds: Int,
        ): PlayerRecordMigration {
            if (oldNumber != UNKNOWN_PLAYER_NUMBER) {
                return records.firstOrNull { it.jerseyNumber == oldNumber }
                    ?: PlayerRecordMigration(jerseyNumber = oldNumber).also { records += it }
            }
            val unknownRecords = records.filter { it.unknownId != null }
            val priorMatch = if (yellows > 0) {
                unknownRecords.firstOrNull { record ->
                    record.priorYellows > 0
                }
            } else {
                unknownRecords.firstOrNull { record ->
                    record.priorReds > 0
                }
            }
            return priorMatch ?: unknownRecords.firstOrNull() ?: newUnknownRecord()
        }

        private fun unknownForFirstYellowEvent(): Int {
            val record = records.firstOrNull {
                it.unknownId != null &&
                    it.priorYellows > 0 &&
                    it.unknownId !in firstYellowEventUsed
            } ?: records.firstOrNull {
                it.unknownId != null &&
                    it.cards.any { card -> card.isYellow() } &&
                    it.unknownId !in firstYellowEventUsed
            } ?: newUnknownRecord()
            val unknownId = record.unknownId!!
            firstYellowEventUsed += unknownId
            lastFirstYellowUnknownId = unknownId
            return unknownId
        }

        private fun unknownForRedEvent(): Int {
            val record = records.firstOrNull {
                it.unknownId != null &&
                    it.priorReds > 0 &&
                    it.unknownId !in redEventUsed
            } ?: records.firstOrNull {
                it.unknownId != null &&
                    it.cards.any { card -> card.isRed() } &&
                    it.unknownId !in redEventUsed
            } ?: newUnknownRecord()
            val unknownId = record.unknownId!!
            redEventUsed += unknownId
            return unknownId
        }

        private fun newUnknownRecord(): PlayerRecordMigration {
            return PlayerRecordMigration(
                jerseyNumber = "",
                unknownId = nextUnknownId++,
            ).also { records += it }
        }

        private fun totalUnknowns(): Int {
            return records.count { it.unknownId != null }
        }
    }

    private data class PlayerRecordMigration(
        val jerseyNumber: String,
        val unknownId: Int? = null,
        val priorYellows: Int = 0,
        val priorReds: Int = 0,
        val cards: MutableList<JsonObject> = mutableListOf(),
    ) {
        fun toJson(totalUnknowns: Int): JsonObject {
            val playerName = if (unknownId == null) {
                ""
            } else if (totalUnknowns == 1) {
                UNKNOWN_PLAYER_NUMBER
            } else {
                "$UNKNOWN_PLAYER_NUMBER ($unknownId)"
            }
            return JsonObject(mapOf(
                "jerseyNumber" to JsonPrimitive(jerseyNumber),
                "playerName" to JsonPrimitive(playerName),
                "priorYellows" to JsonPrimitive(priorYellows),
                "priorReds" to JsonPrimitive(priorReds),
                "cards" to JsonArray(cards),
            ))
        }
    }

    private fun cardEvent(cardType: CardType, index: Int): JsonObject {
        return JsonObject(mapOf(
            "cardType" to JsonPrimitive(cardType.name),
            "index" to JsonPrimitive(index),
        ))
    }

    private fun JsonObject.isYellow(): Boolean {
        return appStateJson.decodeFromJsonElement<CardType>(getValue("cardType")) == CardType.YELLOW
    }

    private fun JsonObject.isRed(): Boolean {
        return appStateJson.decodeFromJsonElement<CardType>(getValue("cardType")) == CardType.RED
    }

    private fun buildPlayerIdentity(jerseyNumber: String, playerName: String): JsonObject {
        return JsonObject(mapOf(
            "jerseyNumber" to JsonPrimitive(jerseyNumber),
            "playerName" to JsonPrimitive(playerName),
        ))
    }
}

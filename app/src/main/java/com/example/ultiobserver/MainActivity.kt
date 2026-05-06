package com.example.ultiobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ultiobserver.ui.theme.UltiObserverTheme
import java.time.Duration
import java.time.LocalTime

private enum class RuleEditTarget(
    val dialogTitle: String,
    val fieldLabel: String,
    val prefixText: String? = null,
    val suffixText: String? = null,
) {
    GAME_TO(
        dialogTitle = "Game To",
        fieldLabel = "Points",
    ),
    HALFTIME(
        dialogTitle = "Halftime",
        fieldLabel = "Minutes",
    ),
    HALF(
        dialogTitle = "Half Cap",
        fieldLabel = "Minutes",
        prefixText = "Half Cap at:",
        suffixText = "minutes after start time.",
    ),
    SOFT(
        dialogTitle = "Soft Cap",
        fieldLabel = "Minutes",
        prefixText = "Soft Cap at:",
        suffixText = "minutes after start time.",
    ),
    HARD(
        dialogTitle = "Hard Cap",
        fieldLabel = "Minutes",
        prefixText = "Hard Cap at:",
        suffixText = "minutes after start time.",
    ),
}

private enum class AppScreen {
    HOME,
    SETUP,
    LIVE,
}

private enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
}

private data class GameListEntry(
    val title: String,
    val subtitle: String,
)

private data class ArchivedGame(
    val state: LiveGameState,
    val subtitle: String,
)

private data class PendingMisconductChoice(
    val baseMessage: String,
)

private data class PendingRedCardChoice(
    val team: TeamId,
    val jerseyNumber: String,
)

private data class PendingUnknownYellowChoice(
    val team: TeamId,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp()
            }
        }
    }
}

@Composable
fun UltiObserverApp() {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var setupState by remember { mutableStateOf(GameSetupState()) }
    var liveState by remember { mutableStateOf<LiveGameState?>(null) }
    var setupMode by remember { mutableStateOf(SetupMode.NEW_GAME) }
    var archivedGames by remember { mutableStateOf(listOf<ArchivedGame>()) }
    var viewingArchivedGame by remember { mutableStateOf<ArchivedGame?>(null) }

    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.HOME -> Unit
            AppScreen.SETUP -> screen = AppScreen.HOME
            AppScreen.LIVE -> screen = AppScreen.HOME
        }
    }

    when (screen) {
        AppScreen.HOME -> {
            HomeScreen(
                currentGame = liveState?.takeIf { it.phase != LivePhase.GAME_OVER }?.let { gameListEntry(it, "Current game") },
                completedGamePendingArchive = liveState?.takeIf { it.phase == LivePhase.GAME_OVER }?.let {
                    gameListEntry(it, "")
                },
                previousGames = archivedGames.map { gameListEntry(it.state, it.subtitle) },
                onResumeCurrentGame = {
                    if (liveState != null && liveState?.phase != LivePhase.GAME_OVER) {
                        viewingArchivedGame = null
                        screen = AppScreen.LIVE
                    }
                },
                onOpenCompletedGame = {
                    liveState?.takeIf { it.phase == LivePhase.GAME_OVER }?.let {
                        viewingArchivedGame = null
                        screen = AppScreen.LIVE
                    }
                },
                onOpenPreviousGame = { index ->
                    val archived = archivedGames.getOrNull(index)
                    if (archived != null) {
                        viewingArchivedGame = archived
                        screen = AppScreen.LIVE
                    }
                },
                onArchiveCompletedGame = {
                    liveState?.takeIf { it.phase == LivePhase.GAME_OVER }?.let { completed ->
                        archivedGames = archivedGames + ArchivedGame(
                            pruneUndoHistory(completed),
                            "",
                        )
                        liveState = null
                        viewingArchivedGame = null
                    }
                },
                onStartNewGame = {
                    liveState?.let { existing ->
                        archivedGames = archivedGames + ArchivedGame(
                            pruneUndoHistory(
                                if (existing.phase == LivePhase.GAME_OVER) {
                                    existing
                                } else {
                                    existing.copy(phase = LivePhase.GAME_OVER, endTime = LocalTime.now())
                                }
                            ),
                            if (existing.phase == LivePhase.GAME_OVER) "" else "Closed when new game started",
                        )
                    }
                    setupState = GameSetupState()
                    liveState = null
                    viewingArchivedGame = null
                    setupMode = SetupMode.NEW_GAME
                    screen = AppScreen.SETUP
                },
            )
        }

        AppScreen.SETUP -> {
            SetupScreen(
                state = setupState,
                onStateChange = { setupState = it },
                primaryButtonLabel = if (setupMode == SetupMode.NEW_GAME) "Start Game" else "Back to Game Screen",
                onPrimaryAction = {
                    if (setupMode == SetupMode.NEW_GAME) {
                        liveState = createLiveGameState(setupState)
                    } else {
                        liveState = liveState?.let { applySetupToLiveGame(it, setupState) }
                    }
                    screen = AppScreen.LIVE
                },
            )
        }

        AppScreen.LIVE -> {
            val currentLiveState = viewingArchivedGame?.state ?: liveState
            if (currentLiveState != null) {
                LiveGameScreen(
                    state = currentLiveState,
                    readOnlySummary = viewingArchivedGame != null,
                    onStateChange = { updated ->
                        if (viewingArchivedGame != null) {
                            viewingArchivedGame = viewingArchivedGame!!.copy(state = updated)
                        } else {
                            liveState = updated
                        }
                    },
                    onUpdateGameSetup = {
                        setupState = liveGameToSetupState(currentLiveState)
                        setupMode = SetupMode.EDIT_CURRENT_GAME
                        screen = AppScreen.SETUP
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    currentGame: GameListEntry?,
    completedGamePendingArchive: GameListEntry?,
    previousGames: List<GameListEntry>,
    onResumeCurrentGame: () -> Unit,
    onOpenCompletedGame: () -> Unit,
    onOpenPreviousGame: (Int) -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onStartNewGame: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "UltiObserver",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Game management for Ultimate observers",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onStartNewGame,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) {
                        Text("Start New Game")
                    }
                }
            }

            if (currentGame != null) {
                SectionCard(
                    title = "Current Game",
                    subtitle = "Tap to resume the active game.",
                ) {
                    HomeGameRow(entry = currentGame, onClick = onResumeCurrentGame)
                }
            }

            if (completedGamePendingArchive != null) {
                SectionCard(
                    title = "Completed Game",
                ) {
                    HomeGameRow(entry = completedGamePendingArchive, onClick = onOpenCompletedGame)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onArchiveCompletedGame,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Archive Completed Game")
                    }
                }
            }

            SectionCard(
                title = "Previous Games",
                subtitle = "Tap a finished game to view its summary.",
            ) {
                if (previousGames.isEmpty()) {
                    Text("No completed games yet.")
                } else {
                    previousGames.forEachIndexed { index, game ->
                        HomeGameRow(entry = game, onClick = { onOpenPreviousGame(index) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    primaryButtonLabel: String,
    onPrimaryAction: () -> Unit,
) {
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showTimeoutRulesDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("UltiObserver Setup") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Game Start Time") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExactTimeField(
                        time = state.startTime,
                        modifier = Modifier.weight(1f),
                        onClick = { showStartTimeDialog = true },
                    )
                    SmallActionButton(label = "-5") {
                        onStateChange(state.copy(startTime = state.startTime.minusMinutes(5)))
                    }
                    SmallActionButton(label = "+5") {
                        onStateChange(state.copy(startTime = state.startTime.plusMinutes(5)))
                    }
                }
            }

            SectionCard(title = "Team Info") {
                TeamEditor(
                    fieldLabel = "Team 1",
                    team = state.teamOne,
                    onTeamChange = { onStateChange(state.copy(teamOne = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                TeamEditor(
                    fieldLabel = "Team 2",
                    team = state.teamTwo,
                    onTeamChange = { onStateChange(state.copy(teamTwo = it)) },
                )
            }

            SectionCard(title = "Starting Pull") {
                Text("Pulling team", fontWeight = FontWeight.SemiBold)
                TeamChoiceRow(
                    firstLabel = state.teamOne.name,
                    secondLabel = state.teamTwo.name,
                    selected = state.pullingTeam,
                    onSelected = { onStateChange(state.copy(pullingTeam = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pulling from", fontWeight = FontWeight.SemiBold)
                FieldEndChoiceRow(
                    selected = state.pullingFromEnd,
                    onSelected = { onStateChange(state.copy(pullingFromEnd = it)) },
                )
            }

            SectionCard(title = "Game Rules") {
                EditableValueRow(
                    label = "Game to",
                    value = state.rules.gameTo.toString(),
                    onClick = { editingRule = RuleEditTarget.GAME_TO },
                )
                EditableValueRow(
                    label = "Halftime",
                    value = "${state.rules.halftimeMinutes} min",
                    onClick = { editingRule = RuleEditTarget.HALFTIME },
                )
                EditableValueRow(
                    label = "Half cap",
                    value = if (state.rules.useHalfCap) "+${state.rules.halfCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.HALF },
                )
                EditableValueRow(
                    label = "Soft cap",
                    value = if (state.rules.useSoftCap) "+${state.rules.softCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.SOFT },
                )
                EditableValueRow(
                    label = "Hard cap",
                    value = if (state.rules.useHardCap) "+${state.rules.hardCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.HARD },
                )
                EditableValueRow(
                    label = "Timeouts",
                    value = formatTimeoutRules(state.rules),
                    onClick = { showTimeoutRulesDialog = true },
                )
            }

            SectionCard(title = "Cards from Previous Games") {
                if (state.priorCards.isEmpty()) {
                    Text("No prior cards recorded yet.")
                } else {
                    state.priorCards.forEachIndexed { index, record ->
                        val teamName = if (record.team == TeamId.TEAM_ONE) {
                            state.teamOne.name
                        } else {
                            state.teamTwo.name
                        }
                        PlayerRecordRow(
                            label = "$teamName #${record.jerseyNumber}",
                            detail = buildPlayerCardDetail(record),
                            onRemove = {
                                onStateChange(
                                    state.copy(priorCards = state.priorCards.filterIndexed { i, _ -> i != index })
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { showPlayerDialog = true }) {
                    Text("Add Card Holder")
                }
            }

            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryButtonLabel)
            }
        }
    }

    if (showPlayerDialog) {
        AddPlayerCardDialog(
            firstTeamName = state.teamOne.name,
            secondTeamName = state.teamTwo.name,
            onDismiss = { showPlayerDialog = false },
            onConfirm = { record ->
                onStateChange(state.copy(priorCards = state.priorCards + record))
                showPlayerDialog = false
            },
        )
    }

    if (showStartTimeDialog) {
        ExactTimeDialog(
            initialTime = state.startTime,
            onDismiss = { showStartTimeDialog = false },
            onConfirm = {
                onStateChange(state.copy(startTime = it))
                showStartTimeDialog = false
            },
        )
    }

    if (editingRule != null) {
        val target = editingRule!!
        when (target) {
            RuleEditTarget.GAME_TO, RuleEditTarget.HALFTIME -> {
                val initialValue = when (target) {
                    RuleEditTarget.GAME_TO -> state.rules.gameTo
                    RuleEditTarget.HALFTIME -> state.rules.halftimeMinutes
                    else -> 0
                }
                IntegerEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    prefixText = target.prefixText,
                    suffixText = target.suffixText,
                    initialValue = initialValue,
                    onDismiss = { editingRule = null },
                    onConfirm = { newValue ->
                        onStateChange(
                            state.copy(
                                rules = when (target) {
                                    RuleEditTarget.GAME_TO -> state.rules.copy(gameTo = newValue.coerceAtLeast(1))
                                    RuleEditTarget.HALFTIME -> state.rules.copy(halftimeMinutes = newValue.coerceAtLeast(1))
                                    else -> state.rules
                                }
                            )
                        )
                        editingRule = null
                    },
                )
            }

            RuleEditTarget.HALF, RuleEditTarget.SOFT, RuleEditTarget.HARD -> {
                val initialValue = when (target) {
                    RuleEditTarget.HALF -> state.rules.halfCapMinutes
                    RuleEditTarget.SOFT -> state.rules.softCapMinutes
                    RuleEditTarget.HARD -> state.rules.hardCapMinutes
                    else -> 0
                }
                val initiallyEnabled = when (target) {
                    RuleEditTarget.HALF -> state.rules.useHalfCap
                    RuleEditTarget.SOFT -> state.rules.useSoftCap
                    RuleEditTarget.HARD -> state.rules.useHardCap
                    else -> true
                }
                CapRuleEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    prefixText = target.prefixText,
                    suffixText = target.suffixText,
                    initialValue = initialValue,
                    initiallyEnabled = initiallyEnabled,
                    onDismiss = { editingRule = null },
                    onConfirm = { enabled, newValue ->
                        onStateChange(
                            state.copy(
                                rules = when (target) {
                                    RuleEditTarget.HALF -> state.rules.copy(useHalfCap = enabled, halfCapMinutes = newValue)
                                    RuleEditTarget.SOFT -> state.rules.copy(useSoftCap = enabled, softCapMinutes = newValue)
                                    RuleEditTarget.HARD -> state.rules.copy(useHardCap = enabled, hardCapMinutes = newValue)
                                    else -> state.rules
                                }
                            )
                        )
                        editingRule = null
                    },
                )
            }
        }
    }

    if (showTimeoutRulesDialog) {
        TimeoutRulesDialog(
            rules = state.rules,
            onDismiss = { showTimeoutRulesDialog = false },
            onConfirm = { updatedRules ->
                onStateChange(state.copy(rules = updatedRules))
                showTimeoutRulesDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveGameScreen(
    state: LiveGameState,
    readOnlySummary: Boolean,
    onStateChange: (LiveGameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
) {
    var showCardsSheet by remember { mutableStateOf(false) }
    var showOtherSheet by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var actionInfoMessage by remember { mutableStateOf<String?>(null) }
    var pendingMisconductChoice by remember { mutableStateOf<PendingMisconductChoice?>(null) }
    var halftimeAlert by remember { mutableStateOf(false) }
    var gameOverAlert by remember { mutableStateOf(false) }

    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val capStatus = remember(now, state) {
        computeNextCapStatus(state, now)
    }
    val activeCountdown = remember(state, nowMillis) {
        activeCountdownDisplay(state, nowMillis)
    }
    val canStartPoint = remember(state, nowMillis) {
        state.phase == LivePhase.BETWEEN_POINTS || halftimeTransitionReady(state, nowMillis)
    }

    LaunchedEffect(state.phase, state.lastEvent) {
        if (state.phase == LivePhase.HALFTIME && state.lastEvent == "Halftime.") {
            halftimeAlert = true
        }
        if (!readOnlySummary && state.phase == LivePhase.GAME_OVER && state.lastEvent == "Game over.") {
            gameOverAlert = true
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                actions = {
                    if (!locked && state.phase != LivePhase.GAME_OVER) {
                        TextButton(onClick = { locked = true }) {
                            Text("Lock")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.phase == LivePhase.GAME_OVER) {
                GameOverSummary(state = state, onUndo = {
                    if (state.undoEntry != null) {
                        onStateChange(undoLastAction(state))
                    }
                }, showUndo = !readOnlySummary && state.undoEntry != null)
            } else {
                StatusLine(
                    currentTime = now,
                    capStatus = capStatus,
                )

                if (activeCountdown != null) {
                    CountdownLine(
                        countdown = activeCountdown,
                        enabled = !locked,
                        onAdjust = { seconds -> onStateChange(addTimeToCountdown(state, seconds)) },
                    )
                }

                FieldSketchCard(
                    state = state,
                    interactionsEnabled = !locked,
                    showPullIndicator = !locked,
                    centerContent = {
                        if (locked) {
                            FieldUnlockControl(onUnlock = { locked = false })
                        } else if (canStartPoint) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(beginLivePoint(state))
                                    locked = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Start Point")
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT && state.countdown != null) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(continueLivePoint(state))
                                    locked = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Continue Point")
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT) {
                            OutlinedButton(
                                onClick = { locked = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Lock")
                            }
                        }
                    },
                    onGoal = { team -> onStateChange(recordGoalFromCurrentState(state, team)) },
                    onTimeout = { team ->
                        if (state.teamFor(team).timeoutsRemaining <= 0) {
                            actionInfoMessage = "${state.teamFor(team).name} is out of timeouts."
                        } else {
                            onStateChange(chargeTimeout(state, team))
                        }
                    },
                    onPullInfraction = { team ->
                        if (team == state.pullingTeam) {
                            val updatedState = recordOffsides(state)
                            onStateChange(updatedState)
                            if (updatedState != state) {
                                actionInfoMessage = offsidesResolutionMessage(updatedState, team)
                            }
                        } else {
                            val updatedState = recordFalseStart(state)
                            onStateChange(updatedState)
                            if (updatedState != state) {
                                actionInfoMessage = "Defense gets to set up."
                            }
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showCardsSheet = true },
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text("Cards / TF")
                    }
                    OutlinedButton(
                        onClick = { showOtherSheet = true },
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text("Other")
                    }
                }

                if (state.undoEntry != null) {
                    OutlinedButton(
                        onClick = { onStateChange(undoLastAction(state)) },
                        enabled = !locked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text(state.undoEntry!!.label)
                    }
                }
            }
        }
    }

    if (showCardsSheet) {
        ModalBottomSheet(onDismissRequest = { showCardsSheet = false }) {
            CardsSheet(
                state = state,
                onAssessment = { result ->
                    onStateChange(result.state)
                    if (result.needsLivePointMisconductChoice) {
                        pendingMisconductChoice = PendingMisconductChoice(result.message)
                    } else {
                        actionInfoMessage = result.message
                    }
                    showCardsSheet = false
                },
            )
        }
    }

    if (showOtherSheet) {
        ModalBottomSheet(onDismissRequest = { showOtherSheet = false }) {
            OtherSheet(
                state = state,
                onUpdateGameSetup = onUpdateGameSetup,
                onAction = { updatedState ->
                    onStateChange(updatedState)
                    if (updatedState != state && updatedState.pullSequenceFalseStartRecorded && !state.pullSequenceFalseStartRecorded) {
                        actionInfoMessage = "Defense gets to set up."
                    }
                    showOtherSheet = false
                },
            )
        }
    }

    if (actionInfoMessage != null) {
        AlertDialog(
            onDismissRequest = { actionInfoMessage = null },
            text = {
                Text(
                    text = actionInfoMessage!!,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            confirmButton = {
                TextButton(onClick = { actionInfoMessage = null }) {
                    Text("OK")
                }
            },
        )
    }

    if (pendingMisconductChoice != null) {
        AlertDialog(
            onDismissRequest = { pendingMisconductChoice = null },
            title = { Text("Misconduct Penalty") },
            text = {
                Text(
                    text = "${pendingMisconductChoice!!.baseMessage}\n\nWas this against the offense or defense?",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionInfoMessage = "${pendingMisconductChoice!!.baseMessage}\n\n${livePointMisconductMessage(againstOffense = true)}"
                        pendingMisconductChoice = null
                    }
                ) {
                    Text("Offense")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            actionInfoMessage = "${pendingMisconductChoice!!.baseMessage}\n\n${livePointMisconductMessage(againstOffense = false)}"
                            pendingMisconductChoice = null
                        }
                    ) {
                        Text("Defense")
                    }
                }
            },
        )
    }

    if (state.pendingCapOffer != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Apply ${capOfferLabel(state.pendingCapOffer!!)}?") },
            text = {
                Text(
                    text = capOfferExplanation(state),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { onStateChange(applyPendingCap(state)) }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { onStateChange(deferPendingCap(state)) }) {
                    Text("No")
                }
            },
        )
    }

    if (halftimeAlert) {
        AlertDialog(
            onDismissRequest = { halftimeAlert = false },
            text = {
                Text(
                    text = "Halftime",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
            },
            confirmButton = {
                TextButton(onClick = { halftimeAlert = false }) {
                    Text("OK")
                }
            },
        )
    }

    if (gameOverAlert) {
        AlertDialog(
            onDismissRequest = { gameOverAlert = false },
            text = {
                Text(
                    text = formatGameOverSummary(state),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            },
            confirmButton = {
                TextButton(onClick = { gameOverAlert = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun FieldUnlockControl(
    onUnlock: () -> Unit,
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    var thumbOffsetPx by remember { mutableStateOf(0f) }
    var dragEnabled by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbDiameter = 40.dp
    val thumbDiameterPx = with(density) { thumbDiameter.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Slide right to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .background(Color(0x66FFFFFF), RoundedCornerShape(26.dp))
                .border(1.dp, Color.Black, RoundedCornerShape(26.dp))
                .pointerInput(trackWidthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragEnabled = offset.x <= trackWidthPx * 0.25f
                            if (dragEnabled) {
                                thumbOffsetPx = 0f
                            }
                        },
                        onDragEnd = {
                            val unlockThreshold = trackWidthPx * 0.75f
                            val thumbCenter = thumbOffsetPx + thumbDiameterPx / 2f
                            if (dragEnabled && thumbCenter >= unlockThreshold) {
                                thumbOffsetPx = 0f
                                dragEnabled = false
                                onUnlock()
                            } else {
                                thumbOffsetPx = 0f
                                dragEnabled = false
                            }
                        },
                        onDragCancel = {
                            thumbOffsetPx = 0f
                            dragEnabled = false
                        },
                    ) { _, dragAmount ->
                        if (dragEnabled) {
                            val maxOffset = (trackWidthPx - thumbDiameterPx - with(density) { 12.dp.toPx() }).coerceAtLeast(0f)
                            thumbOffsetPx = (thumbOffsetPx + dragAmount.x).coerceIn(0f, maxOffset)
                        }
                    }
                },
        ) {
            Text(
                "Unlock",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                    .padding(6.dp)
                    .size(thumbDiameter)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(20.dp)),
            )
        }
    }
}

@Composable
private fun StatusLine(
    currentTime: LocalTime,
    capStatus: CapStatus?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClockTime(currentTime),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = capStatus?.let { "${it.label} ${formatDuration(it.remaining)}" } ?: "Caps passed",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FieldSketchCard(
    state: LiveGameState,
    interactionsEnabled: Boolean,
    showPullIndicator: Boolean,
    centerContent: @Composable (() -> Unit)?,
    onGoal: (TeamId) -> Unit,
    onTimeout: (TeamId) -> Unit,
    onPullInfraction: (TeamId) -> Unit,
) {
    val topSlot = if (state.pullingFromEnd == FieldEnd.FAR) {
        state.pullingTeam
    } else {
        oppositeTeam(state.pullingTeam)
    }
    val bottomSlot = oppositeTeam(topSlot)
    val topTeam = state.teamFor(topSlot)
    val bottomTeam = state.teamFor(bottomSlot)
    val pullFrom = state.pullingFromEnd

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EndZonePanel(
                teamId = topSlot,
                team = topTeam,
                background = topTeam.color.accent.copy(alpha = 0.85f),
                interactionsEnabled = interactionsEnabled,
                isPulling = state.pullingTeam == topSlot,
                pullInfractionEnabled = if (state.pullingTeam == topSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(topSlot) },
                onTimeout = { onTimeout(topSlot) },
                onPullInfraction = { onPullInfraction(topSlot) },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFA8D5A0))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPullIndicator) {
                        PullDirectionIndicator(
                            pullingFromEnd = pullFrom,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent?.invoke()
                    }
                }
            }
            EndZonePanel(
                teamId = bottomSlot,
                team = bottomTeam,
                background = bottomTeam.color.accent,
                interactionsEnabled = interactionsEnabled,
                isPulling = state.pullingTeam == bottomSlot,
                pullInfractionEnabled = if (state.pullingTeam == bottomSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(bottomSlot) },
                onTimeout = { onTimeout(bottomSlot) },
                onPullInfraction = { onPullInfraction(bottomSlot) },
            )
        }
    }
}

@Composable
private fun EndZonePanel(
    teamId: TeamId,
    team: TeamLiveState,
    background: Color,
    interactionsEnabled: Boolean,
    isPulling: Boolean,
    pullInfractionEnabled: Boolean,
    goalEnabled: Boolean,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onPullInfraction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = team.name.ifBlank { defaultTeamName(teamId) },
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = team.score.toString(),
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "TO ${team.timeoutsRemaining}  Cards ${totalCardPoints(team)}  TF ${team.technicalFouls}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pull violations ${pullViolationCount(team)}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactActionButton(label = "Goal", enabled = goalEnabled && interactionsEnabled, onClick = onGoal)
            CompactActionButton(label = "Timeout", enabled = interactionsEnabled, onClick = onTimeout)
            CompactActionButton(
                label = if (isPulling) "Offsides" else "False Start",
                enabled = interactionsEnabled && pullInfractionEnabled,
                onClick = onPullInfraction,
            )
        }
    }
}

@Composable
private fun PullDirectionIndicator(
    pullingFromEnd: FieldEnd,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.FAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (pullingFromEnd == FieldEnd.FAR) "↓" else "↑",
            color = Color.Black,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.NEAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CountdownLine(
    countdown: ActiveCountdownDisplay,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${countdown.label} ${formatDuration(countdown.remaining)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "-5", enabled = enabled) {
                onAdjust(-5)
            }
            SmallActionButton(label = "+5", enabled = enabled) {
                onAdjust(5)
            }
        }
    }
}

@Composable
private fun CardsSheet(
    state: LiveGameState,
    onAssessment: (CardAssessmentResult) -> Unit,
) {
    var pendingYellowTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedCardChoice by remember { mutableStateOf<PendingRedCardChoice?>(null) }
    var pendingUnknownYellowChoice by remember { mutableStateOf<PendingUnknownYellowChoice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cards / Technical Fouls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TeamActionSection(
            label = "${state.teamOne.name}${cardsRoleSuffix(state, TeamId.TEAM_ONE)}",
            issuedCards = state.playerCardsThisGame.filter { it.team == TeamId.TEAM_ONE },
            onYellow = { pendingYellowTeam = TeamId.TEAM_ONE },
            onRed = { pendingRedTeam = TeamId.TEAM_ONE },
            onBlue = { onAssessment(assessBlueCard(state, TeamId.TEAM_ONE)) },
            onTech = { onAssessment(assessTechnicalFoul(state, TeamId.TEAM_ONE)) },
        )
        TeamActionSection(
            label = "${state.teamTwo.name}${cardsRoleSuffix(state, TeamId.TEAM_TWO)}",
            issuedCards = state.playerCardsThisGame.filter { it.team == TeamId.TEAM_TWO },
            onYellow = { pendingYellowTeam = TeamId.TEAM_TWO },
            onRed = { pendingRedTeam = TeamId.TEAM_TWO },
            onBlue = { onAssessment(assessBlueCard(state, TeamId.TEAM_TWO)) },
            onTech = { onAssessment(assessTechnicalFoul(state, TeamId.TEAM_TWO)) },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (pendingYellowTeam != null) {
        PlayerNumberDialog(
            title = "Yellow Card",
            teamName = state.teamFor(pendingYellowTeam!!).name,
            onDismiss = { pendingYellowTeam = null },
            onConfirm = { jerseyNumber ->
                if (
                    jerseyNumber == UNKNOWN_PLAYER_NUMBER &&
                    playerHasYellowThisGame(state, pendingYellowTeam!!, UNKNOWN_PLAYER_NUMBER)
                ) {
                    pendingUnknownYellowChoice = PendingUnknownYellowChoice(pendingYellowTeam!!)
                } else {
                    onAssessment(assessYellowCard(state, pendingYellowTeam!!, jerseyNumber))
                }
                pendingYellowTeam = null
            },
        )
    }

    if (pendingRedTeam != null) {
        PlayerNumberDialog(
            title = "Red Card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            onDismiss = { pendingRedTeam = null },
            onConfirm = { jerseyNumber ->
                if (playerHasYellowThisGame(state, pendingRedTeam!!, jerseyNumber)) {
                    pendingRedCardChoice = PendingRedCardChoice(pendingRedTeam!!, jerseyNumber)
                } else {
                    onAssessment(assessRedCard(state, pendingRedTeam!!, jerseyNumber, RedCardMode.DIRECT_RED))
                }
                pendingRedTeam = null
            },
        )
    }

    if (pendingRedCardChoice != null) {
        RedCardModeDialog(
            teamName = state.teamFor(pendingRedCardChoice!!.team).name,
            jerseyNumber = pendingRedCardChoice!!.jerseyNumber,
            onDismiss = { pendingRedCardChoice = null },
            onDirectRed = {
                onAssessment(
                    assessRedCard(
                        state,
                        pendingRedCardChoice!!.team,
                        pendingRedCardChoice!!.jerseyNumber,
                        RedCardMode.DIRECT_RED,
                    )
                )
                pendingRedCardChoice = null
            },
            onSecondYellow = {
                onAssessment(
                    assessRedCard(
                        state,
                        pendingRedCardChoice!!.team,
                        pendingRedCardChoice!!.jerseyNumber,
                        RedCardMode.SECOND_YELLOW,
                    )
                )
                pendingRedCardChoice = null
            },
        )
    }

    if (pendingUnknownYellowChoice != null) {
        UnknownYellowDialog(
            teamName = state.teamFor(pendingUnknownYellowChoice!!.team).name,
            onDismiss = { pendingUnknownYellowChoice = null },
            onSamePlayer = {
                onAssessment(
                    assessRedCard(
                        state,
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                        RedCardMode.SECOND_YELLOW,
                    )
                )
                pendingUnknownYellowChoice = null
            },
            onDifferentPlayer = {
                onAssessment(
                    assessStandaloneYellowCard(
                        state,
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                    )
                )
                pendingUnknownYellowChoice = null
            },
        )
    }
}

@Composable
private fun TeamActionSection(
    label: String,
    issuedCards: List<InGamePlayerCardRecord>,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onTech: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Yellow", modifier = Modifier.weight(1f), onClick = onYellow)
            SmallActionButton(label = "Red", modifier = Modifier.weight(1f), onClick = onRed)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Blue", modifier = Modifier.weight(1f), onClick = onBlue)
            SmallActionButton(label = "Tech", modifier = Modifier.weight(1f), onClick = onTech)
        }
        if (issuedCards.isNotEmpty()) {
            Text("This game", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
            issuedCards.forEach { record ->
                Text(
                    text = buildIssuedCardSummary(record),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GameOverSummary(
    state: LiveGameState,
    onUndo: () -> Unit,
    showUndo: Boolean,
) {
    val orderedTeams = winnerFirstTeams(state)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Game Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Start time ${formatClockTime(state.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.endTime?.let { endTime ->
                    Text(
                        text = "End time ${formatClockTime(endTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                orderedTeams.forEach { team ->
                    Text(
                        text = "${team.name} ${team.score}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        GameOverTeamSummary(
            team = state.teamOne,
            issuedCards = state.playerCardsThisGame.filter { it.team == TeamId.TEAM_ONE },
        )
        GameOverTeamSummary(
            team = state.teamTwo,
            issuedCards = state.playerCardsThisGame.filter { it.team == TeamId.TEAM_TWO },
        )

        if (showUndo) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
            ) {
                Text("Undo End Game")
            }
        }
    }
}

@Composable
private fun GameOverTeamSummary(
    team: TeamLiveState,
    issuedCards: List<InGamePlayerCardRecord>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (issuedCards.isEmpty()) {
                Text("No yellow or red cards issued.", style = MaterialTheme.typography.bodyMedium)
            } else {
                issuedCards.forEach { record ->
                    Text(buildSummaryIssuedCardText(record), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Blue cards ${team.blueCards}", style = MaterialTheme.typography.bodyMedium)
            Text("Technical fouls ${team.technicalFouls}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AdjustScoreDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = state.teamOne.name,
                    value = teamOneScore,
                    onValueChange = { teamOneScore = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = state.teamTwo.name,
                    value = teamTwoScore,
                    onValueChange = { teamTwoScore = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneScore, teamTwoScore) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AdjustTimeoutsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneTimeouts by remember { mutableStateOf(state.teamOne.timeoutsRemaining) }
    var teamTwoTimeouts by remember { mutableStateOf(state.teamTwo.timeoutsRemaining) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Timeouts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = "${state.teamOne.name} (max ${state.teamOne.timeoutsAllowedThisHalf})",
                    value = teamOneTimeouts,
                    onValueChange = { teamOneTimeouts = it.coerceIn(0, state.teamOne.timeoutsAllowedThisHalf) },
                )
                SmallCountEditor(
                    label = "${state.teamTwo.name} (max ${state.teamTwo.timeoutsAllowedThisHalf})",
                    value = teamTwoTimeouts,
                    onValueChange = { teamTwoTimeouts = it.coerceIn(0, state.teamTwo.timeoutsAllowedThisHalf) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneTimeouts, teamTwoTimeouts) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AdjustCardsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (LiveGameState) -> Unit,
) {
    var teamOneY by remember { mutableStateOf(state.teamOne.yellowCards) }
    var teamOneB by remember { mutableStateOf(state.teamOne.blueCards) }
    var teamOneR by remember { mutableStateOf(state.teamOne.redCards) }
    var teamOneTf by remember { mutableStateOf(state.teamOne.technicalFouls) }
    var teamTwoY by remember { mutableStateOf(state.teamTwo.yellowCards) }
    var teamTwoB by remember { mutableStateOf(state.teamTwo.blueCards) }
    var teamTwoR by remember { mutableStateOf(state.teamTwo.redCards) }
    var teamTwoTf by remember { mutableStateOf(state.teamTwo.technicalFouls) }
    var workingPlayerCards by remember { mutableStateOf(state.playerCardsThisGame) }
    var pendingSteps by remember { mutableStateOf<List<CardAdjustmentStep>>(emptyList()) }

    fun finalizeAdjustment() {
        onConfirm(
            adjustCardsAndTf(
                state,
                teamOneY,
                teamOneB,
                teamOneR,
                teamOneTf,
                teamTwoY,
                teamTwoB,
                teamTwoR,
                teamTwoTf,
                workingPlayerCards,
            )
        )
    }

    fun applyCardAssignment(jerseyNumber: String) {
        val step = pendingSteps.firstOrNull() ?: return
        val updatedRecords = when (step.mode) {
            CardAdjustmentMode.ADD -> addPlayerCardAssignment(
                workingPlayerCards,
                step.team,
                jerseyNumber,
                step.cardType,
            )
            CardAdjustmentMode.REMOVE -> removePlayerCardAssignment(
                workingPlayerCards,
                step.team,
                jerseyNumber,
                step.cardType,
            )
        }
        workingPlayerCards = updatedRecords
        pendingSteps = pendingSteps.drop(1)
        if (pendingSteps.isEmpty()) {
            finalizeAdjustment()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Cards / TF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    CardCountRow("Yellow", teamOneY, { teamOneY += 1 }, { teamOneY = maxOf(0, teamOneY - 1) })
                    CardCountRow("Blue", teamOneB, { teamOneB += 1 }, { teamOneB = maxOf(0, teamOneB - 1) })
                    CardCountRow("Red", teamOneR, { teamOneR += 1 }, { teamOneR = maxOf(0, teamOneR - 1) })
                    CardCountRow("TF", teamOneTf, { teamOneTf += 1 }, { teamOneTf = maxOf(0, teamOneTf - 1) })
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    CardCountRow("Yellow", teamTwoY, { teamTwoY += 1 }, { teamTwoY = maxOf(0, teamTwoY - 1) })
                    CardCountRow("Blue", teamTwoB, { teamTwoB += 1 }, { teamTwoB = maxOf(0, teamTwoB - 1) })
                    CardCountRow("Red", teamTwoR, { teamTwoR += 1 }, { teamTwoR = maxOf(0, teamTwoR - 1) })
                    CardCountRow("TF", teamTwoTf, { teamTwoTf += 1 }, { teamTwoTf = maxOf(0, teamTwoTf - 1) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val steps = buildCardAdjustmentSteps(
                        state = state,
                        teamOneY = teamOneY,
                        teamOneR = teamOneR,
                        teamTwoY = teamTwoY,
                        teamTwoR = teamTwoR,
                    )
                    workingPlayerCards = state.playerCardsThisGame
                    if (steps.isEmpty()) {
                        finalizeAdjustment()
                    } else {
                        pendingSteps = steps
                    }
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    pendingSteps.firstOrNull()?.let { step ->
        when (step.mode) {
            CardAdjustmentMode.ADD -> {
                PlayerNumberDialog(
                    title = "Add ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { applyCardAssignment(it) },
                )
            }
            CardAdjustmentMode.REMOVE -> {
                AssignedCardRemovalDialog(
                    title = "Remove ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    options = removalOptionsForStep(workingPlayerCards, step.team, step.cardType),
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { applyCardAssignment(it) },
                )
            }
        }
    }
}

private data class CardAdjustmentStep(
    val team: TeamId,
    val cardType: CardType,
    val mode: CardAdjustmentMode,
)

private enum class CardAdjustmentMode {
    ADD,
    REMOVE,
}

private data class CardRemovalOption(
    val jerseyNumber: String,
    val label: String,
)

private fun buildCardAdjustmentSteps(
    state: LiveGameState,
    teamOneY: Int,
    teamOneR: Int,
    teamTwoY: Int,
    teamTwoR: Int,
): List<CardAdjustmentStep> {
    return buildList {
        repeat(maxOf(0, teamOneY - state.teamOne.yellowCards)) {
            add(CardAdjustmentStep(TeamId.TEAM_ONE, CardType.YELLOW, CardAdjustmentMode.ADD))
        }
        repeat(maxOf(0, state.teamOne.yellowCards - teamOneY)) {
            add(CardAdjustmentStep(TeamId.TEAM_ONE, CardType.YELLOW, CardAdjustmentMode.REMOVE))
        }
        repeat(maxOf(0, teamOneR - state.teamOne.redCards)) {
            add(CardAdjustmentStep(TeamId.TEAM_ONE, CardType.RED, CardAdjustmentMode.ADD))
        }
        repeat(maxOf(0, state.teamOne.redCards - teamOneR)) {
            add(CardAdjustmentStep(TeamId.TEAM_ONE, CardType.RED, CardAdjustmentMode.REMOVE))
        }
        repeat(maxOf(0, teamTwoY - state.teamTwo.yellowCards)) {
            add(CardAdjustmentStep(TeamId.TEAM_TWO, CardType.YELLOW, CardAdjustmentMode.ADD))
        }
        repeat(maxOf(0, state.teamTwo.yellowCards - teamTwoY)) {
            add(CardAdjustmentStep(TeamId.TEAM_TWO, CardType.YELLOW, CardAdjustmentMode.REMOVE))
        }
        repeat(maxOf(0, teamTwoR - state.teamTwo.redCards)) {
            add(CardAdjustmentStep(TeamId.TEAM_TWO, CardType.RED, CardAdjustmentMode.ADD))
        }
        repeat(maxOf(0, state.teamTwo.redCards - teamTwoR)) {
            add(CardAdjustmentStep(TeamId.TEAM_TWO, CardType.RED, CardAdjustmentMode.REMOVE))
        }
    }
}

private fun removalOptionsForStep(
    records: List<InGamePlayerCardRecord>,
    team: TeamId,
    cardType: CardType,
): List<CardRemovalOption> {
    val matching = records.filter { record ->
        record.team == team && when (cardType) {
            CardType.YELLOW -> record.yellows > 0
            CardType.RED -> record.directReds > 0
        }
    }
    if (matching.isEmpty()) {
        return listOf(CardRemovalOption(UNKNOWN_PLAYER_NUMBER, "N/A / unassigned"))
    }
    return matching.map { record ->
        val count = when (cardType) {
            CardType.YELLOW -> record.yellows
            CardType.RED -> record.directReds
        }
        CardRemovalOption(
            jerseyNumber = record.jerseyNumber,
            label = "${displayPlayerNumber(record.jerseyNumber)} (${cardType.label} $count)",
        )
    }
}

@Composable
private fun CardCountRow(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label $value")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "+1", enabled = true, onClick = onIncrement)
            SmallActionButton(label = "-1", enabled = value > 0, onClick = onDecrement)
        }
    }
}

@Composable
private fun AssignedCardRemovalDialog(
    title: String,
    teamName: String,
    options: List<CardRemovalOption>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                options.forEach { option ->
                    OutlinedButton(
                        onClick = { onConfirm(option.jerseyNumber) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AdjustPullInfractionsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit,
) {
    var teamOneOffsides by remember { mutableStateOf(state.teamOne.offsides) }
    var teamOneFalseStarts by remember { mutableStateOf(state.teamOne.falseStarts) }
    var teamTwoOffsides by remember { mutableStateOf(state.teamTwo.offsides) }
    var teamTwoFalseStarts by remember { mutableStateOf(state.teamTwo.falseStarts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Pull Infractions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    SmallCountEditor("Offsides", teamOneOffsides) { teamOneOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamOneFalseStarts) { teamOneFalseStarts = it.coerceAtLeast(0) }
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    SmallCountEditor("Offsides", teamTwoOffsides) { teamTwoOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamTwoFalseStarts) { teamTwoFalseStarts = it.coerceAtLeast(0) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts) }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TeamCorrectionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun PlayerNumberDialog(
    title: String,
    teamName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jerseyNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Player number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(jerseyNumber.ifBlank { UNKNOWN_PLAYER_NUMBER }) }) {
                Text("Record")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConfirm(UNKNOWN_PLAYER_NUMBER) }) {
                    Text("N/A")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun RedCardModeDialog(
    teamName: String,
    jerseyNumber: String,
    onDismiss: () -> Unit,
    onDirectRed: () -> Unit,
    onSecondYellow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player Already Has Yellow") },
        text = {
            Text("$teamName #$jerseyNumber already has a yellow this game.")
        },
        confirmButton = {
            TextButton(onClick = onDirectRed) {
                Text("Direct Red")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSecondYellow) {
                    Text("Second Yellow")
                }
            }
        },
    )
}

@Composable
private fun UnknownYellowDialog(
    teamName: String,
    onDismiss: () -> Unit,
    onSamePlayer: () -> Unit,
    onDifferentPlayer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unknown Player Number") },
        text = {
            Text("$teamName already has a yellow assigned to N/A. Is this the same player?")
        },
        confirmButton = {
            TextButton(onClick = onSamePlayer) {
                Text("Yes")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDifferentPlayer) {
                    Text("No")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun OtherSheet(
    state: LiveGameState,
    onUpdateGameSetup: () -> Unit,
    onAction: (LiveGameState) -> Unit,
) {
    var showAdjustScoreDialog by remember { mutableStateOf(false) }
    var showAdjustTimeoutsDialog by remember { mutableStateOf(false) }
    var showAdjustCardsDialog by remember { mutableStateOf(false) }
    var showAdjustPullInfractionsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Other", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtherMenuButton(
                    label = "Update Game Setup",
                    onClick = onUpdateGameSetup,
                )
                OtherMenuButton(
                    label = "Adjust Score",
                    onClick = { showAdjustScoreDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Timeouts",
                    onClick = { showAdjustTimeoutsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Cards / TF",
                    onClick = { showAdjustCardsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Pull Infractions",
                    onClick = { showAdjustPullInfractionsDialog = true },
                )
                OtherMenuButton(
                    label = "Swap Ends of Field",
                    onClick = { onAction(swapFieldEnds(state)) },
                )
                OtherMenuButton(
                    label = "Swap Pulling Team",
                    onClick = { onAction(swapPullingTeam(state)) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.halftimeTaken &&
                    (state.phase == LivePhase.BETWEEN_POINTS || state.phase == LivePhase.LIVE_POINT)
                ) {
                    OtherMenuButton(
                        label = "Start Halftime",
                        onClick = { onAction(startHalftimeNow(state)) },
                    )
                }
                if (state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "End Game",
                        onClick = { onAction(endGameNow(state)) },
                    )
                } else {
                    OtherMenuButton(
                        label = "Undo Game Over",
                        onClick = { onAction(undoGameOver(state)) },
                    )
                }
                if (!state.halftimeTaken && !state.halfCapApplied) {
                    OtherMenuButton(
                        label = "Apply Half Cap Now",
                        onClick = { onAction(makeCapNow(state, CapType.HALF)) },
                    )
                }
                if (!state.softCapApplied) {
                    OtherMenuButton(
                        label = "Apply Soft Cap Now",
                        onClick = { onAction(makeCapNow(state, CapType.SOFT)) },
                    )
                }
                if (!state.hardCapApplied && state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "Apply Hard Cap Now",
                        onClick = { onAction(makeCapNow(state, CapType.HARD)) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAdjustScoreDialog) {
        AdjustScoreDialog(
            state = state,
            onDismiss = { showAdjustScoreDialog = false },
            onConfirm = { teamOneScore, teamTwoScore ->
                onAction(adjustScore(state, teamOneScore, teamTwoScore))
                showAdjustScoreDialog = false
            },
        )
    }

    if (showAdjustTimeoutsDialog) {
        AdjustTimeoutsDialog(
            state = state,
            onDismiss = { showAdjustTimeoutsDialog = false },
            onConfirm = { teamOneTimeouts, teamTwoTimeouts ->
                onAction(adjustTimeouts(state, teamOneTimeouts, teamTwoTimeouts))
                showAdjustTimeoutsDialog = false
            },
        )
    }

    if (showAdjustCardsDialog) {
        AdjustCardsDialog(
            state = state,
            onDismiss = { showAdjustCardsDialog = false },
            onConfirm = { updatedState ->
                onAction(updatedState)
                showAdjustCardsDialog = false
            },
        )
    }

    if (showAdjustPullInfractionsDialog) {
        AdjustPullInfractionsDialog(
            state = state,
            onDismiss = { showAdjustPullInfractionsDialog = false },
            onConfirm = { teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts ->
                onAction(
                    adjustPullInfractions(
                        state,
                        teamOneOffsides,
                        teamOneFalseStarts,
                        teamTwoOffsides,
                        teamTwoFalseStarts,
                    )
                )
                showAdjustPullInfractionsDialog = false
            },
        )
    }
}

@Composable
private fun OtherMenuButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun TeamPickDialog(
    title: String,
    firstLabel: String,
    secondLabel: String,
    onDismiss: () -> Unit,
    onSelected: (TeamId) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSelected(TeamId.TEAM_ONE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(firstLabel)
                }
                Button(
                    onClick = { onSelected(TeamId.TEAM_TWO) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(secondLabel)
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ExactTimeDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var hourText by remember { mutableStateOf(toTwelveHour(initialTime).toString()) }
    var minuteText by remember { mutableStateOf(initialTime.minute.toString().padStart(2, '0')) }
    var isPm by remember { mutableStateOf(initialTime.hour >= 12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Start Time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPm,
                        onClick = { isPm = false },
                        label = { Text("AM") },
                    )
                    FilterChip(
                        selected = isPm,
                        onClick = { isPm = true },
                        label = { Text("PM") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hour = hourText.toIntOrNull()?.coerceIn(1, 12) ?: toTwelveHour(initialTime)
                    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: initialTime.minute
                    val normalizedHour = when {
                        isPm && hour < 12 -> hour + 12
                        !isPm && hour == 12 -> 0
                        else -> hour % 24
                    }
                    onConfirm(LocalTime.of(normalizedHour, minute))
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun IntegerEditDialog(
    title: String,
    fieldLabel: String,
    prefixText: String? = null,
    suffixText: String? = null,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (prefixText != null) {
                    Text(prefixText)
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (suffixText != null) {
                    Text(suffixText)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CapRuleEditDialog(
    title: String,
    fieldLabel: String,
    prefixText: String? = null,
    suffixText: String? = null,
    initialValue: Int,
    initiallyEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }
    var enabled by remember { mutableStateOf(initiallyEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("None")
                    Checkbox(
                        checked = !enabled,
                        onCheckedChange = { enabled = !it },
                    )
                }
                if (prefixText != null) {
                    Text(prefixText)
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = enabled,
                )
                if (suffixText != null) {
                    Text(suffixText)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(enabled, valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TimeoutRulesDialog(
    rules: GameRules,
    onDismiss: () -> Unit,
    onConfirm: (GameRules) -> Unit,
) {
    var timeoutsText by remember { mutableStateOf(rules.timeoutsPerHalf.toString()) }
    var hasFloater by remember { mutableStateOf(rules.hasFloaterTimeout) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timeout Rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = timeoutsText,
                    onValueChange = { timeoutsText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Timeouts per half") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("+ a floater")
                    Checkbox(
                        checked = hasFloater,
                        onCheckedChange = { hasFloater = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        rules.copy(
                            timeoutsPerHalf = timeoutsText.toIntOrNull()?.coerceAtLeast(0) ?: rules.timeoutsPerHalf,
                            hasFloaterTimeout = hasFloater,
                        )
                    )
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddPlayerCardDialog(
    firstTeamName: String,
    secondTeamName: String,
    onDismiss: () -> Unit,
    onConfirm: (PlayerCardRecord) -> Unit,
) {
    var selectedTeam by remember { mutableStateOf(TeamId.TEAM_ONE) }
    var jerseyNumber by remember { mutableStateOf("") }
    var priorYellows by remember { mutableStateOf(1) }
    var priorReds by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add player cards") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamChoiceRow(
                    firstLabel = firstTeamName,
                    secondLabel = secondTeamName,
                    selected = selectedTeam,
                    onSelected = { selectedTeam = it },
                )
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Jersey number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                SmallCountEditor(
                    label = "Prior yellows",
                    value = priorYellows,
                    onValueChange = { priorYellows = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = "Prior reds",
                    value = priorReds,
                    onValueChange = { priorReds = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlayerCardRecord(
                            team = selectedTeam,
                            jerseyNumber = jerseyNumber.ifBlank { "0" },
                            priorYellows = priorYellows,
                            priorReds = priorReds,
                        )
                    )
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TeamEditor(
    fieldLabel: String,
    team: TeamSetup,
    onTeamChange: (TeamSetup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = team.name,
            onValueChange = { onTeamChange(team.copy(name = it)) },
            label = { Text(fieldLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ColorChoiceRow(
            selected = team.color,
            onSelected = { onTeamChange(team.copy(color = it)) },
        )
    }
}

@Composable
private fun ExactTimeField(
    time: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Start time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatClockTime(time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EditableValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColorChoiceRow(
    selected: TeamColorChoice,
    onSelected: (TeamColorChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamColorChoice.entries.forEach { colorChoice ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .background(
                        color = if (selected == colorChoice) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(if (selected == colorChoice) 1.dp else 0.dp)
                    .background(
                        color = if (selected == colorChoice) Color(0xFFF2D23C) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(if (selected == colorChoice) 3.dp else 0.dp)
                    .background(
                        color = if (selected == colorChoice) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .padding(if (selected == colorChoice) 1.dp else 0.dp)
                    .border(
                        width = if (selected == colorChoice) 0.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .background(colorChoice.accent, RoundedCornerShape(4.dp))
                    .clickable { onSelected(colorChoice) }
            )
        }
    }
}

@Composable
private fun TeamChoiceRow(
    firstLabel: String,
    secondLabel: String,
    selected: TeamId,
    onSelected: (TeamId) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == TeamId.TEAM_ONE,
            onClick = { onSelected(TeamId.TEAM_ONE) },
            label = { Text(firstLabel.ifBlank { "Team 1" }) },
        )
        FilterChip(
            selected = selected == TeamId.TEAM_TWO,
            onClick = { onSelected(TeamId.TEAM_TWO) },
            label = { Text(secondLabel.ifBlank { "Team 2" }) },
        )
    }
}

@Composable
private fun FieldEndChoiceRow(
    selected: FieldEnd,
    onSelected: (FieldEnd) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == FieldEnd.FAR,
            onClick = { onSelected(FieldEnd.FAR) },
            label = { Text("Far end") },
        )
        FilterChip(
            selected = selected == FieldEnd.NEAR,
            onClick = { onSelected(FieldEnd.NEAR) },
            label = { Text("Near end") },
        )
    }
}

@Composable
private fun SmallCountEditor(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallActionButton(label = "-1") {
                onValueChange((value - 1).coerceAtLeast(0))
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SmallActionButton(label = "+1") {
                onValueChange(value + 1)
            }
        }
    }
}

@Composable
private fun PlayerRecordRow(
    label: String,
    detail: String,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(detail)
            }
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

private fun buildPlayerCardDetail(record: PlayerCardRecord): String {
    return if (record.priorReds > 0) {
        "Y ${record.priorYellows}  R ${record.priorReds}"
    } else {
        "Y ${record.priorYellows}"
    }
}

private fun buildIssuedCardSummary(record: InGamePlayerCardRecord): String {
    val parts = buildList {
        if (record.yellows > 0) {
            add("Y ${record.yellows}")
        }
        if (record.directReds > 0) {
            add("DR ${record.directReds}")
        }
    }
    return "${displayPlayerNumber(record.jerseyNumber)}: ${parts.joinToString("  ")}"
}

private fun buildSummaryIssuedCardText(record: InGamePlayerCardRecord): String {
    val parts = buildList {
        when (record.yellows) {
            1 -> add("Yellow card")
            2 -> add("Two yellow cards")
            in 3..Int.MAX_VALUE -> add("${record.yellows} yellow cards")
        }
        when (record.directReds) {
            1 -> add("Direct red card")
            in 2..Int.MAX_VALUE -> add("${record.directReds} direct red cards")
        }
    }
    return "${displayPlayerNumber(record.jerseyNumber)}: ${parts.joinToString("; ")}"
}

private fun formatGameOverSummary(state: LiveGameState): String {
    val orderedTeams = winnerFirstTeams(state)
    return buildString {
        appendLine("Game is over")
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

private fun winnerFirstTeams(state: LiveGameState): List<TeamLiveState> {
    return listOf(state.teamOne, state.teamTwo).sortedWith(
        compareByDescending<TeamLiveState> { it.score }.thenBy { it.name }
    )
}

private fun displayPlayerNumber(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "N/A"
    } else {
        "#$jerseyNumber"
    }
}

private fun cardsRoleSuffix(state: LiveGameState, team: TeamId): String {
    return if (state.phase == LivePhase.BETWEEN_POINTS || state.phase == LivePhase.HALFTIME) {
        if (team == state.pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}

private fun capOfferLabel(capType: CapType): String {
    return when (capType) {
        CapType.HALF -> "half cap"
        CapType.SOFT -> "soft cap"
        CapType.HARD -> "hard cap"
    }
}

private fun capOfferExplanation(state: LiveGameState): String {
    return when (state.pendingCapOffer) {
        CapType.HALF -> {
            val target = maxOf(state.teamOne.score, state.teamTwo.score) + 1
            "Half cap was at ${formatClockTime(state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong()))}. Halftime target would become $target. Apply now?"
        }
        CapType.SOFT -> {
            val target = maxOf(state.teamOne.score, state.teamTwo.score) + 1
            "Soft cap was at ${formatClockTime(state.startTime.plusMinutes(state.rules.softCapMinutes.toLong()))}. Winning score would become $target. Apply now?"
        }
        CapType.HARD -> {
            if (state.teamOne.score == state.teamTwo.score) {
                "Hard cap was at ${formatClockTime(state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))}. Score is tied, so one more point would be played. Apply now?"
            } else {
                "Hard cap was at ${formatClockTime(state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))}. Score is not tied, so the game would end now. Apply now?"
            }
        }
        null -> ""
    }
}

private fun formatTimeoutRules(rules: GameRules): String {
    return buildString {
        append("${rules.timeoutsPerHalf}/half")
        if (rules.hasFloaterTimeout) {
            append(" + floater")
        }
    }
}

private fun toTwelveHour(time: LocalTime): Int {
    val hour = time.hour % 12
    return if (hour == 0) 12 else hour
}

private fun defaultTeamName(teamId: TeamId): String {
    return if (teamId == TeamId.TEAM_ONE) "Team 1" else "Team 2"
}

private fun oppositeTeam(teamId: TeamId): TeamId {
    return if (teamId == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

private fun totalCardPoints(team: TeamLiveState): Int {
    return team.yellowCards + team.blueCards + (2 * team.redCards)
}

private fun pullViolationCount(team: TeamLiveState): Int {
    return team.offsides + team.falseStarts
}

private data class ActiveCountdownDisplay(
    val label: String,
    val remaining: Duration,
)

private fun activeCountdownDisplay(state: LiveGameState, nowMillis: Long): ActiveCountdownDisplay? {
    val countdown = state.countdown ?: return null
    return if (countdown.kind == CountdownKind.HALFTIME) {
        val halftimeRemainingMillis = countdown.targetEpochMillis - nowMillis
        if (halftimeRemainingMillis > 0L) {
            ActiveCountdownDisplay(
                label = countdown.label,
                remaining = Duration.ofMillis(halftimeRemainingMillis),
            )
        } else {
            val followOn = betweenPointsDisplay(state.pullingFromEnd, countdown.targetEpochMillis, nowMillis)
            ActiveCountdownDisplay(label = followOn.first, remaining = followOn.second)
        }
    } else {
        ActiveCountdownDisplay(
            label = countdown.label,
            remaining = Duration.ofMillis((countdown.targetEpochMillis - nowMillis).coerceAtLeast(0L)),
        )
    }
}

private fun halftimeTransitionReady(state: LiveGameState, nowMillis: Long): Boolean {
    val countdown = state.countdown ?: return false
    return state.phase == LivePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        nowMillis >= countdown.targetEpochMillis
}

private fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStartMillis: Long,
    nowMillis: Long,
): Pair<String, Duration> {
    val pullFromNearEnd = pullingFromEnd == FieldEnd.NEAR
    val durationSeconds = if (pullFromNearEnd) 80 else 60
    val label = if (pullFromNearEnd) "Pull in" else "Signal in"
    val targetMillis = sequenceStartMillis + durationSeconds * 1000L
    return label to Duration.ofMillis((targetMillis - nowMillis).coerceAtLeast(0L))
}

private fun gameListEntry(state: LiveGameState, subtitle: String): GameListEntry {
    return GameListEntry(
        title = "${state.teamOne.name} ${state.teamOne.score} - ${state.teamTwo.score} ${state.teamTwo.name}",
        subtitle = subtitle,
    )
}

private fun pruneUndoHistory(state: LiveGameState): LiveGameState {
    return state.copy(
        countdown = null,
        undoEntry = null,
    )
}

private fun offsidesResolutionMessage(state: LiveGameState, teamId: TeamId): String {
    val team = state.teamFor(teamId)
    val pullViolations = pullViolationCount(team)
    return if (pullViolations <= 1) {
        "Start at brick mark"
    } else {
        "Start at midfield"
    }
}


@Composable
private fun SmallActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
            content()
        }
    }
}

@Composable
private fun HomeGameRow(
    entry: GameListEntry,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(entry.title, fontWeight = FontWeight.Medium)
            Text(
                text = entry.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun LiveGameState.teamFor(team: TeamId): TeamLiveState {
    return if (team == TeamId.TEAM_ONE) teamOne else teamTwo
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    UltiObserverTheme(dynamicColor = false) {
        SetupScreen(
            state = GameSetupState(),
            onStateChange = {},
            primaryButtonLabel = "Start Game",
            onPrimaryAction = {},
        )
    }
}

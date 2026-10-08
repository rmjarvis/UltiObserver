package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Choose the timing-sound destination without leaving Home or disturbing the current game. */
@Composable
internal fun SoundOutputDialog(
    manager: SoundOutputManager,
    onTestSound: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by manager.state.collectAsState()
    LaunchedEffect(manager) {
        manager.refresh()
    }
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound output") },
        text = {
            ScrollableDialogRegion {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose where UltiObserver plays sounds.")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChoiceChipButton(
                            label = "None",
                            selected = state.muted,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "sound-output-none",
                            onClick = {
                                manager.select(SOUND_OUTPUT_NONE)
                            },
                        )
                        ChoiceChipButton(
                            label = "Android default",
                            selected = state.selectedKey == null,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "sound-output-automatic",
                            maxLines = 2,
                            softWrap = true,
                            onClick = {
                                manager.select(null)
                            },
                        )
                        state.outputs.forEach { output ->
                            ChoiceChipButton(
                                label = output.name,
                                selected = state.selectedKey == output.key,
                                modifier = Modifier.fillMaxWidth(),
                                tag = "sound-output-${output.id}",
                                maxLines = 3,
                                softWrap = true,
                                onClick = {
                                    manager.select(output.key)
                                },
                            )
                        }
                    }
                    Text(state.description())
                    state.selectionError?.let { Text(it) }
                    if (state.muted) {
                        SettingsSwitchRow(
                            label = "Also mute vibrations?",
                            checked = state.alsoMuteVibrations,
                            testTag = "sound-output-mute-vibrations",
                            onCheckedChange = {
                                manager.setAlsoMuteVibrations(it)
                            },
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Play a test sound to check your audio setting:")
                            Box(Modifier.align(Alignment.CenterHorizontally)) {
                                BigActionButton(
                                    label = "Test sound",
                                    containerColor = SecondaryColor,
                                    contentColor = OnSecondaryColor,
                                    borderColor = null,
                                    onClick = {
                                        onTestSound()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(
                    label = "Close",
                    onClick = {
                        onDismiss()
                    },
                )
            }
        },
    )
}

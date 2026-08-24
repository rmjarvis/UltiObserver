package rmjarvis.ultiobserver.wear

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Launcher activity for the pre-transport Wear companion prototype. */
internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedTeam by remember { mutableIntStateOf(0) }
            BackHandler(enabled = selectedTeam != 0) {
                selectedTeam = 0
            }

            if (selectedTeam == 0) {
                WearGameScreen(
                    display = WearGameSamples.countdown,
                    onTeamOne = {
                        selectedTeam = 1
                    },
                    onTeamTwo = {
                        selectedTeam = 2
                    },
                    onUndo = {
                        // Phone command transport will be added after the watch surfaces are settled.
                    },
                )
            } else {
                WearTeamActionsScreen(
                    display = if (selectedTeam == 1) {
                        WearGameSamples.animalActions
                    } else {
                        WearGameSamples.machineActions
                    },
                    onGoal = {},
                    onTimeViolation = {},
                    onPullViolation = {},
                    onCard = {},
                    onTechnicalFoul = {},
                    onTimeout = {},
                    onCancel = {
                        selectedTeam = 0
                    },
                )
            }
        }
    }
}

package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

// Profile fields for observer identity and home-screen avatar preference.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    name: String,
    avatarPreference: ObserverAvatarPreference,
    onNameChange: (String) -> Unit,
    onAvatarPreferenceChange: (ObserverAvatarPreference) -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackHome)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextEntry(
                value = name,
                onValueChange = onNameChange,
                labelText = "Name",
                colors = pageOutlinedTextFieldColors(),
                tag = "profile-name-field",
            )
            AvatarPreferenceSelector(
                avatarPreference = avatarPreference,
                onAvatarPreferenceChange = onAvatarPreferenceChange,
            )
        }
    }
}

@Composable
private fun AvatarPreferenceSelector(
    avatarPreference: ObserverAvatarPreference,
    onAvatarPreferenceChange: (ObserverAvatarPreference) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Home avatar",
            style = MaterialTheme.typography.titleMedium,
        )

        RandomAvatarPreferenceButton(
            selected = avatarPreference == ObserverAvatarPreference.RANDOM,
            onClick = {
                onAvatarPreferenceChange(ObserverAvatarPreference.RANDOM)
            },
        )

        Text(
            text = "Or choose a specific avatar:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            concreteObserverAvatarPreferences.chunked(3).forEach { rowPreferences ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowPreferences.forEach { preference ->
                        AvatarPreferenceButton(
                            preference = preference,
                            selected = avatarPreference == preference,
                            onClick = {
                                onAvatarPreferenceChange(preference)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomAvatarPreferenceButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = PanelShape,
        color = choiceContainerColor(selected),
        contentColor = choiceContentColor(selected),
        border = BorderStroke(1.dp, choiceBorderColor(selected)),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
            }
            .testTag("profile-avatar-RANDOM"),
    ) {
        Text(
            text = "Use a random avatar",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AvatarPreferenceButton(
    preference: ObserverAvatarPreference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = PanelShape,
        color = choiceContainerColor(selected),
        contentColor = choiceContentColor(selected),
        border = BorderStroke(1.dp, choiceBorderColor(selected)),
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                this.selected = selected
            }
            .testTag("profile-avatar-${preference.name}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(preference.drawableRes),
                contentDescription = preference.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

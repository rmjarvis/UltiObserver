package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for observer avatar metadata used by Profile and Home.
class TestObserverAvatars {
    /**
     * Test avatar preference ordering, labels, drawable resources, and random sentinel behavior.
     */
    @Test
    fun avatarPreferences() {
        // Concrete chooser order stays stable for random selection and UI presentation.
        assertEquals(
            listOf(
                ObserverAvatarPreference.SPIKY,
                ObserverAvatarPreference.DARK,
                ObserverAvatarPreference.SHORT_AFRO,
                ObserverAvatarPreference.CURLY,
                ObserverAvatarPreference.BLONDE,
                ObserverAvatarPreference.TIGHT_BRAIDS,
                ObserverAvatarPreference.WISPY_BOB,
                ObserverAvatarPreference.PONYTAIL,
                ObserverAvatarPreference.RED_PIGTAILS,
                ObserverAvatarPreference.BLUE,
                ObserverAvatarPreference.BALD,
                ObserverAvatarPreference.GREY,
            ),
            concreteObserverAvatarPreferences,
        )

        // Every preference exposes the label used by profile/home UI.
        val labelsByPreference = mapOf(
            ObserverAvatarPreference.RANDOM to "Random",
            ObserverAvatarPreference.SPIKY to "Spiky brown-haired man",
            ObserverAvatarPreference.DARK to "Dark-haired man with goatee",
            ObserverAvatarPreference.SHORT_AFRO to "Man with short afro and a beard",
            ObserverAvatarPreference.CURLY to "Man with curly reddish blond hair",
            ObserverAvatarPreference.BLONDE to "Blonde woman with a ponytail",
            ObserverAvatarPreference.TIGHT_BRAIDS to "Woman with tight African braids",
            ObserverAvatarPreference.WISPY_BOB to "Woman with a wispy bob",
            ObserverAvatarPreference.PONYTAIL to "Brunette woman with ponytail",
            ObserverAvatarPreference.RED_PIGTAILS to "Woman with bright red pigtails",
            ObserverAvatarPreference.BLUE to "Man with blue ponytail and glasses",
            ObserverAvatarPreference.BALD to "Bald man with full beard",
            ObserverAvatarPreference.GREY to "Grey-haired man with short grey beard",
        )
        ObserverAvatarPreference.entries.forEach { preference ->
            assertEquals(labelsByPreference[preference], preference.label)
        }

        // Every concrete avatar has a drawable resource.
        concreteObserverAvatarPreferences.forEach { preference ->
            assertTrue(preference.drawableRes != 0)
        }

        // RANDOM is a preference sentinel, so it should fail loudly if used as a drawable choice.
        val randomDrawableException = assertThrows(IllegalStateException::class.java) {
            ObserverAvatarPreference.RANDOM.drawableRes
        }
        assertEquals(
            "Random avatar must be resolved before requesting a drawable.",
            randomDrawableException.message,
        )
    }

    /**
     * Test resolving the random preference to a concrete Home avatar.
     */
    @Test
    fun randomAvatar() {
        // Use a fixed chooser to verify random-avatar timing without relying on randomness.
        val viewModel = AppViewModel(
            appStateStorage = NoOpAppStateStorage,
            chooseAvatarIndex = { size ->
                assertEquals(concreteObserverAvatarPreferences.size, size)
                2
            },
        )
        assertEquals(ObserverAvatarPreference.RANDOM, viewModel.avatarPreference)
        assertEquals(concreteObserverAvatarPreferences[2], viewModel.currentHomeAvatar)

        // A concrete avatar preference should be used directly on Home.
        viewModel.updateAvatarPreference(ObserverAvatarPreference.GREY)
        assertEquals(ObserverAvatarPreference.GREY, viewModel.avatarPreference)
        assertEquals(ObserverAvatarPreference.GREY, viewModel.currentHomeAvatar)

        // Returning to random should choose a concrete Home avatar again.
        viewModel.updateAvatarPreference(ObserverAvatarPreference.RANDOM)
        assertEquals(ObserverAvatarPreference.RANDOM, viewModel.avatarPreference)
        assertEquals(concreteObserverAvatarPreferences[2], viewModel.currentHomeAvatar)
    }
}

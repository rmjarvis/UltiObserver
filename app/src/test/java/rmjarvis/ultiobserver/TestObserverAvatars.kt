package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TestObserverAvatars {
    @Test
    fun avatarPreferencesHaveAccessibilityLabelsAndDrawableResources() {
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
        concreteObserverAvatarPreferences.forEach { preference ->
            assertTrue(preference.drawableRes != 0)
        }

        val randomDrawableException = assertThrows(IllegalStateException::class.java) {
            ObserverAvatarPreference.RANDOM.drawableRes
        }
        assertEquals(
            "Random avatar must be resolved before requesting a drawable.",
            randomDrawableException.message,
        )
    }
}

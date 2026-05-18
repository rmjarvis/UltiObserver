package rmjarvis.ultiobserver

import androidx.annotation.DrawableRes
import kotlinx.serialization.Serializable

/// Observer avatar preference stored in Profile and shown on Home.
@Serializable
internal enum class ObserverAvatarPreference {
    RANDOM,
    SPIKY,
    DARK,
    SHORT_AFRO,
    CURLY,
    BLONDE,
    TIGHT_BRAIDS,
    WISPY_BOB,
    PONYTAIL,
    RED_PIGTAILS,
    BLUE,
    BALD,
    GREY,
}

internal val concreteObserverAvatarPreferences =
    ObserverAvatarPreference.entries.filterNot { it == ObserverAvatarPreference.RANDOM }

/// Return the screen-reader label for an image-only avatar selector button.
internal val ObserverAvatarPreference.label: String
    get() {
        return when (this) {
            ObserverAvatarPreference.RANDOM -> "Random"
            ObserverAvatarPreference.SPIKY -> "Spiky brown-haired man"
            ObserverAvatarPreference.DARK -> "Dark-haired man with goatee"
            ObserverAvatarPreference.SHORT_AFRO -> "Man with short afro and a beard"
            ObserverAvatarPreference.CURLY -> "Man with curly reddish blond hair"
            ObserverAvatarPreference.BLONDE -> "Blonde woman with a ponytail"
            ObserverAvatarPreference.TIGHT_BRAIDS -> "Woman with tight African braids"
            ObserverAvatarPreference.WISPY_BOB -> "Woman with a wispy bob"
            ObserverAvatarPreference.PONYTAIL -> "Brunette woman with ponytail"
            ObserverAvatarPreference.RED_PIGTAILS -> "Woman with bright red pigtails"
            ObserverAvatarPreference.BLUE -> "Man with blue ponytail and glasses"
            ObserverAvatarPreference.BALD -> "Bald man with full beard"
            ObserverAvatarPreference.GREY -> "Grey-haired man with short grey beard"
        }
    }

@get:DrawableRes
internal val ObserverAvatarPreference.drawableRes: Int
    get() {
        return when (this) {
            ObserverAvatarPreference.RANDOM -> error("Random avatar must be resolved before requesting a drawable.")
            ObserverAvatarPreference.SPIKY -> R.drawable.observer_avatar_spiky
            ObserverAvatarPreference.DARK -> R.drawable.observer_avatar_dark
            ObserverAvatarPreference.SHORT_AFRO -> R.drawable.observer_avatar_short_afro
            ObserverAvatarPreference.CURLY -> R.drawable.observer_avatar_curly
            ObserverAvatarPreference.BLONDE -> R.drawable.observer_avatar_blonde
            ObserverAvatarPreference.TIGHT_BRAIDS -> R.drawable.observer_avatar_tight_braids
            ObserverAvatarPreference.WISPY_BOB -> R.drawable.observer_avatar_wispy_bob
            ObserverAvatarPreference.PONYTAIL -> R.drawable.observer_avatar_ponytail
            ObserverAvatarPreference.RED_PIGTAILS -> R.drawable.observer_avatar_red_pigtails
            ObserverAvatarPreference.BLUE -> R.drawable.observer_avatar_blue
            ObserverAvatarPreference.BALD -> R.drawable.observer_avatar_bald
            ObserverAvatarPreference.GREY -> R.drawable.observer_avatar_grey
        }
    }

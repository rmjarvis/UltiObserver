Wear OS Companion App
=====================

If you have a Wear OS smart watch, you can install the UltiObserver companion app.
This makes the user experience a whole lot nicer, honestly. You still do all the
setup stuff on the phone for each game. But then once the game starts, you can usually
leave your phone in your pocket for the whole game. Essentially all the game controls
can be done from the watch.

The primary exception is entering the player name and reason for yellow and red cards.
The watch lets you enter the player number and record the card. But if you want to enter
the name and reason, you need to use the phone, either when recording the card or
afterward using the `Editing existing cards` functionality.

Also, anything that requires the `More actions` menu can only be done on the phone.
These are not very commonly needed though during a game, so most of the time you
wouldn't need to use these actions.

You can use the phone and watch interchangeably during a game. If it's ever more convenient
to use the phone, go ahead and do that. When the phone is stashed away, you can use the
watch.

Getting Connected
-----------------

Install UltiObserver on both your phone and your paired Wear OS watch. In UltiObserver's
`Settings` on the phone, set **Watch connection** to **Wear OS**, then open UltiObserver on
the watch.

Before starting a game, the watch will show the message "No active game".
You should use the phone to do all the setup for the game. Once you click **Start game**,
the watch should show the teams and score, along with a countdown for the first pull.

The phone should remain on the active game screen during the game. If you leave the game screen,
the watch will disable the controls and ask you to resume the current game on the phone.
Once you do so, the watch controls will be enabled again.

Main Watch Screen
-----------------

The top half of the screen shows all the relevant time information for the game.
The clock at the top is the official game clock, including any adjustment you made on
the phone.
Underneath that is the time to the next relevant cap. This row also has the rules icon.
Clicking that (or technically anywhere in that row) will open the rules reference page,
just like on the phone.

Below that is the current countdown and next timing cue. If no countdown is currently
active, this area will show some message indicating the current state of the game,
such as "Live point in progress". Other messages show up here when relevant.

The two colored team areas cover most of the bottom half of the screen.
These show the team names, the current score, and which end of the field they are on.
See the **Team display on watch** setting in `Wear OS Settings` to control whether
the teams stay fixed throughout the game or the ends do.

An arrow in the middle indicates the pull direction.
For mixed games, a badge shows the ABBA gender ratio or indicates which team chooses the ratio,
depending on the game's rules.

At the bottom of the screen there is an **Undo** button, just like on the phone,
which will undo the most recent action. After pressing **Undo**, a **Redo** button
will appear, sharing the space at the bottom.
To prevent accidentally messing up your game state, both of these button require a long press
to activate. Just hold the button down for about a second. On most watches, there will be
a slight vibration when the press activates.

Team Action Screen
------------------

If you tap either team area from the main screen, it will take you to a screen with
actions related to that team.

The main section of this screen includes the six team action buttons, which work mostly
the same as the corresponding buttons on phone.
See `In-Game Events` and `Misconduct` for details about their effects.

The only one that works differently is the **Card** button for yellow or red cards.
The watch only lets you record a number for the player getting the card.
It gives you an option to continue entering more information on the phone if you want.
Or you can just enter the number and record the card with that. You can always go back
later on the phone to add more information. See `Editing existing cards`.

Confirmations and rule guidance use the same text as the phone according to the
**Rule Guidance** setting. You can scroll long messages on the watch when necessary.
**Timed** and **None** also work the same way as on the phone,
including automatically accepting the applicable confirmations.

If coach or captain names were entered for the team during setup, there will be a small
information icon (a circled "i") after the team name. Tapping the icon will show a screen
with their names for reference.

Countdown Controls
------------------

From the main watch screen, if you tap the countdown area, it will open up the countdown
controls on the bottom of the screen. 

You can pause or resume the timer, subtract or add 5 seconds, and start a water break when
the game's rules allow it.
When appropriate, this panel also offers **Start point**, **Continue point**, or
**Offense is set**, just as on the phone. Tap the countdown again or **Back** to return
to the main screen.

Watch Timing Alerts
-------------------

The watch will (by default) be the device for all timing cues that use vibration.
They work even with the watch screen off or another app open.
If the watch cannot receive a vibration, the phone vibrates instead.
Sounds continue to play on the phone or its connected earbud regardless.
If you prefer to keep vibrations on your phone, you can change the setting
**Vibrate on watch?** to No.

Note that the choice of sound or vibration is still set individually for each timing cue.
Enabling watch vibration or not doesn't change anything about those choices.
See `Timing Cues` and `Sounds and Vibration`.

Notification Permission
-----------------------

If the watch asks for notification permission, allowing it provides a **Return to game**
shortcut from your watch's home screen when there is a current game.
It looks like a small version of the UltiObserver icon at the bottom of the screen.
This makes it easier to reopen UltiObserver on the watch when a game is in progress if you
need to do something else on your watch.

Declining this permission does not prevent game controls or timing vibrations.
It just means you would have to open the app again the normal way if you navigate away from it.

If the Connection Is Lost
--------------------------

The watch needs a connection to the phone to record actions. If it loses that connection,
it keeps the last score visible but replaces the countdown with **Lost connection** and
disables game actions. Check that the phone is nearby and connected, then tap **Retry**.

The phone always holds the
definitive game state, so you can use that when the watch is not connecting for whatever reason.
Once it reconnects, the watch will update to the current game state.

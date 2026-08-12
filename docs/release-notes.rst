Release History
===============

Version 1.3
-----------

The main additions in this version are landscape mode and smart watch integration.
I don't have a full WearOS companion app yet, so you still can't record things like goals
and timeouts from the watch, but you can at least get timing cue alerts on the watch now.

- Added landscape mode for the active game screen, which shows the two field ends on the left
  and right, rather than top and bottom.
- Added an auto-rotate mode, which follows the phone orientation and keeps the field ends
  assigned to the same physical end of your phone.
- Added the ability to send notifications to a smart watch, including timing cues, the
  current score, and the gender ratio for ABBA.
- Fixed a bug that could crash the app if two buttons are pressed nearly simultaneously.
- Fixed a bug that caused hard-capped, archived games to not be restorable.
- Fixed a very minor bug in how hard cap could potentially interact with the 3rd quarter
  water break. Now a non-game-ending hard cap triggers a water break if one hasn't been
  taken yet in the half.
- Fixed a bug when misconduct is assessed after a pull countdown has expired.
- Added a cue when halftime is over and also cues at 5, 3, and 1 minute before the game starts.
- Swapped the grey team color for purple, since that's probably significantly more common
  for team jersey colors.
- Added a brightness slider to the color picker.
- Recognize when soft and hard caps become irrelevant and no longer emit cues for them.
- Improved (IMO) the default sounds assigned to various timing cues.
- Added an option to automatically advance Goal and Timeout countdowns a little bit to account
  for the time it takes to open the phone and press the button.
- Added a way to sync the app's clock with the tournament's official clock.
- Added the ability to filter archived games by cards assessed in the game.
- Changed the archive listing to show the winning team first.
- Changed the manual water break option from Level 0 to Manual, since Level 0 is really the
  same as None, so this naming was confusing.
- Added the ability to edit card details from the game summary after the game is over.
- Shortened the share text to have only the really useful info you would need to share.
- Gave the More actions menu a category-based navigation, which hopefully makes it easier
  for people to find the right menu item.
- Improved the timing-cue accuracy of associated sounds and vibrations.
- Improved the appearance of some dialogs, especially on phones with large fonts.
- Made halftime undo-able like most other game events in case you accidentally applied half
  cap incorrectly. The prompts for both halftime and game over are now deferrable if
  necessary by pressing Not yet.
- Stopped showing cap and water break prompts after undoing them.

Version 1.2
-----------

The main additions in this version are the youth time between points and following the
USAU Heat and Air Quality Guidelines.

- Added the ability to change both the time between points and the timeout duration. In both cases,
  this refers to the time until the offense needs to be ready. The defense then has 20 seconds
  more to pull or check the disc in.
- Changed the USAU default time between points to be 80 seconds for Youth games.
- Added support for USAU heat and air quality precautions, including water breaks and
  (for level 2) additional time between points and shortened caps.
- Changed the gender ratio indicator for ABBA (by default) to use the common M1, M2, W1, W2
  shorthand, indicating where in the sequence the current point is. You can switch to just
  show the current ratio if you prefer.
- Made the color of the gender ratio indicator settable in Settings.
- Added an option to show less verbose rule guidance for the popup messages during the game.
  Options are: Full, Brief, Timed, and None. See the Settings documentation for details.
- Improved the reliability of sound cues on some devices.
- Made minor adjustments to the user interface in various places to be hopefully a little clearer.

Version 1.1
-----------

Version 1.1 responded to good feedback from initial testers and includes many usability
improvements. Highlights include:

- Added customizable field-end names, so observers can label ends with site-specific names like
  ``Road``, ``Trees``, or ``Scoreboard`` instead of only ``Near end`` / ``Far end``.
- Added setup and in-game options for which field end receives pull timing prompts, including
  both-end and no-prompt modes.
- Added mixed-division gender-ratio support, including ABBA, Gen Zone, Offense Decides, and
  fixed-ratio options.
- Added team contact fields for coach, field captain, and spirit captain names, with quick
  in-game access from the field view and from the game summary.
- Added game information fields for observer names, tournament name, division, level,
  and game context such as pool play or semifinals. (All are optional.) The user's profile name,
  if set, is automatically prepopulated as the first observer name on new game setups.
- Added custom team colors through a full color picker, while keeping quick preset color choices
  for typical colors.
- Added the ability to save setup states for later, so you can pre-fill the basic information
  about all your games for the day, and then only deal with the flip-decided details
  when you get to the field.
- Improved the arrangement of the Setup game screen to be more intuitive.
- Redesigned the live field view with more compact team action buttons and clear field-end labels
  and gender ratio labels when appropriate.
- Improved the contrast of buttons and text entry fields throughout the app to look a bit
  better (in my opinion, ofc) and to have a more consistent style across the app.
- Included time-violation action on main field page and gave it clearer warning/timeout/
  yardage-penalty guidance.
- Improved all the in-game action dialogs with cleaner presentation and better instructions about
  rule consequences where appropriate.
- Added a small rules icon to the right of the cap timer to list what all the cap times are,
  along with the other rules that apply to the current game.
- Reworked yellow and red card entry to support player number, player name, and optional reason
  details.  This includes allowing players with a name but no number.
- Added the ability to edit details about previously assessed in-game yellow and red cards.
  This includes adding or changing a name, number or reason for an existing card.
- Improved completed-game summaries and share text with clearer misconduct details, including
  per-card yellow/red reasons.
- Added an optional defense-check countdown for timeouts and misconduct penalties after the
  offense is set.
- Added a vibration test button in Settings so observers can directly check how that feels.
- Improved timing cue delivery so sounds and vibrations can happen while the screen is asleep.
- Cleanly separated three different kinds of saved/archived games: setup states, in-progress
  games (typically would only be one of these) and archived completed games.
- Added a Crashlytics plug-in to log any app crashes that might happen and let me know so I
  can hopefully fix whatever bug caused it.
- Added comprehensive documentation about how to use the app.
- Fixed a number of bugs I found along the way.

Version 1.0
-----------

Version 1.0 was the first public testing release of UltiObserver with the following features:

- It keeps track of which team is pulling and from which end. The live game screen shows
  the field orientation as seen from one end of the field. This would normally be the end zone
  where you have primary responsibility as an observer.
- It keeps track of the time remaining for observing cues you are responsible for. E.g. if the
  team at your end zone is receiving, it will give cues for 20 and 10 seconds until they need
  to signal readiness. Similar for the pull time if the team at your end zone is pulling.
- For timeouts, it will similarly keep track of the time and tell you when to announce sideline
  clear, 20 to set, etc.
- You can customize which (if any) timing cues you want to have associated sounds and/or haptic
  feedback.
- It keeps track of misconduct cards for players and teams, including players entering the game
  with previous cards during that tournament. It will give you a message reminding you of the
  appropriate misconduct penalty, including tournament suspensions if a player reaches three
  yellows in the tournament for instance. Same for technical fouls.
- It records offsides, false starts, and time violations, and it lets you know what the
  appropriate starting position is in each case.
- It tells you when a relevant cap (half, soft, or hard) is coming up, and you can configure
  it to make a sound or vibrate when the cap goes off.
- During live points, the screen automatically locks (by default) to avoid errant button
  presses if you put your phone in your pocket. You can also manually lock it at any time.
- All actions are undo-able. And even redo-able if you didn't mean to click undo.
- The current rule set is based on USAU games to points.
- It keeps a full event log with timestamps in case you need to go back to see when something
  happened.
- You can manually override just about everything during the game in case there is some weird
  situation you need to fix.
- At the end of a game, the game summary can be easily shared using Android's share action.
- Completed games are archived for later viewing or sharing. You can even restore a game that
  has been archived back into a live state.

Release History
===============

Version 1.1
-----------

Version 1.1 responded to a lot of good feedback from initial testers which prompted a lot of
real usability improvements.

Highlights include:

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
- Fixed a number of bugs I found along the way.

Version 1.0
-----------

Version 1.0 was the first public testing release of UltiObserver.

Features included:

- It keeps track of which team is pulling and from which end. The live game screen shows
  the field orientation as seen from one end of the field. This would normally be the endzone
  where you have primary responsibility as an observer.
- It keeps track of the time remaining for observing cues you are responsible for. E.g. if the
  team at your endzone is receiving, it will give cues for 20 and 10 seconds until they need
  to signal readiness. Similar for the pull time if the team at your endzone is pulling.
- For timeouts, it will similarly keep track of the time and tell you when to announce sideline
  clear, 20 to set, etc.
- You can customize which (if any) timing cues you want to have associated sounds and/or haptic
  feedback.
- It keeps track of misconduct cards for players and teams, including players entering the game
  with previous cards during that tournament. It will give you a message reminding you of the
  appropriate misconduct penalty, including tournament suspensions if a player exceeds three
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

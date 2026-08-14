Overview
========

UltiObserver is a game management app for Ultimate observers, which is intended to replace
both the paper score sheet and the stopwatch observers typically use.

Before the game, you can enter all the relevant information about the teams, the tournament,
rules, field orientation, etc. These can be set up in advance and saved as drafts, so as
much as possible is ready when you arrive at the field. Of course, you would still need to
enter the results of the pre-game flip, such as which team is pulling from which end, once
these details are decided.

During the game, the app helps you track and manage the various events during the game that
require observer attention, including goals, timeouts, pull violations, time caps, and
misconduct -- everything that would normally be recorded on a paper score sheet.

It also provides timing cues for pulls, timeouts, misconduct penalties, and halftime,
showing an active countdown with prompts whenever the observer should announce something.
You can choose to have the app emit sounds or vibrations for some or all timing cues
(you control which ones and how they are indicated).

After recording a goal or timeout, the countdown starts automatically, and you can put your
phone away. Then just listen for the sound or notice the vibration that cues you to announce
the next timing update. For instance, for a pull, when you are at the end zone with the
receiving team, the default sounds are two ticks with 20 seconds remaining and one tick with 10
seconds remaining, prompting you to make the corresponding announcements. The specific sound and
vibration cues are all settable, so you can enable whichever ones you find most useful.

The recommended timing cue mode is to use sounds with an earbud in one ear.
The app is mostly quiet, so it won't interfere with your ability to hear during the game, but
that will let you hear the cue sounds without players hearing them. Vibrations are also
possible, especially if you have a paired smart watch. You can set the cues to be sent to the
watch as notifications, which can then vibrate, alerting you to announce the next cue.
Without a paired smart watch, it's often difficult to feel the vibrations on the phone,
so vibration mode in that case is generally less effective.

After the game, the app will show a game summary page with the final score along with other
details, including all cards that were issued. This page includes a button to share the summary,
so you can quickly send it to the head observer and/or tournament director for the tournament.

What UltiObserver Does
----------------------

UltiObserver tracks the following:

* Information about the game, including: team names, colors, observers, context
  within a tournament, field ends, rules, and starting pull details.
* The current state of an ongoing game, including: orientation of the teams, pull direction,
  gender ratio for mixed games, current score, remaining timeouts available, and how many pull
  violations, cards and technical fouls have been assessed to each team.
* Events that happen during a game, including: goals, timeouts, pull and time violations,
  misconduct on teams or individual players, halftime, and game over;
* Current timing prompts when appropriate including: pulling or receiving a pull, timeout,
  restart after a misconduct penalty, and halftime. The app can optionally use sound or vibration
  for individual cues, as well as for caps coming due.
* Short summaries of the consequences for violations and misconduct with the appropriate restart
  location and whether a check is required. This can be turned off or made extra brief for
  more experienced observers who do not need the assistance.
* Archives for past completed games, setup drafts, and any saved or abandoned in-progress games.
  Event logs and shareable game summaries are available for all past games and the current game.

Current Scope
-------------

UltiObserver is currently only available on Android phones. An iPhone version is
planned, but not yet implemented.
It is so far only targeted at games to a given point total, rather than timed games.
Most of the rules are hard coded to the current USAU rule set, but there are some things
that are settable, including the time between points, timeouts, and halftime.

Contents
--------

.. toctree::
   :maxdepth: 2

   Overview <self>
   install
   quick-start
   home-screen
   profile
   settings
   setup-game
   game-screen
   ingame-events
   misconduct
   timing-cues
   more-actions
   summary
   archive
   privacy
   troubleshooting
   planned-improvements
   release-notes

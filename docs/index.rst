UltiObserver
============

.. figure:: screen-shots/HomePage.png
   :class: phone-screenshot
   :target: _images/HomePage.png
   :alt: UltiObserver home screen with start game, archive, profile, settings, and about buttons

UltiObserver is an app for Ultimate observers.
It helps an observer track and manage the various events during the game that require
the observer's attention, including goals, timeouts, pull violations, time caps,
and misconduct -- everyting that would normally be recorded on a paper score sheet.
It also provides timing cues for pulls, timeouts, misconduct penalties, and halftime,
showing an active countdown with prompts for each time the observer should announce something.
The app is intended to replace both the paper score sheet and the stopwatch observers
typically use.

The recommended way to use the timing cues is to use either sound (with earbuds) or vibration.
After recording a goal or timeout, the countdown starts automatically, and you can put your
phone away. Then just listen for the sound or notice the vibration that cues you to announce
the next timing update. For instance, for a pull, when you are at the endzone with the
receiving team, the default sounds are two clicks at 20 seconds left and one click at 10 seconds,
prompting you to make the corresponding announcements. The specific sounds or vibration cues
are all settable, so you can enable whichever ones you find most useful.

After the game, the app will show a game summary page with the final score along with other
details, including all cards that were issued. This page includes a way to share the summary,
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
  location and whether a check is required.
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

   install
   quick-start
   profile-settings
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

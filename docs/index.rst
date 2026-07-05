UltiObserver
============

UltiObserver is an Android app for Ultimate observers. It helps an observer
track the game state, score, pull timing, timeouts, caps, misconduct, and other
notes that normally go on a paper game card. It also handles the time between
pulls, timeouts, misconduct penalties, and halftime, showing an active countdown
with prompts for each time the observer should announce something.

The app is designed for active field use on a phone in portrait mode. The game
screen normally keeps the observer's end of the field at the bottom of the
display, so the field view matches what you see in front of you.

What UltiObserver Does
----------------------

UltiObserver tracks the following:

* Information about the game, including: team names, colors, observers, context
  within a tournament, field ends, rules, and starting pull details.
* The current state of an ongoing game, including: orientation of the teams, pull direction,
  gender ratio for mixed games, current score, remaining timeouts available, and how many pull
  violations, cards and technical fouls have been assessed to each team.
* Events that happen during a game, including: goals, timeouts called, pull and time violations,
  misconduct on teams or individual players, halftime, and game over;
* Current timing prompts when appropriate including: pulling or receiving a pull, timeout,
  restart after a misconduct penalty, halftime. The app can optionally use sound or vibration
  for individual cues, as well as for caps coming due.
* Short summaries of the consequences for violations and misconduct with the appropriate restart
  location and whether a check is required.
* Archives for past completed games, setup drafts, and any saved or abandoned in-progress games.
  Event logs and shareable game summaries are available for all past games.

Current Scope
-------------

UltiObserver currently targets Android phones and USAU-style games to points.
Timed-game formats, an iPhone version, and additional layout options are
planned improvements rather than current behavior.

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

Overview
========

UltiObserver is a game management app for Ultimate observers, which is intended to replace
both the paper score sheet and the stopwatch observers typically use.
It is currently only available on Android devices. (Sorry iPhone users -- I do plan to
port it to iOS eventually, but I haven't yet.)

Before the game, you can enter all the relevant information about the teams, the tournament,
rules, field orientation, etc. These can be set up in advance and saved as drafts, so as
much as possible is ready when you arrive at the field. At the pre-game flip, you would
finish the setup, recording which team is pulling from which end, as well as which end
of the field you will be during pulls so it can give you the correct timing cues.

During the game, the app helps you track and manage the various events that
require observer attention, including goals, timeouts, pull violations, time caps, and
misconduct -- everything that would normally be recorded on a paper score sheet.
If you have a Wear OS smart watch, you can record these things directly from the watch.

It also provides countdowns for all the times in the game when you need to keep track
of the timing. This includes pulls, timeouts, misconduct penalties, and halftime.
The app shows an active countdown along with prompts for what you should announce and when.
(E.g. 20 seconds til offense set.) With a Wear OS smart watch, it will show the countdown
on the watch.

The app can emit sounds or trigger a vibration (either on the phone or a paired
smart watch) for whichever timing cues you want. There are four possible sounds to choose
from plus the vibration option. My preferred mode is to wear an ear bud in one ear and listen
for the sound indicating which cue I need to announce. For instance, for a pull, when
you are in the end zone with the receiving team, the default sound cue for 20 seconds remaining
is two ticks, and then at 10 seconds it is one tick. So when you hear those sounds, you can
make the corresponding announcement.  The specific sound and vibration cues are all settable,
so you can enable whichever ones you find most useful.

After the game, the app will show a game summary page with the final score along with other
details, including all cards that were issued. This page includes a button to share the summary,
so you can quickly send the misconduct information to the head observer and/or tournament
director for the tournament.

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
  restart after a misconduct penalty, and halftime. The app can use sound or vibration
  for the cues, as well as signal when a cap goes off.
* Short summaries of the consequences for violations and misconduct with the appropriate restart
  location and whether a check is required. This can be turned off or made extra brief for
  more experienced observers who do not need the assistance.
* Archives for past games, which let you view the games' event logs and game summaries.
  Saved setup drafts for upcoming games are also available from the archive screen.

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
   wear-os
   more-actions
   summary
   archive
   troubleshooting
   planned-improvements
   privacy
   release-notes

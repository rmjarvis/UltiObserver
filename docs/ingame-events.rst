In-Game Events
==============

This page has more details about in-game events initiated from the buttons in the
team areas of the active game screen.

Timeouts
--------

UltiObserver tracks each team's remaining timeouts based on the configured game
rules. The button in the team area of the game screen shows the number of timeouts
available for each team in the current half.

The normal action of the **TO** button is to start a countdown for the timeout duration
(70 seconds for USAU).

For timeouts during live play, the countdown will include cues for when to tell the sideline
players to clear the field and when to announce 20 and 10 seconds remaining for the offense
to get set. If you have defensive countdowns enabled in the settings
(see :ref:`settings`), then
after the offense is set, it will show the defensive countdown with cues for 20 and 10 seconds
for the defense to check the disc in.

For timeouts between points, the pull countdown will merely add the timeout duration to the
normal between-points countdown. It also adds a cue for one minute until either the offense
needs to be set or until the pull, depending on which end you are on.

If a team has no timeouts remaining, tapping **TO** shows a message indicating this.
It will also remind you of the consequence in case you need it if the attempt was made during
a live point.

.. _pull-violations:

Pull Violations
---------------

The button label for a pull violation is either **Offsides** or **False start**, depending
on whether they are pulling or receiving. If they have any prior pull violations (of either
type), the total number of pull violations is shown on the button as well, as this impacts
the consequences of the next violation.

Clicking the button for the pull violation opens a popup message telling you the consequences
of the violations -- where the offense should put the disc into play and whether there is a check.

For mixed games, you can record a majority pull violation by clicking **Offsides** and then
clicking **This was a Majority pull rule violation** on the popup page. The consequences are the
same in either case, but this will record the violation event with the correct name.

.. _time-violations:

Time Violations
---------------

If the offense does not signal readiness in time or the defense does not pull in time, you
may record a time violation by clicking the **Time viol.** button. This does different things
depending on whether this was the first or later violation.

The first violation is just a warning, so the team gets a new short countdown to either pull
or signal readiness.

On later violations, it will automatically charge the team with a timeout if they have one
and start a normal countdown with the timeout duration.

If the team is out of timeouts, the popup message will remind you of the consequent yardage
penalty and restart.

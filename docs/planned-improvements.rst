Planned Improvements
====================

These are improvements already being considered. If you have other ideas, open
an issue on `GitHub <https://github.com/rmjarvis/UltiObserver/issues>`_.

Timed games
-----------

UltiObserver is currently built around USAU games to points. I'd like to add modes
where the game can be to time, either quarters or halves. This would help enable
compatibility for professional games such as PUL, WUL, and UFA. I'm not sure how best
to have the app handle the quarter time, since this is normally handled by a stadium
clock. Feel free to comment `here <https://github.com/rmjarvis/UltiObserver/issues/1>`__
if you have thoughts about this.

Alternate rule sets
-------------------

The app currently uses USAU rules for everything. Some things are modifiable, including
timeout duration and the time between points, but it would be nice to be able to set these
with a single click for common non-USAU contexts.
In particular, it would be nice to have buttons for professional games such as PUL, WUL, and UFA,
which could also make any other adjustments to the rules that these games require.
WFDF variants would be nice to include as well to encode the places where those rules
differ from USAU rules. If you have thoughts about this, feel free to comment
`here <https://github.com/rmjarvis/UltiObserver/issues/2>`__.

Multi-Observer communication
----------------------------

It would be useful for the app to allow observers to communicate with each other
during a game if both (or more) observers are using the app. It would also be nice if
the app could automatically sync events between the two phones, so only one observer
would need to enter the details for a misconduct or violation. This would require some
significant scoping out though. If you have thoughts about this, feel free to comment
`here <https://github.com/rmjarvis/UltiObserver/issues/5>`__.

iOS version
-----------

From the beginning, I've separated the back-end model state code from the front-end
user interface code. For iOS, I believe I can directly reuse the back end, so I would
only need to write new front-end code for iOS devices. It's still a lot of work, but
much less so than starting from scratch. If this would be of value to you, feel free to
say so `here <https://github.com/rmjarvis/UltiObserver/issues/6>`__. The more people who
request this, the more likely I am to do it sooner than later.

Smart watch interface
---------------------

UltiObserver can already mirror compact timing and score notifications through the phone's
standard Android notifications. I'd also like to add a full smart watch interface using Wear OS,
so the most common actions could be controlled from a watch. Then the phone interface would
only be needed for setup and actions that require a keyboard like entering a name and number for
a yellow card. If you have thoughts about this, feel free to suggest things
`here <https://github.com/rmjarvis/UltiObserver/issues/7>`__.

More Ideas
----------

Use `GitHub Issues <https://github.com/rmjarvis/UltiObserver/issues>`_
for feature requests or workflow suggestions.

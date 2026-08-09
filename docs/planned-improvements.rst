Planned Improvements
====================

These are improvements already being considered. If you have other ideas, open
an issue on GitHub.

.. _GitHub Issues: https://github.com/rmjarvis/UltiObserver/issues

Timed games
-----------

UltiObserver is currently built around USAU games to points. I'd like to add modes
where the game can be to time, either quarters or halves. This would help enable
compatibility for professional games such as PUL, WUL, and UFA.

Alternate rule sets
-------------------

The app currently uses USAU rules for everything. It would be nice to be able to modify
some of these rules, including countdown timings particularly, so the app would be
useful for non-USAU games, including professional games such as PUL, WUL, and UFA.
WFDF variants would be nice to include as well to encode the places where those rules
differ from USAU rules.

Multi-Observer communication
----------------------------

It would be useful for the app to allow observers to communicate with each other
during a game if both (or more) observers are using the app. It would also be nice if
the app could automatically sync events between the two phones, so only one observer
would need to enter the details for a misconduct or violation. This would require some
significant scoping out though.

iOS version
-----------

From the beginning, I've separated the back-end model state code from the front-end
user interface code. For iOS, I believe I can directly reuse the back end, so I would
only need to write new front-end code for iOS devices. It's still a lot of work, but
much less so than starting from scratch.

Smart watch interface
---------------------

UltiObserver can already mirror compact timing and score notifications through the phone's
standard Android notifications. I'd also like to add a full smart watch interface, so the most
common actions could be controlled from a watch. Then the phone interface would only be needed
for setup and some more intensive actions like entering a name and number for a yellow card.

More Ideas
----------

Use `GitHub Issues`_ for feature requests or workflow suggestions.

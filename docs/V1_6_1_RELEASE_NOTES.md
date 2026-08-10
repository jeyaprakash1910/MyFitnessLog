A fix for "can't check right now".

The server this app talks to goes to sleep when nobody has used it for a while, and takes up to two minutes to wake up. The app was only waiting ten seconds before giving up, so the first check after a quiet spell always failed, even though nothing was actually wrong.

It now waits long enough. If it does catch the server mid-wake it says so, and trying again a moment later works.

Nothing else has changed.

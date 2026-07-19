# Screen 2 -- Flow 2

# Numeric Channel Entry Overlay

## Purpose

This overlay allows the user to quickly tune to a channel by entering
its channel number using the remote's numeric keypad.

The overlay is intentionally minimal, elegant and optimized for a TV
experience.

It never interrupts live playback.

------------------------------------------------------------------------

# Entry Conditions

The overlay appears immediately after the first numeric key is pressed
during live playback.

Each additional numeric key updates the entered channel number.

------------------------------------------------------------------------

# Design Principles

-   TV-first interaction.
-   Extremely lightweight.
-   Fast channel switching.
-   Minimal visual noise.
-   Consistent with the application's dark premium design language.

------------------------------------------------------------------------

# Visual Style

-   Floating centered panel.
-   Very dark bluish-charcoal surface.
-   Soft vertical gradient.
-   Rounded corners.
-   White primary text.
-   Muted gray secondary text.
-   Accent blue reserved for valid metadata when required.
-   No glow.
-   No heavy shadow.
-   No glass blur.

The overlay should feel calm, premium and unobtrusive.

------------------------------------------------------------------------

# Layout

The panel is centered on screen.

It contains only:

1.  Large channel number.
2.  Channel name (when recognized).
3.  Optional small status text for invalid entries.

The entered number is the visual focal point.

The channel name sits directly beneath with generous spacing.

No icons, keypad, buttons or decorative graphics are displayed.

------------------------------------------------------------------------

# Behaviour

## First Digit

-   Open the overlay.
-   Display the entered digit.
-   Start the entry timeout.

## Additional Digits

-   Append to the current number.
-   Restart the timeout.
-   If the number matches a valid channel, immediately display its
    channel name.

## Timeout

After approximately 2--3 seconds of inactivity:

-   Valid number:
    -   Tune to the channel.
    -   Close the overlay.
-   Invalid number:
    -   Display a brief "Channel not found" message.
    -   Dismiss automatically after a short delay.

The maximum accepted digits should adapt to the application's highest
configured channel number rather than being hard-coded.

------------------------------------------------------------------------

# DPAD / Remote Behaviour

## Numeric Keys

Update the entered channel number.

## OK

If the current number is valid:

-   Tune immediately.
-   Close the overlay.

If invalid:

-   Show "Channel not found".
-   Dismiss after a short delay.

## Back

-   Cancel numeric entry.
-   Close the overlay.
-   Continue playing the current channel.

------------------------------------------------------------------------

# Playback

Playback continues uninterrupted while entering numbers.

The overlay never pauses, buffers or restarts playback.

------------------------------------------------------------------------

# Motion

Opening: - Smooth fade-in with a subtle scale-up (150--180 ms).

Updating: - Digits update instantly. - Channel name crossfades
smoothly. - No flicker.

Closing: - Smooth fade-out (150--180 ms).

No bounce. No exaggerated animations.

------------------------------------------------------------------------

# State Rules

-   Re-entering a digit while the overlay is visible updates the
    existing overlay.
-   Never destroy and recreate the overlay for each key press.
-   Restart the timeout after every numeric input.
-   Once tuning completes, clear the entered number.

------------------------------------------------------------------------

# Exit Conditions

-   Successful channel tune.
-   User presses Back.
-   Invalid entry timeout completes.

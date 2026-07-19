# Screen 2 -- Flow 1

# Playback Information Overlay

## Purpose

This is the primary on-screen display (OSD) shown during live TV
playback.

It appears when: - Playback starts. - The user changes channels. - The
user presses the OK button while watching live TV.

The overlay automatically disappears after **5 seconds** of inactivity.

If another channel is selected before the timeout expires, the existing
overlay updates in place and the timeout restarts.

------------------------------------------------------------------------

# Design Principles

-   Minimal and informative.
-   Never interrupt playback.
-   Read-only information.
-   No playback controls.
-   Optimized for DTH/live TV rather than media playback.

------------------------------------------------------------------------

# Visual Style

-   Very dark bluish-charcoal translucent panels.
-   Subtle vertical gradient.
-   Flat layered surfaces.
-   White primary text.
-   Muted gray secondary text.
-   Accent blue only where appropriate.
-   Rounded corners with consistent spacing.
-   Smooth fade animations.
-   No glow, bounce or heavy effects.

------------------------------------------------------------------------

# Layout

## Top Left

-   Current group/category.

## Top Right

-   Current date.
-   Current time.

## Bottom Left

-   Large channel logo.
-   Channel number.
-   Channel name.

## Bottom Center

-   Current programme title.
-   Programme start and end time.
-   Read-only programme progress bar.
-   Next programme title.

## Bottom Right

Display available technical information, such as:

-   Resolution
-   Audio format
-   Frame rate (optional)
-   Subtitle indicator (only when available)

Only supported metadata should be shown.

------------------------------------------------------------------------

# Programme Progress

The progress indicator represents only the progress of the currently
airing programme.

Rules:

-   Informational only.
-   Never seekable.
-   Never draggable.
-   No scrub handle.
-   Updates automatically as time progresses.

------------------------------------------------------------------------

# Playback

Live playback continues uninterrupted.

Opening or closing the overlay never pauses, restarts or buffers
playback.

------------------------------------------------------------------------

# DPAD Behaviour

## OK

Display the overlay.

If already visible, reset the timeout.

## Channel Up / Down

Tune to the requested channel.

Immediately update overlay content.

Restart the timeout.

------------------------------------------------------------------------

# Motion

Opening: - Smooth fade-in (\~180 ms).

Updating: - Metadata updates in place. - No flicker.

Closing: - Smooth fade-out (\~180 ms).

------------------------------------------------------------------------

# Removed Controls

The overlay must not contain:

-   Pause
-   Play
-   Seek bar
-   Playback timeline
-   Previous/Next controls
-   Current playback timestamp
-   Live badge
-   Any transport controls

This overlay is purely informational.

------------------------------------------------------------------------

# Exit Conditions

-   Automatically hide after 5 seconds of inactivity.
-   Any supported interaction resets the timeout.
-   Playback remains visible at all times.

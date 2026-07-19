# Flow 3 -- Navigation Rail

## Purpose

Flow 3 is entered from Flow 2 when the user presses **LEFT** while the
Category Panel is focused.

This flow exposes the application's primary navigation destinations
while keeping the guide visible and preserving the user's browsing
context.

------------------------------------------------------------------------

# Entry Conditions

-   User is in Flow 2.
-   User presses LEFT.
-   Navigation Rail slides in from the left.
-   Focus lands on the previously active navigation destination.

------------------------------------------------------------------------

# Design Principles

-   Overlay-based navigation.
-   Preserve browsing context.
-   Never reset guide position.
-   TV remote first.
-   Lightweight transitions.

------------------------------------------------------------------------

# Visual Style

-   Same design language as Flows 1 and 2.
-   Very dark bluish-charcoal background.
-   Subtle vertical gradient.
-   Flat layered surfaces.
-   White primary text.
-   Muted gray secondary text.
-   Matte light-gray focus surface.
-   No glow.
-   No shadow.
-   No heavy translucency.

------------------------------------------------------------------------

# Layout

The screen consists of:

1.  Navigation Rail
2.  Category Panel
3.  Channel Column
4.  Program Grid

The guide remains fully visible.

The Category Panel remains visible but becomes inactive.

Approximate Navigation Rail width: 12--14% of the display.

The Category Panel automatically shifts to accommodate the rail.

------------------------------------------------------------------------

# Navigation Rail

Contains the application's primary destinations.

Examples include top-level sections such as live TV, settings, and other
application-specific destinations.

The available entries are determined by the application's supported
features.

Each item contains:

-   Icon
-   Label

Rows use equal height and consistent spacing.

------------------------------------------------------------------------

# Focus Behaviour

Initial focus:

-   Previously active navigation destination.

Scrolling:

-   Focus moves until approximately the vertical center.
-   After reaching the center, the focus remains visually fixed while
    the list scrolls beneath it.

Focus appearance:

-   Matte light-gray surface.
-   Subtle premium "pop" animation.
-   Duration approximately 150--180 ms.
-   No glow.
-   No bounce.
-   No exaggerated scaling.

------------------------------------------------------------------------

# DPAD Navigation

## Up

Move to previous navigation destination.

## Down

Move to next navigation destination.

## Right

Return to Flow 2.

Restore the previously focused category without resetting its scroll
position.

## OK

Open the selected application destination.

Behaviour depends on the selected destination.

## Left

No further navigation level.

Remain within the Navigation Rail.

## Back

Close the guide completely and return to live playback.

------------------------------------------------------------------------

# State Persistence

Moving between Flows 1, 2 and 3 must preserve:

-   Active navigation destination.
-   Active category.
-   Focused channel.
-   Focused program.
-   Vertical scroll position.
-   Horizontal timeline position.
-   Applied channel filter.

The user must always return to the exact browsing context.

------------------------------------------------------------------------

# Playback

Live playback continues while Flow 3 is open.

Opening or closing the Navigation Rail never interrupts playback.

------------------------------------------------------------------------

# Motion

Opening:

-   Smooth slide-in from the left.

Closing:

-   Smooth slide-out.

Transitions:

-   Approximately 150--180 ms.
-   No zoom.
-   No bounce.
-   Minimal easing.

------------------------------------------------------------------------

# Exit Conditions

RIGHT: Return to Flow 2.

OK: Open the selected application destination.

BACK: Close guide and return to full-screen playback.

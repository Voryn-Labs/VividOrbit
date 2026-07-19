# Flow 1A -- Context Actions Panel

## Purpose

This flow is triggered when the user performs a **long press on the OK
button** while a channel/program is focused within the guide.

The guide remains visible in the background.

This is an overlay, **not** a separate screen.

Closing the panel always restores focus to the exact program that was
selected before the panel opened.

------------------------------------------------------------------------

# Design Principles

-   Overlay the existing guide.
-   Never replace the guide.
-   Do not interrupt live playback.
-   Preserve the current guide position and timeline.
-   Opening and closing should feel lightweight and instantaneous.

------------------------------------------------------------------------

# Layout

-   Display as a contextual side panel anchored over the guide.
-   Keep the guide visible underneath.
-   Do not navigate away from the guide.
-   Opening the panel must not change the current channel selection,
    timeline position, or focused program.

------------------------------------------------------------------------

# Supported Actions

This panel contains all **channel-specific management features**
supported by the application.

Typical actions include:

-   Add or Remove from Favorites
-   Assign Static Channel Number
-   Add Channel to Group
-   Move Channel Between Groups
-   Multi-select Channels
-   Channel-specific Settings
-   Other channel management actions supported by the application

The visible actions are dynamically determined by the application's
available capabilities.

------------------------------------------------------------------------

# DPAD Navigation

## Up

Move to the previous action.

## Down

Move to the next action.

## OK

Execute the selected action.

## Back

Close the panel and return to the guide.

## Left

Close the panel and restore focus to the guide.

------------------------------------------------------------------------

# Focus Behaviour

-   Focus is trapped inside the panel while it is open.
-   The guide never receives focus until the panel is dismissed.
-   Closing the panel restores the exact previously focused program.

------------------------------------------------------------------------

# Playback

-   Live playback continues uninterrupted.
-   Opening this panel never restarts playback.

------------------------------------------------------------------------

# Motion

-   Smooth slide/fade transition.
-   No zoom effects.
-   No bounce.
-   No glow.
-   Minimal, responsive animation.

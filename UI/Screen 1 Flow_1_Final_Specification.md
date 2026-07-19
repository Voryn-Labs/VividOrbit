The following should be considered/superseed for the below text
---

# Scrolling & Focus Continuity

## Vertical Navigation

When navigating using the Up or Down buttons:

### Initial Navigation

- The focus moves normally between rows.
- The highlighted row travels downward or upward until it reaches approximately the vertical center of the visible guide.

### Continuous Navigation

Once the focus reaches the center region of the guide:

- The highlighted row remains visually fixed in the center.
- Additional Up or Down presses scroll the channel list underneath the fixed focus.
- The timeline position remains unchanged.
- The selected program maintains its horizontal time position while navigating vertically.

This creates a stable browsing experience where the user's eye remains on the focused row while surrounding content scrolls.

---

# Focus Animation

Changing focus should include a subtle navigation animation.

When moving between rows or program cells:

- The focus surface should smoothly translate to the next item.
- During the transition, the focused surface should slightly expand ("pop") before settling into its final size.
- The effect must remain subtle and premium.
- No glow.
- No bounce.
- No exaggerated scaling.
- Animation duration should remain approximately 150–180 ms.

The animation should provide clear visual feedback without becoming distracting.


# Flow 1 -- Guide Initial State (Final Specification)

## Purpose

This flow represents the initial Electronic Program Guide (EPG) overlay
displayed when the user presses the **Guide/Menu** button during live TV
playback.

This document is the implementation source of truth. The implementation
must follow this specification exactly and should not infer additional
behavior.

------------------------------------------------------------------------

# 1. Design Principles

-   TV remote (DPAD) first.
-   Fast, predictable navigation.
-   High information density while maintaining readability.
-   Smooth transitions with minimal visual noise.
-   No decorative animations.
-   No dependency on any third-party application's behavior.

------------------------------------------------------------------------

# 2. Layout

The guide occupies the entire interface.

There is **no preview video area**.

There is **no dedicated information header**.

The layout consists of:

1.  Timeline
2.  Channel Column
3.  Program Grid

------------------------------------------------------------------------

# 3. Visual Theme

-   Very dark bluish-charcoal background.
-   Extremely subtle vertical gradient.
-   Flat layered surfaces.
-   No blur.
-   No acrylic.
-   No heavy translucency.
-   Very low-contrast separators.
-   Primary text: white.
-   Secondary text: muted gray.

------------------------------------------------------------------------

# 4. Timeline

Displayed across the top.

Contains:

-   Current date
-   Current time
-   Future time markers

Characteristics:

-   Thin horizontal strip.
-   Approximately 6% of guide height.
-   Thin vertical dividers.
-   Time labels centered.
-   When viewing "now", display a thin current-time indicator spanning
    the grid.
-   Once horizontally scrolled away from "now", the current-time
    indicator is no longer shown.

------------------------------------------------------------------------

# 5. Channel Column

Fixed horizontally.

Approximate width: 28--30% of the screen.

Each row contains:

-   Channel Number
-   Channel Logo
-   Channel Name

Rules:

-   Channel column never scrolls horizontally.
-   Logo size is fixed.
-   Logos remain vertically centered.
-   Channel names truncate gracefully.
-   Uniform row height.

------------------------------------------------------------------------

# 6. Program Grid

Occupies remaining width.

Rules:

-   Width of program cells is determined strictly by duration.
-   Time defines horizontal alignment.
-   Every channel shares identical vertical time boundaries.
-   Never stretch or compress cells for visual balance.
-   Very short programs keep a minimum readable width; truncate text if
    required.
-   Program grid scrolls horizontally while the channel column remains
    fixed.

------------------------------------------------------------------------

# 7. Responsive Layout

Do not target a fixed number of visible channels.

Instead:

-   Preserve visual density.
-   Scale row height proportionally with display size.
-   Avoid oversized rows on large TVs.
-   Avoid cramped rows on small displays.

Target density equivalent to approximately 9--10 rows on a 1080p
display.

------------------------------------------------------------------------

# 8. Focus Behaviour

Initial focus:

-   Current program of the currently playing channel.

Focus appearance:

-   Solid light matte gray surface.
-   No glow.
-   No outline.
-   No scaling.
-   No shadow.

Navigation:

Up - Previous channel.

Down - Next channel.

Focus moves until reaching the final visible row. Only then does
vertical scrolling begin.

Horizontal time position is preserved while moving vertically.

------------------------------------------------------------------------

# 9. DPAD Navigation

## Left

-   Move to previous program.
-   Walk backwards one program at a time.
-   Never jump directly to the beginning.
-   Only when the first visible/current program is reached does another
    Left transition into Flow 2.

## Right

-   Move to next adjacent program.
-   When reaching the edge of the visible timeline, horizontally scroll
    the timeline and program grid together.
-   Channel column never moves.

## OK

Current program on current channel: - Close guide. - Continue playback.

Current program on another channel: - Tune channel. - Close guide.

Future program: - Tune selected channel. - Start current live
playback. - Never schedule recordings or reminders.

## Back

-   Close guide.
-   Return immediately to full-screen playback.

------------------------------------------------------------------------

# 10. Playback

Live playback continues while the guide is displayed.

Opening the guide never restarts playback.

------------------------------------------------------------------------

# 11. Context Actions

Long-press OK opens an anchored contextual panel.

Rules:

-   Overlay the existing guide.
-   Never open a separate screen.
-   Anchor to the focused channel row.
-   Closing restores focus to the exact previous position.

Supported actions:

-   Assign static channel number
-   Add to group
-   Remove from group
-   Favourite / Unfavourite
-   Multi-select
-   Bulk move channels

------------------------------------------------------------------------

# 12. Motion

-   Smooth panel transitions.
-   Approximately 150--180 ms.
-   No zoom.
-   No bounce.
-   No exaggerated easing.

------------------------------------------------------------------------

# 13. Layout Metrics

-   Channel column: \~29% width.
-   Program grid: \~71% width.
-   Timeline: thin (\~6% height).
-   Equal row heights.
-   Fixed logo box.
-   Consistent left text padding inside program cells.
-   Consistent vertical alignment across every row.

------------------------------------------------------------------------

# 14. Locked Behaviour

-   Channel column is the visual anchor.
-   Timeline and program grid always move together horizontally.
-   Program widths always represent actual duration.
-   Horizontal scrolling never affects channel alignment.
-   Vertical scrolling never changes the selected timeline position.
-   Flow 2 is entered only after pressing Left from the first
    visible/current program.

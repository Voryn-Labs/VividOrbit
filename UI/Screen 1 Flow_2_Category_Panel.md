# Flow 2 -- Category / Group Selection Panel

## Purpose

Flow 2 is entered from Flow 1 when the user presses LEFT while focused
on the first visible/current program.

This flow allows browsing channel groups/categories while keeping the
guide visible.

------------------------------------------------------------------------

# Entry Conditions

-   User is in Flow 1.
-   Focus is on the first visible/current program.
-   User presses LEFT.

The category panel slides in from the left.

------------------------------------------------------------------------

# Visual Style

-   Same visual language as Flow 1.
-   Very dark bluish-charcoal background.
-   Subtle vertical gradient.
-   Flat layered surfaces.
-   Very subtle separators.
-   White primary text.
-   Muted gray secondary text.
-   Accent blue only for active metadata.
-   Focus surface is matte light gray.
-   No glow, outline, shadow or exaggerated scaling.

------------------------------------------------------------------------

# Layout

The screen consists of:

1.  Category panel (left)
2.  Channel column (center)
3.  Program grid (right)

The timeline, channel column and program grid remain visible.

The category panel overlays the left side without replacing the guide.

Approximate category panel width: 25--28% of the display.

------------------------------------------------------------------------

# Category List

Each row contains one category/group.

Rules:

-   Equal row heights.
-   Long names truncate with ellipsis.
-   List scrolls vertically.
-   Category width never changes.

------------------------------------------------------------------------

# Behaviour

Changing focus immediately changes the active category.

No confirmation is required.

As focus moves:

-   Channel list updates immediately.
-   Program guide updates immediately.
-   Timeline position is preserved.
-   Horizontal scroll position is preserved.
-   Playback continues uninterrupted.

Categories behave as live filters rather than selectable menu items.

------------------------------------------------------------------------

# Focus Behaviour

Initial focus:

-   Currently active category.

Scrolling:

-   Focus moves normally until reaching approximately the vertical
    center.
-   Afterwards the focus remains visually fixed while the category list
    scrolls beneath it.

Focus appearance:

-   Light matte gray surface.
-   Slight premium "pop" animation (150--180 ms).
-   No glow.
-   No bounce.
-   No heavy scaling.

------------------------------------------------------------------------

# DPAD Navigation

## Up

Previous category.

## Down

Next category.

Changing focus immediately applies the filter.

## Right

Collapse the category panel.

Return to Flow 1.

Restore focus to the corresponding program/channel.

## OK

Same behaviour as RIGHT.

The category has already been applied while navigating.

OK simply exits the category panel.

## Left

Enter Flow 3 (Navigation Rail).

## Back

Close the guide completely.

Return to live playback.

------------------------------------------------------------------------

# Layout Rules

-   Channel column never moves horizontally.
-   Timeline and program grid remain synchronized.
-   Program widths remain proportional to duration.
-   Existing horizontal time position is never reset when changing
    categories.

------------------------------------------------------------------------

# Playback

Live playback continues while the category panel is open.

Opening or closing Flow 2 never interrupts playback.

------------------------------------------------------------------------

# Motion

Opening: - Smooth slide-in from the left.

Closing: - Smooth slide-out.

Transitions: - 150--180 ms. - No zoom. - No bounce. - No exaggerated
easing.

------------------------------------------------------------------------

# Exit Conditions

RIGHT or OK: Return to Flow 1.

LEFT: Enter Flow 3.

BACK: Close guide and resume full-screen playback.

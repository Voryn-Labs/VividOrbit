# Phase 10 -- UI Completion & Interaction Polish

## Purpose

Complete the remaining interactive dialogs and finalize focus, DPAD
navigation and animation behavior to achieve a production-quality TV
experience.

## Part 1 -- Remaining Functional Dialogs & Sub-screens

### Channel Search

-   Floating overlay matching the application theme.
-   Live filtering while typing.
-   DPAD navigation through results.
-   OK tunes immediately.
-   Restore previous guide state on exit.

### Audio Track Selector

-   Overlay listing available audio tracks.
-   Current track preselected.
-   OK switches immediately.
-   Playback continues uninterrupted.
-   Back restores previous focus.

### Channel Management Dialogs

Includes: - Assign Static Channel Number - Rename Channel - Edit
supported channel metadata

Rules: - Modal overlay. - Validate input. - Preserve focus after
closing. - Apply changes immediately.

### Import / Export Settings

-   Same visual language as Settings.
-   Confirmation before destructive actions.
-   Progress indication.
-   Success/failure feedback.
-   Never interrupt playback unnecessarily.

## Part 2 -- Focus & Input Polish

### Global DPAD Rules

-   Focus must never be lost.
-   Opening overlays saves focus.
-   Closing overlays restores exact previous focus.
-   Deterministic navigation only.

### Focus Animation

-   Shared FocusAnimator only.
-   150--180 ms.
-   Approx. 1.02x scale.
-   Matte light-gray focus.
-   No glow, bounce or heavy shadow.

### Overlay Focus

-   One overlay owns focus.
-   Background overlays never receive focus.
-   OverlayManager controls all overlay transitions.

### Input Consistency

-   UP/DOWN: vertical navigation.
-   LEFT/RIGHT: contextual navigation.
-   OK: activate.
-   BACK: close/return.
-   Numeric keys: Numeric Channel Entry.
-   Context trigger: Context Actions panel.

## Verification

-   Search filters correctly.
-   Audio switching works.
-   Dialogs persist changes.
-   DPAD focus remains stable.
-   Overlay state restores correctly.
-   Visual style is consistent across all dialogs.

## Completion Criteria

-   Remaining dialogs implemented.
-   Focus behavior unified.
-   Overlay behavior consistent.
-   Application reaches production-quality interaction polish.

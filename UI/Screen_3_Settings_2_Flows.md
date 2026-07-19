# Screen 3 -- Settings

## Flow 1 -- Settings Home

### Purpose

Opened when the user selects **Settings** from the Navigation Rail.

### Layout

-   Right-side settings panel slides in over the guide.
-   Guide remains dimmed and visible behind the panel.
-   Panel occupies approximately 35--40% of screen width.
-   Large title at the top.
-   Scrollable list of setting categories below.
-   Dark bluish-charcoal theme with subtle gradient.
-   White primary text, muted gray secondary text.
-   Matte light-gray focus highlight.
-   No glow, blur or heavy shadows.

### Navigation

-   **Up/Down:** Move between setting categories.
-   **OK/Right:** Open the selected category (Flow 2).
-   **Left/Back:** Close settings and return to the Navigation Rail with
    previous focus restored.

### Motion

-   Slide in/out from the right.
-   150--180 ms smooth transition.
-   Preserve guide state underneath.

------------------------------------------------------------------------

## Flow 2 -- Settings Category

### Purpose

Displays the options for the selected settings category.

### Layout

-   Same right-side panel and visual theme.
-   Header changes to the selected category name.
-   Scrollable list of settings.
-   Each row contains:
    -   Setting title
    -   Optional description
    -   Appropriate control (toggle, value, selector, etc.)
-   Functions are supplied by the application; this specification
    defines only the layout and interaction.

### Navigation

-   **Up/Down:** Navigate setting rows.
-   **OK:** Activate, toggle, or open the selected setting.
-   **Left:** Return to Flow 1 with the previously focused category
    restored.
-   **Back:** Return to Flow 1.

### State

-   Preserve scroll position and focused row while remaining inside the
    same category.
-   Returning from Flow 2 restores the previously focused category in
    Flow 1.

### Motion

-   Content transitions smoothly without replacing the entire panel.
-   No flicker, bounce or zoom.

### Playback

-   Live playback continues uninterrupted in the background.
-   Opening and closing settings never interrupts playback.

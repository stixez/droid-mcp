package io.droidmcp.overlay

/**
 * Configuration for a floating overlay shown via [OverlayController.show].
 *
 * Locked as a data class (rather than an overloaded `show` signature) so
 * adding callbacks or fields later stays non-breaking.
 *
 * **Rendering:** [iconRes] (if set) and [label] (if set) are laid out
 * horizontally in a single `LinearLayout` — icon first, then label. If both
 * are set, the label is rendered to the right of the icon with 16dp leading
 * padding. If neither is set, the overlay renders as a small empty chip.
 *
 * [onClick] is required. The others are optional:
 *  - [onLongPress] — fires on long-tap (default 500ms hold). Use for a
 *    secondary action like voice activation.
 *  - [onDragEnd] — fires once on release after a drag, reporting final screen
 *    coordinates so the host can persist position across sessions.
 *
 * [iconRes] holds a Drawable resource id. It is not declared `@DrawableRes`
 * because the overlay module deliberately doesn't pull `androidx.annotation`
 * into its dependency closure; the contract is informal — the caller is
 * expected to pass a valid resource id.
 */
data class OverlayConfig(
    val label: String? = null,
    /** Drawable resource id (informal `@DrawableRes` contract). */
    val iconRes: Int? = null,
    val onClick: () -> Unit,
    val onLongPress: (() -> Unit)? = null,
    val onDragEnd: ((x: Int, y: Int) -> Unit)? = null,
)

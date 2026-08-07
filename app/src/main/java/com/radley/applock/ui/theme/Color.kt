package com.radley.applock.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted verbatim from dribbble.com/shots/26185810 (Conceptzilla).
 *
 * Seven of these are the shot's published swatches; [Surface1] and [Ember] are derived,
 * because the source palette has no mid-elevation surface and no error tone.
 */

/** #030303 — app background. The shot's darkest swatch. */
val Ink = Color(0xFF030303)

/** Derived: [Ink] warm-shifted for cards and keypad keys that must read as raised. */
val Surface1 = Color(0xFF141110)

/** #56352C — deep brown, for elevated/selected surfaces and gradient washes. */
val Cocoa = Color(0xFF56352C)

/** #965F4E — the accent. Filled PIN dots, switches, focus rings. */
val Clay = Color(0xFF965F4E)

/** #C6B9B2 — warm taupe highlight. */
val Taupe = Color(0xFFC6B9B2)

/** #F5F3F1 — primary text and on-primary. */
val Bone = Color(0xFFF5F3F1)

/** #9A9593 — secondary text. */
val Ash = Color(0xFF9A9593)

/** #646363 — outlines and disabled states. */
val Slate = Color(0xFF646363)

/** Derived: [Clay] pushed toward red, so errors stay inside the palette's warmth. */
val Ember = Color(0xFFB4503C)

# Design decisions

The viewer uses the official Material 3 Expressive Compose implementation. The home screen has a flexible app bar, an original sample and a single primary file-opening action. It deliberately has no welcome flow, promotional copy, badges or navigation destinations without content.

The model is the main content. Contextual controls live in a floating toolbar with at least 16dp of window clearance and 48dp targets. A single animation toggle has greater emphasis. View settings and factual model information use separate bottom sheets. These sheets scroll, avoiding fixed-height text containers for localization and larger fonts. Full screen has a visible exit button and supports system Back.

The expressive theme supplies its type scale, default shapes and spring motion. Medium flexible app bars replace older medium app bars. Buttons and icon buttons use their expressive shape APIs. Loading uses the expressive indicator. Wallpaper-derived Material color roles are the default; non-dynamic light and dark palettes are available.

The original GPT-image icon has a separate solid adaptive background, transparent foreground and monochrome alpha layer. All layers follow the 108dp Android adaptive icon coordinate system with visible content inside the 66dp safe area. The operating system tints the monochrome glyph. `icon-preview.png` shows the adaptive mask and several themed variants.

Words describe actions and states. Error messages state a usable next step. Instructions appear only inside the controls sheet. Strings are separate resources, values use locale-sensitive formatters, and directional navigation mirrors independently of physical camera controls. All 23 translations were authored by the requested GPT-6 Luna max subagent; resource parity is validated, with no claim of human linguistic review.

Accessibility includes standard component roles, labels for actionable icons, headings, loading announcements, a readable model surface, custom camera actions and zoom state, equivalent visible controls, keyboard navigation, native scrollable dialogs and sheets, and 48dp targets. Device checks and their limits are recorded separately in `verification/RESULTS.md`.

Official documents consulted are linked in the project README. Browser reference snapshots were used while reading the M3 Expressive overview, toolbars, buttons, motion, bottom sheets and Apple's Writing guidance. These are research material, not application content.

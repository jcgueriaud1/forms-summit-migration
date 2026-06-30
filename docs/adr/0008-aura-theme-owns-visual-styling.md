# The Aura theme owns visual styling; Forms visual attributes are dropped

Oracle Forms carries per-item and per-visual-attribute styling: typography
(`FontSize`/`FontName`/`FontStyle`/`FontWeight`/`FontSpacing`), colour
(`BackColor`/`ForegroundColor`/`PromptForegroundColor`), prompt placement and
alignment, borders/bevels, fills, and named `VisualAttribute` groups. A literal
migration would port each of these onto individual Vaadin components, reproducing
Forms' ad-hoc, per-field look field-by-field.

**Decision:** the **Vaadin Aura theme** is the single owner of visual styling for
the POC. Aura is loaded via `@StyleSheet(Aura.STYLESHEET)` on the
`AppShellConfigurator` (`Application`), and it must be the **first** `@StyleSheet`
so application stylesheets layer on top of it. Forms visual attributes are
therefore **dropped, not translated** — encoded in `migration-rules.yaml` as the
"Aura ignore list": typography, colour, prompt layout/alignment, borders, fills,
rendering, and `VisualAttribute*` groups all carry `ignore: "Aura theme owns …"`
consistently across every view-side target.

A corollary: **no Lumo.** Aura and Lumo tokens/utility classes must not be mixed
(see the theming guidance), so `LumoUtility.*` classes and `--lumo-*` properties
are not used. Where a specific element needs sizing or spacing, style it with
Aura/shared theme tokens (`var(--aura-font-size-*)`, `var(--vaadin-gap)`,
`var(--vaadin-padding)`, …) rather than hardcoded values or Lumo utilities.

**Why:** the POC is a credibility artifact for Forms architects. A consistent,
modern theme that "just works" across all migrated views is a stronger
demonstration than faithfully reproducing Forms' per-field visual sprawl, and it
keeps the migration rules legible — visual attributes have one disposition (drop),
not dozens of bespoke mappings. Centralising styling in the theme also means a
look-and-feel change is a theme change, not a sweep across every view. The choice
shapes styling app-wide, so it is recorded; it is reversible in principle by
swapping the theme stylesheet, but the ignore list assumes the theme, not the
forms, defines appearance.

**Consequence / seam (scorecard behavioral-parity row):** migrated screens will
**not** look pixel-identical to the Forms originals — bespoke per-field fonts and
colours are intentionally gone, replaced by Aura's consistent system. This is an
honest, deliberate difference: visual fidelity is traded for theme-driven
consistency and maintainability.

# Prototype verdict — compact Emerald theme + surface treatment (Aura)

**Question:** With Aura owning all visual styling (ADR-0008), what brand palette,
density, and section structure should the migrated app use? A Forms migration
wants a **compact** layout.

**Decision (2026-07-01): compact + Emerald + bordered panels.**

- **Density:** compact — `--aura-base-size: 12`, `--aura-base-font-size: 12`,
  edge-to-edge inset, flat surfaces (`--aura-surface-level: -0.5`), square radius,
  Inter font.
- **Brand accent:** Emerald (`#009966` light / `#34D399` dark) on a Neutral
  background. Secondary buttons stay neutral; accent is reserved for primary.
- **Structure:** form sections grouped into **bordered panels** — a `.panel`
  wrapper with a subtle 1px outline + container fill.

**How chosen:** `/theme-lab` prototype (`ThemeLabView`) with a floating switcher.
First round compared brand palettes (Corporate Blue / Fresh Emerald / Premium
Indigo) → Emerald picked. Second round fixed emerald + compact and compared
surface treatments (Flat / Bordered panels / Elevated cards) → **Bordered panels**
picked over flat (too plain) and elevated cards (heavier than a dense data-entry
form wants).

**Rejected:** flat (no visual grouping of fields); elevated cards (shadow +
raised surface too showy / less dense).

**Now live:** the decision is baked into
`src/main/resources/META-INF/resources/summit-theme.css` (imported by
`styles.css`, loaded after Aura via `@StyleSheet("styles.css")` in
`Application.java`). The prototype view + switcher + losing `[data-theme]` blocks
have been deleted. The real ORDERS view should wrap its header + line-item
sections with the `.panel` class to get the chosen look.

---
name: Tessle
description: A monochrome, frosted-glass workspace where every claim is shown next to the fact that proves it.
colors:
  ground-light: "oklch(0.975 0.002 260)"
  surface-light: "oklch(1 0 0)"
  surface-2-light: "oklch(0.965 0.004 260)"
  ink-light: "oklch(0.2 0.012 260)"
  ink-2-light: "#5b6270"
  line-light: "oklch(0.915 0.004 260)"
  line-strong-light: "oklch(0.8 0.006 260)"
  accent-light: "#d63a14"
  on-accent-light: "#ffffff"
  ground-dark: "oklch(0.155 0.006 260)"
  surface-dark: "oklch(0.205 0.007 260)"
  surface-2-dark: "oklch(0.235 0.008 260)"
  ink-dark: "oklch(0.96 0.003 260)"
  ink-2-dark: "#a4abb8"
  line-dark: "oklch(0.3 0.008 260)"
  line-strong-dark: "oklch(0.42 0.01 260)"
  accent-dark: "#ff6b45"
  on-accent-dark: "#1a0a05"
  ok-bg-light: "oklch(0.94 0.04 160)"
  ok-fg-light: "oklch(0.38 0.08 160)"
  warn-bg-light: "oklch(0.95 0.06 85)"
  warn-fg-light: "oklch(0.42 0.09 70)"
  bad-bg-light: "oklch(0.94 0.04 25)"
  bad-fg-light: "oklch(0.45 0.15 25)"
  ok-bg-dark: "oklch(0.3 0.05 160)"
  ok-fg-dark: "oklch(0.86 0.1 160)"
  warn-bg-dark: "oklch(0.32 0.06 85)"
  warn-fg-dark: "oklch(0.88 0.1 85)"
  bad-bg-dark: "oklch(0.31 0.07 25)"
  bad-fg-dark: "oklch(0.85 0.1 25)"
typography:
  title:
    fontFamily: "Geist, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(1.75rem, 1.2rem + 2vw, 2.5rem)"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.03em"
  body:
    fontFamily: "Geist, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "'tnum' 1, 'cv11' 1"
  label:
    fontFamily: "Geist, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
  column-head:
    fontFamily: "Geist, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    letterSpacing: "0.04em"
  mono:
    fontFamily: "Geist Mono, ui-monospace, monospace"
rounded:
  sm: "6px"
  md: "8px"
  lg: "10px"
  xl: "12px"
  pill: "999px"
spacing:
  xs: "0.375rem"
  sm: "0.5rem"
  md: "0.75rem"
  lg: "1rem"
  xl: "2rem"
  rail: "264px"
components:
  button:
    backgroundColor: "transparent"
    textColor: "{colors.ink-light}"
    rounded: "{rounded.md}"
    padding: "0 1rem"
    height: "2.5rem"
  button-primary:
    backgroundColor: "{colors.accent-light}"
    textColor: "{colors.on-accent-light}"
    rounded: "{rounded.md}"
    padding: "0 1rem"
    height: "2.5rem"
  button-small:
    rounded: "{rounded.md}"
    padding: "0 0.625rem"
    height: "2rem"
  input:
    textColor: "{colors.ink-light}"
    rounded: "{rounded.md}"
    padding: "0 0.75rem"
    height: "2.5rem"
  pill:
    backgroundColor: "{colors.surface-2-light}"
    textColor: "{colors.ink-2-light}"
    rounded: "{rounded.pill}"
    padding: "0.125rem 0.625rem"
  nav-link:
    textColor: "{colors.ink-2-light}"
    rounded: "{rounded.md}"
    padding: "0 0.75rem"
    height: "2.5rem"
  nav-link-current:
    backgroundColor: "{colors.surface-light}"
    textColor: "{colors.ink-light}"
  menu:
    rounded: "{rounded.xl}"
    padding: "0.5rem"
    width: "17rem"
---

# Design System: Tessle

## Overview

**Creative North Star: "Monochrome Proof"**

A neutral scale carries the whole interface and one vermilion accent is spent rarely. Frosted glass (added at the user's request) gives the shell its depth: the rail, top bar, popovers and sign-in panel blur a near-neutral, cool-tinted ambient mesh. The mesh is deliberately not warm or teal, so the accent stays the only warm colour on screen. Both themes are first-class: light is the default, dark follows the OS preference unless the user forces a theme.

The system is dense but calm, built for long working sessions by builders and developers. Its signature is the proof line: a claim paired with the fact that proves it, as in a role pill beside what that role allows. Screens say less and show the evidence.

Divergence noted: the direction contract names a light vermilion of #F04A24. The build ships #d63a14, which reaches 4.7:1 with white. The build is normative.

**Key Characteristics:**
- Two real themes, set before first paint via `data-theme` on `<html>`; "system" stores nothing.
- Glass for shell and overlays; near-opaque glass for data tables.
- One accent, used for the primary action, focus, caret/selection, and the current location.
- Geist at tight tracking with tabular figures.
- One soft rise on entry; reduced motion and reduced transparency honoured.

## Colors

A cool-tinted neutral ladder (hue 260) with a single vermilion accent and three muted status pairs.

### Primary
- **Vermilion** (light #d63a14, dark #ff6b45): the primary button fill, the focus ring, caret and accent-color of form controls, the current-nav icon, and the 10% tint behind owner/admin pills. White on light vermilion is 4.7:1; dark text (#1a0a05) on dark vermilion is 6.8:1.

### Neutral
- **Ground** (light oklch(0.975 0.002 260), dark oklch(0.155 0.006 260)): page background, under the fixed ambient mesh.
- **Surface** (white / oklch(0.205 0.007 260)): raised planes, the basis for glass mixes.
- **Surface 2** (oklch(0.965 0.004 260) / oklch(0.235 0.008 260)): hover fills, pill and table-header backing.
- **Ink** (oklch(0.2 0.012 260) / oklch(0.96 0.003 260)): primary text.
- **Ink 2** (#5b6270 / #a4abb8): secondary text, nav at rest, placeholders, proof-line copy.
- **Line / Line Strong** (oklch(0.915 …) and oklch(0.8 …) light; oklch(0.3 …) and oklch(0.42 …) dark): hairline dividers, and control borders respectively.

### Status
- **OK, Warn, Bad** each a background and foreground pair per theme (see frontmatter). Muted, always paired with text; never colour alone.

### Named Rules
**The One Voice Rule.** The accent is the only chromatic colour in the chrome. If a second hue is needed for meaning, it is a status pair with text, not decoration.

**The Cool Mesh Rule.** The ambient mesh stays near-neutral and cool (three radial lobes, hue 250-262). No warm or teal washes.

## Typography

**Display, Body Font:** Geist (via next/font, with ui-sans-serif, system-ui, sans-serif)
**Mono Font:** Geist Mono (with ui-monospace), used for code-like values.

**Character:** One family at 400, 600 weights, tight tracking on the title, tabular figures everywhere (`tnum`) so numbers align in tables.

### Hierarchy
- **Title** (600, clamp(1.75rem, 1.2rem + 2vw, 2.5rem), 1.1, -0.03em, balanced wrap): the page title, the largest step.
- **Body** (400, 0.9375rem, 1.5): all running text and table cells.
- **Label** (600, 0.8125rem to 0.875rem): pills, field labels, small buttons.
- **Column head** (600, 0.75rem, 0.04em, uppercase): table column headers only.

### Named Rules
**The Two Weights Rule.** 400 for reading, 600 for naming. No other weights.

## Layout

At 1024px and above: a 264px sticky glass rail (logo, workspace switcher, primary nav) beside a column holding a 56px sticky glass top bar (breadcrumb left; theme toggle and account menu right) and a centred main of at most 64rem (`max-w-5xl`) with 1rem/2rem horizontal and 2rem/2.5rem vertical padding. Below 1024px the rail is replaced by a glass strip under the top bar holding the switcher and a two-column nav. A skip link is the first focusable element. Spacing runs on a 0.25rem-based Tailwind scale; controls are 2.5rem tall (2rem small) for comfortable targets. The proof line caps at 68ch.

## Elevation & Depth

Depth is optical rather than structural: translucent glass over a mesh, a hairline lit edge, and a soft offset shadow. Nothing else casts shadows. Current nav gets a 1px hairline ring instead of a shadow.

### Shadow Vocabulary
- **Glass shadow, light** (`0 0 0 1px ink 6%, 0 1px 0 0 white 80% inset, 0 10px 30px -12px rgb(20 24 40 / 0.2)`): rail, top bar, popovers, tables, sign-in panel.
- **Glass shadow, dark** (`0 1px 0 0 white 8% inset, 0 14px 36px -14px rgb(0 0 0 / 0.7)`).
- **Glass fill**: 56% surface (46% dark); **strong** 82% (78% dark). **Edge**: white 85% (18% dark). **Blur**: `blur(24px) saturate(1.8)`.

### Named Rules
**The Opaque Data Rule.** Tables and popover menus use the strong glass fill. Figures must keep contrast; frost is for chrome.

**The Fallback Rule.** Under `prefers-reduced-transparency: reduce`, or where backdrop-filter is unsupported, glass and strong glass collapse to solid surface and blur to none.

**The Unprefixed Blur Rule.** Write only the unprefixed `backdrop-filter` in CSS. Next's CSS optimizer drops the property when a `-webkit-` form is authored, and Chromium ignores the prefixed form, so the glass silently vanishes.

## Shapes

Soft but disciplined: controls and nav links 8px, focus ring 6px, tables 10px, popovers 12px, pills fully round. Borders are 1px hairlines everywhere. Rail and top bar are square, with a border on their inner edge only.

## Components

### Buttons
- **Shape:** 8px radius, 2.5rem tall (small 2rem), 1px strong-line border, weight 600.
- **Default (ghost):** transparent with hairline; hover fills Surface 2; active nudges 1px down.
- **Primary:** solid vermilion with on-accent text; hover mixes 12% ink into the fill.
- **Quiet:** no border, Ink 2 text. **Disabled:** 50% opacity, not-allowed cursor.

### Inputs / Fields
- **Style:** 2.5rem, 8px radius, strong-line border, 80% translucent surface fill. Select shares the style at auto width. Fields stack label (600, 0.875rem) over control with a 0.375rem gap; hints are Ink 2 at 400.
- **Focus:** global 2px vermilion outline with 2px offset; caret and accent-color are vermilion.

### Pills
Round, hairline, Surface 2, 600 at 0.8125rem. Owner and admin take the vermilion tint and an Ink label. State is carried by the word, not the colour alone.

### Navigation
Nav links are 2.5rem, Ink 2 with an icon; hover fills Surface 2; the current page (`aria-current="page"`) gets Surface fill, Ink text, a hairline ring and a vermilion icon.

### Data Tables
Near-opaque glass wrapper (10px, scrolls horizontally), tabular figures, uppercase 0.75rem column heads on a translucent Surface 2, hairline row dividers, Surface 2 row hover, numeric columns right-aligned (`.num`). Cell padding tightens on narrow screens.

### Menus
Native Popover API (light dismiss, Escape, focus return): 12px, strong glass, 17rem minimum, 0.5rem padding, 2.5rem items with Surface 2 hover. They fade and lift 4px in over 0.16s.

### Proof Line (signature)
A wrapping row under the page title, max 68ch, pairing a claim with its evidence, e.g. the caller's role pill followed by what that role allows. Use it wherever a screen asserts something about the user's state.

### Motion
One gesture: children of `.rise` rise 8px and fade in over 0.5s (ease `cubic-bezier(0.16, 1, 0.3, 1)`), staggered 0.04s for the first four. Control transitions are 0.18s on the same ease. Under reduced motion all durations collapse to 0.01ms.

### Icons
lucide-react, decorative (`aria-hidden`), one stroke weight (library default), sized 1.125rem in nav.

## Do's and Don'ts

### Do:
- **Do** define every colour for both themes and check AA in each.
- **Do** pair every status or role colour with a word.
- **Do** use the strong glass fill for anything that carries data.
- **Do** keep new screens behind the proof-line pattern: title, proof line, actions, content.
- **Do** author `backdrop-filter` unprefixed only.
- **Do** honour reduced motion and reduced transparency in anything new.

### Don't:
- **Don't** add a second accent hue or gradient fills on buttons.
- **Don't** build card-grid dashboards; use tables and proof lines.
- **Don't** put warm or teal washes into the ambient mesh.
- **Don't** put body text on thin glass.
- **Don't** introduce weights other than 400 and 600.

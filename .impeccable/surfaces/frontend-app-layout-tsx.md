---
version: 1
slug: "frontend-app-layout-tsx"
primary_target: "frontend/app/layout.tsx"
related_targets: []
---

# App shell and screens (Operate)

Scope: the whole signed-in app shell plus sign-in, Apps, Members and invitation-accept screens. Mode: Operate. Both a light and a dark theme are mandatory (PRODUCT.md). The user asked for glassmorphism as part of the look.

Audience and task: builders and developers working all day in one workspace, switching between workspaces, creating Apps, managing members. Constraints: WCAG 2.2 AA in both themes, keyboard operation, reduced-motion and reduced-transparency respected, works to phone width.

Unresolved: the real data surfaces (forms, reports, pages) do not exist yet; the shell must take them without redesign.

## Direction contract

THESIS: Monochrome Proof with frosted glass. A neutral scale carries everything, one vermilion accent is spent rarely, and every claim the screen makes is paired with the fact that proves it. It refuses the card-grid dashboard and the colourful SaaS gradient.

OWN-WORLD: Near-white to near-black neutral scale in two true themes, vermilion (#F04A24 light, #FF6B45 dark) only for the primary action and live focus. Geist at two weights, tight tracking, tabular figures. Frosted glass (backdrop blur plus saturate, a hairline light edge, soft offset shadow) for the rail, top bar, popovers and the sign-in panel over a near-neutral, cool-tinted ambient mesh (glass needs light to frost; no warm or teal washes, so the vermilion accent stays the only warm colour); data tables stay near-opaque so figures keep contrast. Ghost buttons with hairlines; the primary fills solid. Soft rise as content enters.

STORY: A signed-in person sees at once which workspace they are in, what their role lets them do, and the Apps in front of them, then acts without hunting. A signed-out visitor gets one calm panel to enter an email and a code.

FIRST VIEWPORT: 1440x900. Left, a 264px glass rail: workspace switcher at the top, nav (Apps, Members) below, account at the foot. Top, a glass bar with the breadcrumb on the left and theme toggle plus account menu on the right. Centre column: page title Apps at the largest step, then one proof line stating the caller's role and what it allows, then search and the primary New app, then the table of Apps. Primary action sits top right of the content column.

FORM: Monochrome Proof (digital-design-canon-monochrome-product-marketing), a dealt challenger chosen from re-roll 1 of seed 468222a9; the glass treatment is the user's addition.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

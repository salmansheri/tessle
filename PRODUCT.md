# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Two audiences, treated as equals by the shell:

- **Non-technical builders**: operations, HR, finance and admin staff who create forms, reports and custom pages without writing code, manually or with AI help.
- **Developers and IT**: people who extend those pages with custom HTML and React, connect data sources, and administer workspaces.

Both live in the same workspace, switch between several workspaces, and are signed in for long working sessions. Roles in a workspace are owner, admin, builder, editor and viewer, so the same screen must make sense to someone who may only read it.

## Product Purpose

Tessle is a multi-tenant low-code platform. People build forms, reports and pages on top of the databases their organization already runs, publish them to colleagues, and extend them with code when needed. Success is a non-developer shipping a working internal tool against real company data without waiting on engineering, while IT keeps control of access and data.

## Positioning

It works on the databases a company already has. One form or report definition runs unchanged on PostgreSQL, Oracle, MSSQL and MySQL, with row- and column-level permissions enforced for views and exports. Competitors that copy data into their own store or support a single database cannot truthfully say this.

## Operating Context

- Used inside an organization, by signed-in members of a workspace; some forms may later be public.
- A person can belong to several workspaces and switches between them; everything shown is scoped to the active workspace.
- Everything a user builds is a versioned definition (form, report, page) that moves from draft to published, and apps group definitions.
- AI output is always a draft that a human reviews and publishes, never auto-published.
- Custom HTML/React runs in a sandbox on a separate origin.

## Capabilities and Constraints

Built today (Milestone 1): email-code sign-in, cookie sessions with silent renewal, sign out and sign out everywhere, workspaces with a switcher, members with roles and email invitations, and apps (create and list, role-enforced). Google sign-in is deferred.

Planned, in order: data source manager, form builder with submissions, report builder with Excel/CSV/PDF export, visual page builder, AI builder, in-app messaging, sandboxed custom code, workflows and API keys.

Terminology is fixed (see `CONTEXT.md`): Workspace, Membership, Active Workspace, App, Definition, Form, Report, Page, Draft, Published version, Data source, Access rule, Submission. Avoid "tenant", "organization", "project" and "config" in the interface.

Technical: Next.js App Router, React 19, Tailwind 4, TypeScript strict; all server state through generated React Query hooks. GSAP (free, including plugins) is available for motion and must respect `prefers-reduced-motion`.

## Brand Commitments

"Tessle" is a working name; no logo, palette, typography or voice has been committed to, so the visual identity is open, with one binding requirement from the user: **the interface must ship both a light and a dark theme** (not a single theme with a dark variant bolted on). Both must meet the accessibility standard below.

## Evidence on Hand

No customers, testimonials, case studies, benchmarks or pricing exist. Future work must not invent any.

## Product Principles

1. **Your data stays where it is.** The interface should make it obvious which database and which workspace anything belongs to; never imply data is copied or owned by Tessle.
2. **One calm surface for two kinds of people.** A builder and a developer share screens; advanced controls appear when reached for, not as clutter for everyone.
3. **A human approves.** Drafts, diffs and publish steps are explicit; nothing consequential happens silently.
4. **Roles are visible, not surprising.** What a viewer cannot do is shown as such, with the reason, instead of a failing button.
5. **Boring where it should be, sharp where it matters.** This is a work tool used all day; trust and speed come before spectacle.

## Accessibility & Inclusion

WCAG 2.2 AA across all screens: sufficient contrast in light and dark, full keyboard operation, visible focus, screen-reader labels, reduced-motion support, and layouts that work down to phone width.

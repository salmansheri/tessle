# Tessle

A multi-tenant low-code platform where non-developers build forms, reports and pages (manually or with AI), and developers extend it with custom HTML and React.

## Language

### Tenancy

**Workspace**:
An organization's isolated space on the platform. Owns its Apps, Data sources and Memberships.
_Avoid_: Tenant, organization, account

**User**:
A person who signs in. May belong to many Workspaces.
_Avoid_: Account, member (use Membership for the relationship)

**Membership**:
A User's role within one Workspace (owner, admin, builder, editor, viewer).
_Avoid_: Permission, seat

**Data source**:
A connection to an external database, owned by exactly one Workspace and never shared.
_Avoid_: Connection, database, tenant database

**Access rule**:
A row or column restriction attached to a Data source or table, applying to every Form, Report and export that touches it. Distinct from a Membership role.
_Avoid_: Permission, row policy

### Building

**App**:
A container inside a Workspace holding Definitions. The unit of export/import and environment promotion.
_Avoid_: Project, solution

**Definition**:
A versioned JSON artifact that the platform interprets at runtime. Always a Form, a Report or a Page.
_Avoid_: Config, schema (a Definition has a schema, it is not one), template

**Form**:
A Definition describing fields, validation and logic for collecting data.

**Report**:
A Definition describing a query over a Data source and how to present or export the result.

**Page**:
A Definition describing a screen: a visual component tree, or custom HTML or React code.

**Draft**:
The editable, unpublished state of a Definition. AI output is always a Draft.

**Published version**:
An immutable version of a Definition that end users run.

### Data

**Submission**:
One filled-in Form, stored with the Form version it was submitted against.
_Avoid_: Response, entry, record

**Platform store**:
The built-in storage where Submissions live by default. Reports can read it like any Data source.
_Avoid_: Internal database

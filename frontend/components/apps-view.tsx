"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search } from "lucide-react";
import { useId, useState } from "react";
import { RoleNote } from "@/components/role-note";
import { CreateWorkspaceForm } from "@/components/workspace-menu";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import { listAppsQueryKey } from "@/lib/api/generated/@tanstack/react-query.gen";
import { createApp, listApps } from "@/lib/api/generated/sdk.gen";
import { withRenewal } from "@/lib/api/session";
import { useWorkspace } from "@/lib/api/use-workspace";
import { formatAbsolute, formatRelative } from "@/lib/format";

const CAN_BUILD = ["owner", "admin", "builder"];

function createMessage(error: unknown): string {
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return "Your role cannot create apps.";
    if (error.status === 409) return "An app with that name already exists in this workspace.";
    if (error.status === 400) return "Use 1 to 80 visible characters, without control characters.";
  }
  return "Could not create the app. Please try again.";
}

export function AppsView() {
  const queryClient = useQueryClient();
  const { workspaces, active, role, isPending } = useWorkspace();
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const formId = useId();

  // Mirrors the server (Role.atLeast(builder) and is_builder). The server is the authority; this only
  // decides whether to show the form, so drift shows up as a 403 message, never as a security hole.
  const canCreate = !!role && CAN_BUILD.includes(role);

  const apps = useQuery({
    // The workspace id is part of the key so switching workspaces loads that workspace's apps.
    queryKey: [...listAppsQueryKey(), active?.id],
    queryFn: async () => (await unwrap(withRenewal(() => listApps()))).data ?? [],
    enabled: !!active,
    retry: false,
  });

  const create = useMutation({
    mutationFn: async (appName: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => createApp({ body: { name: appName } })));
    },
    onSuccess: async () => {
      setName("");
      setCreating(false);
      await queryClient.invalidateQueries({ queryKey: listAppsQueryKey() });
    },
  });

  if (isPending) return <p role="status" className="text-ink-2">Loading…</p>;

  if (!active) {
    const hasAny = (workspaces?.length ?? 0) > 0;
    return (
      <div className="rise grid gap-5">
        <h1 className="page-title">{hasAny ? "Choose a workspace" : "Create your first workspace"}</h1>
        <p className="proof">
          {hasAny
            ? "Pick one from the menu at the top of the sidebar, or create another."
            : "A workspace is where your team's apps, data sources and members live. You become its owner."}
        </p>
        <CreateWorkspaceForm />
      </div>
    );
  }

  const shown = (apps.data ?? []).filter((a) => a.name.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <div className="rise grid gap-6">
      <div className="grid gap-3">
        <h1 className="page-title">Apps</h1>
        {role && <RoleNote role={role} />}
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <label className="relative min-w-52 flex-1">
          <span className="sr-only">Search apps</span>
          <Search aria-hidden className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-2" />
          <input className="input pl-9" placeholder="Search apps" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        {canCreate && (
          <button type="button" className="btn btn-primary" aria-expanded={creating} aria-controls={formId} onClick={() => setCreating((v) => !v)}>
            <Plus aria-hidden className="size-4" />
            New app
          </button>
        )}
      </div>

      {creating && canCreate && (
        <form
          id={formId}
          className="glass flex flex-wrap items-end gap-3 rounded-xl p-4"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate(name.trim());
          }}
        >
          <label className="field min-w-52 flex-1">
            App name
            <input className="input" autoFocus required maxLength={80} value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <button type="submit" className="btn btn-primary" disabled={create.isPending || name.trim() === ""}>
            Create app
          </button>
          <button type="button" className="btn btn-quiet" onClick={() => setCreating(false)}>
            Cancel
          </button>
          {create.isError && (
            <p role="alert" className="basis-full text-[0.8125rem] text-[var(--bad-fg)]">
              {createMessage(create.error)}
            </p>
          )}
        </form>
      )}

      {apps.isError && (
        <p role="alert">Could not load the apps. If this keeps happening, restart the backend and reload.</p>
      )}

      {apps.data && (
        <div className="table-wrap">
          {shown.length === 0 ? (
            <div className="grid gap-1 p-8 text-center">
              <p className="font-semibold">{apps.data.length === 0 ? "No apps yet" : "No apps match your search"}</p>
              <p className="text-ink-2">
                {apps.data.length === 0
                  ? canCreate
                    ? "Create the first app to start building forms, reports and pages."
                    : "Someone with a builder role or above can create the first one."
                  : "Try a different name."}
              </p>
            </div>
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col" className="num">Created</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((app) => (
                  <tr key={app.id}>
                    <td className="font-semibold">{app.name}</td>
                    <td className="num text-ink-2">
                      <time dateTime={app.createdAt} title={formatAbsolute(app.createdAt)}>
                        {formatRelative(app.createdAt)}
                      </time>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

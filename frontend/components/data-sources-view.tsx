"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, CircleCheck, CircleDashed, Pencil, Plus, PlugZap } from "lucide-react";
import { useId, useState } from "react";
import { RoleNote } from "@/components/role-note";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import { listDataSourcesQueryKey } from "@/lib/api/generated/@tanstack/react-query.gen";
import { createDataSource, enableDataSource, listDataSources, testDataSource, updateDataSource } from "@/lib/api/generated/sdk.gen";
import type { DataSourceResponse, TestResponse } from "@/lib/api/generated/types.gen";
import { withRenewal } from "@/lib/api/session";
import { type RoleName, useWorkspace } from "@/lib/api/use-workspace";
import { formatAbsolute, formatRelative } from "@/lib/format";

const MANAGERS: RoleName[] = ["owner", "admin"];
const BUILDERS: RoleName[] = ["owner", "admin", "builder"];

const ROLE_TEXT: Record<RoleName, string> = {
  owner: "You can add, test, enable and change Data sources.",
  admin: "You can add, test, enable and change Data sources.",
  builder: "You can see which Data sources exist. Connection details and changes are for owners and admins.",
  editor: "Data sources are for builders and above.",
  viewer: "Data sources are for builders and above.",
};

const TYPE_LABEL: Record<string, string> = { postgresql: "PostgreSQL", mysql: "MySQL", oracle: "Oracle", sqlserver: "SQL Server" };

type Action = "save" | "test" | "enable";

function failure(error: unknown, action: Action, fallback: string): string {
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return "Your role cannot do that.";
    if (error.status === 404) return "That Data source no longer exists.";
    if (error.status === 409) {
      if (action === "enable") return "Run a passing connection test first. Changing how it connects clears the earlier test.";
      if (action === "test") return "Someone changed this Data source while it was being tested. Reload and test again.";
      return "That logical name is already used in this workspace, or someone else changed this Data source. Reload and try again.";
    }
    if (error.status === 400) return "Check the fields. The host is a name or address only, and the logical name is lowercase letters, digits and underscores.";
  }
  return fallback;
}

function Health({ ds }: { ds: DataSourceResponse }) {
  const { lastTestOk, lastTestAt, lastTestError } = ds.health;
  if (lastTestOk === undefined || lastTestOk === null) {
    return <span className="pill"><CircleDashed aria-hidden className="size-3.5" />Not tested</span>;
  }
  const when = lastTestAt ? (
    <time dateTime={lastTestAt} title={formatAbsolute(lastTestAt)} className="font-normal text-ink-2">{formatRelative(lastTestAt)}</time>
  ) : null;
  return lastTestOk ? (
    <span className="inline-flex flex-wrap items-center gap-2"><span className="pill pill-ok"><CircleCheck aria-hidden className="size-3.5" />Test passed</span>{when}</span>
  ) : (
    <span className="inline-flex flex-wrap items-center gap-2" title={lastTestError ?? undefined}>
      <span className="pill pill-bad"><CircleAlert aria-hidden className="size-3.5" />Test failed</span>{when}
    </span>
  );
}

type Draft = { label: string; logicalName: string; host: string; port: string; database: string; username: string; password: string };
const EMPTY: Draft = { label: "", logicalName: "", host: "", port: "5432", database: "", username: "", password: "" };

function Form({ editing, onDone }: { editing: DataSourceResponse | null; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Draft>(
    editing
      ? { label: editing.label, logicalName: editing.logicalName, host: editing.host ?? "", port: String(editing.port ?? 5432), database: editing.database ?? "", username: editing.username ?? "", password: "" }
      : EMPTY,
  );
  const blank = [draft.label, draft.host, draft.database, draft.username].some((v) => v.trim() === "");
  const set = (key: keyof Draft) => (e: React.ChangeEvent<HTMLInputElement>) => setDraft((d) => ({ ...d, [key]: e.target.value }));

  const save = useMutation({
    mutationFn: async () => {
      await ensureCsrf();
      const port = Number(draft.port);
      if (editing) {
        await unwrap(
          withRenewal(() =>
            updateDataSource({
              path: { id: editing.id },
              body: { label: draft.label.trim(), host: draft.host.trim(), port, database: draft.database.trim(), username: draft.username.trim(), ...(draft.password ? { password: draft.password } : {}) },
            }),
          ),
        );
      } else {
        await unwrap(
          withRenewal(() =>
            createDataSource({
              body: { logicalName: draft.logicalName.trim(), label: draft.label.trim(), type: "postgresql", host: draft.host.trim(), port, database: draft.database.trim(), username: draft.username.trim(), password: draft.password },
            }),
          ),
        );
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: listDataSourcesQueryKey() });
      onDone();
    },
  });

  return (
    <form
      className="glass grid gap-4 rounded-xl p-4 sm:grid-cols-2"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <h2 className="text-lg font-semibold tracking-tight sm:col-span-2">{editing ? `Edit ${editing.label}` : "Add a Data source"}</h2>
      <label className="field">
        Display label
        <input className="input" required maxLength={80} value={draft.label} onChange={set("label")} />
      </label>
      <label className="field">
        Logical name
        <input className="input" required pattern="[a-z][a-z0-9_]{1,39}" disabled={!!editing} value={draft.logicalName} onChange={set("logicalName")} aria-describedby="ln-hint" />
        <span id="ln-hint" className="field-hint">{editing ? "Fixed after creation, so exports never break." : "Lowercase letters, digits and underscores. It cannot be changed later."}</span>
      </label>
      <label className="field">
        Database type
        <select className="select min-h-10" disabled value="postgresql" onChange={() => {}}>
          <option value="postgresql">PostgreSQL</option>
        </select>
        <span className="field-hint">MySQL, Oracle and SQL Server are coming.</span>
      </label>
      <label className="field">
        Host
        <input className="input" required maxLength={253} pattern="[A-Za-z0-9._\-]+" value={draft.host} onChange={set("host")} placeholder="db.example.com" />
      </label>
      <label className="field">
        Port
        <input className="input" type="number" required min={1} max={65535} value={draft.port} onChange={set("port")} />
      </label>
      <label className="field">
        Database
        <input className="input" required maxLength={128} value={draft.database} onChange={set("database")} />
      </label>
      <label className="field">
        User name
        <input className="input" required maxLength={128} autoComplete="off" value={draft.username} onChange={set("username")} />
      </label>
      <label className="field">
        Password
        <input className="input" type="password" required={!editing} maxLength={256} autoComplete="new-password" value={draft.password} onChange={set("password")} aria-describedby="pw-hint" />
        <span id="pw-hint" className="field-hint">{editing ? "Leave blank to keep the current password. It is never shown." : "Stored encrypted and never shown again."}</span>
      </label>
      {editing && (
        <p className="proof sm:col-span-2">Changing how it connects clears the last test and switches it off until you test and enable it again.</p>
      )}
      <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
        <button type="submit" className="btn btn-primary" disabled={save.isPending || blank}>{editing ? "Save changes" : "Add Data source"}</button>
        <button type="button" className="btn btn-quiet" onClick={onDone}>Cancel</button>
        {save.isError && <p role="alert" className="text-[0.8125rem] text-[var(--bad-fg)]">{failure(save.error, "save", "Could not save the Data source. Please try again.")}</p>}
      </div>
    </form>
  );
}

export function DataSourcesView() {
  const queryClient = useQueryClient();
  const { active, role, isPending } = useWorkspace();
  const [form, setForm] = useState<"new" | DataSourceResponse | null>(null);
  const [outcome, setOutcome] = useState<{ id: string; result: TestResponse } | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const [actionKind, setActionKind] = useState<Action>("test");
  const formId = useId();

  const canSee = !!role && BUILDERS.includes(role);
  const canManage = !!role && MANAGERS.includes(role);

  const sources = useQuery({
    queryKey: [...listDataSourcesQueryKey(), active?.id],
    queryFn: async () => (await unwrap(withRenewal(() => listDataSources()))).data ?? [],
    enabled: !!active && canSee,
    retry: false,
  });

  const reload = () => queryClient.invalidateQueries({ queryKey: listDataSourcesQueryKey() });
  const test = useMutation({
    onMutate: () => {
      setActionError(null);
      setActionKind("test");
      setOutcome(null);
    },
    mutationFn: async (id: string) => {
      await ensureCsrf();
      const r = await unwrap(withRenewal(() => testDataSource({ path: { id } })));
      setOutcome({ id, result: r.data as TestResponse });
    },
    onError: setActionError,
    onSettled: reload,
  });
  const enable = useMutation({
    onMutate: () => {
      setActionError(null);
      setActionKind("enable");
    },
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => enableDataSource({ path: { id } })));
    },
    onError: setActionError,
    onSettled: reload,
  });

  if (isPending) return <p role="status" className="text-ink-2">Loading…</p>;
  if (!active) {
    return (
      <div className="rise grid gap-3">
        <h1 className="page-title">Data sources</h1>
        <p className="proof">Choose a workspace from the menu at the top of the sidebar to see its Data sources.</p>
      </div>
    );
  }

  return (
    <div className="rise grid gap-6">
      <div className="grid gap-3">
        <h1 className="page-title">Data sources</h1>
        {role && <RoleNote role={role} text={ROLE_TEXT[role]} />}
        <p className="text-ink-2">Connections to databases your organization already runs. Tessle reads them where they are and does not copy their data.</p>
      </div>

      {!canSee ? (
        <p className="proof">Ask a builder, admin or owner if you need something connected.</p>
      ) : (
        <>
          {canManage && (
            <div>
              <button type="button" className="btn btn-primary" aria-expanded={form === "new"} aria-controls={formId} onClick={() => setForm(form === "new" ? null : "new")}>
                <Plus aria-hidden className="size-4" />
                Add Data source
              </button>
            </div>
          )}
          {form && canManage && (
            <div id={formId}>
              <Form key={form === "new" ? "new" : form.id} editing={form === "new" ? null : form} onDone={() => {
                setOutcome(null);
                setForm(null);
              }} />
            </div>
          )}

          {sources.isError && <p role="alert">Could not load the Data sources. If this keeps happening, restart the backend and reload.</p>}

          {sources.data && (
            <div className="table-wrap">
              {sources.data.length === 0 ? (
                <div className="grid gap-1 p-8 text-center">
                  <p className="font-semibold">No Data sources yet</p>
                  <p className="text-ink-2">{canManage ? "Add a database to start building on your own data." : "An owner or admin can connect the first one."}</p>
                </div>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Label</th>
                      <th scope="col">Type</th>
                      <th scope="col">State</th>
                      <th scope="col">Connection</th>
                      {canManage && <th scope="col"><span className="sr-only">Actions</span></th>}
                    </tr>
                  </thead>
                  <tbody>
                    {sources.data.map((ds) => (
                      <tr key={ds.id}>
                        <td>
                          <span className="block font-semibold">{ds.label}</span>
                          <span className="text-ink-2">{ds.logicalName}</span>
                        </td>
                        <td>{TYPE_LABEL[ds.type] ?? ds.type}</td>
                        <td><span className={`pill ${ds.state === "enabled" ? "pill-ok" : ""}`}>{ds.state === "enabled" ? "Enabled" : "Disabled"}</span></td>
                        <td><Health ds={ds} /></td>
                        {canManage && (
                          <td className="num">
                            <span className="inline-flex flex-wrap justify-end gap-2">
                              <button type="button" className="btn btn-quiet btn-sm" disabled={test.isPending} onClick={() => test.mutate(ds.id)}>
                                <PlugZap aria-hidden className="size-4" />
                                Test<span className="sr-only"> {ds.label}</span>
                              </button>
                              {ds.state === "disabled" && ds.health.lastTestOk === true && (
                                <button type="button" className="btn btn-sm" disabled={enable.isPending} onClick={() => enable.mutate(ds.id)}>
                                  Enable<span className="sr-only"> {ds.label}</span>
                                </button>
                              )}
                              <button type="button" className="btn btn-quiet btn-sm" onClick={() => {
                                setOutcome(null);
                                setForm(ds);
                              }}>
                                <Pencil aria-hidden className="size-4" />
                                Edit<span className="sr-only"> {ds.label}</span>
                              </button>
                            </span>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          <div role="status" aria-live="polite">
            {outcome && (
              <p className={outcome.result.ok ? "text-[var(--ok-fg)]" : "text-[var(--bad-fg)]"}>
                {sources.data?.find((d) => d.id === outcome.id)?.label ?? "Data source"}: {outcome.result.message}
              </p>
            )}
          </div>
          {actionError ? <p role="alert" className="text-[var(--bad-fg)]">{failure(actionError, actionKind, "Something went wrong. Please try again.")}</p> : null}
        </>
      )}
    </div>
  );
}

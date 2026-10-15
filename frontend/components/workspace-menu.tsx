"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import { listWorkspacesQueryKey } from "@/lib/api/generated/@tanstack/react-query.gen";
import { activateWorkspace, createWorkspace } from "@/lib/api/generated/sdk.gen";
import { withRenewal } from "@/lib/api/session";
import { SESSION_KEY } from "@/lib/api/use-session";
import { useWorkspace } from "@/lib/api/use-workspace";

/** Mounted once: a person with exactly one workspace should not have to pick it after every sign-in. */
export function AutoActivateWorkspace() {
  const queryClient = useQueryClient();
  const { workspaces, active } = useWorkspace();
  const done = useRef(false);
  const activate = useMutation({
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => activateWorkspace({ path: { id } })));
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSION_KEY }),
  });
  useEffect(() => {
    if (!done.current && workspaces?.length === 1 && !active) {
      done.current = true;
      activate.mutate(workspaces[0].id);
    }
  }, [workspaces, active, activate]);
  return null;
}

/** Creates a workspace and makes it active. Used in the workspace menu and in the first-run empty state. */
export function CreateWorkspaceForm({ compact = false, onDone }: { compact?: boolean; onDone?: () => void | Promise<void> }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const create = useMutation({
    mutationFn: async (workspaceName: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => createWorkspace({ body: { name: workspaceName } })));
    },
    onSuccess: async () => {
      setName("");
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: SESSION_KEY }),
        queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() }),
      ]);
      await onDone?.();
    },
  });
  return (
    <form
      className={compact ? "flex items-center gap-2 p-1" : "flex max-w-md flex-wrap items-center gap-3"}
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate(name.trim());
      }}
    >
      <input
        className={`input flex-1 ${compact ? "min-h-9" : ""}`}
        aria-label="New workspace name"
        placeholder={compact ? "New workspace" : "Workspace name, for example Northwind Finance"}
        required
        maxLength={80}
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <button type="submit" className={compact ? "btn btn-sm" : "btn btn-primary"} disabled={create.isPending || name.trim() === ""} aria-label={compact ? "Create workspace" : undefined}>
        <Plus aria-hidden className="size-4" />
        {compact ? null : "Create workspace"}
      </button>
      {create.isError && (
        <p role="alert" className="basis-full text-[0.8125rem] text-[var(--bad-fg)]">
          Could not create the workspace. Use 1 to 80 visible characters.
        </p>
      )}
    </form>
  );
}

export function WorkspaceMenu() {
  const menuId = useId();
  const queryClient = useQueryClient();
  const { workspaces, active } = useWorkspace();

  // The active workspace lives in the access cookie, so the session query must reload too.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: SESSION_KEY }),
      queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() }),
    ]);
  const close = () => document.getElementById(menuId)?.hidePopover();

  const activate = useMutation({
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => activateWorkspace({ path: { id } })));
    },
    onSuccess: async () => {
      close();
      await refresh();
    },
  });



  return (
    <>
      <button
        type="button"
        popoverTarget={menuId}
        aria-label="Switch workspace"
        className="flex w-full items-center gap-3 rounded-lg border border-line bg-surface/70 p-2.5 text-left transition-colors hover:bg-surface"
      >
        <span className="grid size-8 shrink-0 place-items-center rounded-md bg-ink text-[0.8125rem] font-semibold text-surface">
          {(active?.name ?? "?").slice(0, 1).toUpperCase()}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-semibold">{active?.name ?? "Choose a workspace"}</span>
          <span className="block truncate text-[0.8125rem] text-ink-2">{active ? active.role : "None active"}</span>
        </span>
        <ChevronsUpDown aria-hidden className="size-4 shrink-0 text-ink-2" />
      </button>

      <div id={menuId} popover="auto" className="menu menu-workspace glass">
        <p className="px-2.5 pb-1 pt-1 text-[0.8125rem] font-semibold text-ink-2">Workspaces</p>
        {workspaces?.map((w) => (
          <button
            key={w.id}
            type="button"
            className="menu-item"
            aria-current={w.id === active?.id}
            disabled={activate.isPending}
            onClick={() => (w.id === active?.id ? close() : activate.mutate(w.id))}
          >
            <span className="truncate">{w.name}</span>
            <span className="flex items-center gap-2 text-[0.8125rem] text-ink-2">
              {w.role}
              {w.id === active?.id && <Check aria-hidden className="size-4 text-accent" />}
            </span>
          </button>
        ))}
        <div className="menu-sep" />
        <CreateWorkspaceForm compact onDone={async () => { close(); await refresh(); }} />
        {activate.isError && (
          <p role="alert" className="px-2.5 pb-1 text-[0.8125rem] text-[var(--bad-fg)]">
            That did not work. Check the name and try again.
          </p>
        )}
      </div>
    </>
  );
}

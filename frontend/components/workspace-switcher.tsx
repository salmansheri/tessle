"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import {
  getMeQueryKey,
  listWorkspacesOptions,
  listWorkspacesQueryKey,
} from "@/lib/api/generated/@tanstack/react-query.gen";
import { activateWorkspace, createWorkspace } from "@/lib/api/generated/sdk.gen";

export function WorkspaceSwitcher({ activeId }: { activeId?: string | null }) {
  const queryClient = useQueryClient();
  const { data: workspaces } = useQuery(listWorkspacesOptions());
  const [name, setName] = useState("");

  // The active workspace lives in the access cookie, so the session query must reload too.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: getMeQueryKey() }),
      queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() }),
    ]);

  const activate = useMutation({
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(activateWorkspace({ path: { id } }));
    },
    onSuccess: refresh,
  });

  // The id in the access cookie can outlive a membership; only trust it if the user still belongs there.
  const validActiveId = workspaces?.some((w) => w.id === activeId) ? activeId : null;

  // A user with a single workspace should not have to pick it after every sign-in.
  const autoActivated = useRef(false);
  useEffect(() => {
    if (!autoActivated.current && workspaces?.length === 1 && !validActiveId) {
      autoActivated.current = true;
      activate.mutate(workspaces[0].id);
    }
  }, [workspaces, validActiveId, activate]);

  const create = useMutation({
    mutationFn: async (workspaceName: string) => {
      await ensureCsrf();
      await unwrap(createWorkspace({ body: { name: workspaceName } }));
    },
    onSuccess: async () => {
      setName("");
      await refresh();
    },
  });

  return (
    <section aria-label="Workspaces" className="flex flex-col items-center gap-3 text-sm">
      {workspaces && workspaces.length > 0 && (
        <label className="flex items-center gap-2">
          Workspace
          <select
            value={validActiveId ?? ""}
            disabled={activate.isPending}
            onChange={(e) => e.target.value && activate.mutate(e.target.value)}
            className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700"
          >
            {!validActiveId && <option value="">Choose a workspace…</option>}
            {workspaces.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} ({w.role})
              </option>
            ))}
          </select>
        </label>
      )}
      <form
        className="flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate(name);
        }}
      >
        <input
          aria-label="New workspace name"
          placeholder="New workspace"
          required
          maxLength={80}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700"
        />
        <button
          type="submit"
          disabled={create.isPending}
          className="rounded-md bg-foreground px-3 py-1 font-medium text-background disabled:opacity-60"
        >
          Create
        </button>
      </form>
      {(activate.isError || create.isError) && (
        <p role="alert">Something went wrong. Please try again.</p>
      )}
    </section>
  );
}

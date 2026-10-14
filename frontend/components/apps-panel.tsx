"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import {
  listAppsQueryKey,
  listWorkspacesOptions,
} from "@/lib/api/generated/@tanstack/react-query.gen";
import { createApp, listApps } from "@/lib/api/generated/sdk.gen";
import { withRenewal } from "@/lib/api/session";

// Mirrors the server (Role.atLeast(builder) and the is_builder SQL helper). The server is the authority;
// this only decides whether to show the form, so a drift shows up as a 403 message, not a security hole.
const CAN_BUILD = ["owner", "admin", "builder"];

function createMessage(error: unknown): string {
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return "Your role cannot create apps.";
    if (error.status === 409) return "An app with that name already exists in this workspace.";
    if (error.status === 400) return "Enter a name with at least one visible character and no control characters.";
  }
  return "Could not create the app. Please try again.";
}

/** Apps of the active workspace. The create form only appears for roles the server will accept. */
export function AppsPanel({ activeId }: { activeId: string }) {
  const queryClient = useQueryClient();
  const { data: workspaces } = useQuery(listWorkspacesOptions());
  const role = workspaces?.find((w) => w.id === activeId)?.role;
  const canCreate = !!role && CAN_BUILD.includes(role);
  const [name, setName] = useState("");

  const apps = useQuery({
    // The workspace id is part of the key so switching workspaces loads that workspace's apps.
    queryKey: [...listAppsQueryKey(), activeId],
    queryFn: async () => (await unwrap(withRenewal(() => listApps()))).data ?? [],
    enabled: !!role,
    retry: false,
  });

  const create = useMutation({
    mutationFn: async (appName: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => createApp({ body: { name: appName } })));
    },
    onSuccess: async () => {
      setName("");
      await queryClient.invalidateQueries({ queryKey: listAppsQueryKey() });
    },
  });

  if (!role) return null;

  return (
    <section aria-label="Apps" className="flex w-full max-w-md flex-col gap-3">
      <h2 className="text-lg font-medium">Apps</h2>
      {apps.isError && <p role="alert">Could not load the apps.</p>}
      {apps.data && apps.data.length === 0 && (
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          {canCreate ? "No apps yet. Create the first one below." : "No apps yet."}
        </p>
      )}
      <ul className="flex flex-col divide-y divide-zinc-200 dark:divide-zinc-800">
        {apps.data?.map((app) => (
          <li key={app.id} className="py-2">
            {app.name}
          </li>
        ))}
      </ul>
      {canCreate && (
        <form
          className="flex items-center gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate(name.trim());
          }}
        >
          <input
            aria-label="New app name"
            placeholder="New app"
            required
            maxLength={80}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700"
          />
          <button
            type="submit"
            disabled={create.isPending || name.trim() === ""}
            className="rounded-md bg-foreground px-3 py-1 font-medium text-background disabled:opacity-60"
          >
            Create
          </button>
        </form>
      )}
      {create.isError && (
        <p role="alert">
          {createMessage(create.error)}
        </p>
      )}
    </section>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { listWorkspacesOptions } from "./generated/@tanstack/react-query.gen";
import { useSession } from "./use-session";

export type RoleName = "owner" | "admin" | "builder" | "editor" | "viewer";

/** The Workspaces the user belongs to, which one is active, and the user's role in it. */
export function useWorkspace() {
  const session = useSession();
  const list = useQuery({ ...listWorkspacesOptions(), enabled: !!session.data });
  const workspaces = list.data;
  const active = workspaces?.find((w) => w.id === session.data?.activeWorkspaceId);
  return {
    workspaces,
    active,
    role: active?.role as RoleName | undefined,
    isPending: session.isPending || (!!session.data && list.isPending),
  };
}

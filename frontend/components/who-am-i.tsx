"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import { getMeQueryKey } from "@/lib/api/generated/@tanstack/react-query.gen";
import { logout, logoutEverywhere } from "@/lib/api/generated/sdk.gen";
import { fetchSession, refreshSession } from "@/lib/api/session";
import { AppsPanel } from "@/components/apps-panel";
import { WorkspaceSwitcher } from "@/components/workspace-switcher";

const SESSION_KEY = getMeQueryKey();

// Access tokens last 10 minutes; renew comfortably before that while the tab is open.
const RENEW_EVERY_MS = 8 * 60 * 1000;

export function WhoAmI() {
  const queryClient = useQueryClient();
  const { data, isPending } = useQuery({
    queryKey: SESSION_KEY,
    queryFn: fetchSession,
    retry: false,
  });

  useEffect(() => {
    if (!data) return;
    const timer = setInterval(async () => {
      try {
        if (await refreshSession()) {
          await queryClient.invalidateQueries({ queryKey: SESSION_KEY });
        }
      } catch {
        // offline or server down: the next /me call recovers through its own 401 path
      }
    }, RENEW_EVERY_MS);
    return () => clearInterval(timer);
  }, [data, queryClient]);

  const signOut = useMutation({
    mutationFn: async (everywhere: boolean) => {
      await ensureCsrf();
      const call = () => (everywhere ? logoutEverywhere() : logout());
      let result = await call();
      // The access cookie may have expired; renew once so the server can identify the session to end.
      if (result.response?.status === 401 && (await refreshSession())) {
        result = await call();
      }
      await unwrap(Promise.resolve(result));
    },
    // Only show "signed out" once the server confirmed it; otherwise the session is still alive.
    onSuccess: () => queryClient.setQueryData(SESSION_KEY, null),
  });

  if (isPending) return <p role="status">Checking session…</p>;
  if (!data) {
    return (
      <Link href="/sign-in" className="underline">
        Sign in
      </Link>
    );
  }
  return (
    <div className="flex flex-col items-center gap-2">
      <p role="status">Signed in as {data.email}</p>
      <div className="flex gap-4 text-sm">
        <button
          type="button"
          className="underline"
          disabled={signOut.isPending}
          onClick={() => signOut.mutate(false)}
        >
          Sign out
        </button>
        <button
          type="button"
          className="underline"
          disabled={signOut.isPending}
          onClick={() => signOut.mutate(true)}
        >
          Sign out everywhere
        </button>
      </div>
      <WorkspaceSwitcher activeId={data.activeWorkspaceId} />
      {data.activeWorkspaceId && (
        <Link href="/members" className="text-sm underline">
          Members
        </Link>
      )}
      {data.activeWorkspaceId && <AppsPanel activeId={data.activeWorkspaceId} />}
      {signOut.isError && (
        <p role="alert" className="text-sm">
          Could not sign out. Please try again.
        </p>
      )}
    </div>
  );
}

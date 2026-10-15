"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { LogOut, UserRound } from "lucide-react";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import { logout, logoutEverywhere } from "@/lib/api/generated/sdk.gen";
import { withRenewal } from "@/lib/api/session";
import { SESSION_KEY, useSession } from "@/lib/api/use-session";

const MENU_ID = "account-menu";

export function AccountMenu() {
  const queryClient = useQueryClient();
  const { data: user } = useSession();

  const signOut = useMutation({
    mutationFn: async (everywhere: boolean) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => (everywhere ? logoutEverywhere() : logout())));
    },
    // Only show "signed out" once the server confirmed it; otherwise the session is still alive.
    onSuccess: () => {
      document.getElementById(MENU_ID)?.hidePopover();
      queryClient.setQueryData(SESSION_KEY, null);
    },
  });

  return (
    <>
      <button type="button" popoverTarget={MENU_ID} aria-label="Account menu" className="btn btn-quiet btn-sm">
        <UserRound aria-hidden className="size-4" />
        <span className="hidden max-w-40 truncate sm:inline">{user?.email}</span>
      </button>
      <div id={MENU_ID} popover="auto" className="menu menu-account glass">
        <p className="truncate px-2.5 pb-1 pt-1 text-[0.8125rem] text-ink-2">Signed in as {user?.email}</p>
        <div className="menu-sep" />
        <button type="button" className="menu-item" disabled={signOut.isPending} onClick={() => signOut.mutate(false)}>
          <span>Sign out</span>
          <LogOut aria-hidden className="size-4 text-ink-2" />
        </button>
        <button type="button" className="menu-item" disabled={signOut.isPending} onClick={() => signOut.mutate(true)}>
          <span>Sign out everywhere</span>
          <span className="text-[0.8125rem] text-ink-2">all devices</span>
        </button>
        {signOut.isError && (
          <p role="alert" className="px-2.5 py-1 text-[0.8125rem] text-[var(--bad-fg)]">
            Could not sign out. Please try again.
          </p>
        )}
      </div>
    </>
  );
}

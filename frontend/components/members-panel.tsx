"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import {
  getMeQueryKey,
  listInvitationsQueryKey,
  listMembersQueryKey,
} from "@/lib/api/generated/@tanstack/react-query.gen";
import {
  changeMemberRole,
  inviteMember,
  listInvitations,
  listMembers,
  removeMember,
  revokeInvitation,
} from "@/lib/api/generated/sdk.gen";
import { fetchSession, withRenewal } from "@/lib/api/session";

const ROLES = ["owner", "admin", "builder", "editor", "viewer"] as const;
type RoleName = (typeof ROLES)[number];

function describe(error: unknown): string {
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return "You do not have permission to do that.";
    if (error.status === 409) return "That change is not possible. A workspace must keep at least one owner, and someone with a pending owner invitation or an existing membership cannot be invited again.";
    if (error.status === 429) return "Too many invitations were sent recently. Please try again later.";
  }
  return "Something went wrong. Please try again.";
}

export function MembersPanel() {
  const queryClient = useQueryClient();
  const session = useQuery({ queryKey: getMeQueryKey(), queryFn: fetchSession, retry: false });
  const active = session.data?.activeWorkspaceId;
  // Called through the SDK (not the generated query options) so a failure keeps its HTTP status.
  const members = useQuery({
    queryKey: listMembersQueryKey(),
    queryFn: async () => (await unwrap(withRenewal(() => listMembers()))).data ?? [],
    enabled: !!active,
    retry: false,
  });
  const invitations = useQuery({
    queryKey: listInvitationsQueryKey(),
    queryFn: async () => (await unwrap(withRenewal(() => listInvitations()))).data ?? [],
    enabled: !!active,
    retry: false,
  });

  // One message for the latest action: cleared when a new action starts, so an old failure never lingers.
  const [error, setError] = useState<unknown>(null);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<RoleName>("viewer");

  const reload = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: listMembersQueryKey() }),
      queryClient.invalidateQueries({ queryKey: listInvitationsQueryKey() }),
    ]);

  const change = useMutation({
    onMutate: () => setError(null),
    onError: setError,
    mutationFn: async (v: { userId: string; role: RoleName }) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => changeMemberRole({ path: { userId: v.userId }, body: { role: v.role } })));
    },
    onSettled: reload,
  });
  const remove = useMutation({
    onMutate: () => setError(null),
    onError: setError,
    mutationFn: async (userId: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => removeMember({ path: { userId } })));
    },
    onSettled: reload,
  });
  const invite = useMutation({
    onMutate: () => setError(null),
    onError: setError,
    mutationFn: async (v: { email: string; role: RoleName }) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => inviteMember({ body: v })));
    },
    onSuccess: async () => {
      setEmail("");
      await reload();
    },
  });
  const revoke = useMutation({
    onMutate: () => setError(null),
    onError: setError,
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => revokeInvitation({ path: { id } })));
    },
    onSettled: reload,
  });

  if (session.isPending) return <p role="status">Loading…</p>;
  if (!session.data) {
    return (
      <p>
        <Link href="/sign-in?next=/members" className="underline">Sign in</Link> to see members.
      </p>
    );
  }
  if (!active) return <p>Choose a workspace on the <Link href="/" className="underline">home page</Link> first.</p>;

  // Only members with a management role can invite; the server is the judge, the page just reports.
  const canManage = invitations.isSuccess;

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Members</h1>
      {members.isError && (
        <p role="alert">
          {members.error instanceof ApiStatusError && members.error.status === 403
            ? "You are not a member of this workspace. Switch to one you belong to on the home page."
            : "Could not load the members. If this keeps happening, restart the backend and reload."}
        </p>
      )}

      <ul className="flex flex-col divide-y divide-zinc-200 dark:divide-zinc-800">
        {members.data?.map((m) => (
          <li key={m.userId} className="flex items-center justify-between gap-3 py-2">
            <span>{m.email}</span>
            <span className="flex items-center gap-2">
              <select
                aria-label={`Role of ${m.email}`}
                value={m.role}
                disabled={!canManage || change.isPending}
                onChange={(e) => change.mutate({ userId: m.userId, role: e.target.value as RoleName })}
                className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 text-sm dark:border-zinc-700"
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
              {canManage && (
                <button type="button" className="text-sm underline" disabled={remove.isPending} onClick={() => remove.mutate(m.userId)}>
                  Remove
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>

      {canManage && (
        <>
          <form
            className="flex flex-wrap items-center gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              invite.mutate({ email, role });
            }}
          >
            <input
              type="email"
              required
              aria-label="Email to invite"
              placeholder="person@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700"
            />
            <select
              aria-label="Role to invite as"
              value={role}
              onChange={(e) => setRole(e.target.value as RoleName)}
              className="rounded-md border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700"
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>{r}</option>
              ))}
            </select>
            <button type="submit" disabled={invite.isPending} className="rounded-md bg-foreground px-3 py-1 font-medium text-background disabled:opacity-60">
              Invite
            </button>
          </form>

          {invitations.data && invitations.data.length > 0 && (
            <section aria-label="Pending invitations">
              <h2 className="mb-2 text-lg font-medium">Pending invitations</h2>
              <ul className="flex flex-col gap-1 text-sm">
                {invitations.data.map((i) => (
                  <li key={i.id} className="flex items-center justify-between gap-3">
                    <span>{i.email} as {i.role}</span>
                    <button type="button" className="underline" disabled={revoke.isPending} onClick={() => revoke.mutate(i.id)}>
                      Withdraw
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
      {error && <p role="alert">{describe(error)}</p>}
    </>
  );
}

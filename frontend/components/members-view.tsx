"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Mail, Send, X } from "lucide-react";
import { useState } from "react";
import { RoleNote } from "@/components/role-note";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import {
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
import { withRenewal } from "@/lib/api/session";
import { useSession } from "@/lib/api/use-session";
import { type RoleName, useWorkspace } from "@/lib/api/use-workspace";
import { formatAbsolute, formatRelative } from "@/lib/format";

const ROLES: RoleName[] = ["owner", "admin", "builder", "editor", "viewer"];

function describe(error: unknown): string {
  if (error instanceof ApiStatusError) {
    if (error.status === 403) return "You do not have permission to do that.";
    if (error.status === 409)
      return "That change is not possible. A workspace must keep at least one owner, and someone with a pending owner invitation or an existing membership cannot be invited again.";
    if (error.status === 429) return "Too many invitations were sent recently. Please try again later.";
    if (error.status === 400) return "Check the email address and role, then try again.";
  }
  return "Something went wrong. Please try again.";
}

export function MembersView() {
  const queryClient = useQueryClient();
  const { data: me } = useSession();
  const { active, role, isPending } = useWorkspace();
  const canManage = role === "owner" || role === "admin";

  // Called through the SDK (not the generated query options) so a failure keeps its HTTP status.
  const members = useQuery({
    queryKey: [...listMembersQueryKey(), active?.id],
    queryFn: async () => (await unwrap(withRenewal(() => listMembers()))).data ?? [],
    enabled: !!active,
    retry: false,
  });
  const invitations = useQuery({
    queryKey: [...listInvitationsQueryKey(), active?.id],
    queryFn: async () => (await unwrap(withRenewal(() => listInvitations()))).data ?? [],
    enabled: !!active && canManage,
    retry: false,
  });

  const [email, setEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<RoleName>("viewer");
  // One message for the latest action: cleared when a new action starts, so an old failure never lingers.
  const [error, setError] = useState<unknown>(null);

  const reload = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: listMembersQueryKey() }),
      queryClient.invalidateQueries({ queryKey: listInvitationsQueryKey() }),
    ]);
  const common = { onMutate: () => setError(null), onError: setError, onSettled: reload };

  const change = useMutation({
    ...common,
    mutationFn: async (v: { userId: string; role: RoleName }) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => changeMemberRole({ path: { userId: v.userId }, body: { role: v.role } })));
    },
  });
  const remove = useMutation({
    ...common,
    mutationFn: async (userId: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => removeMember({ path: { userId } })));
    },
  });
  const invite = useMutation({
    ...common,
    mutationFn: async (v: { email: string; role: RoleName }) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => inviteMember({ body: v })));
    },
    onSuccess: () => setEmail(""),
  });
  const revoke = useMutation({
    ...common,
    mutationFn: async (id: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => revokeInvitation({ path: { id } })));
    },
  });

  if (isPending) return <p role="status" className="text-ink-2">Loading…</p>;
  if (!active) {
    return (
      <div className="rise grid gap-3">
        <h1 className="page-title">Members</h1>
        <p className="proof">Choose a workspace from the menu at the top of the sidebar to see its members.</p>
      </div>
    );
  }

  // Owners may assign any role; admins may not create owners (the server enforces both).
  const assignable = role === "owner" ? ROLES : ROLES.filter((r) => r !== "owner");

  return (
    <div className="rise grid gap-8">
      <div className="grid gap-3">
        <h1 className="page-title">Members</h1>
        {role && <RoleNote role={role} />}
      </div>

      {members.isError && (
        <p role="alert">
          {members.error instanceof ApiStatusError && members.error.status === 403
            ? "You are not a member of this workspace. Switch to one you belong to from the sidebar."
            : "Could not load the members. If this keeps happening, restart the backend and reload."}
        </p>
      )}

      <section aria-labelledby="people" className="grid gap-3">
        <h2 id="people" className="text-lg font-semibold tracking-tight">
          People <span className="font-normal text-ink-2">({members.data?.length ?? 0})</span>
        </h2>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Email</th>
                <th scope="col">Role</th>
                {canManage && <th scope="col"><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {members.data?.map((m) => {
                const isOwnerRow = m.role === "owner";
                const locked = !canManage || (isOwnerRow && role !== "owner");
                return (
                  <tr key={m.userId}>
                    <td>
                      <span className="block max-w-36 truncate sm:max-w-none" title={m.email}>
                        {m.email}
                        {m.userId === me?.id && <span className="ml-2 text-ink-2">(you)</span>}
                      </span>
                    </td>
                    <td>
                      {locked ? (
                        <span className={`pill pill-${m.role}`}>{m.role}</span>
                      ) : (
                        <select
                          className="select max-w-28 sm:max-w-none"
                          aria-label={`Role of ${m.email}`}
                          value={m.role}
                          disabled={change.isPending}
                          onChange={(e) => change.mutate({ userId: m.userId, role: e.target.value as RoleName })}
                        >
                          {ROLES.filter((r) => assignable.includes(r) || r === m.role).map((r) => (
                            <option key={r} value={r}>{r}</option>
                          ))}
                        </select>
                      )}
                    </td>
                    {canManage && (
                      <td className="num">
                        {!locked && (
                          <button type="button" aria-label={`Remove ${m.email}`} className="btn btn-quiet btn-sm" disabled={remove.isPending} onClick={() => remove.mutate(m.userId)}>
                            <X aria-hidden className="size-4" />
                            <span className="sr-only sm:not-sr-only">Remove</span>
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {!canManage && (
          <p className="text-ink-2">Only owners and admins can invite people or change roles.</p>
        )}
      </section>

      {canManage && (
        <section aria-labelledby="invite" className="grid gap-4">
          <h2 id="invite" className="text-lg font-semibold tracking-tight">Invite someone</h2>
          <form
            className="glass flex flex-wrap items-end gap-3 rounded-xl p-4"
            onSubmit={(e) => {
              e.preventDefault();
              invite.mutate({ email: email.trim(), role: inviteRole });
            }}
          >
            <label className="field min-w-56 flex-1">
              Email address
              <input type="email" className="input" required placeholder="person@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <label className="field">
              Role
              <select className="select min-h-10" value={inviteRole} onChange={(e) => setInviteRole(e.target.value as RoleName)}>
                {assignable.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
            </label>
            <button type="submit" className="btn btn-primary" disabled={invite.isPending || email.trim() === ""}>
              <Send aria-hidden className="size-4" />
              Send invitation
            </button>
            {invite.isSuccess && <p role="status" className="basis-full text-ink-2">Invitation sent. It expires in 7 days.</p>}
          </form>

          {invitations.data && invitations.data.length > 0 && (
            <div className="grid gap-2">
              <h3 className="font-semibold">Pending invitations</h3>
              <ul className="table-wrap divide-y divide-line">
                {invitations.data.map((i) => (
                  <li key={i.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                    <span className="flex min-w-0 items-center gap-3">
                      <Mail aria-hidden className="size-4 shrink-0 text-ink-2" />
                      <span className="truncate">{i.email}</span>
                      <span className={`pill pill-${i.role}`}>{i.role}</span>
                    </span>
                    <span className="flex items-center gap-3 text-ink-2">
                      <time dateTime={i.expiresAt} title={formatAbsolute(i.expiresAt)}>
                        expires {formatRelative(i.expiresAt)}
                      </time>
                      <button type="button" className="btn btn-quiet btn-sm" disabled={revoke.isPending} onClick={() => revoke.mutate(i.id)}>
                        Withdraw
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {error ? <p role="alert" className="text-[var(--bad-fg)]">{describe(error)}</p> : null}
    </div>
  );
}

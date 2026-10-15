"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import { acceptInvitation } from "@/lib/api/generated/sdk.gen";
import { withRenewal } from "@/lib/api/session";
import { useSession } from "@/lib/api/use-session";


export function AcceptInvitation() {
  const token = useSearchParams().get("token");
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: user, isPending } = useSession();

  const accept = useMutation({
    mutationFn: async (value: string) => {
      await ensureCsrf();
      await unwrap(withRenewal(() => acceptInvitation({ body: { token: value } })));
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries();
      router.push("/members");
    },
  });

  if (!token) {
    return (
      <div className="grid gap-2">
        <h1 className="page-title">Invitation link incomplete</h1>
        <p role="alert" className="text-ink-2">This link is missing its token. Open the link from your invitation email again.</p>
      </div>
    );
  }
  if (isPending) return <p role="status" className="text-ink-2">Checking your session…</p>;

  if (!user) {
    const back = `/invitations/accept?token=${encodeURIComponent(token)}`;
    return (
      <div className="grid gap-5">
        <div className="grid gap-1.5">
          <h1 className="page-title">You&apos;re invited</h1>
          <p className="text-ink-2">Sign in with the email address the invitation was sent to, then accept it.</p>
        </div>
        <Link href={`/sign-in?next=${encodeURIComponent(back)}`} className="btn btn-primary no-underline">
          Sign in to continue
        </Link>
      </div>
    );
  }

  return (
    <div className="grid gap-5">
      <div className="grid gap-1.5">
        <h1 className="page-title">You&apos;re invited</h1>
        <p className="text-ink-2">
          Signed in as <strong className="font-semibold text-ink">{user.email}</strong>. Accepting adds you to the workspace and makes it active.
        </p>
      </div>
      <button type="button" disabled={accept.isPending} onClick={() => accept.mutate(token)} className="btn btn-primary">
        Accept invitation
      </button>
      {accept.isError && (
        <p role="alert" className="text-[var(--bad-fg)]">
          This invitation is invalid, has expired, or was sent to a different email address.
        </p>
      )}
    </div>
  );
}

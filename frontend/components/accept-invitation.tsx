"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ensureCsrf } from "@/lib/api/csrf";
import { unwrap } from "@/lib/api/errors";
import { getMeQueryKey } from "@/lib/api/generated/@tanstack/react-query.gen";
import { acceptInvitation } from "@/lib/api/generated/sdk.gen";
import { fetchSession } from "@/lib/api/session";

const SESSION_KEY = getMeQueryKey();

export function AcceptInvitation() {
  const token = useSearchParams().get("token");
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data: user, isPending } = useQuery({
    queryKey: SESSION_KEY,
    queryFn: fetchSession,
    retry: false,
  });

  const accept = useMutation({
    mutationFn: async (value: string) => {
      await ensureCsrf();
      await unwrap(acceptInvitation({ body: { token: value } }));
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries();
      router.push("/members");
    },
  });

  if (!token) return <p role="alert">This invitation link is incomplete.</p>;
  if (isPending) return <p role="status">Checking session…</p>;

  if (!user) {
    const back = `/invitations/accept?token=${encodeURIComponent(token)}`;
    return (
      <div className="flex max-w-sm flex-col gap-3 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">You&apos;re invited</h1>
        <p>Sign in with the email address the invitation was sent to, then accept it.</p>
        <Link href={`/sign-in?next=${encodeURIComponent(back)}`} className="underline">
          Sign in to continue
        </Link>
      </div>
    );
  }

  return (
    <div className="flex max-w-sm flex-col gap-3 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">You&apos;re invited</h1>
      <p>Signed in as {user.email}.</p>
      <button
        type="button"
        disabled={accept.isPending}
        onClick={() => accept.mutate(token)}
        className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        Accept invitation
      </button>
      {accept.isError && (
        <p role="alert">
          This invitation is invalid, has expired, or was sent to a different email address.
        </p>
      )}
    </div>
  );
}

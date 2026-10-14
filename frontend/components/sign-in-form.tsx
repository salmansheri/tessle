"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ensureCsrf } from "@/lib/api/csrf";
import { ApiStatusError, unwrap } from "@/lib/api/errors";
import { safeNext } from "@/lib/safe-next";
import { requestOtp, verifyOtp } from "@/lib/api/generated/sdk.gen";

function isRateLimited(error: unknown): boolean {
  return error instanceof ApiStatusError && error.status === 429;
}

export function SignInForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [codeSent, setCodeSent] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const requestCode = useMutation({
    mutationFn: (address: string) => unwrap(requestOtp({ body: { email: address } })),
  });
  const verifyCode = useMutation({
    mutationFn: (input: { email: string; code: string }) =>
      unwrap(verifyOtp({ body: input })),
  });

  async function sendCode(event: React.FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      await ensureCsrf();
      await requestCode.mutateAsync(email);
      setCodeSent(true);
    } catch (error) {
      setMessage(
        isRateLimited(error)
          ? "Too many attempts. Please wait a few minutes and try again."
          : "Enter a valid email address and try again.",
      );
    }
  }

  async function signIn(event: React.FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      await ensureCsrf();
      await verifyCode.mutateAsync({ email, code });
      await queryClient.invalidateQueries();
      router.push(next);
    } catch (error) {
      setMessage(
        isRateLimited(error)
          ? "Too many attempts. Please wait a few minutes and try again."
          : "That code is invalid or has expired.",
      );
    }
  }

  return (
    <form
      onSubmit={codeSent ? signIn : sendCode}
      className="flex w-full max-w-sm flex-col gap-4"
    >
      <h1 className="text-2xl font-semibold tracking-tight">Sign in</h1>
      <label className="flex flex-col gap-1 text-sm">
        Email
        <input
          type="email"
          required
          autoComplete="email"
          value={email}
          disabled={codeSent}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded-md border border-zinc-300 bg-transparent px-3 py-2 disabled:opacity-60 dark:border-zinc-700"
        />
      </label>
      {codeSent && (
        <label className="flex flex-col gap-1 text-sm">
          6-digit code from your email
          <input
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            required
            autoFocus
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            className="rounded-md border border-zinc-300 bg-transparent px-3 py-2 tracking-widest dark:border-zinc-700"
          />
        </label>
      )}
      <button
        type="submit"
        disabled={requestCode.isPending || verifyCode.isPending}
        className="rounded-md bg-foreground px-4 py-2 font-medium text-background disabled:opacity-60"
      >
        {codeSent ? "Sign in" : "Email me a code"}
      </button>
      {codeSent && (
        <button
          type="button"
          onClick={() => {
            setCodeSent(false);
            setCode("");
            setMessage(null);
          }}
          className="text-left text-sm underline"
        >
          Use a different email
        </button>
      )}
      <p role="status" aria-live="polite" className="min-h-5 text-sm">
        {message}
      </p>
    </form>
  );
}

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

  const busy = requestCode.isPending || verifyCode.isPending;

  return (
    <form onSubmit={codeSent ? signIn : sendCode} className="grid gap-5">
      <div className="grid gap-1.5">
        <h1 className="page-title">Sign in</h1>
        <p className="text-ink-2">
          {codeSent
            ? `We sent a 6-digit code to ${email}. It expires in 10 minutes.`
            : "Enter your email and we will send you a one-time code. No password needed."}
        </p>
      </div>

      <label className="field">
        Email address
        <input
          type="email"
          required
          autoComplete="email"
          autoFocus={!codeSent}
          className="input"
          value={email}
          disabled={codeSent}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>

      {codeSent && (
        <label className="field">
          6-digit code
          <input
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            required
            autoFocus
            className="input text-center font-mono text-xl tracking-[0.5em]"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          />
        </label>
      )}

      <button type="submit" disabled={busy} className="btn btn-primary">
        {codeSent ? "Sign in" : "Email me a code"}
      </button>

      {codeSent && (
        <button
          type="button"
          className="btn btn-quiet"
          onClick={() => {
            setCodeSent(false);
            setCode("");
            setMessage(null);
          }}
        >
          Use a different email
        </button>
      )}

      <div role="status" aria-live="polite">
        {message && <p className="text-[0.875rem] text-[var(--bad-fg)]">{message}</p>}
      </div>
    </form>
  );
}

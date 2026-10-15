import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthFrame } from "@/components/auth-frame";
import { SignInForm } from "@/components/sign-in-form";

export const metadata: Metadata = { title: "Sign in" };

export default function SignInPage() {
  return (
    <AuthFrame>
      <Suspense>
        <SignInForm />
      </Suspense>
    </AuthFrame>
  );
}

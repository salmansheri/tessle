import { Suspense } from "react";
import { SignInForm } from "@/components/sign-in-form";

export default function SignInPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-6">
      <Suspense>
        <SignInForm />
      </Suspense>
    </main>
  );
}

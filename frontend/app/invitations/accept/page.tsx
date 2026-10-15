import type { Metadata } from "next";
import { Suspense } from "react";
import { AcceptInvitation } from "@/components/accept-invitation";
import { AuthFrame } from "@/components/auth-frame";

export const metadata: Metadata = { title: "Accept invitation" };

export default function AcceptInvitationPage() {
  return (
    <AuthFrame>
      <Suspense>
        <AcceptInvitation />
      </Suspense>
    </AuthFrame>
  );
}

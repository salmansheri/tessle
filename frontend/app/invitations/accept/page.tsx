import { Suspense } from "react";
import { AcceptInvitation } from "@/components/accept-invitation";

export default function AcceptInvitationPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-6">
      <Suspense>
        <AcceptInvitation />
      </Suspense>
    </main>
  );
}

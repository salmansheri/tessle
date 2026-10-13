import { BackendStatus } from "@/components/backend-status";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-4">
      <h1 className="text-3xl font-semibold tracking-tight">Tessle</h1>
      <BackendStatus />
    </main>
  );
}

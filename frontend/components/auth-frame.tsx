import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";

/** The signed-out frame: one calm glass panel over the ground, with the theme choice within reach. */
export function AuthFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between px-5 py-4 sm:px-8">
        <Logo className="text-lg" />
        <ThemeToggle />
      </header>
      <main id="main" className="grid flex-1 place-items-center px-4 pb-16">
        <div className="glass rise w-full max-w-md rounded-2xl p-6 sm:p-8">{children}</div>
      </main>
    </div>
  );
}

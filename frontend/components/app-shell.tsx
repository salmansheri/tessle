"use client";

import { AppWindow, Database, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { AccountMenu } from "@/components/account-menu";
import { Logo } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { AutoActivateWorkspace, WorkspaceMenu } from "@/components/workspace-menu";
import { useSession } from "@/lib/api/use-session";
import { useWorkspace } from "@/lib/api/use-workspace";

const NAV = [
  { href: "/", label: "Apps", Icon: AppWindow, builderOnly: false },
  { href: "/data-sources", label: "Data sources", Icon: Database, builderOnly: true },
  { href: "/members", label: "Members", Icon: Users, builderOnly: false },
];

const BUILDERS = ["owner", "admin", "builder"];

/** Data sources are for builders and above; editors and viewers never see the link (the server refuses them anyway). */
function NavLinks({ pathname }: { pathname: string }) {
  const { role } = useWorkspace();
  const canSeeData = !!role && BUILDERS.includes(role);
  return NAV.filter((n) => !n.builderOnly || canSeeData).map(({ href, label, Icon }) => (
    <Link key={href} href={href} className="nav-link" aria-current={pathname === href ? "page" : undefined}>
      <Icon aria-hidden className="size-[1.125rem]" />
      {label}
    </Link>
  ));
}

/** The signed-in frame. Signed-out visitors are sent to sign in and brought back afterwards. */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const session = useSession();
  const { active } = useWorkspace();

  useEffect(() => {
    if (!session.isPending && !session.data) {
      router.replace(`/sign-in?next=${encodeURIComponent(pathname)}`);
    }
  }, [session.isPending, session.data, pathname, router]);

  // Render the frame while the session loads (so every segment is visible to Next's instant-navigation
  // check and the user sees no blank flash); only a settled signed-out state swaps to the redirect notice.
  if (!session.isPending && !session.data) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <p role="status" className="text-ink-2">Loading…</p>
      </div>
    );
  }

  const section = NAV.find((n) => n.href === pathname)?.label ?? "";

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[264px_1fr]">
      <AutoActivateWorkspace />
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>

      <aside className="glass rail sticky top-0 hidden h-dvh flex-col gap-6 p-3 lg:flex">
        <Link href="/" aria-label="Tessle home" className="px-1.5 pt-2 text-lg text-ink no-underline">
          <Logo />
        </Link>
        <WorkspaceMenu />
        <nav aria-label="Primary" className="grid gap-1">
          <NavLinks pathname={pathname} />
        </nav>
      </aside>

      <div className="flex min-w-0 flex-col">
        <header className="glass topbar sticky top-0 z-20 flex h-14 items-center justify-between gap-3 px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3 text-ink-2">
            <Link href="/" aria-label="Tessle home" className="text-ink no-underline lg:hidden">
              <Logo className="[&>span]:hidden sm:[&>span]:inline" />
            </Link>
            <nav aria-label="Breadcrumb" className="hidden min-w-0 items-center gap-2 truncate lg:flex">
              <span className="truncate">{active?.name ?? "No workspace"}</span>
              <span aria-hidden>/</span>
              <span className="font-semibold text-ink">{section}</span>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <AccountMenu />
          </div>
        </header>

        {/* Below the large breakpoint the rail is gone; keep switching and navigation one tap away. */}
        <div className="glass topbar grid gap-2 px-4 py-2 lg:hidden">
          <WorkspaceMenu />
          <nav aria-label="Primary" className="grid grid-cols-3 gap-1">
            <NavLinks pathname={pathname} />
          </nav>
        </div>

        <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-8 sm:py-10">
          {children}
        </main>
      </div>
    </div>
  );
}

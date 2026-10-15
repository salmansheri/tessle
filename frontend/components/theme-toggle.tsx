"use client";

import { Monitor, Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

type Theme = "system" | "light" | "dark";
const KEY = "tessle:theme";

const OPTIONS: { value: Theme; label: string; Icon: typeof Sun }[] = [
  { value: "system", label: "System theme", Icon: Monitor },
  { value: "light", label: "Light theme", Icon: Sun },
  { value: "dark", label: "Dark theme", Icon: Moon },
];

function apply(theme: Theme) {
  const root = document.documentElement;
  if (theme === "system") delete root.dataset.theme;
  else root.dataset.theme = theme;
  try {
    if (theme === "system") localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, theme);
  } catch {
    // storage blocked: the choice still applies for this page view
  }
}

export function ThemeToggle() {
  // Read after mount: the server cannot know the stored choice.
  const [theme, setTheme] = useState<Theme>("system");
  useEffect(() => {
    const stored = document.documentElement.dataset.theme;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setTheme(stored === "light" || stored === "dark" ? stored : "system");
  }, []);

  return (
    <div role="group" aria-label="Theme" className="inline-flex rounded-lg border border-line-strong p-0.5">
      {OPTIONS.map(({ value, label, Icon }) => (
        <button
          key={value}
          type="button"
          aria-pressed={theme === value}
          aria-label={label}
          title={label}
          onClick={() => {
            setTheme(value);
            apply(value);
          }}
          className="grid size-8 place-items-center rounded-md text-ink-2 transition-colors hover:text-ink aria-pressed:bg-surface aria-pressed:text-ink aria-pressed:shadow-[0_0_0_1px_var(--line)]"
        >
          <Icon aria-hidden className="size-4" />
        </button>
      ))}
    </div>
  );
}

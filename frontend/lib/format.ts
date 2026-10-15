const absolute = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });
const relative = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });

const STEPS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 31_536_000],
  ["month", 2_592_000],
  ["day", 86_400],
  ["hour", 3_600],
  ["minute", 60],
];

export function formatAbsolute(iso: string): string {
  return absolute.format(new Date(iso));
}

/** "3 hours ago", "yesterday". Falls back to "just now" under a minute. */
export function formatRelative(iso: string, now = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  for (const [unit, size] of STEPS) {
    if (Math.abs(seconds) >= size) return relative.format(Math.round(seconds / size), unit);
  }
  return "just now";
}

/** Four offset tiles: a tessellation, drawn once and reused. */
export function Logo({ className = "" }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-semibold tracking-tight ${className}`}>
      <svg aria-hidden viewBox="0 0 24 24" className="size-6">
        <rect x="2" y="2" width="9" height="9" rx="2.5" fill="var(--accent)" />
        <rect x="13" y="2" width="9" height="9" rx="2.5" fill="currentColor" opacity=".9" />
        <rect x="2" y="13" width="9" height="9" rx="2.5" fill="currentColor" opacity=".45" />
        <rect x="13" y="13" width="9" height="9" rx="2.5" fill="currentColor" opacity=".18" />
      </svg>
      Tessle
    </span>
  );
}

const BASE = "http://internal.invalid";

/**
 * A post-sign-in destination is only honored if it stays on this site. Browsers strip tabs and newlines
 * from URLs, so "/\t/evil.com" would otherwise become "//evil.com"; parsing it the way a browser does and
 * comparing origins catches that and every similar trick.
 */
export function safeNext(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || /[\u0000-\u001f\u007f\\]/.test(value)) {
    return "/";
  }
  try {
    const url = new URL(value, BASE);
    if (url.origin !== BASE) return "/";
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return "/";
  }
}

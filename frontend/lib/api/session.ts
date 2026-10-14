import { readCookie } from "@/lib/cookies";
import { ensureCsrf } from "./csrf";
import { getMe, refreshSession as requestRefresh } from "./generated/sdk.gen";

// Readable flag set at sign-in (the real tokens are HttpOnly). No flag means no session to look for.
const SESSION_HINT = "tessle_s";
const LAST_REFRESH_KEY = "tessle:last-refresh";
const RECENT_MS = 30_000;

function recentlyRefreshed(): boolean {
  try {
    return Date.now() - Number(localStorage.getItem(LAST_REFRESH_KEY) ?? 0) < RECENT_MS;
  } catch {
    return false;
  }
}

async function doRefresh(): Promise<boolean> {
  // Another tab may have just rotated the token; reusing the old one would revoke the session.
  if (recentlyRefreshed()) return true;
  await ensureCsrf();
  const { response } = await requestRefresh();
  const ok = response?.status === 204;
  if (ok) {
    try {
      localStorage.setItem(LAST_REFRESH_KEY, String(Date.now()));
    } catch {
      // storage unavailable: refreshing still works, tabs just cannot share the timestamp
    }
  }
  return ok;
}

/** Renews the session cookies. One tab at a time, because rotation makes concurrent use of a token look like theft. */
export async function refreshSession(): Promise<boolean> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return await navigator.locks.request("tessle-refresh", doRefresh);
  }
  return doRefresh();
}

/** The signed-in user, renewing the session once if the access cookie has expired. Null when signed out. */
export async function fetchSession() {
  if (!readCookie(SESSION_HINT)) return null;
  let result = await getMe();
  if (result.response?.status === 401 && (await refreshSession())) {
    result = await getMe();
  }
  return result.data ?? null;
}

/** Runs an SDK call, and if the access cookie has expired, renews the session once and retries. */
export async function withRenewal<T extends { response?: Response }>(call: () => Promise<T>): Promise<T> {
  const result = await call();
  if (result.response?.status === 401 && (await refreshSession())) {
    return call();
  }
  return result;
}

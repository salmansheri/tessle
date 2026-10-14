import { client } from "./generated/client.gen";
import { getCsrf } from "./generated/sdk.gen";

const COOKIE = "XSRF-TOKEN";
const HEADER = "X-XSRF-TOKEN";

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const hit = document.cookie
    .split("; ")
    .find((c) => c.startsWith(`${name}=`));
  return hit ? decodeURIComponent(hit.slice(name.length + 1)) : undefined;
}

// Double-submit CSRF: echo the readable cookie back as a header on every state-changing request.
client.interceptors.request.use((request) => {
  if (!["GET", "HEAD", "OPTIONS"].includes(request.method)) {
    const token = readCookie(COOKIE);
    if (token) request.headers.set(HEADER, token);
  }
  return request;
});

/** Makes sure the CSRF cookie exists before a state-changing call. */
export async function ensureCsrf(): Promise<void> {
  if (!readCookie(COOKIE)) await getCsrf();
}

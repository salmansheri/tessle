/** Carries the HTTP status, which the generated client drops when it throws (it throws only the parsed body). Status 0 means no response (network failure). */
export class ApiStatusError extends Error {
  constructor(readonly status: number) {
    super(`Request failed with status ${status}`);
  }
}

/** Awaits a generated SDK call made without throwOnError and throws ApiStatusError on any non-2xx. */
export async function unwrap<T extends { response?: Response }>(
  call: Promise<T>,
): Promise<T> {
  const result = await call;
  if (!result.response?.ok) throw new ApiStatusError(result.response?.status ?? 0);
  return result;
}

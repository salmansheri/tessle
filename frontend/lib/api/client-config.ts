import type { CreateClientConfig } from "./generated/client.gen";

// Same-site: the browser always calls /api on this origin; next.config.ts rewrites it to the backend.
export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: "",
});

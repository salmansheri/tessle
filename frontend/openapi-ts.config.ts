import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "./openapi.json",
  output: "./lib/api/generated",
  plugins: [
    {
      name: "@hey-api/client-fetch",
      runtimeConfigPath: "@/lib/api/client-config",
    },
    "@tanstack/react-query",
  ],
});

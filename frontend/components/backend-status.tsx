"use client";

import { useQuery } from "@tanstack/react-query";
import { getHealthOptions } from "@/lib/api/generated/@tanstack/react-query.gen";

export function BackendStatus() {
  const { data, isPending, isError } = useQuery(getHealthOptions());
  return (
    <p role="status">
      Backend: {isPending ? "checking…" : isError ? "unreachable" : data.status}
    </p>
  );
}

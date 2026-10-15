"use client";

import { useQuery } from "@tanstack/react-query";
import { getMeQueryKey } from "./generated/@tanstack/react-query.gen";
import { fetchSession } from "./session";

export const SESSION_KEY = getMeQueryKey();

/** The signed-in user (null when signed out), renewed once on an expired access cookie. */
export function useSession() {
  return useQuery({
    queryKey: SESSION_KEY,
    queryFn: fetchSession,
    retry: false,
    staleTime: 30_000,
  });
}

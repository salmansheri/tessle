"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { getMeOptions } from "@/lib/api/generated/@tanstack/react-query.gen";

export function WhoAmI() {
  const { data, isPending } = useQuery({ ...getMeOptions(), retry: false });
  if (isPending) return <p role="status">Checking session…</p>;
  if (!data) {
    return (
      <Link href="/sign-in" className="underline">
        Sign in
      </Link>
    );
  }
  return <p role="status">Signed in as {data.email}</p>;
}

import type { Metadata } from "next";
import { DataSourcesView } from "@/components/data-sources-view";

export const metadata: Metadata = { title: "Data sources" };

export default function DataSourcesPage() {
  return <DataSourcesView />;
}

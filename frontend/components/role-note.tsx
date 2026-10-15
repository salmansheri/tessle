import type { RoleName } from "@/lib/api/use-workspace";

const CAPABILITY: Record<RoleName, string> = {
  owner: "You can do everything here, including managing owners.",
  admin: "You can build and manage members, but not owners.",
  builder: "You can create and change apps; you cannot manage members.",
  editor: "You can work in apps; you cannot create new ones or manage members.",
  viewer: "You can look but not change anything.",
};

/** The page's signature: a claim about what you may do, next to the role that proves it. */
export function RoleNote({ role }: { role: RoleName }) {
  return (
    <p className="proof">
      <span className={`pill pill-${role}`}>{role}</span>
      <span>{CAPABILITY[role]}</span>
    </p>
  );
}

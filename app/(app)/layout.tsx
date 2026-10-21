import { Sidebar } from "@/components/sidebar";
import { requireSession } from "@/lib/auth";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const session = await requireSession();
  return <div className="app-shell"><Sidebar session={session}/><main className="main-content">{children}</main></div>;
}

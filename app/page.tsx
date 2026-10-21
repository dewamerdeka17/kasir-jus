import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";

export default async function Home() {
  const session = await getSession();
  redirect(session ? (session.role === "ADMIN" ? "/dashboard" : "/kasir") : "/login");
}

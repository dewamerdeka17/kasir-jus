import { SignJWT, jwtVerify } from "jose";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { prisma } from "@/lib/prisma";

export const SESSION_COOKIE = "jus_session";
const secret = new TextEncoder().encode(process.env.SESSION_SECRET || "local-only-insecure-secret-change-me");

export type Session = { userId: number; name: string; role: "ADMIN" | "CASHIER" };

export async function createSession(data: Session, maxAge = 60 * 60 * 12) {
  const token = await new SignJWT({ name: data.name, role: data.role })
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(String(data.userId))
    .setIssuedAt()
    .setExpirationTime(`${maxAge}s`)
    .sign(secret);
  const store = await cookies();
  store.set(SESSION_COOKIE, token, { httpOnly: true, sameSite: "lax", secure: process.env.NODE_ENV === "production", path: "/", maxAge });
}

export async function getSession(): Promise<Session | null> {
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, secret);
    const userId = Number(payload.sub);
    if (!userId || (payload.role !== "ADMIN" && payload.role !== "CASHIER")) return null;
    const user = await prisma.user.findUnique({ where: { id: userId }, select: { id: true, name: true, role: true, status: true } });
    if (!user || user.status !== "ACTIVE") return null;
    return { userId: user.id, name: user.name, role: user.role };
  } catch {
    return null;
  }
}

export async function requireSession() {
  const session = await getSession();
  if (!session) redirect("/login");
  return session;
}

export async function requireAdmin() {
  const session = await requireSession();
  if (session.role !== "ADMIN") redirect("/kasir");
  return session;
}

"use server";

import bcrypt from "bcryptjs";
import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { z } from "zod";
import { createSession, SESSION_COOKIE } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

type LoginState = { error?: string };
const attempts = new Map<string, { count: number; resetAt: number }>();

export async function loginAction(_: LoginState, formData: FormData): Promise<LoginState> {
  const parsed = z.object({ identity: z.string().trim().min(1), password: z.string().min(1) }).safeParse(Object.fromEntries(formData));
  if (!parsed.success) return { error: "Username dan password wajib diisi." };
  const key = parsed.data.identity.toLowerCase();
  const current = attempts.get(key);
  if (current && current.resetAt > Date.now() && current.count >= 5) return { error: "Terlalu banyak percobaan. Coba lagi dalam 15 menit." };
  if (!current || current.resetAt <= Date.now()) attempts.set(key, { count: 0, resetAt: Date.now() + 15 * 60_000 });

  const user = await prisma.user.findFirst({ where: { OR: [{ username: key }, { email: key }] } });
  if (!user || user.status !== "ACTIVE" || !(await bcrypt.compare(parsed.data.password, user.passwordHash))) {
    const item = attempts.get(key)!;
    item.count += 1;
    return { error: "Username/email atau password salah." };
  }
  attempts.delete(key);
  const remember = formData.get("remember") === "on";
  await createSession({ userId: user.id, name: user.name, role: user.role }, remember ? 60 * 60 * 24 * 30 : 60 * 60 * 12);
  redirect(user.role === "ADMIN" ? "/dashboard" : "/kasir");
}

export async function logoutAction() {
  (await cookies()).delete(SESSION_COOKIE);
  redirect("/login");
}

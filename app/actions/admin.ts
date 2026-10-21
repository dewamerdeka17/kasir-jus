"use server";

import bcrypt from "bcryptjs";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { requireAdmin, requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

const int = (value: FormDataEntryValue | null) => Number(value || 0);
const priceFields = z.object({ sellingPrice: z.number().int().positive(), discountPercent: z.number().int().min(0).max(100) });
const productFields = priceFields.extend({ name: z.string().trim().min(2), code: z.string().trim().min(2), emoji: z.string().trim().min(1), categoryId: z.number().int().positive(), costPrice: z.number().int().min(0), stock: z.number().int().min(0) });

export async function createProduct(formData: FormData) {
  await requireSession();
  const data = productFields.parse({ name: formData.get("name"), code: formData.get("code"), emoji: formData.get("emoji") || "🥤", categoryId: int(formData.get("categoryId")), costPrice: int(formData.get("costPrice")), sellingPrice: int(formData.get("sellingPrice")), discountPercent: int(formData.get("discountPercent")), stock: int(formData.get("stock")) });
  await prisma.product.create({ data });
  revalidatePath("/produk"); revalidatePath("/kasir"); revalidatePath("/stok");
}

export async function updateProduct(formData: FormData) {
  const session = await requireSession();
  const id = int(formData.get("id"));
  const prices = priceFields.parse({ sellingPrice: int(formData.get("sellingPrice")), discountPercent: int(formData.get("discountPercent")) });
  const data = session.role === "ADMIN"
    ? { ...productFields.parse({ ...prices, name: formData.get("name"), code: formData.get("code"), emoji: formData.get("emoji") || "🥤", categoryId: int(formData.get("categoryId")), costPrice: int(formData.get("costPrice")), stock: int(formData.get("stock")) }), status: formData.get("status") === "ACTIVE" ? "ACTIVE" as const : "INACTIVE" as const }
    : prices;
  await prisma.product.update({ where: { id }, data });
  revalidatePath("/produk"); revalidatePath("/kasir"); revalidatePath("/stok");
}

export async function deleteProduct(formData: FormData) {
  await requireAdmin();
  const id = int(formData.get("id"));
  const used = await prisma.transactionItem.count({ where: { productId: id } });
  if (used) await prisma.product.update({ where: { id }, data: { status: "INACTIVE" } });
  else await prisma.product.delete({ where: { id } });
  revalidatePath("/produk"); revalidatePath("/kasir");
}

export async function adjustStock(formData: FormData) {
  await requireAdmin();
  const productId = int(formData.get("productId"));
  const quantity = int(formData.get("quantity"));
  const product = await prisma.product.findUniqueOrThrow({ where: { id: productId } });
  const delta = quantity - product.stock;
  await prisma.$transaction([prisma.product.update({ where: { id: productId }, data: { stock: quantity } }), prisma.stockMovement.create({ data: { productId, type: "ADJUSTMENT", quantity: delta, description: String(formData.get("description") || "Penyesuaian manual") } })]);
  revalidatePath("/stok"); revalidatePath("/produk"); revalidatePath("/kasir");
}

export async function createCategory(formData: FormData) {
  await requireAdmin();
  const name = z.string().trim().min(2).parse(formData.get("name"));
  await prisma.category.create({ data: { name } }); revalidatePath("/kategori"); revalidatePath("/produk"); revalidatePath("/kasir");
}

export async function deleteCategory(formData: FormData) {
  await requireAdmin();
  const id = int(formData.get("id"));
  if (await prisma.product.count({ where: { categoryId: id } })) return;
  await prisma.category.delete({ where: { id } }); revalidatePath("/kategori");
}

export async function createUser(formData: FormData) {
  await requireAdmin();
  const data = z.object({ name: z.string().trim().min(2), username: z.string().trim().min(3), email: z.string().email().optional().or(z.literal("")), password: z.string().min(8), role: z.enum(["ADMIN", "CASHIER"]) }).parse(Object.fromEntries(formData));
  await prisma.user.create({ data: { name: data.name, username: data.username.toLowerCase(), email: data.email || null, passwordHash: await bcrypt.hash(data.password, 12), role: data.role } }); revalidatePath("/users");
}

export async function toggleUser(formData: FormData) {
  const session = await requireAdmin();
  const id = int(formData.get("id"));
  if (id === session.userId) return;
  const user = await prisma.user.findUniqueOrThrow({ where: { id } });
  await prisma.user.update({ where: { id }, data: { status: user.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" } }); revalidatePath("/users");
}

export async function resetUserPassword(formData: FormData) {
  await requireAdmin();
  const id = int(formData.get("id"));
  const password = z.string().min(8).parse(formData.get("password"));
  await prisma.user.update({ where: { id }, data: { passwordHash: await bcrypt.hash(password, 12) } });
  revalidatePath("/users");
}

export async function updateSettings(formData: FormData) {
  await requireAdmin();
  for (const key of ["storeName", "address", "whatsapp", "instagram", "tiktok", "receiptFooter", "cashierDiscountAllowed"]) {
    const value = String(formData.get(key) ?? (key === "cashierDiscountAllowed" ? "false" : ""));
    await prisma.setting.upsert({ where: { key }, update: { value }, create: { key, value } });
  }
  revalidatePath("/settings"); revalidatePath("/kasir");
}

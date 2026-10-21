import { prisma } from "@/lib/prisma";
import { requireSession } from "@/lib/auth";
import { PosClient } from "./pos-client";
import { productPrice } from "@/lib/products";

export default async function KasirPage() {
  const session = await requireSession();
  const [products, categories, settings] = await Promise.all([
    prisma.product.findMany({ where: { status: "ACTIVE" }, select: { id: true, code: true, name: true, emoji: true, categoryId: true, sellingPrice: true, discountPercent: true, stock: true, category: { select: { name: true } } }, orderBy: { name: "asc" } }),
    prisma.category.findMany({ select: { id: true, name: true }, orderBy: { name: "asc" } }),
    prisma.setting.findUnique({ where: { key: "cashierDiscountAllowed" }, select: { value: true } }),
  ]);
  return <PosClient cashierName={session.name} products={products.map((p) => ({ id: p.id, code: p.code, name: p.name, emoji: p.emoji, categoryId: p.categoryId, category: p.category.name, originalPrice: p.sellingPrice, price: productPrice(p.sellingPrice, p.discountPercent), discountPercent: p.discountPercent, stock: p.stock }))} categories={categories} allowDiscount={session.role === "ADMIN" || settings?.value === "true"}/>;
}

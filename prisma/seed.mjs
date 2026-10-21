import { PrismaClient } from "@prisma/client";
import bcrypt from "bcryptjs";

const prisma = new PrismaClient();

const products = [
  ["Jus Semangka", 8000, 4000, "🍉"], ["Jus Melon", 8000, 4000, "🍈"],
  ["Jus Tomat", 8000, 4000, "🍅"], ["Jus Wortel", 8000, 4000, "🥕"],
  ["Jus Nanas", 8000, 4000, "🍍"], ["Jus Apel", 10000, 5000, "🍎"],
  ["Jus Jambu", 10000, 5000, "🍐"], ["Jus Sirsak", 10000, 5000, "🥤"],
  ["Jus Strawberry", 10000, 5000, "🍓"], ["Jus Mangga", 12000, 6000, "🥭"],
  ["Jus Buah Naga", 12000, 6000, "🐉"], ["Jus Markisa", 12000, 6000, "🟡"],
  ["Jus Sawo", 12000, 6000, "🥝"], ["Jus Alpukat", 13000, 6500, "🥑"],
  ["Jus Durian", 13000, 7000, "🟢"]
];

async function main() {
  const passwordHash = await bcrypt.hash("admin123", 12);
  const cashierHash = await bcrypt.hash("kasir123", 12);
  await prisma.user.upsert({ where: { username: "admin" }, update: {}, create: { name: "Pemilik", username: "admin", email: "admin@jusmerdeka.local", passwordHash, role: "ADMIN" } });
  await prisma.user.upsert({ where: { username: "kasir" }, update: {}, create: { name: "Dewa", username: "kasir", email: "kasir@jusmerdeka.local", passwordHash: cashierHash, role: "CASHIER" } });

  const fruit = await prisma.category.upsert({ where: { name: "Jus Buah" }, update: {}, create: { name: "Jus Buah" } });
  const mix = await prisma.category.upsert({ where: { name: "Jus Mix" }, update: {}, create: { name: "Jus Mix" } });
  for (const [index, product] of products.entries()) {
    const [name, sellingPrice, costPrice, emoji] = product;
    await prisma.product.upsert({
      where: { code: `JUS-${String(index + 1).padStart(3, "0")}` },
      update: {},
      create: { code: `JUS-${String(index + 1).padStart(3, "0")}`, name, sellingPrice, costPrice, emoji, stock: 20 + (index % 4) * 5, categoryId: fruit.id }
    });
  }
  const mixes = [["Jus Nanas + Wortel", "MIX-001"], ["Jus Apel + Tomat", "MIX-002"]];
  for (const [name, code] of mixes) await prisma.product.upsert({ where: { code }, update: {}, create: { code, name, sellingPrice: 12000, costPrice: 6000, emoji: "🥤", stock: 18, categoryId: mix.id } });

  const settings = { storeName: "Jus Merdeka Jumbo", address: "Jl. Merdeka No. 45", whatsapp: "0812-3456-7890", receiptFooter: "Terima kasih, sehat selalu!", cashierDiscountAllowed: "true" };
  for (const [key, value] of Object.entries(settings)) await prisma.setting.upsert({ where: { key }, update: { value }, create: { key, value } });
}

main().finally(() => prisma.$disconnect());

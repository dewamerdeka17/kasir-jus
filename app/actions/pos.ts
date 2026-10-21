"use server";

import { PaymentMethod, Prisma, TransactionStatus } from "@prisma/client";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { requireAdmin, requireSession } from "@/lib/auth";
import { jakartaDayRange } from "@/lib/dates";
import { prisma } from "@/lib/prisma";
import { productPrice } from "@/lib/products";

const transactionSchema = z.object({
  items: z.array(z.object({ productId: z.number().int().positive(), quantity: z.number().int().positive().max(99) })).min(1),
  discount: z.number().int().min(0),
  paymentMethod: z.nativeEnum(PaymentMethod),
  amountPaid: z.number().int().min(0),
});

export type TransactionResult = { ok: true; transactionId: number; invoiceNumber: string } | { ok: false; error: string };

export async function createTransaction(input: unknown): Promise<TransactionResult> {
  const session = await requireSession();
  const parsed = transactionSchema.safeParse(input);
  if (!parsed.success) return { ok: false, error: "Data pesanan tidak valid." };
  const cashierDiscount = session.role === "ADMIN" ? true : (await prisma.setting.findUnique({ where: { key: "cashierDiscountAllowed" }, select: { value: true } }))?.value === "true";
  const uniqueItems = new Map<number, number>();
  for (const item of parsed.data.items) uniqueItems.set(item.productId, (uniqueItems.get(item.productId) || 0) + item.quantity);

  try {
    const result = await prisma.$transaction(async (tx) => {
      const products = await tx.product.findMany({ where: { id: { in: [...uniqueItems.keys()] }, status: "ACTIVE" } });
      if (products.length !== uniqueItems.size) throw new Error("Salah satu produk tidak tersedia.");
      let subtotal = 0;
      for (const product of products) {
        const quantity = uniqueItems.get(product.id)!;
        if (product.stock < quantity) throw new Error(`Stok ${product.name} hanya tersisa ${product.stock}.`);
        subtotal += productPrice(product.sellingPrice, product.discountPercent) * quantity;
      }
      const discount = cashierDiscount ? Math.min(parsed.data.discount, subtotal) : 0;
      const total = subtotal - discount;
      if (parsed.data.paymentMethod === "CASH" && parsed.data.amountPaid < total) throw new Error("Uang pembayaran tidak mencukupi.");
      const amountPaid = parsed.data.paymentMethod === "CASH" ? parsed.data.amountPaid : total;
      const { start, end, key } = jakartaDayRange();
      const sequence = await tx.transaction.count({ where: { createdAt: { gte: start, lt: end } } }) + 1;
      const invoiceNumber = `TRX-${key}-${String(sequence).padStart(3, "0")}`;
      const transaction = await tx.transaction.create({
        data: {
          invoiceNumber, userId: session.userId, subtotal, discount, total, paymentMethod: parsed.data.paymentMethod,
          amountPaid, changeAmount: Math.max(0, amountPaid - total),
          items: { create: products.map((product) => { const price = productPrice(product.sellingPrice, product.discountPercent); return { productId: product.id, productName: product.name, quantity: uniqueItems.get(product.id)!, price, costPrice: product.costPrice, subtotal: price * uniqueItems.get(product.id)! }; }) },
        },
      });
      for (const product of products) {
        const quantity = uniqueItems.get(product.id)!;
        const changed = await tx.product.updateMany({ where: { id: product.id, stock: { gte: quantity } }, data: { stock: { decrement: quantity } } });
        if (changed.count !== 1) throw new Error(`Stok ${product.name} berubah. Silakan ulangi transaksi.`);
        await tx.stockMovement.create({ data: { productId: product.id, type: "SALE", quantity: -quantity, description: `Penjualan ${invoiceNumber}` } });
      }
      return transaction;
    }, { maxWait: 5000, timeout: 10000 });
    revalidatePath("/kasir"); revalidatePath("/dashboard"); revalidatePath("/transaksi"); revalidatePath("/stok"); revalidatePath("/laporan");
    return { ok: true, transactionId: result.id, invoiceNumber: result.invoiceNumber };
  } catch (error) {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") return { ok: false, error: "Nomor transaksi bentrok. Silakan klik bayar lagi." };
    return { ok: false, error: error instanceof Error ? error.message : "Transaksi gagal disimpan." };
  }
}

export async function cancelTransaction(id: number) {
  await requireAdmin();
  await prisma.$transaction(async (tx) => {
    const item = await tx.transaction.findUnique({ where: { id }, include: { items: true } });
    if (!item || item.status === TransactionStatus.CANCELLED) return;
    await tx.transaction.update({ where: { id }, data: { status: "CANCELLED" } });
    for (const line of item.items) {
      await tx.product.update({ where: { id: line.productId }, data: { stock: { increment: line.quantity } } });
      await tx.stockMovement.create({ data: { productId: line.productId, type: "CANCELLATION", quantity: line.quantity, description: `Pembatalan ${item.invoiceNumber}` } });
    }
  });
  revalidatePath("/dashboard"); revalidatePath("/transaksi"); revalidatePath(`/transaksi/${id}`); revalidatePath("/stok");
}

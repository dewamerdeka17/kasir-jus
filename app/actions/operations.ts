"use server";

import { revalidatePath } from "next/cache";
import { z } from "zod";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

const stockSchema = z.object({
  productId: z.coerce.number().int().positive(),
  quantity: z.coerce.number().int().min(1).max(100_000),
  description: z.string().trim().max(200).optional(),
});

const expenseSchema = z.object({
  category: z.enum(["BAHAN_BAKU", "OPERASIONAL", "TRANSPORTASI", "PERALATAN", "LAINNYA"]),
  amount: z.coerce.number().int().min(1).max(2_000_000_000),
  description: z.string().trim().min(3).max(300),
  expenseDate: z.string().date(),
});

export async function addStock(formData: FormData) {
  const session = await requireSession();
  const data = stockSchema.parse(Object.fromEntries(formData));
  const description = data.description || `Stok masuk oleh ${session.name}`;

  await prisma.$transaction([
    prisma.product.update({
      where: { id: data.productId },
      data: { stock: { increment: data.quantity } },
    }),
    prisma.stockMovement.create({
      data: {
        productId: data.productId,
        createdById: session.userId,
        type: "IN",
        quantity: data.quantity,
        description,
      },
    }),
  ]);

  revalidatePath("/stok");
  revalidatePath("/produk");
  revalidatePath("/kasir");
}

export async function createExpense(formData: FormData) {
  const session = await requireSession();
  const data = expenseSchema.parse(Object.fromEntries(formData));

  await prisma.expense.create({
    data: {
      userId: session.userId,
      category: data.category,
      amount: data.amount,
      description: data.description,
      expenseDate: new Date(`${data.expenseDate}T12:00:00+07:00`),
    },
  });

  revalidatePath("/pengeluaran");
  revalidatePath("/laporan");
  revalidatePath("/dashboard");
}

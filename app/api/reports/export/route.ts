import { requireAdmin } from "@/lib/auth";
import { monthRange } from "@/lib/dates";
import { prisma } from "@/lib/prisma";

const escape = (value: unknown) => `"${String(value ?? "").replaceAll('"','""')}"`;
export async function GET(){await requireAdmin();const range=monthRange();const rows=await prisma.transaction.findMany({where:{createdAt:{gte:range.start,lt:range.end}},include:{user:true},orderBy:{createdAt:"asc"}});const csv=[["Invoice","Tanggal","Kasir","Subtotal","Diskon","Total","Metode","Status"],...rows.map(t=>[t.invoiceNumber,t.createdAt.toISOString(),t.user.name,t.subtotal,t.discount,t.total,t.paymentMethod,t.status])].map(row=>row.map(escape).join(",")).join("\r\n");const month=new Intl.DateTimeFormat("id-ID",{month:"long",year:"numeric",timeZone:"Asia/Jakarta"}).format(new Date()).replaceAll(" ","-");return new Response("\uFEFF"+csv,{headers:{"Content-Type":"text/csv; charset=utf-8","Content-Disposition":`attachment; filename="Laporan-Penjualan-${month}.csv"`}})}

import Link from "next/link";
import { CircleDollarSign, Download, ReceiptText, ShoppingBasket, TrendingUp } from "lucide-react";
import { SalesChart } from "@/components/sales-chart";
import { requireAdmin } from "@/lib/auth";
import { monthRange } from "@/lib/dates";
import { rupiah } from "@/lib/format";
import { prisma } from "@/lib/prisma";

export default async function ReportsPage() {
  await requireAdmin();
  const range = monthRange();
  const [transactions, expenseSum] = await Promise.all([
    prisma.transaction.findMany({ where: { status: "COMPLETED", createdAt: { gte: range.start, lt: range.end } }, include: { items: true } }),
    prisma.expense.aggregate({ where: { expenseDate: { gte: range.start, lt: range.end } }, _sum: { amount: true } }),
  ]);
  const revenue = transactions.reduce((sum, transaction) => sum + transaction.total, 0);
  const sold = transactions.reduce((sum, transaction) => sum + transaction.items.reduce((itemSum, item) => itemSum + item.quantity, 0), 0);
  const grossProfit = transactions.reduce((sum, transaction) => sum + transaction.items.reduce((itemSum, item) => itemSum + (item.price - item.costPrice) * item.quantity, 0) - transaction.discount, 0);
  const expenses = expenseSum._sum.amount || 0;
  const netProfit = grossProfit - expenses;
  const byDay = new Map<string, number>();
  const productMap = new Map<string, number>();
  const payment = new Map<string, number>();

  for (const transaction of transactions) {
    const key = new Intl.DateTimeFormat("id-ID", { day: "2-digit", month: "short", timeZone: "Asia/Jakarta" }).format(transaction.createdAt);
    byDay.set(key, (byDay.get(key) || 0) + transaction.total);
    payment.set(transaction.paymentMethod, (payment.get(transaction.paymentMethod) || 0) + transaction.total);
    for (const item of transaction.items) productMap.set(item.productName, (productMap.get(item.productName) || 0) + item.quantity);
  }

  const chart = [...byDay].map(([label, total]) => ({ label, total }));
  const top = [...productMap].sort((a, b) => b[1] - a[1]).slice(0, 10);
  const stats = [
    ["Omzet Bulan Ini", rupiah(revenue), TrendingUp],
    ["Pengeluaran", rupiah(expenses), ReceiptText],
    ["Produk Terjual", `${sold} cup`, ShoppingBasket],
    ["Laba Bersih", rupiah(netProfit), CircleDollarSign],
  ] as const;

  return <>
    <header className="page-header"><div><span className="eyebrow green">ANALITIK</span><h1>Laporan Penjualan</h1><p>Laba bersih sudah dikurangi pengeluaran bulan berjalan.</p></div><Link href="/api/reports/export" className="button secondary"><Download size={17}/> Export CSV</Link></header>
    <section className="stat-grid">{stats.map(([label, value, Icon]) => <article className="card stat-card" key={label}><div className="stat-top"><span>{label}</span><div className="stat-icon"><Icon size={18}/></div></div><div className="stat-value">{value}</div><div className="stat-note">Bulan berjalan</div></article>)}</section>
    <section className="content-grid">
      <article className="card section-card"><div className="section-title"><h2>Penjualan Harian</h2><span className="badge">Bulan ini</span></div>{chart.length ? <SalesChart data={chart}/> : <div className="empty">Belum ada transaksi bulan ini.</div>}</article>
      <article className="card section-card"><div className="section-title"><h2>Metode Pembayaran</h2></div><div className="payment-report">{[...payment].map(([name, total]) => <div key={name}><span>{name}</span><b>{rupiah(total)}</b><i style={{ width: `${revenue ? total / revenue * 100 : 0}%` }}/></div>)}{!payment.size && <div className="empty">Belum ada data.</div>}</div></article>
    </section>
    <article className="card section-card" style={{ marginTop: 16 }}><div className="section-title"><h2>Produk Terlaris Bulan Ini</h2></div><div className="table-wrap"><table className="data-table"><thead><tr><th>Peringkat</th><th>Produk</th><th>Jumlah Terjual</th></tr></thead><tbody>{top.map(([name, quantity], index) => <tr key={name}><td>#{index + 1}</td><td><strong>{name}</strong></td><td>{quantity} cup</td></tr>)}</tbody></table>{!top.length && <div className="empty">Belum ada produk terjual.</div>}</div></article>
  </>;
}

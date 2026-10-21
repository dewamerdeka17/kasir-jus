import { CalendarDays, HandCoins, PlusCircle, ReceiptText } from "lucide-react";
import { createExpense } from "@/app/actions/operations";
import { monthRange } from "@/lib/dates";
import { rupiah } from "@/lib/format";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

const categoryLabels: Record<string, string> = {
  BAHAN_BAKU: "Bahan baku",
  OPERASIONAL: "Operasional",
  TRANSPORTASI: "Transportasi",
  PERALATAN: "Peralatan",
  LAINNYA: "Lainnya",
};

const dateOnly = (value: Date) => new Intl.DateTimeFormat("id-ID", {
  dateStyle: "medium",
  timeZone: "Asia/Jakarta",
}).format(value);

export default async function ExpensesPage() {
  const session = await requireSession();
  const range = monthRange();
  const access = session.role === "CASHIER" ? { userId: session.userId } : {};
  const [expenses, monthly] = await Promise.all([
    prisma.expense.findMany({ where: access, include: { user: true }, orderBy: [{ expenseDate: "desc" }, { createdAt: "desc" }], take: 100 }),
    prisma.expense.findMany({ where: { ...access, expenseDate: { gte: range.start, lt: range.end } } }),
  ]);
  const monthlyTotal = monthly.reduce((total, expense) => total + expense.amount, 0);
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Jakarta" }).format(new Date());

  return <>
    <header className="page-header"><div><span className="eyebrow green">KAS KELUAR</span><h1>Pengeluaran</h1><p>Catat belanja bahan, operasional, dan kebutuhan toko lainnya.</p></div></header>
    <section className="stat-grid expense-stats">
      <article className="card stat-card"><div className="stat-top"><span>Total bulan ini</span><div className="stat-icon"><HandCoins size={18}/></div></div><div className="stat-value">{rupiah(monthlyTotal)}</div><div className="stat-note">{session.role === "ADMIN" ? "Seluruh kasir" : "Catatan saya"}</div></article>
      <article className="card stat-card"><div className="stat-top"><span>Jumlah catatan</span><div className="stat-icon"><ReceiptText size={18}/></div></div><div className="stat-value">{monthly.length}</div><div className="stat-note">Bulan berjalan</div></article>
    </section>
    <div className="split-grid expense-layout">
      <form action={createExpense} className="card form-card compact">
        <div className="form-title"><PlusCircle/><div><h3>Tambah pengeluaran</h3><p className="muted">Dicatat sebagai {session.name}</p></div></div>
        <label>Tanggal<input name="expenseDate" type="date" defaultValue={today} max={today} required/></label>
        <label>Kategori<select name="category" defaultValue="BAHAN_BAKU"><option value="BAHAN_BAKU">Bahan baku</option><option value="OPERASIONAL">Operasional</option><option value="TRANSPORTASI">Transportasi</option><option value="PERALATAN">Peralatan</option><option value="LAINNYA">Lainnya</option></select></label>
        <label>Jumlah (Rp)<input name="amount" type="number" min="1" step="1" inputMode="numeric" placeholder="Contoh: 50000" required/></label>
        <label>Keterangan<textarea name="description" minLength={3} maxLength={300} placeholder="Contoh: Beli mangga 5 kg" required/></label>
        <button className="button primary wide"><HandCoins size={17}/> Simpan Pengeluaran</button>
      </form>
      <article className="card section-card">
        <div className="section-title"><h2>{session.role === "ADMIN" ? "Riwayat pengeluaran" : "Pengeluaran saya"}</h2><span className="badge"><CalendarDays size={14}/> 100 terbaru</span></div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>Tanggal</th><th>Kategori</th><th>Keterangan</th>{session.role === "ADMIN" && <th>Kasir</th>}<th>Jumlah</th></tr></thead><tbody>
          {expenses.map((expense) => <tr key={expense.id}><td>{dateOnly(expense.expenseDate)}</td><td><span className="badge gray">{categoryLabels[expense.category] || expense.category}</span></td><td>{expense.description}</td>{session.role === "ADMIN" && <td>{expense.user.name}</td>}<td><strong className="expense-amount">-{rupiah(expense.amount)}</strong></td></tr>)}
        </tbody></table>{!expenses.length && <div className="empty">Belum ada pengeluaran yang dicatat.</div>}</div>
      </article>
    </div>
  </>;
}

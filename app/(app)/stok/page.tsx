import { AlertTriangle, CheckCircle2, PackagePlus, XCircle } from "lucide-react";
import { addStock } from "@/app/actions/operations";
import { dateTime } from "@/lib/format";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function StockPage() {
  const session = await requireSession();
  const [products, movements] = await Promise.all([
    prisma.product.findMany({ include: { category: true }, orderBy: [{ stock: "asc" }, { name: "asc" }] }),
    prisma.stockMovement.findMany({
      where: session.role === "CASHIER" ? { createdById: session.userId } : {},
      include: { product: true, createdBy: true },
      orderBy: { createdAt: "desc" },
      take: 20,
    }),
  ]);
  const counts = {
    safe: products.filter((product) => product.stock > 10).length,
    low: products.filter((product) => product.stock > 0 && product.stock <= 10).length,
    out: products.filter((product) => product.stock === 0).length,
  };

  return <>
    <header className="page-header"><div><span className="eyebrow green">INVENTORI</span><h1>Stok</h1><p>{session.role === "ADMIN" ? "Pilih produk dan masukkan jumlah stok yang baru datang." : "Lihat ketersediaan stok produk."}</p></div></header>
    <section className="stock-summary">
      <div className="card"><CheckCircle2/><span>Aman</span><strong>{counts.safe}</strong></div>
      <div className="card warn"><AlertTriangle/><span>Menipis</span><strong>{counts.low}</strong></div>
      <div className="card out"><XCircle/><span>Habis</span><strong>{counts.out}</strong></div>
    </section>
    <article className="card section-card">
      <div className="section-title"><h2>Daftar produk</h2>{session.role === "ADMIN" && <span className="badge"><PackagePlus size={14}/> Stok masuk</span>}</div>
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Produk</th><th>Kategori</th><th>Stok saat ini</th><th>Status</th>{session.role === "ADMIN" && <th>Tambah stok</th>}</tr></thead><tbody>
        {products.map((product) => {
          const state = product.stock === 0 ? "Habis" : product.stock <= 10 ? "Menipis" : "Aman";
          return <tr key={product.id}>
            <td><span className="table-product"><i>{product.emoji}</i><strong>{product.name}</strong></span></td>
            <td>{product.category.name}</td><td><strong>{product.stock} cup</strong></td>
            <td><span className={`badge ${product.stock === 0 ? "danger" : product.stock <= 10 ? "warn" : ""}`}>{state}</span></td>
            {session.role === "ADMIN" && <td><form action={addStock} className="inline-form stock-add-form"><input type="hidden" name="productId" value={product.id}/><input aria-label={`Jumlah stok ${product.name}`} name="quantity" type="number" min="1" placeholder="Jumlah" required/><input aria-label={`Catatan stok ${product.name}`} name="description" placeholder="Catatan (opsional)"/><button className="button secondary">Tambah</button></form></td>}
          </tr>;
        })}
      </tbody></table></div>
    </article>
    <article className="card section-card" style={{ marginTop: 16 }}>
      <div className="section-title"><h2>{session.role === "ADMIN" ? "Riwayat stok terbaru" : "Riwayat stok saya"}</h2></div>
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Waktu</th><th>Produk</th><th>Jumlah</th><th>Dicatat oleh</th><th>Catatan</th></tr></thead><tbody>
        {movements.map((movement) => <tr key={movement.id}><td>{dateTime(movement.createdAt)}</td><td><strong>{movement.product.name}</strong></td><td><span className={movement.quantity >= 0 ? "stock-positive" : "danger-text"}>{movement.quantity >= 0 ? "+" : ""}{movement.quantity} cup</span></td><td>{movement.createdBy?.name || "Sistem/Admin lama"}</td><td>{movement.description}</td></tr>)}
      </tbody></table>{!movements.length && <div className="empty">Belum ada penambahan stok.</div>}</div>
    </article>
  </>;
}

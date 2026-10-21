import Link from "next/link";
import { Edit3, Plus, Trash2 } from "lucide-react";
import { deleteProduct } from "@/app/actions/admin";
import { requireSession } from "@/lib/auth";
import { rupiah } from "@/lib/format";
import { productPrice } from "@/lib/products";
import { prisma } from "@/lib/prisma";

export default async function ProductsPage() {
  const session = await requireSession();
  const products = await prisma.product.findMany({ include: { category: true }, orderBy: { name: "asc" } });
  return <>
    <header className="page-header"><div><span className="eyebrow green">KATALOG</span><h1>{session.role === "ADMIN" ? "Manajemen Produk" : "Produk & Harga"}</h1><p>{session.role === "ADMIN" ? "Tambah menu, ubah harga jual, dan atur diskon produk." : "Lihat produk, harga jual, dan diskon yang tersedia."}</p></div>{session.role === "ADMIN" && <Link href="/produk/tambah" className="button primary"><Plus size={17}/> Tambah Produk</Link>}</header>
    <article className="card section-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Produk</th><th>Kategori</th><th>Harga</th><th>Diskon</th>{session.role === "ADMIN" && <th>Modal</th>}<th>Stok</th><th>Status</th><th>Aksi</th></tr></thead><tbody>
      {products.map((product) => { const finalPrice = productPrice(product.sellingPrice, product.discountPercent); return <tr key={product.id}>
        <td><span className="table-product"><i>{product.emoji}</i><span><strong>{product.name}</strong><small>{product.code}</small></span></span></td>
        <td>{product.category.name}</td>
        <td>{product.discountPercent > 0 ? <span className="discounted-table-price"><s>{rupiah(product.sellingPrice)}</s><strong>{rupiah(finalPrice)}</strong></span> : <strong>{rupiah(product.sellingPrice)}</strong>}</td>
        <td><span className={`badge ${product.discountPercent ? "warn" : "gray"}`}>{product.discountPercent}%</span></td>
        {session.role === "ADMIN" && <td>{rupiah(product.costPrice)}</td>}<td>{product.stock}</td>
        <td><span className={`badge ${product.status === "INACTIVE" ? "gray" : ""}`}>{product.status === "ACTIVE" ? "Aktif" : "Nonaktif"}</span></td>
        <td><div className="row-actions"><Link href={`/produk/${product.id}/edit`} className="icon-action" title="Edit harga dan diskon"><Edit3 size={16}/></Link>{session.role === "ADMIN" && <form action={deleteProduct}><input type="hidden" name="id" value={product.id}/><button className="icon-action danger-text" title="Hapus"><Trash2 size={16}/></button></form>}</div></td>
      </tr>; })}
    </tbody></table>{!products.length && <div className="empty">Belum ada produk.</div>}</div></article>
  </>;
}

import Link from "next/link";
import { BadgePercent, Save } from "lucide-react";
import { createProduct, updateProduct } from "@/app/actions/admin";
import { rupiah } from "@/lib/format";

type ProductInput = { id: number; name: string; code: string; emoji: string; categoryId: number; costPrice: number; sellingPrice: number; discountPercent: number; stock: number; status: "ACTIVE" | "INACTIVE" };

export function ProductForm({ categories, product, priceOnly = false }: { categories: { id: number; name: string }[]; product?: ProductInput; priceOnly?: boolean }) {
  if (product && priceOnly) return <form action={updateProduct} className="card form-card price-editor">
    <input type="hidden" name="id" value={product.id}/>
    <div className="form-title"><span className="product-form-emoji">{product.emoji}</span><div><h3>{product.name}</h3><p className="muted">Harga saat ini {rupiah(product.sellingPrice)}</p></div></div>
    <div className="form-grid">
      <label>Harga jual<input type="number" name="sellingPrice" min="1" defaultValue={product.sellingPrice} inputMode="numeric" required/></label>
      <label>Diskon produk (%)<input type="number" name="discountPercent" min="0" max="100" defaultValue={product.discountPercent} inputMode="numeric" required/></label>
    </div>
    <p className="form-hint"><BadgePercent size={16}/> Diskon otomatis dipakai di halaman kasir dan saat transaksi disimpan.</p>
    <div className="form-actions"><Link href="/produk" className="button ghost">Batal</Link><button className="button primary"><Save size={17}/> Simpan Harga</button></div>
  </form>;

  const action = product ? updateProduct : createProduct;
  return <form action={action} className="card form-card">
    {product && <input type="hidden" name="id" value={product.id}/>}<div className="form-grid">
      <label>Nama produk<input name="name" defaultValue={product?.name} placeholder="Jus Mangga" required minLength={2}/></label>
      <label>Kode produk<input name="code" defaultValue={product?.code} placeholder="JUS-018" required/></label>
      <label>Kategori<select name="categoryId" defaultValue={product?.categoryId} required>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
      <label>Emoji / ikon<input name="emoji" defaultValue={product?.emoji || "🥤"} required/></label>
      <label>Harga modal<input type="number" name="costPrice" min="0" defaultValue={product?.costPrice || 0} inputMode="numeric" required/></label>
      <label>Harga jual<input type="number" name="sellingPrice" min="1" defaultValue={product?.sellingPrice || 0} inputMode="numeric" required/></label>
      <label>Diskon produk (%)<input type="number" name="discountPercent" min="0" max="100" defaultValue={product?.discountPercent || 0} inputMode="numeric" required/></label>
      <label>Stok<input type="number" name="stock" min="0" defaultValue={product?.stock || 0} inputMode="numeric" required/></label>
      {product && <label>Status<select name="status" defaultValue={product.status}><option value="ACTIVE">Aktif</option><option value="INACTIVE">Tidak aktif</option></select></label>}
    </div>
    <div className="form-actions"><Link href="/produk" className="button ghost">Batal</Link><button className="button primary"><Save size={17}/>{product ? "Simpan Perubahan" : "Tambah Produk"}</button></div>
  </form>;
}

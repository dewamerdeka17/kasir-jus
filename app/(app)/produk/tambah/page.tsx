import { ProductForm } from "@/components/product-form";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function AddProductPage() { await requireSession(); const categories=await prisma.category.findMany({orderBy:{name:"asc"}}); return <><header className="page-header"><div><span className="eyebrow green">PRODUK BARU</span><h1>Tambah Produk</h1><p>Isi informasi menu yang akan dijual.</p></div></header>{categories.length ? <ProductForm categories={categories}/> : <div className="card empty">Belum ada kategori. Minta admin menambahkan kategori terlebih dahulu.</div>}</>; }

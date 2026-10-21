import { notFound } from "next/navigation";
import { ProductForm } from "@/components/product-form";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function EditProductPage({params}:{params:Promise<{id:string}>}) { const session=await requireSession(); const {id}=await params; const [product,categories]=await Promise.all([prisma.product.findUnique({where:{id:Number(id)}}),prisma.category.findMany({orderBy:{name:"asc"}})]); if(!product)notFound(); return <><header className="page-header"><div><span className="eyebrow green">EDIT PRODUK</span><h1>{product.name}</h1><p>{session.role === "ADMIN" ? "Perbarui detail produk dan simpan perubahan." : "Ubah harga jual dan diskon produk."}</p></div></header><ProductForm categories={categories} product={product} priceOnly={session.role === "CASHIER"}/></>; }

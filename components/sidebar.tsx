import Link from "next/link";
import { BarChart3, Boxes, ClipboardList, FolderTree, Gauge, HandCoins, LogOut, PackageOpen, ReceiptText, Settings, ShoppingCart, UsersRound } from "lucide-react";
import { logoutAction } from "@/app/actions/auth";
import type { Session } from "@/lib/auth";

const adminNav = [
  ["/dashboard", "Dashboard", Gauge], ["/kasir", "Kasir", ShoppingCart], ["/transaksi", "Transaksi", ReceiptText],
  ["/produk", "Produk", PackageOpen], ["/kategori", "Kategori", FolderTree], ["/stok", "Stok", Boxes],
  ["/pengeluaran", "Pengeluaran", HandCoins], ["/laporan", "Laporan", BarChart3], ["/users", "Admin", UsersRound], ["/settings", "Pengaturan", Settings],
] as const;
const cashierNav = [["/kasir", "Kasir", ShoppingCart], ["/produk", "Produk", PackageOpen], ["/transaksi", "Transaksi Saya", ClipboardList], ["/stok", "Tambah Stok", Boxes], ["/pengeluaran", "Pengeluaran", HandCoins]] as const;

export function Sidebar({ session }: { session: Session }) {
  const nav = session.role === "ADMIN" ? adminNav : cashierNav;
  const mobileNav = session.role === "ADMIN"
    ? [["/dashboard", "Home", Gauge], ["/kasir", "Kasir", ShoppingCart], ["/produk", "Produk", PackageOpen], ["/transaksi", "Transaksi", ReceiptText], ["/stok", "Stok", Boxes], ["/pengeluaran", "Keluar", HandCoins]] as const
    : cashierNav;
  return <><aside className="sidebar"><Link href="/dashboard" prefetch={false} className="sidebar-brand"><div className="brand-mark">JM</div><div><strong>Jus Merdeka</strong><span>Jumbo POS</span></div></Link><nav>{nav.map(([href,label,Icon]) => <Link href={href} prefetch={false} key={href}><Icon size={19}/><span>{label}</span></Link>)}</nav><div className="sidebar-user"><div className="avatar">{session.name.charAt(0)}</div><div><strong>{session.name}</strong><span>{session.role === "ADMIN" ? "Pemilik" : "Kasir"}</span></div><form action={logoutAction}><button title="Logout" aria-label="Keluar dari akun"><LogOut size={18}/></button></form></div></aside><nav className="bottom-nav" aria-label="Navigasi utama">{mobileNav.map(([href,label,Icon]) => <Link href={href} prefetch={false} key={href}><Icon size={20}/><span>{label}</span></Link>)}<form action={logoutAction} className="mobile-logout"><button type="submit" aria-label="Keluar dari akun"><LogOut size={20}/><span>Logout</span></button></form></nav></>;
}

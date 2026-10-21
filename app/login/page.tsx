import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { LoginForm } from "./login-form";

export default async function LoginPage() {
  if (await getSession()) redirect("/dashboard");
  return <main className="login-page"><section className="login-brand"><div className="brand-mark large">JM</div><span className="eyebrow">FRESH • FAST • FRIENDLY</span><h1>Kasir segar untuk bisnis yang terus tumbuh.</h1><p>Kelola transaksi, stok, dan laporan Jus Merdeka Jumbo dalam satu tempat.</p><div className="fruit-orbit">🍊 <span>🥭</span> 🥑</div></section><section className="login-panel"><div className="login-card"><div className="mobile-logo"><div className="brand-mark">JM</div><strong>Jus Merdeka</strong></div><span className="eyebrow green">SELAMAT DATANG</span><h2>Masuk ke akun Anda</h2><p className="muted">Gunakan akun admin atau kasir untuk melanjutkan.</p><LoginForm /></div></section></main>;
}

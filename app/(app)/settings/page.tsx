import { Save, Store } from "lucide-react";
import { updateSettings } from "@/app/actions/admin";
import { requireAdmin } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function SettingsPage() {
  await requireAdmin();
  const rows = await prisma.setting.findMany();
  const s = Object.fromEntries(rows.map((x) => [x.key, x.value]));
  return <>
    <header className="page-header"><div><span className="eyebrow green">KONFIGURASI</span><h1>Pengaturan Toko</h1><p>Informasi ini digunakan pada halaman dan struk.</p></div></header>
    <form action={updateSettings} className="card form-card">
      <div className="form-title"><Store/><div><h3>Informasi toko</h3><p className="muted">Identitas Jus Merdeka Jumbo</p></div></div>
      <div className="form-grid">
        <label>Nama toko<input name="storeName" defaultValue={s.storeName}/></label>
        <label>Nomor WhatsApp<input name="whatsapp" defaultValue={s.whatsapp}/></label>
        <label className="full-field">Alamat<textarea name="address" defaultValue={s.address}/></label>
        <label>Instagram<input name="instagram" defaultValue={s.instagram}/></label>
        <label>TikTok<input name="tiktok" defaultValue={s.tiktok}/></label>
        <label className="full-field">Header struk<textarea name="receiptHeader" defaultValue={s.receiptHeader} placeholder="Contoh: Selamat datang di Jus Merdeka"/></label>
        <label className="full-field">Footer struk<textarea name="receiptFooter" defaultValue={s.receiptFooter}/></label>
        <label>Ukuran kertas struk<select name="receiptPaperWidth" defaultValue={s.receiptPaperWidth || "80"}><option value="58">58 mm</option><option value="80">80 mm</option></select></label>
        <label>Model struk<select name="receiptModel" defaultValue={s.receiptModel || "STANDARD"}><option value="STANDARD">Standar</option><option value="COMPACT">Ringkas</option><option value="DETAILED">Detail</option></select></label>
        <label className="check-field"><input type="checkbox" name="cashierDiscountAllowed" value="true" defaultChecked={s.cashierDiscountAllowed === "true"}/> Izinkan kasir memberikan diskon</label>
      </div>
      <p className="muted settings-help">Untuk printer Bluetooth, pasangkan printer ke perangkat terlebih dahulu, lalu pilih printer tersebut dari dialog cetak browser.</p>
      <div className="form-actions"><button className="button primary"><Save size={17}/> Simpan Pengaturan</button></div>
    </form>
  </>;
}

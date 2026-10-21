"use client";

import { Save, Store } from "lucide-react";
import { updateSettings } from "@/app/actions/admin";
import { useState } from "react";

type ReceiptSettings = {
  storeName?: string;
  whatsapp?: string;
  address?: string;
  instagram?: string;
  tiktok?: string;
  receiptHeader?: string;
  receiptFooter?: string;
  receiptPaperWidth?: string;
  receiptModel?: string;
  cashierDiscountAllowed?: string;
};

export function ReceiptSettingsForm({ initial }: { initial: ReceiptSettings }) {
  const [values, setValues] = useState({
    storeName: initial.storeName || "Jus Merdeka Jumbo",
    whatsapp: initial.whatsapp || "",
    address: initial.address || "",
    instagram: initial.instagram || "",
    tiktok: initial.tiktok || "",
    receiptHeader: initial.receiptHeader || "",
    receiptFooter: initial.receiptFooter || "Terima kasih, sehat selalu!",
    receiptPaperWidth: initial.receiptPaperWidth === "58" ? "58" : "80",
    receiptModel: initial.receiptModel || "STANDARD",
    cashierDiscountAllowed: initial.cashierDiscountAllowed === "true",
  });
  const set = (name: keyof typeof values, value: string | boolean) => setValues((current) => ({ ...current, [name]: value }));
  const compact = values.receiptModel === "COMPACT";
  const detailed = values.receiptModel === "DETAILED";

  return <div className="settings-with-preview">
    <form action={updateSettings} className="card form-card">
      <div className="form-title"><Store/><div><h3>Informasi toko</h3><p className="muted">Identitas Jus Merdeka Jumbo</p></div></div>
      <div className="form-grid">
        <label>Nama toko<input name="storeName" value={values.storeName} onChange={(e) => set("storeName", e.target.value)}/></label>
        <label>Nomor WhatsApp<input name="whatsapp" value={values.whatsapp} onChange={(e) => set("whatsapp", e.target.value)}/></label>
        <label className="full-field">Alamat<textarea name="address" value={values.address} onChange={(e) => set("address", e.target.value)}/></label>
        <label>Instagram<input name="instagram" value={values.instagram} onChange={(e) => set("instagram", e.target.value)}/></label>
        <label>TikTok<input name="tiktok" value={values.tiktok} onChange={(e) => set("tiktok", e.target.value)}/></label>
        <label className="full-field">Header struk<textarea name="receiptHeader" value={values.receiptHeader} onChange={(e) => set("receiptHeader", e.target.value)} placeholder="Contoh: Selamat datang di Jus Merdeka"/></label>
        <label className="full-field">Footer struk<textarea name="receiptFooter" value={values.receiptFooter} onChange={(e) => set("receiptFooter", e.target.value)}/></label>
        <label>Ukuran kertas struk<select name="receiptPaperWidth" value={values.receiptPaperWidth} onChange={(e) => set("receiptPaperWidth", e.target.value)}><option value="58">58 mm</option><option value="80">80 mm</option></select></label>
        <label>Model struk<select name="receiptModel" value={values.receiptModel} onChange={(e) => set("receiptModel", e.target.value)}><option value="STANDARD">Standar</option><option value="COMPACT">Ringkas</option><option value="DETAILED">Detail</option></select></label>
        <label className="check-field"><input type="checkbox" name="cashierDiscountAllowed" value="true" checked={values.cashierDiscountAllowed} onChange={(e) => set("cashierDiscountAllowed", e.target.checked)}/> Izinkan kasir memberikan diskon</label>
      </div>
      <p className="muted settings-help">Untuk printer Bluetooth, pasangkan printer ke perangkat terlebih dahulu, lalu pilih printer tersebut dari dialog cetak browser.</p>
      <div className="form-actions"><button className="button primary"><Save size={17}/> Simpan Pengaturan</button></div>
    </form>
    <aside className="receipt-preview-panel">
      <div className="preview-heading"><div><span className="eyebrow green">PREVIEW LANGSUNG</span><h2>Contoh struk</h2></div><span className="badge">{values.receiptPaperWidth} mm · {values.receiptModel === "STANDARD" ? "Standar" : values.receiptModel === "COMPACT" ? "Ringkas" : "Detail"}</span></div>
      <section className={`receipt receipt-preview paper-${values.receiptPaperWidth} receipt-model-${values.receiptModel.toLowerCase()}`}>
        <div className="receipt-logo">JM</div><h2>{values.storeName || "Nama toko"}</h2><p className="receipt-store-address">{values.address || "Alamat toko"}</p>{values.whatsapp && <p className="receipt-contact">Pemesanan: {values.whatsapp}</p>}
        {values.receiptHeader && <p className="receipt-header-text">{values.receiptHeader}</p>}
        <div className="receipt-dash"/><div className="receipt-meta"><span>No. transaksi</span><b>INV-000123</b><span>Tanggal</span><b>21/10/2026 12:30</b><span>Kasir</span><b>Kasir Demo</b></div><div className="receipt-dash"/>
        <div className="receipt-items"><div><strong>Jus Mangga</strong><span>2 × Rp12.000</span><b>Rp24.000</b></div><div><strong>Jus Alpukat</strong><span>1 × Rp15.000</span><b>Rp15.000</b></div></div><div className="receipt-dash"/>
        <div className="receipt-totals"><span>Subtotal</span><b>Rp39.000</b>{!compact&&<><span>Diskon</span><b>-Rp0</b></>}<strong>TOTAL</strong><strong>Rp39.000</strong>{detailed&&<><span>Bayar</span><b>Rp50.000</b><span>Kembali</span><b>Rp11.000</b><span>Metode</span><b>TUNAI</b></>}</div><div className="receipt-dash"/>
        <p className="receipt-footer">{values.receiptFooter || "Footer struk"}<br/>Datang kembali ya 🥭</p>
      </section>
    </aside>
  </div>;
}

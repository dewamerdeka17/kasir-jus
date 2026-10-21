import { ReceiptSettingsForm } from "@/components/receipt-settings-form";
import { requireAdmin } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function SettingsPage() {
  await requireAdmin();
  const rows = await prisma.setting.findMany();
  const s = Object.fromEntries(rows.map((x) => [x.key, x.value]));
  return <>
    <header className="page-header"><div><span className="eyebrow green">KONFIGURASI</span><h1>Pengaturan Toko</h1><p>Informasi ini digunakan pada halaman dan struk.</p></div></header>
    <ReceiptSettingsForm initial={s}/>
  </>;
}

# Jus Merdeka Jumbo POS

Website kasir responsif berdasarkan PRD, dibangun dengan Next.js 16, TypeScript, Prisma, dan PostgreSQL. Aplikasi juga tersedia sebagai PWA yang dapat dipasang dan proyek Android TWA.

## Menjalankan aplikasi

```powershell
npm install
npx prisma generate
npx vercel env pull .env.local --environment=development
npx vercel env run -e development -- npx prisma db push
npm run db:seed
npm run dev
```

Buka `http://localhost:3000`.

## Akun awal

- Admin: `admin` / `admin123`
- Kasir: `kasir` / `kasir123`

Ganti password dan `SESSION_SECRET` sebelum digunakan di jaringan atau produksi.

## Fitur MVP

- Login aman dengan password bcrypt, sesi cookie, pembatasan percobaan, dan akses berbasis peran.
- Dashboard omzet, transaksi, produk terjual, keuntungan, grafik 7 hari, dan produk terlaris.
- POS responsif dengan pencarian realtime, kategori, keranjang, diskon nominal/persen, pembayaran, dan kembalian.
- Penyimpanan transaksi serta pengurangan stok secara atomik.
- Invoice otomatis `TRX-YYYYMMDD-XXX`, struk 58/80 mm, cetak, dan simpan PDF melalui dialog print.
- Produk, kategori, penyesuaian stok, pengguna, pengaturan toko, riwayat, pembatalan, laporan, dan export CSV.
- Layout desktop/tablet serta bottom navigation pada smartphone.

## Pemeriksaan kualitas

```powershell
npm run lint
npm run typecheck
npm run build
```

Database persisten menggunakan Neon PostgreSQL melalui integrasi Vercel. Gunakan `vercel env run` untuk menjalankan perintah Prisma tanpa menyalin rahasia database.

## Di luar MVP

Stok bahan baku, resep otomatis, supplier, multi-cabang, PWA/offline, integrasi QRIS langsung, upload gambar ke object storage, backup terjadwal, serta export Excel/PDF server-side disiapkan untuk tahap berikutnya sesuai PRD.

## Android

Proyek Android di `native/android` menyediakan **kasir offline untuk satu HP** dengan SQLite lokal, pembayaran tunai, stok, laporan, backup/restore JSON, dan cetak struk Bluetooth Classic/SPP ESC/POS. Aset kasir disertakan dalam APK; data website tidak otomatis tersinkron. Lihat [panduan Android](native/android/README.md) untuk build, pemasangan, batasan, dan pengujian printer fisik. APK debug dan hasil pengujian tersedia sebagai artifact workflow **Android offline APK** jika build berhasil.

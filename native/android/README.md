# JusMerdeka Android — kasir offline (v2)

APK ini memuat layar kasir dari aset lokal dan memakai SQLite di penyimpanan privat Android. Tidak memerlukan server, akun website, koneksi internet, atau pemasangan pertama secara online; masuk menggunakan PIN lokal. Manifest tidak meminta izin INTERNET. Website Next.js tetap terpisah.

## Fitur versi awal

- Produk, kategori, harga jual, HPP, stok tersedia, dan pengarsipan produk.
- Keranjang, diskon nominal, pembayaran **tunai**, kembalian, transaksi dan stok dalam satu transaksi SQLite.
- ID permintaan pembayaran disimpan sebelum checkout. Pemulihan pembayaran setelah aplikasi tertutup memakai ID yang sama agar tidak menambah transaksi/stok dua kali.
- Nomor invoice memakai ID SQLite yang unik; cetak gagal tidak membatalkan transaksi.
- Riwayat 500 transaksi terbaru, detail/struk, pembatalan idempoten dengan pengembalian stok. Semua transaksi tetap masuk backup dan laporan.
- Laporan harian WIB: omzet, jumlah transaksi, item terjual, laba kotor berdasarkan HPP yang disalin saat penjualan.
- Printer Bluetooth **Classic/SPP + ESC/POS**, kertas 58 mm/32 karakter atau 80 mm/48 karakter, tes cetak dan cetak ulang.
- Backup JSON lewat pemilih file Android dan pemulihan dengan validasi + transaksi atomik, termasuk kasbon dan pelunasan. Ukuran backup maksimum 25 MB. Backup menyimpan semua transaksi termasuk pembatalan, produk termasuk arsip, dan identitas toko; tidak menyimpan keranjang atau pilihan printer.

## Build APK

Diperlukan JDK 17 dan Android SDK platform 36. Dari folder ini:

```sh
bash gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows: `gradlew.bat testDebugUnitTest lintDebug assembleDebug`.

Hasil: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions **Android offline APK** menjalankan pemeriksaan dan mengunggah artifact **JusMerdeka-offline-debug-apk**. Buka tab Actions, pilih run yang berhasil, unduh artifact, ekstrak ZIP, lalu pasang APK di Android 7+. APK debug hanya untuk uji coba. Untuk penggunaan rutin, buat release bertanda tangan dengan keystore pemilik yang disimpan aman dan digunakan kembali untuk pembaruan; pemasangan ulang dengan tanda tangan berbeda mengharuskan uninstall dan dapat menghapus data.

## Mulai menggunakan

1. Buka aplikasi dalam mode pesawat. Login sebagai Admin, periksa 17 menu awal dari GitHub dan isi stok nyata pada tab Produk.
2. Isi nama/alamat/nomor pemesanan pada Setelan.
3. Buat transaksi tunai; periksa kembalian dan stok. Tutup/buka aplikasi untuk memeriksa data tetap ada.
4. Jika ada pembayaran yang belum terkonfirmasi setelah aplikasi tertutup, tekan **Cek ulang pembayaran** sebelum menjual lagi. Tidak perlu membuat pembayaran baru.
5. Ketika printer tersedia, pasangkan di pengaturan Bluetooth Android lalu pilih di Setelan aplikasi. Android 12+ meminta izin Perangkat terdekat. Aplikasi hanya mengakses perangkat yang sudah dipasangkan; tidak memindai atau meminta lokasi.
6. Jalankan Tes cetak, lalu cetak transaksi dari detail riwayat. Jika hasil cetak gagal/tidak pasti, periksa kertas sebelum cetak ulang; jangan ulangi pembayaran.
7. Simpan backup rutin **ke luar HP kasir**. Pemulihan mengganti seluruh data saat ini setelah dua konfirmasi. File yang rusak ditolak dan data lama tetap ada. File backup berupa teks biasa; simpan secara privat.

## Batasan dan pengujian perangkat

- Untuk **satu HP kasir**. Tidak ada sinkronisasi website/multi-perangkat, impor data PostgreSQL, akun server, QRIS otomatis, pengeluaran operasional, ataupun transaksi non-tunai pada versi awal. Data website tidak otomatis disalin. Ponsel dan akses aplikasinya perlu dikunci oleh pemilik.
- Pengubahan stok di layar Produk adalah penggantian jumlah tersedia, belum buku audit penyesuaian stok.
- Pilihan printer BLE, USB, Wi-Fi dan SDK printer khusus merek belum didukung. Pembelian printer perlu verifikasi tipe ESC/POS Classic/SPP Android. Struk memakai ASCII untuk kompatibilitas; nama non-ASCII ditampilkan sebagai spasi pada cetakan.
- Struk 58 mm disimpan saat checkout; cetak 80 mm disusun dari snapshot item dan identitas toko saat ini. Pembatalan diberi penanda DIBATALKAN pada cetak ulang.
- Tidak ada percobaan cetak otomatis: pengiriman Bluetooth dapat gagal setelah sebagian kertas tercetak. Pengiriman berhasil tidak menjamin printer memiliki kertas.
- Uji nyata wajib: instal pertama tanpa internet, mode pesawat + Bluetooth, pembayaran, tutup paksa setelah bayar, stok tidak cukup, pembatalan dua kali, backup/restore pada perangkat lain, izin Bluetooth ditolak, printer mati/kertas habis, kertas 58/80 mm, serta orientasi HP.
- Unit test Robolectric juga memeriksa PIN/otorisasi, migrasi menu, kasbon/pelunasan, dan checkout tahan restart/idempoten, rollback stok/pembayaran, penggabungan item, pembatalan idempoten, laporan, backup/restore dan sanitasi struk. Uji ini tidak menggantikan pengujian printer fisik.

## Login PIN, akses, menu website dan kasbon (v2.1)

- Saat aplikasi dibuka, pilih **Kasir** (PIN `123456`) atau **Admin** (PIN `654321`). PIN tidak ditampilkan pada layar login dan tidak disimpan dalam penyimpanan browser. Sesi di memori Activity; Keluar atau penutupan Activity memerlukan login lagi. PIN bawaan ini merupakan akses lokal sederhana, bukan akun server atau perlindungan terhadap pemilik perangkat yang memodifikasi APK. Lima PIN salah memblokir percobaan selama 30 detik; hitungan tetap ada jika aplikasi dibuka ulang.
- Kasir hanya melihat **Kasir, Kasbon, Riwayat**. Admin melihat seluruh menu. Ubah produk/stok, laporan/HPP, pembatalan, identitas toko dan backup/restore diperiksa di bridge native dan ditolak untuk kasir. Pilih printer tersedia di detail struk bagi kedua peran.
- Aset `pos/catalog.json` berasal dari **`prisma/seed.mjs` pada branch main**, bukan pembacaan database produksi: 15 Jus Buah dan 2 Jus Mix beserta harga jual/HPP. Migrasi menambah nama yang belum ada (termasuk pemeriksaan produk arsip), tanpa menimpa harga, kategori, stok atau transaksi yang sudah ada. Stok menu baru **0**; admin harus mengisi stok nyata sebelum penjualan. Perubahan menu/harga yang hanya tersimpan di database website tidak otomatis ikut.
- Pilih metode **Kasbon** di Kasir, isi nama pelanggan dan nomor HP opsional. Penjualan mengurangi stok satu kali dan membuat tagihan dalam transaksi SQLite yang sama. Tidak ada uang diterima saat pencatatan kasbon. Struk memberi penanda kasbon saat pembelian.
- Kasbon menampilkan sisa utang dan riwayat pembayaran. Terima pembayaran sebagian/lunas; simpan ID permintaan sebelum memproses agar pemulihan tidak mencatat pembayaran dua kali. Pembayaran melebihi sisa ditolak. Pembayaran kasbon tidak menambah omzet atau mengurangi stok lagi. Transaksi kasbon yang sudah dibayar sebagian tidak dapat dibatalkan, karena versi ini belum mendukung pengembalian pembayaran.
- Laporan omzet/laba memakai penjualan, **termasuk kasbon**, bukan jumlah kas tunai yang masuk. Struk penjualan merupakan snapshot saat pembelian; perkembangan pelunasan dilihat di menu Kasbon.
- Backup format **2** mencakup kasbon dan pembayaran. Format 1 dari APK lama tetap dapat dipulihkan. PIN dan sesi tidak disertakan dalam backup.
- Sebelum memperbarui APK debug, **backup data lama dahulu**. Debug APK dari runner berbeda dapat memakai tanda tangan berbeda. Jika Android menolak pembaruan, data lama perlu diekspor dahulu, kemudian APK lama dihapus, APK baru dipasang, dan backup dipulihkan oleh admin. Jangan menghapus aplikasi sebelum memastikan backup tersedia.

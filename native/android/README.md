# JusMerdeka Android — kasir offline v2.3

APK memuat aset lokal dan SQLite di penyimpanan privat Android. Tidak memerlukan server atau internet, termasuk saat pertama dibuka. Website Next.js dan database produksi tetap terpisah.

## Login dan akses

Pilih **Kasir** dengan PIN `123456` atau **Admin** dengan PIN `654321`. PIN tidak ditampilkan pada layar login atau disimpan dalam browser. Sesi berada di memori Activity; keluar atau penutupan Activity memerlukan PIN lagi. Lima percobaan salah memblokir login 30 detik, termasuk setelah aplikasi dibuka ulang. PIN bawaan adalah pembatas akses lokal, bukan akun server atau perlindungan terhadap APK yang dimodifikasi.

- Kasir hanya melihat **Kasir, Pengeluaran, Riwayat**. Kasir dapat memilih cetak atau tanpa nota; pengaturan printer dan struk hanya untuk admin.
- Admin memiliki akses produk/stok, laporan, pembatalan, identitas toko serta backup/pemulihan.
- Bridge native memeriksa izin, tidak hanya menyembunyikan tombol. Kasir tidak menerima HPP dari API native dan tidak dapat membatalkan transaksi/pengeluaran atau mengubah produk/stok.

## Transaksi dan menu

- Pembayaran **Tunai/QRIS manual**, diskon nominal, kembalian, invoice unik, snapshot item/HPP, riwayat 500 terbaru, struk dan pembatalan atomik dengan pengembalian stok.
- ID pembayaran disimpan sebelum checkout. Pemulihan setelah aplikasi tertutup memakai ID sama supaya stok/penjualan tidak tercatat dua kali. Cetak gagal tidak membatalkan transaksi.
- `assets/pos/catalog.json` berasal dari 17 produk awal di `prisma/seed.mjs` branch main: 15 Jus Buah dan 2 Jus Mix, beserta harga/kategori/HPP. Stok baru **0**; admin mengisi stok nyata.
- Migrasi menambah nama yang belum ada tanpa menimpa produk aktif/arsip, harga, stok atau transaksi lama. Perubahan yang hanya tersimpan di database website tidak otomatis ikut.
- UI HP memiliki kategori menu, kartu harga/stok, tombol cepat ke pesanan, panel pembayaran, navigasi berdasarkan peran, dan formulir pengeluaran. Desktop/tablet memakai dua kolom.

## Pengeluaran

Menu Kasbon diganti dengan **Pengeluaran** dan pilihan pembayaran kasbon dihapus. Kasbon baru ditolak di bridge native. Catatan kasbon/pembayaran lama tetap dipertahankan di database dan backup serta dapat dibaca dalam riwayat penjualan lama; tidak diubah menjadi pengeluaran.

Pengeluaran berisi tanggal, kategori, nominal rupiah dan keterangan. Kategori sama dengan website: Bahan baku, Operasional, Transportasi, Peralatan, Lainnya. Peran pencatat ditentukan native. Kasir melihat catatan dari peran Kasir; Admin melihat seluruh catatan. Satu PIN kasir berarti tidak ada pemisahan per individu kasir.

Penyimpanan menggunakan ID permintaan untuk pemulihan tanpa duplikasi. Catatan tersimpan tetap ada setelah aplikasi ditutup. Admin dapat membatalkan catatan; jejaknya tetap tersedia, dan ringkasan mengecualikan catatan batal. Tidak mengurangi stok secara otomatis. Daftar menampilkan 500 terbaru untuk tanggal pilihan, sementara total dihitung atas semua catatan tanggal itu.

Laporan harian WIB menampilkan omzet, jumlah transaksi, item terjual, laba kotor, pengeluaran dan laba kotor dikurangi pengeluaran. Ini perhitungan ringkasan, bukan sistem akuntansi persediaan. Omzet bisa memuat kasbon dari versi sebelumnya; jangan disamakan dengan kas masuk.

## Printer

Admin memasangkan printer di pengaturan Bluetooth Android lalu memilihnya di Setelan Admin. Ukuran kertas, tes cetak, dan editor/preview struk juga hanya untuk admin. Kasir mencetak ke printer yang sudah ditetapkan. Mendukung **Bluetooth Classic/SPP + ESC/POS**, 58 mm/32 karakter dan 80 mm/48 karakter. Android 12+ meminta Perangkat terdekat. Tidak memindai perangkat atau meminta lokasi. BLE, USB, Wi-Fi dan SDK khusus merek belum didukung.

Tidak ada cetak ulang otomatis. Kegagalan dapat terjadi setelah sebagian kertas tercetak; periksa hasil sebelum mengulang cetak. Transaksi baru menyimpan snapshot struk 58 dan 80 mm, sehingga perubahan identitas toko tidak mengubah struk lama. Transaksi dari versi lebih lama yang belum punya snapshot 80 mm memakai formatter dan identitas saat ini ketika dicetak 80 mm. Non-ASCII diganti spasi pada cetakan demi kompatibilitas.

## Backup dan pembaruan

Backup JSON melalui pemilih file Android, maksimum 25 MB. Format **4** mencakup produk, stok, penjualan, identitas toko, pengeluaran dan catatan kasbon lama. Format 1/2/3 tetap dapat dipulihkan. Keranjang, PIN, sesi dan pilihan printer tidak disertakan. Backup teks biasa harus disimpan privat **di luar HP kasir**.

Pemulihan mengganti data setelah konfirmasi. Dokumen tidak valid ditolak; kegagalan insert membatalkan seluruh pemulihan. Jangan menghapus data aplikasi tanpa backup.

**Backup sebelum memperbarui APK debug.** Build dari runner berbeda dapat memakai tanda tangan berbeda. Jika Android menolak update, ekspor data dari aplikasi lama sebelum uninstall, pasang APK baru lalu pulihkan backup lewat Admin. Untuk pemakaian rutin, gunakan release dengan keystore pemilik yang disimpan aman dan dipakai kembali saat pembaruan.

## Build dan verifikasi

Diperlukan JDK 17 dan Android SDK 36. Dari folder ini:

```sh
bash gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows: `gradlew.bat testDebugUnitTest lintDebug assembleDebug`. Hasil: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions **Android offline APK** mengunggah artifact APK debug, laporan pengujian dan pratinjau HP. Unduh ZIP, ekstrak lalu pasang APK. Android 7+ memerlukan versi Android System WebView yang mendukung JavaScript modern.

Pengujian mencakup role/PIN/otorisasi native, migrasi menu, checkout/retry/pembatalan, catatan lama, pengeluaran/idempotensi/backup, validasi pemulihan dan struk. Playwright menguji aset lokal/CSP, login, batas navigasi kasir, transaksi, formulir produk dan pengeluaran, nominal dan tampilan HP. Tetap perlu uji perangkat fisik: mode pesawat, restart/tutup paksa, backup ke HP lain, izin Bluetooth ditolak, printer mati/kertas habis dan kertas 58/80 mm.

Batasan: satu HP; tidak ada sinkronisasi website/multi-perangkat, QRIS otomatis, supplier/resep atau buku audit penyesuaian stok. Stok pada Produk adalah jumlah tersedia saat ini.

## Pembayaran, nota dan preview (v2.3)

- Tunai: tombol **10k, 20k, 50k, 100k** mengisi nominal uang diterima, bukan menambahkannya. Uang pas dan input manual tetap tersedia; pembayaran kurang ditolak.
- QRIS: menggunakan QRIS toko yang sudah tersedia di luar aplikasi. Kasir wajib mencentang pembayaran sudah diterima. Nominal dibukukan tepat sesuai total tagihan dan tidak menghasilkan kembalian. Ini pencatatan manual, bukan integrasi payment gateway, pemindaian atau verifikasi bank otomatis.
- Pilih **Cetak nota** atau **Tanpa nota** sebelum bayar. Cetak hanya dimulai setelah penjualan tersimpan. Pemulihan pembayaran yang belum terkonfirmasi tidak otomatis mengulang cetak; gunakan tombol cetak nota pada detail jika diperlukan. Tanpa cetak tetap menyimpan transaksi dan stok.
- Admin dapat mengubah nama/alamat/nomor toko, pesan atas/bawah dan lebar 58/80 mm. Preview Tunai/QRIS memakai `Receipt.format`, sama dengan formatter printer, dan tidak mengubah pengaturan sebelum Simpan.
- Printer, tes cetak, lebar kertas dan preview ditolak di bridge native untuk kasir. Kasir tidak bisa mengubah printer atau ukuran kertas dari detail struk.
- UI menghapus label Offline siap, menambah panel pembayaran berwarna, nominal cepat, konfirmasi QRIS, pilihan nota, dan preview kertas. Semua aset tetap dibundel untuk penggunaan tanpa internet.

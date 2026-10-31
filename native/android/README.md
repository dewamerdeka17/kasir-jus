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

## Laporan Excel (v2.4)

Admin membuka Laporan, memilih 1 hari / 7 hari / 1 bulan kalender, memilih tanggal acuan, lalu menekan Ekspor laporan Excel (.xlsx). Periode 7 hari berakhir pada tanggal acuan dan mencakup enam hari sebelumnya. Bulan kalender mencakup tanggal pertama sampai terakhir bulan pilihan. Semua waktu laporan memakai WIB. Pilih lokasi simpan melalui pemilih file Android; ekspor tetap bekerja tanpa internet dan hanya diizinkan untuk admin.

Workbook menggunakan template XLSX asli buatan Artifact Tool yang dibundel di `assets/reports/report-template.xlsx`. Angka, tanggal dan jam bertipe numerik, nominal berformat rupiah, header dibekukan, dan tabel dapat difilter. Sheet Ringkasan berisi omzet, transaksi, unit, Tunai/QRIS, pengeluaran, best seller dan tanggal teramai. Harian memuat rincian hari beserta formula; Best Seller mengurutkan produk berdasarkan unit lalu subtotal item. Rincian Tunai dan QRIS berada pada sheet terpisah. Pengeluaran menyertakan catatan batal dengan status; Transaksi Lain memuat transaksi batal dan kasbon lama, agar tidak salah dihitung sebagai Tunai atau QRIS.

Best seller memakai jumlah unit pada transaksi sah. Subtotal produk ditampilkan sebelum diskon nota; omzet memakai total nota setelah diskon. Tanggal teramai memakai jumlah transaksi sah, lalu omzet terbesar, kemudian tanggal paling awal jika seri. Periode kosong tidak membuat produk/tanggal teramai fiktif. Semua transaksi periode diambil dari database, tanpa batas 500 pada riwayat layar. Formula ringkasan dan harian memakai rincian; ranking best seller/tanggal teramai merupakan snapshot saat ekspor, bukan ranking dinamis untuk data baru yang ditambahkan manual di Excel.

HPP dihapus dari tampilan dan formulir Produk. Harga/modal historis di database dan backup tidak dihapus; mengedit produk tanpa HPP tidak mengubah modal lama. Ekspor tidak memuat HPP atau laba berbasis HPP. Angka penjualan dikurangi pengeluaran diberi label sesuai perhitungannya, bukan laba bersih.

## Remote monitoring (v2.5)

The private dashboard is the previous owner-private Site (ChatGPT sign-in). From its **Koneksi HP** screen download a connection file, then on the same phone choose **Admin → Setelan → Pantau dari jauh → Pilih file koneksi**. Initial connection expires in 24 hours; delete the downloaded file after importing it. Making a new file revokes the previous writer. Cloud data remains viewable after revocation.

Uploads run on a serialized background executor while the activity is open, on launch, roughly every 60 seconds, and after ledger changes. No Android background service is promised: closing/force-stopping the app stops uploads until reopened. Network errors never roll back or block a checkout. Admin can force an upload or disconnect locally; remotely revoke on the dashboard to invalidate its device writer. TLS only, no redirects, keys encrypted with Android Keystore AES-GCM, keys excluded from backups/source/APK. SQLite is the only writer; remote monitoring never changes stock, receipts, or local transactions. One phone is the primary ledger. Importing an older backup replaces the cloud snapshot on the next upload, matching the local restore semantics.

Snapshots include all sales, expenses and products, including cancellations and archived records; no HPP, PIN, customer contact or receipt bodies are sent. Upload size is bounded to 12 MB. Server validates totals and IDs, records receipt time and snapshot time, and rejects stale upload revisions; retrying cannot duplicate sales because the uploaded snapshot replaces the prior snapshot rather than appending it. Dashboard XLSX reports use the same styled template and WIB report conventions as the APK.

## Vercel delta synchronization (v3.0)

The new connection format (`version: 2`) accepts HTTPS Vercel origins and a per-device writer key without any ChatGPT service credential. The APK remains compatible with existing version-1 Sites configurations.

For Vercel, the phone first reads the acknowledged server revision, then compares canonical hashes of all locally authoritative records with its last acknowledged checkpoint. Only changed products/sales/expenses and removal tombstones are transmitted in bounded batches (at most 100 records and about 250 KB). An atomic server commit exposes the whole update at once. The checkpoint is saved locally only after the exact revision is acknowledged. Failed sends leave it unchanged; retries upsert stable record IDs rather than appending duplicates. Lost checkpoint/uncertain acknowledgment triggers a full atomic reset on the next send, so restoring older backups also removes cloud records no longer in the local ledger. No local sale or expense is deleted after transfer.

The independent Next.js backend and SQL migration are in `remote-monitor/`. Its production database connection is active. Open https://jusmerdeka-monitor.vercel.app, sign in with the existing admin web account, and download a new version-2 connection file for APK 3.0. Existing Sites connection files continue to use the prior full-snapshot endpoint.

## Bluetooth print delivery (v3.0.1)

Print jobs now pause after ESC/POS initialization, send small paced chunks, and keep the RFCOMM socket open briefly after flushing. The connection is no longer closed immediately after a single write. The complete job has a bounded timeout; uncertain/partial writes are never automatically resent because that could duplicate a receipt. The UI explicitly says data delivery does not confirm physical paper output. Model-specific Iware compatibility still requires the exact printer model and a physical test; no hardware acknowledgement is claimed.

## Printer setup UI (v3.1)

Admin printer setup is now a guided in-app picker: Bluetooth/permission readiness, paired device selection with an explicit save button, then a paper test. No device is silently selected. Saved device name, address suffix, and current selection are shown, with actionable empty/permission/Bluetooth-off states and a settings shortcut. Returning from Android Bluetooth settings refreshes the picker. Only paired devices can be saved, and changing/clearing selection is blocked during a print job.

Test delivery and physical output are separate states. After a test is sent, the admin must choose whether the paper came out and was readable. An explicit confirmation is labelled as such, not as a hardware acknowledgement. Changing printer invalidates old test confirmations, and stale test callbacks cannot mark another printer as verified. Cashiers cannot access the picker, device list, Android Bluetooth settings shortcuts, selection or test confirmation actions.

# Uji Tambahan: Versi 1.0.2 (versionCode 10002)

Untuk: uji di Redmi Pad 2 dengan APK rilis bertanda tangan. Melengkapi `UJI-TABLET-1.0.1.md`; skenario di sana tetap berlaku.
APK laundry: `D:\Android\A3-releases\1.0.2-code10002\app-laundry-release.apk`.
APK cafe: `D:\Android\A3-releases\1.0.3-code10003\app-cafe-release.apk` (cafe 1.0.2 digantikan 1.0.3 sebelum pernah dipasang; jangan pakai `app-cafe-release.apk` di folder 1.0.2).
Kandidat terbaru: cafe 1.0.5 di `D:\Android\A3-releases\1.0.5-code10005\` (memperbaiki S3: offline dengan shift berjalan tidak lagi mengunci semua akun) dan laundry 1.0.9 di `D:\Android\A3-releases\laundry-1.0.9-code10009\` (memperbaiki status WhatsApp yang selalu "Belum Dikirim" dan menambah keterangan percobaan kirim; lihat bagian 3b). Keduanya menambah pembaruan mandiri; skenario di sini tetap berlaku untuknya. Uji pembaruan: lihat `PANDUAN-UPDATE-MANDIRI.md` bagian D.
Cek `SHA256SUMS.txt` di tiap folder. Tag git: `cafe-v1.0.3`, `laundry-v1.0.2`.
Kandidat berikutnya: cafe 1.0.6 di `D:\Android\A3-releases\cafe-1.0.6-code10006\` dan laundry 1.0.10 di `D:\Android\A3-releases\laundry-1.0.10-code10010\` (kas awal standar 0 didukung; lihat bagian 3c).
Kandidat berikutnya: cafe 1.0.7 di `D:\Android\A3-releases\cafe-1.0.7-code10007\` dan laundry 1.0.11 di `D:\Android\A3-releases\laundry-1.0.11-code10011\` (izin printer; lihat bagian 3d).

Perubahan sejak 1.0.1: perbaikan tampilan POS cafe (UI-1), pesan PIN manager yang jelas (UI-2), jenis bayar di riwayat cafe dan laundry (UI-3), kunci layar otomatis saat idle (AUT-05), log diagnostik sesi. Rumus uang dan sinkron tidak berubah.

## 1. Pemasangan sebagai update

Kunci tanda tangan sama dengan 1.0.1, sehingga bisa menimpa tanpa uninstall:

```
adb install -r app-cafe-release.apk
adb install -r app-laundry-release.apk
```

Jika muncul `INSTALL_FAILED_USER_RESTRICTED`, lihat layar tablet dan ketuk Install/Izinkan, lalu ulangi.

| # | Periksa | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| P1 | Versi di Info aplikasi | Cafe 1.0.3, laundry 1.0.2 | |
| P2 | Data lama masih ada (shift terbuka, antrean sinkron, riwayat) | Tidak hilang setelah update | |

## 2. Cafe

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| T1 | Pesanan dine-in 2 item (25.000 dan 20.000) dengan diskon preset 10% | Daftar item terlihat jelas; total 44.600 | |
| T2 | Diskon manual: PIN manager salah | Pesan "PIN Manager salah. Sisa N percobaan" | |
| T3 | Salah 3 kali | Pesan terkunci dengan sisa menit; PIN benar pun ditolak selama terkunci | |
| T4 | Tablet belum pernah sinkron akun manager | Pesan akun manager belum tersedia | |
| T5 | Void atau refund dengan PIN salah | Pesan yang sama jelasnya | |
| T6 | Riwayat Selesai | Setiap kartu menampilkan Tunai atau QRIS | |

## 3. Laundry

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| T7 | Daftar pesanan: lunas tunai, DP, lunas QRIS | Label pembayaran menyebut jenis, mis. "Lunas · Tunai", "DP · QRIS" | |
| T8 | Pesanan belum dibayar | Label "Belum Lunas" tanpa jenis | |

## 3b. Status WhatsApp laundry (versi 1.0.4)

Sebelum 1.0.4, layar pesanan membaca status tanpa token login, sehingga selalu tampil "Belum Dikirim". Perlu internet.

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| W1 | Buka pesanan Siap atau Diambil, ketuk ikon segarkan di kartu WhatsApp | Status berubah sesuai server: WA Terkirim, WA Antrean, WA Gagal, atau Belum Dikirim (server memang tidak punya catatan) |  |
| W2 | Matikan Wi-Fi, ketuk segarkan | "Status Tak Terbaca" dengan keterangan "Gagal memeriksa status ke server"; bukan "Belum Dikirim" |  |
| W3 | Bandingkan W1 dengan kenyataan: tanyakan pelanggan apakah pesan WhatsApp masuk | Status di tablet sama dengan kenyataan. Bila server mengatakan terkirim tapi pelanggan tidak menerima, atau sebaliknya, catat nomor pesanan |  |
| W4 | Jika masih "Status Tak Terbaca" saat online | Catat kode yang tampil (mis. kode 401, 403, 404, 500) dan foto layar; kode itu menunjukkan sisi mana yang bermasalah |  |
| W6 | (1.0.5) Pesan berstatus WA Antrean: lihat keterangan di samping status | "belum pernah dicoba kirim" (pengirim di server tidak jalan) atau "N percobaan, terakhir ..." (sudah dicoba, cek galat di server). Catat teks yang tampil dan tempel ke laporan untuk pengelola backend |  |
| W7 | (1.0.6) Buat pesanan laundry untuk pelanggan A (nomor HP terisi). Lalu di pesanan baru, cari nama A lewat pencarian pelanggan (online) dan pilih. Buka lagi pesanan pertama | Nama dan nomor HP A di pesanan pertama tetap utuh; tombol "Kirim via WA" masih ada. Pada 1.0.5 ke bawah nama berubah jadi "Pelanggan Athia" dan nomor hilang |  |
| W8 | (1.0.7) Pasang, buka laundry, tunggu beberapa detik, buka pesanan yang tadinya "Pelanggan Athia" (mis. L3-0002) | Nama dan nomor HP pelanggan kembali, tombol "Kirim via WA" muncul. Bila tetap "Pelanggan Athia", jalankan `adb logcat -d \| Select-String "OrderCustomerRepair"` dan lapor |  |
| W9 | (1.0.8) Pesanan baru, tab Pelanggan Baru: ketik nomor HP pelanggan yang sudah terdaftar (mis. nomor abul) | Muncul "Nomor ini sudah terdaftar atas nama ...", tombol berubah "Pakai Pelanggan Ini". Setelah dipilih, pesanan lama pelanggan itu tetap utuh (nama, nomor, tombol WA). Nomor yang belum terdaftar tetap bisa dibuat baru seperti biasa |  |
| W10 | (1.0.9) Buat pesanan, bayar (tunai atau QRIS), di layar "Pembayaran Berhasil" ketuk Selesai | Kembali ke layar Order yang kosong (pesanan baru). Menu Cucian lalu bisa dibuka dan menampilkan daftar, bukan layar pembayaran |  |
| W11 | (1.0.9) Dari menu Cucian, terima pelunasan, lalu ketuk Selesai | Kembali ke menu Cucian |  |
| W12 | (1.0.9) Di layar Pembayaran, ketuk tab menu lain (mis. Cucian) tanpa menekan Selesai, lalu buka tab Order dan Cucian bergantian | Tidak pernah muncul layar "Pembayaran Berhasil" yang lama |  |
| W13 | (1.0.9) Menu Order, kolom "Cari layanan": ketik sebagian nama (mis. "bed"), lalu dua kata (mis. "cuci express"), lalu kata yang tidak ada | Daftar layanan menyempit, ada keterangan "N dari M layanan"; kata yang tidak ada menampilkan pesan "tidak ditemukan"; tombol Hapus mengembalikan semua layanan; layanan yang sudah dipilih tetap terpilih |  |
| W5 | Cari pelanggan lewat pencarian di pesanan baru (online) | Hasil muncul (koneksi kini membawa token login) |  |

## 3c. Kas awal standar 0 (cafe 1.0.6, laundry 1.0.10)

Owner mengatur Kas awal standar = 0 di web (Pengaturan, Kas) untuk kedua usaha. Sebelum versi ini, tablet mengabaikan 0 dan memakai Rp200.000 bawaan.

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| KA1 | Atur standar 0 di web, sinkronkan tablet, buka layar Buka Shift (cafe dan laundry) | "Standar Kas Awal" Rp0, total Rp0, tombol buka shift aktif tanpa mengisi pecahan |  |
| KA2 | Dengan standar 0, isi pecahan lebih dari 0 | Diminta alasan selisih; setelah diisi shift bisa dibuka |  |
| KA3 | Buka shift dengan kas awal 0, lakukan transaksi tunai, tutup shift | Kas seharusnya = penjualan tunai saja; sama dengan server dan Laporan Z |  |
| KA4 | Kembalikan standar ke 200000 di web, sinkron, buka Buka Shift lalu kosongkan semua pecahan | Standar kembali Rp200.000; kas awal 0 ditolak |  |

## 3d. Izin printer Bluetooth (cafe 1.0.7, laundry 1.0.11)

Penyebab "Printer Putus" di laundry: izin Perangkat di sekitar ditolak permanen, dan tanpa izin SCAN koneksi gagal karena cancelDiscovery. Uji dengan mencabut izin lewat adb (aplikasi akan tertutup, buka lagi).

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| PB1 | Cabut izin CONNECT: adb shell pm revoke <paket> android.permission.BLUETOOTH_CONNECT, buka aplikasi | Muncul dialog "Izin printer belum aktif" dengan Buka Pengaturan dan Nanti |  |
| PB2 | Ketuk Buka Pengaturan, aktifkan Perangkat di sekitar, kembali ke aplikasi | Status printer berubah ke Printer Siap tanpa membuka ulang aplikasi |  |
| PB3 | Beri CONNECT tetapi cabut SCAN (pm revoke ... BLUETOOTH_SCAN), buka aplikasi, ketuk status printer, cetak satu struk | Printer Siap dan struk tercetak (versi lama gagal di sini) |  |
| PB4 | Ulangi PB1 sampai PB3 di cafe dan laundry (paket id.a3coffeeshop dan id.athialoundry) | Perilaku sama di keduanya |  |

## 4. Kunci layar otomatis (cafe dan laundry)

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| K1 | Login, isi keranjang, diamkan 2 menit | Pindah ke pilih akun dan PIN; setelah PIN benar keranjang utuh | |
| K2 | Sama dengan K1, Wi-Fi mati | Tetap terkunci 2 menit; hanya kasir terakhir login yang bisa dibuka | |
| K3 | Tekan Home, tunggu lebih dari 2 menit, buka aplikasi lagi | Meminta PIN | |
| K4 | Tekan Home, hentikan paksa aplikasi, tunggu lebih dari 2 menit, buka lagi | Meminta PIN | |
| K5 | Ulangi K4 dengan jeda kurang dari 2 menit | Langsung masuk tanpa PIN | |
| K6 | Sentuh layar berkala selama 5 menit | Tidak terkunci | |

## 5. Kunci layar saat shift berjalan (cafe)

Transaksi dalam satu shift tercatat atas nama pembuka shift. Karena itu, selama shift berjalan hanya pembuka shift atau manager/owner yang boleh membuka kunci.

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| S1 | Kasir A buka shift, layar terkunci idle, lihat daftar akun | Hanya A, manager, dan owner yang tampil; kasir B tidak |  |
| S2 | Manager membuka kunci lalu bertransaksi | Berhasil; transaksi tercatat atas nama A (pembuka shift); persetujuan PIN tercatat atas nama manager |  |
| S3 | Shift terbuka, Wi-Fi mati, layar terkunci idle (atau aplikasi dibuka ulang) | Akun cadangan (kasir terakhir login online) tetap tampil dan bisa masuk dengan PIN-nya, agar tablet tidak buntu; pembatasan pembuka shift hanya berlaku saat daftar akun diambil dari server. Versi 1.0.4 ke bawah gagal di sini (tak ada akun yang tampil) |  |
| S4 | Tutup shift, lalu kunci layar | Semua akun boleh masuk lagi |  |

## 6. Jika ada yang gagal

Catat nomor skenario, langkah, hasil nyata, dan foto layar. Untuk masalah sesi atau kunci layar, ambil log segera:

```
adb logcat -d | Select-String -Pattern "AuthDiag|MasterDataSync"
```

Hentikan dan laporkan bila: data hilang setelah update, total berbeda dari server, atau layar terbuka tanpa PIN setelah lewat 2 menit.

## 7. Hasil

| Tanggal | Penguji | Perangkat | Versi | Lulus | Catatan |
| --- | --- | --- | --- | --- | --- |
| | | | 1.0.2 (10002) | / 20 | |

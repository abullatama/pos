# Uji Tambahan: Versi 1.0.2 (versionCode 10002)

Untuk: uji di Redmi Pad 2 dengan APK rilis bertanda tangan. Melengkapi `UJI-TABLET-1.0.1.md`; skenario di sana tetap berlaku.
APK laundry: `D:\Android\A3-releases\1.0.2-code10002\app-laundry-release.apk`.
APK cafe: `D:\Android\A3-releases\1.0.3-code10003\app-cafe-release.apk` (cafe 1.0.2 digantikan 1.0.3 sebelum pernah dipasang; jangan pakai `app-cafe-release.apk` di folder 1.0.2).
Kandidat terbaru: `D:\Android\A3-releases\1.0.4-code10004\` (cafe 1.0.4, laundry 1.0.3) menambah pembaruan mandiri; skenario di sini tetap berlaku untuknya. Uji pembaruan: lihat `PANDUAN-UPDATE-MANDIRI.md` bagian D.
Cek `SHA256SUMS.txt` di tiap folder. Tag git: `cafe-v1.0.3`, `laundry-v1.0.2`.

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
| S3 | Wi-Fi mati, kasir terakhir login bukan pembuka shift dan bukan manager | Pesan "Shift sedang dibuka oleh ..."; tidak bisa masuk sampai internet pulih atau manager datang |  |
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

# Panduan Pembaruan Aplikasi Mandiri (OPS-03)

Berlaku untuk cafe (A3 Coffeeshop) dan laundry (Athia Laundry) pada satu tablet. Pembaruan dikirim pengembang lewat tautan Google Drive; owner memasangnya dari Menu Admin. Tidak perlu akun Google di tablet.

## A. Untuk owner: cara memasang pembaruan

Lakukan di luar jam ramai, dan hanya setelah pengembang memberi tahu ada versi baru.

1. **Tutup shift** di aplikasi (cafe dan laundry), dan pastikan indikator sinkron hijau (tidak ada yang "Menunggu sinkron"). Pembaruan ditolak bila ada shift berjalan atau antrean belum kosong.
2. Buka tautan Drive dari pengembang di **Chrome** (jangan akun lain), lalu ketuk **Unduh**. Ada dua berkas: `app-cafe-release.apk` dan `app-laundry-release.apk`. Unduh yang diminta pengembang.
3. Buka aplikasi yang akan diperbarui (misal cafe) lalu **Menu Admin**, masukkan PIN owner.
4. Pada kolom **Pembaruan Aplikasi** ketuk **Pasang Pembaruan APK**, lalu pilih berkas APK yang tadi diunduh (biasanya di folder Download).
5. Layar menampilkan ringkasan, misalnya "Terpasang 1.0.3, akan menjadi 1.0.4". Android lalu meminta konfirmasi: ketuk **Perbarui / Install**.
6. Aplikasi menutup dan terbuka lagi dengan versi baru. Login seperti biasa. Data dan transaksi tidak hilang.
7. Ulangi untuk aplikasi kedua dengan berkas miliknya.
8. Jika mode kios (pin layar) tadi aktif, aplikasi mematikannya sementara. **Aktifkan lagi** dari Menu Admin setelah selesai.

### Pesan yang mungkin muncul dan artinya
| Pesan | Yang dilakukan |
| --- | --- |
| "Update diblokir: shift aktif" atau "antrean belum kosong" | Tutup shift, tunggu sinkron hijau, coba lagi |
| "Berkas ini untuk aplikasi Athia Laundry, bukan A3 Coffeeshop" | Salah aplikasi. Buka Menu Admin di aplikasi yang sesuai berkas |
| "Versi ... sudah terpasang" | Tidak perlu dipasang lagi |
| "Berkas ini versi lebih lama" | Jangan dipasang. Minta berkas terbaru dari pengembang |
| "Bukan berkas resmi dari pengembang" | Jangan dipasang. Hubungi pengembang |
| "Berkas bukan aplikasi yang valid atau rusak" | Unduh ulang dari Drive |
| "Izin memasang pembaruan belum aktif" | Aktifkan "Izinkan dari sumber ini" pada layar yang terbuka, lalu ulangi. Bila bingung, hubungi pengembang |

## B. Untuk pengembang: menyiapkan tablet (sekali)

1. Pasang versi pertama yang memuat fitur ini dengan `adb install -r` untuk **kedua** aplikasi. Versi sebelumnya tidak punya izin pemasangan, jadi tidak bisa memasang dirinya sendiri.
2. Aktifkan izin untuk kedua aplikasi: **Pengaturan > Aplikasi > Akses aplikasi khusus > Instal aplikasi tidak dikenal**, pilih **A3 Coffeeshop** dan **Athia Laundry**, aktifkan **Izinkan dari sumber ini**. Atau buka Menu Admin dan coba memasang sekali; layar pengaturan akan terbuka sendiri.
3. Pastikan Chrome ada di tablet dan bisa mengunduh dari Drive. Uji unduh satu berkas.
4. Uji sekali pemasangan nyata (bagian D) sebelum tablet diserahkan.

## C. Untuk pengembang: setiap rilis baru

1. Naikkan `versionCode` dan `versionName` di `app-cafe/build.gradle.kts` dan/atau `app-laundry/build.gradle.kts`. `versionCode` wajib lebih tinggi dari yang terpasang.
2. Jalankan seluruh tes, lalu bangun APK rilis. Tanda tangan harus memakai keystore yang sama (Android menolak yang lain).
3. Arsipkan ke `D:\Android\A3-releases\<versi>-code<kode>\` dengan `SHA256SUMS.txt` dan `mapping`. Beri tag git.
4. Uji sebagai update dari versi sebelumnya dengan data nyata di satu tablet (pilot) sebelum dikirim ke owner.
5. Unggah kedua APK ke folder Drive pribadi, bagikan tautan **hanya ke owner**. Kirim juga nomor versi dan nilai `SHA256` lewat WhatsApp agar owner dapat memastikan berkas benar.
6. Beri tahu owner kapan boleh memasang (di luar jam ramai).
7. Bila rilis bermasalah, tidak ada downgrade: buat rilis perbaikan dengan `versionCode` lebih tinggi.

## D. Uji pemasangan di tablet (bagian dari uji 1.0.4)

Butuh dua versi: satu terpasang, satu lebih tinggi (boleh versi uji dengan `versionCode` dinaikkan satu).

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| U1 | Unduh APK dari Drive lewat Chrome di tablet | Berkas ada di Download | |
| U2 | Menu Admin: ketuk Pasang Pembaruan APK dengan shift terbuka | Diblokir dengan alasan jelas | |
| U3 | Tutup shift, sinkron hijau, pilih APK versi lebih tinggi | Ringkasan "Terpasang X, akan menjadi Y", dialog Android muncul, terpasang | |
| U4 | Setelah U3 | Versi baru, data lama (riwayat, antrean) masih ada | |
| U5 | Pilih APK laundry di Menu Admin cafe | Ditolak: "untuk aplikasi Athia Laundry, bukan A3 Coffeeshop" | |
| U6 | Pilih APK dengan versi sama atau lebih rendah | Ditolak dengan pesan | |
| U7 | Mode kios aktif lalu pasang | Kios dimatikan sementara; dialog Android tetap muncul | |
| U8 | Izin "tidak dikenal" dimatikan lalu pasang | Layar pengaturan terbuka dengan pesan jelas | |
| U9 | Pilih berkas bukan APK atau rusak | Pesan berkas rusak | |

Catat hasil dan foto layar. Bila dialog pemasang tidak muncul saat kios aktif (U7), laporkan: bisa jadi mode kios harus dimatikan manual sebelum update.
